package com.example.dailygoals.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.dailygoals.MainActivity
import com.example.dailygoals.engine.AlarmRing

/**
 * Keeps one real alarm clock: the next time any goal's alarm should ring. setAlarmClock makes Android treat it
 * as a true alarm (status bar icon, allowed in doze), so it needs no extra permission.
 */
class GoalAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun ringIntent(goalId: Long, date: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, GoalAlarmReceiver::class.java)
            .setAction(ACTION_RING)
            .putExtra(EXTRA_GOAL_ID, goalId)
            .putExtra(EXTRA_DATE, date),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Schedules [ring], or cancels the alarm when there is nothing to ring. */
    fun schedule(ring: AlarmRing?) {
        if (ring == null) {
            alarmManager.cancel(ringIntent(0, "", REQUEST_MAIN))
            return
        }
        set(ring.at.toEpochMilli(), ringIntent(ring.goalId, ring.date.toString(), REQUEST_MAIN))
    }

    /** A snooze rings the same goal again after a few minutes, without touching the regular alarm. */
    fun snooze(goalId: Long, date: String, atMillis: Long) {
        set(atMillis, ringIntent(goalId, date, REQUEST_SNOOZE))
    }

    private fun set(atMillis: Long, operation: PendingIntent) {
        runCatching { alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, showIntent()), operation) }
    }

    companion object {
        const val ACTION_RING = "com.example.dailygoals.GOAL_ALARM_RING"
        const val EXTRA_GOAL_ID = "goal_id"
        const val EXTRA_DATE = "date"
        private const val REQUEST_MAIN = 21
        private const val REQUEST_SNOOZE = 22
    }
}
