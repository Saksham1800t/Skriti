package com.example.dailygoals.engine

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewScheduleTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val sunday = LocalDate.of(2026, 11, 15)
    private val minute = 18 * 60

    private fun at(date: LocalDate, hour: Int, min: Int = 0) = LocalDateTime.of(date, java.time.LocalTime.of(hour, min))

    @Test
    fun theWeekRunsMondayToSundayAndIsDueOnSundayAtTheChosenTime() {
        assertEquals(monday, ReviewSchedule.weekStart(sunday))
        assertEquals(at(sunday, 18), ReviewSchedule.dueAt(monday, minute))
        assertEquals(at(sunday, 18), ReviewSchedule.dueAt(sunday, minute))
    }

    @Test
    fun nothingIsGeneratedBeforeSundayEvening() {
        assertNull(ReviewSchedule.weekToGenerate(at(sunday, 17, 59), minute, emptySet()))
        assertNull(ReviewSchedule.weekToGenerate(at(monday, 12), minute, emptySet()))
    }

    @Test
    fun theReviewIsGeneratedOnceFromSundayEvening() {
        assertEquals(monday, ReviewSchedule.weekToGenerate(at(sunday, 18), minute, emptySet()))
        assertNull(ReviewSchedule.weekToGenerate(at(sunday, 21), minute, setOf(monday)))
    }

    @Test
    fun aMissedSundayIsMadeUpOnceTheNextWeek() {
        val nextMonday = monday.plusWeeks(1)
        assertEquals(monday, ReviewSchedule.weekToGenerate(at(nextMonday, 9), minute, setOf(monday.minusWeeks(1))))
        assertNull(ReviewSchedule.weekToGenerate(at(nextMonday, 9), minute, setOf(monday)))
        assertNull(ReviewSchedule.weekToGenerate(at(nextMonday, 9), minute, emptySet()))
    }

    @Test
    fun theNextWakeIsThisSundayThenNextSundayOnceDone() {
        assertEquals(at(sunday, 18), ReviewSchedule.nextWake(at(monday, 8), minute, emptySet()))
        assertEquals(at(sunday.plusWeeks(1), 18), ReviewSchedule.nextWake(at(sunday, 19), minute, setOf(monday)))
        assertEquals(at(sunday, 18, 1), ReviewSchedule.nextWake(at(sunday, 18, 0), minute, emptySet()))
    }
}
