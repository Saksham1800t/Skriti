package com.example.dailygoals.alarm

import android.content.Context
import java.time.LocalDate

/** How many times each goal's alarm was snoozed on a date, so the snooze limit holds even if the service restarts. */
class AlarmStore(context: Context) {
    private val prefs = context.getSharedPreferences("goal_alarms", Context.MODE_PRIVATE)

    private fun key(goalId: Long, date: LocalDate) = "snooze:$goalId:$date"

    fun snoozesUsed(goalId: Long, date: LocalDate): Int = prefs.getInt(key(goalId, date), 0)

    fun countSnooze(goalId: Long, date: LocalDate) {
        val editor = prefs.edit().putInt(key(goalId, date), snoozesUsed(goalId, date) + 1)
        val old = date.minusDays(KEEP_DAYS).toString()
        prefs.all.keys.filter { it.startsWith("snooze:") && it.substringAfterLast(':') < old }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun clearSnoozes(goalId: Long, date: LocalDate) = prefs.edit().remove(key(goalId, date)).apply()

    private companion object {
        const val KEEP_DAYS = 3L
    }
}
