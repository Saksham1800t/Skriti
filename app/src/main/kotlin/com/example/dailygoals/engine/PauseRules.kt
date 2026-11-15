package com.example.dailygoals.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A stretch of paused days. [end] is the last paused day; null means paused until the user resumes. */
data class GoalPause(val goalId: Long, val start: LocalDate, val end: LocalDate?) {
    /** The first day the goal counts again, or null while the pause has no end. */
    val resumesOn: LocalDate? get() = end?.plusDays(1)
}

enum class HabitPauseLength { DAYS_3, DAYS_7, DAYS_14, UNTIL_DATE, UNTIL_RESUMED }

/**
 * A paused goal creates no check-ins, warnings, failures or new blocks, and its paused days are neutral:
 * they never lower consistency, break a streak or count against a quota.
 */
object PauseRules {
    fun lastDay(length: HabitPauseLength, start: LocalDate, chosen: LocalDate?): LocalDate? = when (length) {
        HabitPauseLength.DAYS_3 -> start.plusDays(2)
        HabitPauseLength.DAYS_7 -> start.plusDays(6)
        HabitPauseLength.DAYS_14 -> start.plusDays(13)
        HabitPauseLength.UNTIL_DATE -> chosen
        HabitPauseLength.UNTIL_RESUMED -> null
    }

    fun isValid(length: HabitPauseLength, start: LocalDate, chosen: LocalDate?): Boolean =
        length != HabitPauseLength.UNTIL_DATE || (chosen != null && !chosen.isBefore(start))

    fun isPaused(pauses: List<GoalPause>, goalId: Long, date: LocalDate): Boolean =
        pauses.any { it.goalId == goalId && !date.isBefore(it.start) && (it.end == null || !date.isAfter(it.end)) }

    fun current(pauses: List<GoalPause>, goalId: Long, today: LocalDate): GoalPause? =
        pauses.firstOrNull { it.goalId == goalId && isPaused(listOf(it), goalId, today) }

    /** Every paused day of the goal between [from] and [to], so the day rules can treat them as neutral. */
    fun pausedDates(pauses: List<GoalPause>, goalId: Long, from: LocalDate, to: LocalDate): Set<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter { isPaused(pauses, goalId, it) }.toSet()

    /** Resuming today ends the pause yesterday; a pause that has not started yet is removed (null). */
    fun resumedToday(pause: GoalPause, today: LocalDate): GoalPause? =
        if (!pause.start.isBefore(today)) null else pause.copy(end = today.minusDays(1))

    /** Pauses whose last day is over: the goal counts again from their resume day. */
    fun justResumed(pauses: List<GoalPause>, today: LocalDate): List<GoalPause> =
        pauses.filter { pause -> pause.resumesOn?.let { !it.isAfter(today) } == true }
}

enum class RescueCheck { OK, FUTURE, TOO_OLD, ALREADY_RESCUED, WEEK_USED }

/**
 * One rescue day per ISO week protects a streak when the user is sick or travelling. It makes a date neutral
 * for every goal, and only today or yesterday may be rescued so it cannot rewrite old history.
 */
object RescueRules {
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun check(date: LocalDate, today: LocalDate, used: Collection<LocalDate>): RescueCheck = when {
        date.isAfter(today) -> RescueCheck.FUTURE
        date.isBefore(today.minusDays(1)) -> RescueCheck.TOO_OLD
        date in used -> RescueCheck.ALREADY_RESCUED
        used.any { weekStart(it) == weekStart(date) } -> RescueCheck.WEEK_USED
        else -> RescueCheck.OK
    }

    /** The date used in the week containing [today], or null when the rescue day is still available. */
    fun usedThisWeek(today: LocalDate, used: Collection<LocalDate>): LocalDate? = used.firstOrNull { weekStart(it) == weekStart(today) }
}

/** Dates that do not count for or against a goal. */
object NeutralDays {
    fun forGoal(goalId: Long, base: Set<LocalDate>, rescued: Set<LocalDate>, pauses: List<GoalPause>, from: LocalDate, to: LocalDate): Set<LocalDate> =
        base + rescued + PauseRules.pausedDates(pauses, goalId, from, to)
}
