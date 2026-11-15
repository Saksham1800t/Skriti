package com.example.dailygoals.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.dailygoals.engine.WallpaperRules
import com.example.dailygoals.engine.WallpaperStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Everything one frame needs. Times are in milliseconds; [warn] is 0 when calm and 1 when fully warning. */
class SceneInput(
    val style: WallpaperStyle,
    val goalNames: List<String>,
    val stake: String?,
    val countdown: String,
    val fractionLeft: Float,
    val warn: Float,
    val rippleSeconds: Float,
    val nowMillis: Long,
    val still: Boolean,
    val sway: Float,
    val prefix: String,
    val suffix: String,
    val moreText: (Int) -> String,
)

/**
 * The calm and warning scenes, drawn with Canvas only. The same code draws the live wallpaper, the in-app
 * preview and the static picture. Text stays in the open band under the icon rows and above the dock.
 */
class WarningScene {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val embers = List(54) { i -> Ember(rnd(i, 1), rnd(i, 2), rnd(i, 3), rnd(i, 4) * 6.2832f) }
    private val discs = List(7) { i -> Disc(0.1f + rnd(i, 5) * 0.8f, 0.12f + rnd(i, 6) * 0.76f, 0.15f + rnd(i, 7) * 0.85f, 40f + rnd(i, 8) * 70f) }

    private class Ember(val x: Float, val y: Float, val z: Float, val phase: Float)

    private class Disc(val x: Float, val y: Float, val z: Float, val r: Float)

    private fun rnd(i: Int, k: Int): Float {
        val x = sin(i * 12.9898 + k * 78.233) * 43758.5453
        return (x - kotlin.math.floor(x)).toFloat()
    }

