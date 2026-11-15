package com.example.dailygoals.engine

import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalAlarm
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.TimeWindow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class AlarmError { NEEDS_WINDOW, NEEDS_TIME, OUTSIDE_WINDOW, NO_DAYS }

data class AlarmRing(val goalId: Long, val date: LocalDate, val at: Instant)

object AlarmRules {
    private const val LOOK_AHEAD_DAYS = 8

    /** An alarm that is on needs a window, a time the user chose inside it, and at least one day. */
    fun validate(alarm: GoalAlarm, window: TimeWindow?): Set<AlarmError> = buildSet {
        if (!alarm.enabled) return@buildSet
        if (window == null) add(AlarmError.NEEDS_WINDOW)
        val minute = alarm.minute
        if (minute == null) {
            add(AlarmError.NEEDS_TIME)
        } else if (window != null && (minute < window.startMinute || minute > window.endMinute)) {
            add(AlarmError.OUTSIDE_WINDOW)
        }
        if (alarm.days.isEmpty()) add(AlarmError.NO_DAYS)
    }

    /** The next time this goal's alarm should ring after [now], or null. [skip] holds paused and rescued dates. */
    fun nextRing(goal: Goal, now: LocalDateTime, zone: ZoneId, skip: (LocalDate) -> Boolean = { false }): AlarmRing? {
        val alarm = goal.alarm
        val minute = alarm.minute ?: return null
        if (!alarm.enabled || goal.state != GoalState.ACTIVE || goal.schedule.window == null || goal.schedule.isQuota) return null
        val nowInstant = now.atZone(zone).toInstant()
        val today = now.toLocalDate()
        for (offset in 0..LOOK_AHEAD_DAYS) {
            val date = today.plusDays(offset.toLong())
            if (date.dayOfWeek !in alarm.days || !DueDates.isDue(goal.schedule, date) || skip(date)) continue
            val at = LocalTimes.resolve(date, minute, zone).toInstant()
            if (at.isAfter(nowInstant)) return AlarmRing(goal.id, date, at)
        }
        return null
    }

    /** The one alarm to schedule: the earliest across every goal. */
    fun earliest(goals: List<Goal>, now: LocalDateTime, zone: ZoneId, skip: (Long, LocalDate) -> Boolean = { _, _ -> false }): AlarmRing? =
        goals.mapNotNull { goal -> nextRing(goal, now, zone) { date -> skip(goal.id, date) } }.minByOrNull { it.at }

    fun canSnooze(alarm: GoalAlarm, snoozesUsed: Int): Boolean = snoozesUsed < alarm.snoozeLimit.coerceIn(GoalAlarm.SNOOZE_LIMIT_RANGE)

    fun snoozeAt(nowMillis: Long, alarm: GoalAlarm): Long = nowMillis + alarm.snoozeMinutes.coerceAtLeast(1) * 60_000L
}
