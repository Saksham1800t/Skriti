package com.example.dailygoals.blocker

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.PromptMark
import com.example.dailygoals.engine.ViolationAnswer
import com.example.dailygoals.engine.ViolationRules
import com.example.dailygoals.model.Goal
import com.example.dailygoals.notify.NotificationChannels
import java.time.LocalTime
import kotlinx.coroutines.launch

/**
 * Asks "did you just use this app?" when a tempting app of an avoid goal comes to the front. Only the package
 * name and the time are used, never what is on screen. Nothing happens unless the user answers Yes.
 */
class ViolationPrompter(private val app: SkritiApp) {
    private var sessionPackage: String? = null
    private var sessionStartMillis = 0L
    private var goals: List<Goal> = emptyList()
    private var goalsReadAt = Long.MIN_VALUE / 2
    private var last: PromptMark? = null

    /** Called for every window change; cheap unless the package belongs to an avoid goal. */
    fun onFront(packageName: String, passThrough: Set<String>) {
        if (packageName == app.packageName || packageName in passThrough) return
        val now = System.currentTimeMillis()
        if (packageName != sessionPackage) {
            sessionPackage = packageName
            sessionStartMillis = now
        }
        val sessionStart = sessionStartMillis
        app.scope.launch {
            if (now - goalsReadAt > GOALS_CACHE_MILLIS) {
                goals = app.repository.watchedGoals()
                goalsReadAt = now
            }
            if (goals.none { packageName in it.avoidance.temptingPackages }) return@launch
            val minute = LocalTime.now().let { it.hour * 60 + it.minute }
            val goal = goals.firstOrNull { ViolationRules.shouldPrompt(it, packageName, minute, now, sessionStart, last) } ?: return@launch
            last = PromptMark(packageName, goal.id, now)
            val eventId = app.repository.recordViolationPrompt(goal.id, packageName, now)
            ViolationNotifier(app).ask(eventId, goal, packageName)
        }
    }

    private companion object {
        const val GOALS_CACHE_MILLIS = 30_000L
    }
}

class ViolationNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission")
    fun ask(eventId: Long, goal: Goal, packageName: String) {
        if (!manager.areNotificationsEnabled()) return
        val pm = context.packageManager
        val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
        val question = context.getString(R.string.violation_text, goal.name)
        val plan = goal.meaning.plan
        val notification = NotificationCompat.Builder(context, NotificationChannels.WARNINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.violation_title, appName))
            .setContentText(plan ?: question)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (plan != null) "$question\n$plan" else question))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.violation_yes), reply(eventId, goal.id, ViolationAnswer.YES))
            .addAction(0, context.getString(R.string.violation_no), reply(eventId, goal.id, ViolationAnswer.NO))
            .build()
        runCatching { manager.notify("violation", eventId.toInt(), notification) }
    }

    private fun reply(eventId: Long, goalId: Long, answer: ViolationAnswer): PendingIntent = PendingIntent.getBroadcast(
        context,
        (eventId * 2 + if (answer == ViolationAnswer.YES) 1 else 0).toInt(),
        Intent(context, ViolationReceiver::class.java)
            .setAction(ViolationReceiver.ACTION_ANSWER)
            .putExtra(ViolationReceiver.EXTRA_EVENT, eventId)
            .putExtra(ViolationReceiver.EXTRA_GOAL, goalId)
            .putExtra(ViolationReceiver.EXTRA_ANSWER, answer.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class ViolationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ANSWER) return
        val event = intent.getLongExtra(EXTRA_EVENT, -1)
        val goal = intent.getLongExtra(EXTRA_GOAL, -1)
        val answer = runCatching { ViolationAnswer.valueOf(intent.getStringExtra(EXTRA_ANSWER).orEmpty()) }.getOrNull() ?: return
        if (event < 0 || goal < 0) return
        NotificationManagerCompat.from(context).cancel("violation", event.toInt())
        val app = context.applicationContext as SkritiApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.repository.answerViolation(event, goal, answer)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.example.dailygoals.VIOLATION_ANSWER"
        const val EXTRA_EVENT = "event"
        const val EXTRA_GOAL = "goal"
        const val EXTRA_ANSWER = "answer"
    }
}