    /** The calm state: the user's own wallpaper if one was stored, otherwise a quiet blue gradient. */
    fun drawCalm(canvas: Canvas, w: Float, h: Float, normal: Bitmap?, nowMillis: Long, sway: Float) {
        if (normal != null) {
            val scale = max(w / normal.width, h / normal.height)
            val dw = normal.width * scale
            val dh = normal.height * scale
            rect.set((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
            canvas.drawBitmap(normal, null, rect, null)
            return
        }
        fill.shader = LinearGradient(0f, 0f, w, h, Color.parseColor("#22304e"), Color.parseColor("#0b1020"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        for (i in 0 until 7) {
            fill.color = Color.argb((255 * (0.035 + 0.01 * (i % 3))).toInt(), 255, 255, 255)
            val cx = w * (0.15f + 0.13f * i) + sway * (6 + i * 3)
            val cy = h * (0.7f + 0.04f * sin(i + nowMillis / 6000.0).toFloat())
            canvas.drawCircle(cx, cy, (40 + i * 10) * w / 360f, fill)
        }
    }

    fun draw(canvas: Canvas, w: Float, h: Float, normal: Bitmap?, input: SceneInput) {
        drawCalm(canvas, w, h, normal, input.nowMillis, input.sway)
        if (input.warn <= 0.01f) return
        val layer = canvas.saveLayerAlpha(0f, 0f, w, h, (input.warn * 255).toInt().coerceIn(0, 255))
        drawWarning(canvas, w, h, input)
        canvas.restoreToCount(layer)
        if (!input.still && input.rippleSeconds < 1.2f) {
            val a = max(0f, 1f - input.rippleSeconds / 1.2f)
            stroke.color = Color.argb((178 * a).toInt(), 255, 90, 110)
            stroke.strokeWidth = 5f * w / 360f
            stroke.clearShadowLayer()
            for (i in 0 until 2) {
                canvas.drawCircle(w / 2, h * 0.5f, max(0f, input.rippleSeconds * 620f * w / 360f - i * 60f), stroke)
            }
        }
    }

    private fun drawWarning(canvas: Canvas, w: Float, h: Float, input: SceneInput) {
        val k = w / 360f
        val t = input.nowMillis.toDouble()
        val mov = if (input.still) 0f else 1f
        val sway = input.sway
        val cx = w / 2
        val cy = h * 0.5f
        val left = input.fractionLeft.coerceAtLeast(0.02f)
        when (input.style) {
            WallpaperStyle.PULSE -> {
                fill.color = Color.parseColor("#10050a"); canvas.drawRect(0f, 0f, w, h, fill)
                val b = if (input.still) 0.6f else 0.5f + 0.5f * sin(t / 1400.0).toFloat()
                fill.shader = RadialGradient(cx + sway * 14 * k, cy, 360f * k, intArrayOf(argb(0.55f + 0.25f * b, 255, 70, 95), argb(0.32f, 190, 25, 55), argb(0f, 16, 5, 10)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, fill); fill.shader = null
                val n = 9
                for (i in 0 until n) {
                    val z = if (input.still) (i + 0.5f) / n else (((t / 7000.0) + i.toDouble() / n) % 1.0).toFloat()
                    val r = (14f + z.toDouble().pow(2.3).toFloat() * 470f) * k
                    stroke.color = argb(sin(PI * z).toFloat() * 0.5f, 255, 95, 120)
                    stroke.strokeWidth = (1f + z * 6f) * k
                    stroke.clearShadowLayer()
                    rect.set(cx + sway * (10f + 60f * z) * mov * k - r, cy + 10f * z * k - r * 0.92f, cx + sway * (10f + 60f * z) * mov * k + r, cy + 10f * z * k + r * 0.92f)
                    canvas.drawOval(rect, stroke)
                }
                ring(canvas, cx, h * 0.69f, (56f + 40f * left) * k, left, 4f * k, Color.parseColor("#ff6a84"), 14f * k)
            }
            WallpaperStyle.EMBERS -> {
                fill.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.parseColor("#0e0508"), Color.parseColor("#22090d"), Color.parseColor("#5a1810")), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, fill)
                fill.shader = RadialGradient(cx, h + 40f * k, 380f * k, argb(0.55f, 255, 120, 40), argb(0f, 255, 120, 40), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, fill); fill.shader = null
                for (layer in 0..2) {
                    for (e in embers) {
                        if ((e.z * 3).toInt().coerceAtMost(2) != layer) continue
                        val speed = 0.35f + layer * 0.55f
                        val y = if (input.still) e.y else ((((e.y - (t / 11000.0) * speed) % 1.0) + 1.0) % 1.0).toFloat()
                        val x = e.x * w + (if (input.still) 0f else sin(t / 1100.0 + e.phase).toFloat() * (6f + layer * 6f) * k) + sway * (8f + layer * 26f) * mov * k
                        val size = (1.2f + layer * 2.2f) * (if (layer == 2) 2.2f else 1f) * k
                        val fade = min(1f, y * 1.5f) * (0.35f + layer * 0.3f)
                        fill.color = argb(fade, 255, if (layer == 2) 177 else 138, if (layer == 2) 92 else 61)
                        if (layer == 2) fill.setShadowLayer(14f * k, 0f, 0f, argb(fade, 255, 138, 61)) else fill.clearShadowLayer()
                        canvas.drawCircle(x, y * h, size, fill)
                    }
                }
                fill.clearShadowLayer()
            }
            WallpaperStyle.GLITCH -> {
                fill.color = Color.parseColor("#07070f"); canvas.drawRect(0f, 0f, w, h, fill)
                val hz = h * 0.42f
                fill.shader = LinearGradient(0f, hz - 60f * k, 0f, hz + 60f * k, intArrayOf(argb(0f, 255, 40, 100), argb(0.35f, 255, 40, 100), argb(0f, 255, 40, 100)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, hz - 60f * k, w, hz + 60f * k, fill); fill.shader = null
                stroke.color = argb(0.55f, 255, 60, 120); stroke.strokeWidth = 1f * k; stroke.clearShadowLayer()
                for (i in -14..14) canvas.drawLine(cx + sway * 20f * mov * k + i * 6f * k, hz, cx + sway * 30f * mov * k + i * 92f * k, h, stroke)
                val off = if (input.still) 0f else ((t / 1800.0) % 1.0).toFloat()
                for (i in 0 until 12) {
                    val z = (i + off) / 12f
                    val y = hz + z.toDouble().pow(2.2).toFloat() * (h - hz)
                    stroke.color = argb(min(1f, z * 1.4f) * 0.7f * 0.55f, 255, 60, 120)
                    canvas.drawLine(0f, y, w, y, stroke)
                }
                fill.color = argb(0.07f, 255, 59, 107)
                var y = 0f
                while (y < h) { canvas.drawRect(0f, y, w, y + 2f * k, fill); y += 5f * k }
                fill.color = Color.parseColor("#07070f"); canvas.drawRect(0f, 0f, w, hz - 120f * k, fill)
            }
            WallpaperStyle.MINIMAL -> {
                fill.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#3b1019"), Color.parseColor("#1a060b"), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, fill); fill.shader = null
                for (d in discs.sortedBy { it.z }) {
                    val x = d.x * w + sway * 70f * d.z * mov * k + (if (input.still) 0f else sin(t / 3000.0 + d.r).toFloat() * 8f * d.z * k)
                    val y = d.y * h + (if (input.still) 0f else cos(t / 3600.0 + d.r).toFloat() * 9f * d.z * k)
                    val r = d.r * (0.6f + d.z) * k
                    fill.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r, argb(0.10f + 0.18f * d.z, 255, 120, 140), argb(0f, 255, 90, 120), Shader.TileMode.CLAMP)
                    canvas.drawCircle(x, y, r, fill)
                }
                fill.shader = null
                ring(canvas, cx, h * 0.69f, 70f * k, 1f - left, 8f * k, Color.parseColor("#ff7088"), 10f * k)
            }
        }
        words(canvas, w, h, input, mov)
    }

    private fun ring(canvas: Canvas, cx: Float, cy: Float, r: Float, frac: Float, width: Float, color: Int, glow: Float) {
        stroke.strokeWidth = width
        stroke.clearShadowLayer()
        stroke.color = argb(0.14f, 255, 255, 255)
        canvas.drawCircle(cx, cy, r, stroke)
        stroke.color = color
        stroke.setShadowLayer(glow, 0f, 0f, color)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, -90f, 360f * max(0.002f, frac), false, stroke)
        stroke.clearShadowLayer()
    }

    private fun words(canvas: Canvas, w: Float, h: Float, input: SceneInput, mov: Float) {
        val k = w / 360f
        val cx = w / 2
        val names = WallpaperRules.displayNames(input.goalNames, input.moreText)
        val splitNow = input.style == WallpaperStyle.GLITCH && !input.still && (input.nowMillis % 4200) < 260
        fun pass(dx: Float, color: Int, glow: Float) {
            text.color = color
            if (glow > 0f) text.setShadowLayer(glow * k, 0f, 0f, color) else text.clearShadowLayer()
            text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            text.textSize = 21f * k; canvas.drawText(input.prefix, cx + dx, h * 0.5436f, text)
            text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            text.textSize = 27f * k
            names.forEachIndexed { i, name -> canvas.drawText(name, cx + dx, h * 0.59f + i * 34f * k, text) }
            text.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            text.textSize = 74f * k; canvas.drawText(input.countdown, cx + dx + input.sway * 4f * mov * k, h * 0.7564f, text)
            text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            text.textSize = 16f * k; canvas.drawText(input.suffix, cx + dx, h * 0.7949f, text)
            input.stake?.let {
                text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                text.textSize = 19f * k; canvas.drawText(it, cx + dx, h * 0.8564f, text)
            }
        }
        pass(0f, argb(0.5f, 0, 0, 0), 18f)
        if (splitNow) {
            pass(-3f * k, argb(0.75f, 255, 59, 107), 0f)
            pass(3f * k, argb(0.75f, 56, 232, 255), 0f)
        }
        pass(0f, Color.WHITE, 0f)
        text.clearShadowLayer()
    }

    private fun argb(alpha: Float, r: Int, g: Int, b: Int) = Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), r, g, b)

}
