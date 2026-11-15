package com.example.dailygoals.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.RemoteViews
import com.example.dailygoals.MainActivity
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.DayPlanner
import com.example.dailygoals.engine.PauseRules
import com.example.dailygoals.engine.WidgetModel
import com.example.dailygoals.engine.WidgetRules
import com.example.dailygoals.engine.WidgetState
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.notify.ActionReceiver
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/** Today's ring and the goals still open, with a tick to tap. Two sizes share one renderer. */
class TodayWidget4x2 : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}

class TodayWidget2x2 : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}

object WidgetUpdater {
    private const val MAX_ROWS = 4
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    /** Redraws every placed widget from today's plan. Safe to call whenever data changes. */
    fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val big = manager.getAppWidgetIds(ComponentName(context, TodayWidget4x2::class.java))
        val small = manager.getAppWidgetIds(ComponentName(context, TodayWidget2x2::class.java))
        if (big.isEmpty() && small.isEmpty()) return
        val app = context.applicationContext as SkritiApp
        app.scope.launch {
            val (model, dateText) = load(app)
            big.forEach { manager.updateAppWidget(it, bigViews(context, model, dateText)) }
            small.forEach { manager.updateAppWidget(it, smallViews(context, model)) }
        }
    }

    private fun bigViews(context: Context, model: WidgetModel, dateText: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today_4x2)
        views.setImageViewBitmap(R.id.ring, ring(context, model, 68))
        views.setTextViewText(R.id.date, dateText)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        val rowIds = intArrayOf(R.id.row_0, R.id.row_1, R.id.row_2, R.id.row_3)
        val tickIds = intArrayOf(R.id.tick_0, R.id.tick_1, R.id.tick_2, R.id.tick_3)
        val nameIds = intArrayOf(R.id.name_0, R.id.name_1, R.id.name_2, R.id.name_3)
        val missed = context.getColor(R.color.widget_missed)
        val text = context.getColor(R.color.widget_text)
        for (i in 0 until MAX_ROWS) {
            val row = model.rows.getOrNull(i)
            views.setViewVisibility(rowIds[i], if (row == null) android.view.View.GONE else android.view.View.VISIBLE)
            if (row == null) continue
            views.setTextViewText(nameIds[i], row.name)
            views.setTextColor(nameIds[i], if (row.state == WidgetState.MISSED) missed else text)
            views.setImageViewBitmap(tickIds[i], tick(context, row.state))
            if (row.state == WidgetState.PENDING) views.setOnClickPendingIntent(tickIds[i], done(context, row.goalId, model))
        }
        views.setViewVisibility(R.id.more, if (model.more > 0) android.view.View.VISIBLE else android.view.View.GONE)
        if (model.more > 0) views.setTextViewText(R.id.more, context.getString(R.string.widget_more, model.more))
        views.setViewVisibility(R.id.empty, if (model.total == 0) android.view.View.VISIBLE else android.view.View.GONE)
        return views
    }

    private fun smallViews(context: Context, model: WidgetModel): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today_2x2)
        views.setImageViewBitmap(R.id.ring, ring(context, model, 78))
        views.setTextViewText(R.id.summary, context.getString(R.string.widget_summary, model.kept, model.total))
        views.setTextViewText(R.id.next, model.nextText?.let { context.getString(R.string.widget_next, it) }.orEmpty())
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun done(context: Context, goalId: Long, model: WidgetModel): PendingIntent = PendingIntent.getBroadcast(
        context,
        (goalId.toInt() * 10) + 7,
        Intent(context, ActionReceiver::class.java)
            .setAction(ActionReceiver.ACTION_DONE)
            .putExtra(ActionReceiver.EXTRA_GOAL_ID, goalId)
            .putExtra(ActionReceiver.EXTRA_DATE, java.time.LocalDate.now().toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun ring(context: Context, model: WidgetModel, sizeDp: Int): Bitmap {
        val size = dp(context, sizeDp) * 2
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = size * 0.1f
        val rect = RectF(stroke, stroke, size - stroke, size - stroke)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        paint.color = Color.argb(60, 136, 136, 136)
        canvas.drawArc(rect, 0f, 360f, false, paint)
        val fraction = if (model.total == 0) 0f else model.kept.toFloat() / model.total
        if (fraction > 0f) {
            paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), Color.parseColor("#C6F432"), Color.parseColor("#2EE6A6"), Shader.TileMode.CLAMP)
            canvas.drawArc(rect, -90f, 360f * fraction, false, paint)
            paint.shader = null
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.widget_text)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("${model.kept}/${model.total}", size / 2f, size / 2f + text.textSize * 0.34f, text)
        return bitmap
    }

    private fun tick(context: Context, state: WidgetState): Bitmap {
        val size = dp(context, 22) * 2
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val centre = size / 2f
        val radius = size * 0.34f
        when (state) {
            WidgetState.KEPT -> {
                paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), Color.parseColor("#C6F432"), Color.parseColor("#2EE6A6"), Shader.TileMode.CLAMP)
                canvas.drawCircle(centre, centre, radius, paint)
                paint.shader = null
                paint.style = Paint.Style.STROKE; paint.strokeWidth = size * 0.07f; paint.color = Color.parseColor("#0C1405"); paint.strokeCap = Paint.Cap.ROUND
                val check = Path().apply { moveTo(size * 0.36f, size * 0.52f); lineTo(size * 0.46f, size * 0.62f); lineTo(size * 0.65f, size * 0.40f) }
                canvas.drawPath(check, paint)
            }
            WidgetState.MISSED -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = size * 0.07f; paint.color = context.getColor(R.color.widget_missed)
                canvas.drawCircle(centre, centre, radius, paint)
            }
            WidgetState.PENDING -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = size * 0.07f; paint.color = context.getColor(R.color.widget_text_muted)
                canvas.drawCircle(centre, centre, radius, paint)
            }
        }
        return bitmap
    }

    internal suspend fun load(app: SkritiApp): Pair<WidgetModel, String> {
        val now = app.clock.now()
        val goals = app.repository.goals.first().filter { it.state == GoalState.ACTIVE }
        val rescued = app.repository.rescueDays.first()
        val pauses = app.repository.pauses.first()
        val today = now.toLocalDate()
        val shown = if (today in rescued) emptyList() else goals.filterNot { PauseRules.isPaused(pauses, it.id, today) }
        val plan = DayPlanner.plan(shown, app.repository.checkIns.first(), today, now)
        return WidgetRules.model(plan.items, MAX_ROWS, plan.nextDeadlineMinute) to now.toLocalDate().format(DATE_FORMAT)
    }
}
