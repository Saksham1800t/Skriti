package com.example.dailygoals.wallpaper

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.WallpaperRules
import kotlin.math.sin

/** Shows the chosen warning style full screen, with sample goals, so it can be judged without failing a goal. Tap to close. */
class WallpaperPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = (application as SkritiApp).wallpaper
        setContentView(PreviewView(this, controller.store))
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private class PreviewView(context: Context, private val store: WallpaperStore) : View(context) {
    private val scene = WarningScene()
    private val startedAt = System.currentTimeMillis()
    private val total = 3_600_000L
    private val reduce = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    private val names = listOf(context.getString(R.string.wallpaper_sample_goal_1), context.getString(R.string.wallpaper_sample_goal_2))
    private val stake = context.getString(R.string.wallpaper_sample_stake)

    init {
        setOnClickListener { (context as ComponentActivity).finish() }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val now = System.currentTimeMillis()
        val elapsed = now - startedAt
        val warn = if (reduce) 1f else (elapsed / 800f).coerceIn(0f, 1f)
        val input = SceneInput(
            style = store.style,
            goalNames = names,
            stake = stake,
            countdown = WallpaperRules.countdown(total - elapsed * 20),
            fractionLeft = WallpaperRules.fractionLeft(0, total, elapsed * 20),
            warn = warn,
            rippleSeconds = elapsed / 1000f,
            nowMillis = now,
            still = reduce,
            sway = if (reduce) 0f else (sin(now / 2600.0) * 0.6).toFloat(),
            prefix = context.getString(R.string.wallpaper_prefix),
            suffix = context.getString(R.string.wallpaper_suffix),
            moreText = { context.getString(R.string.wallpaper_more, it) },
        )
        scene.draw(canvas, width.toFloat(), height.toFloat(), null, input)
        if (!reduce) postInvalidateOnAnimation()
    }
}
