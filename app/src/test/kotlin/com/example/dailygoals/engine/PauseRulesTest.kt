package com.example.dailygoals.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseRulesTest {
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun theLengthChoicesGiveTheLastPausedDay() {
        assertEquals(monday.plusDays(2), PauseRules.lastDay(HabitPauseLength.DAYS_3, monday, null))
        assertEquals(monday.plusDays(6), PauseRules.lastDay(HabitPauseLength.DAYS_7, monday, null))
        assertEquals(monday.plusDays(13), PauseRules.lastDay(HabitPauseLength.DAYS_14, monday, null))
        assertEquals(monday.plusDays(20), PauseRules.lastDay(HabitPauseLength.UNTIL_DATE, monday, monday.plusDays(20)))
        assertNull(PauseRules.lastDay(HabitPauseLength.UNTIL_RESUMED, monday, null))
    }

    @Test
    fun aChosenEndDateMustNotBeBeforeTheStart() {
        assertFalse(PauseRules.isValid(HabitPauseLength.UNTIL_DATE, monday, null))
        assertFalse(PauseRules.isValid(HabitPauseLength.UNTIL_DATE, monday, monday.minusDays(1)))
        assertTrue(PauseRules.isValid(HabitPauseLength.UNTIL_DATE, monday, monday))
        assertTrue(PauseRules.isValid(HabitPauseLength.UNTIL_RESUMED, monday, null))
    }

    @Test
    fun aPauseCoversItsFirstAndLastDayAndOnlyForItsOwnGoal() {
        val pauses = listOf(GoalPause(1, monday, monday.plusDays(2)))
        assertFalse(PauseRules.isPaused(pauses, 1, monday.minusDays(1)))
        assertTrue(PauseRules.isPaused(pauses, 1, monday))
        assertTrue(PauseRules.isPaused(pauses, 1, monday.plusDays(2)))
        assertFalse(PauseRules.isPaused(pauses, 1, monday.plusDays(3)))
        assertFalse(PauseRules.isPaused(pauses, 2, monday))
        assertEquals(monday.plusDays(3), pauses.single().resumesOn)
    }

    @Test
    fun aPauseUntilResumedHasNoEndAndNoResumeDay() {
        val pauses = listOf(GoalPause(1, monday, null))
        assertTrue(PauseRules.isPaused(pauses, 1, monday.plusDays(400)))
        assertNull(pauses.single().resumesOn)
        assertTrue(PauseRules.justResumed(pauses, monday.plusDays(400)).isEmpty())
    }

    @Test
    fun resumingEndsYesterdayAndAPauseNotStartedYetIsRemoved() {
        val running = GoalPause(1, monday, null)
        assertEquals(monday.plusDays(1), PauseRules.resumedToday(running, monday.plusDays(2))!!.end)
        assertNull(PauseRules.resumedToday(running, monday))
        assertNull(PauseRules.resumedToday(GoalPause(1, monday.plusDays(5), null), monday))
    }

    @Test
    fun aPauseThatEndedIsReportedAsResumedOnItsResumeDay() {
        val pauses = listOf(GoalPause(1, monday, monday.plusDays(2)))
        assertTrue(PauseRules.justResumed(pauses, monday.plusDays(2)).isEmpty())
        assertEquals(pauses, PauseRules.justResumed(pauses, monday.plusDays(3)))
    }

    @Test
    fun pausedDatesAreExpandedForTheDayRules() {
        val pauses = listOf(GoalPause(1, monday, monday.plusDays(1)), GoalPause(1, monday.plusDays(4), null))
        assertEquals(
            setOf(monday, monday.plusDays(1), monday.plusDays(4), monday.plusDays(5)),
            PauseRules.pausedDates(pauses, 1, monday, monday.plusDays(5)),
        )
        assertEquals(monday.plusDays(1), PauseRules.current(pauses, 1, monday.plusDays(1))!!.end)
        assertNull(PauseRules.current(pauses, 1, monday.plusDays(3)))
    }

    @Test
    fun rescueIsOnePerIsoWeekAndOnlyForTodayOrYesterday() {
        val wednesday = LocalDate.of(2026, 11, 11)
        assertEquals(RescueCheck.OK, RescueRules.check(wednesday, wednesday, emptyList()))
        assertEquals(RescueCheck.OK, RescueRules.check(wednesday.minusDays(1), wednesday, emptyList()))
        assertEquals(RescueCheck.TOO_OLD, RescueRules.check(wednesday.minusDays(2), wednesday, emptyList()))
        assertEquals(RescueCheck.FUTURE, RescueRules.check(wednesday.plusDays(1), wednesday, emptyList()))
        assertEquals(RescueCheck.ALREADY_RESCUED, RescueRules.check(wednesday, wednesday, listOf(wednesday)))
        assertEquals(RescueCheck.WEEK_USED, RescueRules.check(wednesday, wednesday, listOf(monday)))
        assertEquals(wednesday, RescueRules.usedThisWeek(wednesday.plusDays(1), listOf(wednesday)))
        assertNull(RescueRules.usedThisWeek(wednesday.plusDays(7), listOf(wednesday)))
    }

    @Test
    fun theWeekRunsMondayToSundayAndANewWeekFreesTheRescueDay() {
        val sunday = LocalDate.of(2026, 11, 15)
        val nextMonday = LocalDate.of(2026, 11, 16)
        assertEquals(monday, RescueRules.weekStart(sunday))
        assertEquals(nextMonday, RescueRules.weekStart(nextMonday))
        assertEquals(RescueCheck.OK, RescueRules.check(nextMonday, nextMonday, listOf(sunday)))
        assertEquals(RescueCheck.WEEK_USED, RescueRules.check(sunday, sunday, listOf(monday)))
    }

    @Test
    fun neutralDaysCombineTimeZoneSkipsRescueDaysAndThatGoalsPauses() {
        val pauses = listOf(GoalPause(1, monday, monday), GoalPause(2, monday.plusDays(1), monday.plusDays(1)))
        val neutral = NeutralDays.forGoal(1, setOf(monday.plusDays(3)), setOf(monday.plusDays(4)), pauses, monday, monday.plusDays(6))
        assertEquals(setOf(monday, monday.plusDays(3), monday.plusDays(4)), neutral)
    }
}
