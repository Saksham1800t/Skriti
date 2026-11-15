package com.example.dailygoals.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.dailygoals.R
import com.example.dailygoals.engine.WallpaperRules
import kotlin.math.sin

/**
 * The app's own live wallpaper. It has two states: calm (the user's stored wallpaper, drawn once and then
 * left alone) and warning (animated). The app flips between them itself through [WallpaperStore], so no
 * system prompt is needed after the one-time setup. It draws only while it is visible, about 30 frames a
 * second, and nothing runs while it is calm.
 */
class SkritiWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = SceneEngine()

    private inner class SceneEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val store = WallpaperStore(applicationContext)
        private val scene = WarningScene()
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var width = 0
        private var height = 0
        private var normal: Bitmap? = null
        private var normalLoaded = false
        private var warn = 0f
        private var rippleSeconds = 9f
        private var lastFrame = 0L
        private var sway = 0f
        private var wasWarning = false
        private var lastNames: List<String> = emptyList()
        private var lastStake: String? = null

        private val tick = object : Runnable {
            override fun run() {
                drawFrame()
                if (needsAnimation()) handler.postDelayed(this, if (reduceMotion()) STILL_TICK_MILLIS else FRAME_MILLIS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            store.prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            store.prefs.unregisterOnSharedPreferenceChangeListener(this)
            handler.removeCallbacksAndMessages(null)
            normal?.recycle()
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            if (key !in WATCHED_KEYS) return
            if (key == WallpaperStore.KEY_NORMAL_VERSION) normalLoaded = false
            restart()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) {
                lastFrame = 0L
                restart()
            } else {
                handler.removeCallbacks(tick)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            normalLoaded = false
            restart()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }

        private fun restart() {
            handler.removeCallbacks(tick)
            if (visible) handler.post(tick)
        }

        private fun activeWarning() = store.warning?.takeIf { it.endsAtMillis > System.currentTimeMillis() }

        private fun needsAnimation() = visible && (activeWarning() != null || warn > 0.01f)

        private fun reduceMotion(): Boolean =
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

        private fun ensureNormal() {
            if (normalLoaded || width == 0) return
            normalLoaded = true
            normal?.recycle()
            normal = if (store.hasNormalWallpaper) decode(width, height) else null
        }

        private fun decode(w: Int, h: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(store.normalFile.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
            return BitmapFactory.decodeFile(store.normalFile.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }

        private fun drawFrame() {
            if (width == 0) return
            ensureNormal()
            val now = System.currentTimeMillis()
            val state = activeWarning()
            val still = reduceMotion()
            val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.1f)
            lastFrame = now
            val goal = if (state != null) 1f else 0f
            if (state != null && !wasWarning) rippleSeconds = 0f
            wasWarning = state != null
            warn += (goal - warn) * (dt / if (still) 0.2f else 0.4f).coerceAtMost(1f)
            if (state == null && warn < 0.01f) warn = 0f
            if (still && state != null) warn = 1f
            rippleSeconds += dt
            sway = if (still) 0f else (sin(now / 2600.0) * 0.6).toFloat()
            if (state != null) {
                lastNames = state.goalNames
                lastStake = state.stake
            }
            val input = SceneInput(
                style = store.style,
                goalNames = lastNames,
                stake = lastStake,
                countdown = WallpaperRules.countdown((state?.endsAtMillis ?: now) - now),
                fractionLeft = WallpaperRules.fractionLeft(store.startedAtMillis, state?.endsAtMillis ?: now, now),
                warn = warn,
                rippleSeconds = rippleSeconds,
                nowMillis = now,
                still = still,
                sway = sway,
                prefix = getString(R.string.wallpaper_prefix),
                suffix = getString(R.string.wallpaper_suffix),
                moreText = { getString(R.string.wallpaper_more, it) },
            )
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            try {
                scene.draw(canvas, width.toFloat(), height.toFloat(), normal, input)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    companion object {
        private const val FRAME_MILLIS = 33L
        private const val STILL_TICK_MILLIS = 1000L
        private val WATCHED_KEYS = setOf(WallpaperStore.KEY_WARNING, WallpaperStore.KEY_STYLE, WallpaperStore.KEY_NORMAL_VERSION)
    }
}
