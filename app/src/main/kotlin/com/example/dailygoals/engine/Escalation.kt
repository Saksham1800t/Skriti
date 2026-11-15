package com.example.dailygoals.engine

import com.example.dailygoals.model.Goal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How firm a warning is. Each stage uses firmer wording than the one before it. */
enum class EscalationStage { REMINDER, STRONGER, FINAL }

/**
 * The ladder for a due goal: a reminder when the window opens (or at the check time), a stronger reminder
 * about halfway through the window (30 minutes later for an all-day goal), and a final warning ten minutes
 * before the window or its grace ends. Only a goal that can fail on its own has a final warning.
 */
object Escalation {
    const val FINAL_LEAD_MINUTES = 10L
    const val ALL_DAY_STRONGER_MINUTES = 30L

    fun stageFor(goal: Goal, date: LocalDate, at: Instant, zone: ZoneId, dayEndMinute: Int): EscalationStage {
        val first = firstWarning(goal, date, zone)
        val stop = WarningPlanner.stopsAt(goal, date, zone, dayEndMinute)
        val failsByItself = goal.discipline.failOnWindowEnd || goal.discipline.failOnDayEnd
        if (failsByItself && !at.isBefore(stop.minus(Duration.ofMinutes(FINAL_LEAD_MINUTES))) && at.isAfter(first)) return EscalationStage.FINAL
        val window = goal.schedule.window
        val stronger = if (window != null) {
            val start = LocalTimes.resolve(date, window.startMinute, zone).toInstant()
            val end = LocalTimes.resolve(date, window.endMinute, zone).toInstant()
            start.plus(Duration.between(start, end).dividedBy(2))
        } else {
            first.plus(Duration.ofMinutes(ALL_DAY_STRONGER_MINUTES))
        }
        return if (!at.isBefore(stronger)) EscalationStage.STRONGER else EscalationStage.REMINDER
    }

    private fun firstWarning(goal: Goal, date: LocalDate, zone: ZoneId): Instant {
        val minute = goal.schedule.window?.startMinute ?: goal.reminder.allDayMinute
        return LocalTimes.resolve(date, minute, zone).toInstant()
    }
}

data class QuietHours(val enabled: Boolean = false, val startMinute: Int = 22 * 60, val endMinute: Int = 7 * 60) {
    /** The span may cross midnight, for example 22:00 to 07:00. */
    fun contains(minuteOfDay: Int): Boolean = enabled && when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }
}

/**
 * Stops notification fatigue. A suppressed warning is still shown inside the app. The final warning is the
 * last chance before a goal fails, so the daily cap never silences it, and quiet hours do not either.
 */
object NotificationCap {
    const val DEFAULT_MAX_PER_GOAL_PER_DAY = 6
    val MAX_RANGE = 1..20

    fun allows(stage: EscalationStage, sentToday: Int, maxPerDay: Int, quiet: QuietHours, minuteOfDay: Int): Boolean = when {
        stage == EscalationStage.FINAL -> true
        quiet.contains(minuteOfDay) -> false
        else -> sentToday < maxPerDay.coerceIn(MAX_RANGE)
    }
}
