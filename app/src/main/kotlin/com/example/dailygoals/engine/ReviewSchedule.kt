package com.example.dailygoals.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** The weekly review is made on Sunday at a time the user chooses, and covers Monday to Sunday. */
object ReviewSchedule {
    const val DEFAULT_MINUTE = 18 * 60

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The Sunday moment when the week containing [date] is reviewed. */
    fun dueAt(date: LocalDate, minute: Int): LocalDateTime =
        date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atStartOfDay().plusMinutes(minute.toLong())

    /**
     * The week that should have a review now and does not yet, or null. A missed Sunday is made up the next time
     * the app runs, so a phone that was off still gets its review, once.
     */
    fun weekToGenerate(now: LocalDateTime, minute: Int, existing: Set<LocalDate>): LocalDate? {
        val thisWeek = weekStart(now.toLocalDate())
        val thisDue = dueAt(now.toLocalDate(), minute)
        if (!now.isBefore(thisDue)) return thisWeek.takeIf { it !in existing }
        val lastWeek = thisWeek.minusWeeks(1)
        return if (lastWeek !in existing && existing.isNotEmpty()) lastWeek else null
    }

    /** The next moment the app should wake to make a review. */
    fun nextWake(now: LocalDateTime, minute: Int, existing: Set<LocalDate>): LocalDateTime {
        val thisDue = dueAt(now.toLocalDate(), minute)
        val thisWeek = weekStart(now.toLocalDate())
        return if (now.isBefore(thisDue) || thisWeek in existing) {
            if (now.isBefore(thisDue)) thisDue else dueAt(now.toLocalDate().plusWeeks(1), minute)
        } else {
            now.plusMinutes(1)
        }
    }
}
