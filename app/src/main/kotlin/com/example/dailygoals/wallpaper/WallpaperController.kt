package com.example.dailygoals.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import com.example.dailygoals.R
import com.example.dailygoals.engine.FailedGoal
import com.example.dailygoals.engine.WallpaperMode
import com.example.dailygoals.engine.WallpaperRules
import com.example.dailygoals.engine.WarningWallpaper
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Puts the warning wallpaper on and takes it off, through the live wallpaper when it is set up and otherwise through one still picture. */
class WallpaperController(private val context: Context, val store: WallpaperStore) {
    private val manager: WallpaperManager = WallpaperManager.getInstance(context)

    fun serviceComponent() = ComponentName(context, SkritiWallpaperService::class.java)

    /** True when this app's live wallpaper is the current wallpaper. */
    fun liveActive(): Boolean = manager.wallpaperInfo?.component == serviceComponent()

    fun setupIntent(): Intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, serviceComponent())

    /** The failed goals today become the warning. Safe to call often; nothing is rewritten when nothing changed. */
    fun refresh(failed: List<FailedGoal>, nowMillis: Long, nextMidnightMillis: Long) {
        val state = WallpaperRules.state(failed, store.restore, nowMillis, nextMidnightMillis)
        if (state != store.warning) store.warning = state
        val useStatic = store.mode == WallpaperMode.STATIC || !liveActive()
        if (useStatic) applyStatic(state) else if (store.staticApplied) restoreStatic()
    }

    private fun flags(): Int = WallpaperManager.FLAG_SYSTEM or if (store.includeLockScreen) WallpaperManager.FLAG_LOCK else 0

    private fun applyStatic(state: WarningWallpaper?) {
        if (state == null) {
            if (store.staticApplied) restoreStatic()
            return
        }
        // Never leave the user on the warning picture with nothing to go back to.
        if (!store.hasNormalWallpaper) return
        if (store.staticApplied && store.staticKey == key(state)) return
        val metrics = context.resources.displayMetrics
        val bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
        val ends = Instant.ofEpochMilli(state.endsAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        val input = SceneInput(
            style = store.style,
            goalNames = state.goalNames,
            stake = state.stake,
            countdown = ends,
            fractionLeft = 0.5f,
            warn = 1f,
            rippleSeconds = 9f,
            nowMillis = 0L,
            still = true,
            sway = 0f,
            prefix = context.getString(R.string.wallpaper_prefix),
            suffix = context.getString(R.string.wallpaper_suffix_static),
            moreText = { context.getString(R.string.wallpaper_more, it) },
        )
        WarningScene().draw(Canvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat(), null, input)
        runCatching { manager.setBitmap(bitmap, null, true, flags()) }
            .onSuccess {
                store.staticApplied = true
                store.staticKey = key(state)
            }
        bitmap.recycle()
    }

    private fun restoreStatic() {
        val file = store.normalFile
        if (!file.exists()) {
            store.staticApplied = false
            return
        }
        runCatching { file.inputStream().use { manager.setStream(it, null, true, flags()) } }
            .onSuccess { store.staticApplied = false }
    }

    private fun key(state: WarningWallpaper) = state.goalNames.joinToString("|") + "#" + store.style.name

    /**
     * Copies the picked picture into private storage, scaled down, because Android does not let apps read the
     * current wallpaper back. Returns false only when the picture cannot be read or written.
     */
    fun saveNormalWallpaper(uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds the decoder returns no bitmap by design; only the size is wanted here.
        val opened = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
            true
        } ?: false
        if (!opened) {
            android.util.Log.w("SkritiWallpaper", "could not open the picture: $uri")
            return@runCatching false
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            android.util.Log.w("SkritiWallpaper", "the picture has no readable size: $uri")
            return@runCatching false
        }
        val target = maxOf(context.resources.displayMetrics.heightPixels, MIN_TARGET)
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= target) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching false
        try {
            FileOutputStream(store.normalFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        } finally {
            bitmap.recycle()
        }
        store.bumpNormalVersion()
        true
    }.onFailure { android.util.Log.w("SkritiWallpaper", "could not save the normal wallpaper", it) }.getOrDefault(false)

    private companion object {
        const val MIN_TARGET = 1600
    }
}
