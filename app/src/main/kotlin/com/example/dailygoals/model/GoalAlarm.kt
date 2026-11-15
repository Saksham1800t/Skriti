package com.example.dailygoals.model

import java.time.DayOfWeek

/**
 * An optional real alarm for a goal with a time window, off by default. The app never picks the time: it has
 * no default, and the goal cannot be saved with the alarm on until one is chosen inside the window.
 */
data class GoalAlarm(
    val enabled: Boolean = false,
    val minute: Int? = null,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val snoozeLimit: Int = DEFAULT_SNOOZE_LIMIT,
    /** Dismissing also ticks the goal; otherwise it stays pending so the warnings keep going. */
    val dismissTicksGoal: Boolean = false,
) {
    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 5
        const val DEFAULT_SNOOZE_LIMIT = 3
        val SNOOZE_MINUTE_CHOICES = listOf(1, 5, 10, 15)
        val SNOOZE_LIMIT_RANGE = 0..10
    }
}
