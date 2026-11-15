package com.example.dailygoals.engine

import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalAlarm
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.TimeWindow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRulesTest {
    private val zone = ZoneOffset.UTC
    private val friday = LocalDate.of(2026, 11, 13)
    private val window = TimeWindow(5 * 60, 6 * 60 + 30)

    private fun goal(id: Long = 1, alarm: GoalAlarm = GoalAlarm(enabled = true, minute = 5 * 60 + 15), schedule: GoalSchedule = GoalSchedule(window = window, startDate = friday.minusDays(30))) =
        Goal(id = id, name = "Wake up", schedule = schedule, alarm = alarm)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))

    private fun instant(date: LocalDate, hour: Int, minute: Int) = at(date, hour, minute).atZone(zone).toInstant()

    @Test
    fun anAlarmThatIsOffNeedsNothing() {
        assertEquals(emptySet<AlarmError>(), AlarmRules.validate(GoalAlarm(), null))
    }

    @Test
    fun anAlarmThatIsOnNeedsAWindowATimeInsideItAndADay() {
        assertEquals(setOf(AlarmError.NEEDS_WINDOW), AlarmRules.validate(GoalAlarm(true, 300), null))
        assertEquals(setOf(AlarmError.NEEDS_TIME), AlarmRules.validate(GoalAlarm(true, null), window))
        assertEquals(setOf(AlarmError.OUTSIDE_WINDOW), AlarmRules.validate(GoalAlarm(true, 4 * 60 + 59), window))
        assertEquals(setOf(AlarmError.OUTSIDE_WINDOW), AlarmRules.validate(GoalAlarm(true, 6 * 60 + 31), window))
        assertEquals(setOf(AlarmError.NO_DAYS), AlarmRules.validate(GoalAlarm(true, 300, days = emptySet()), window))
        assertEquals(emptySet<AlarmError>(), AlarmRules.validate(GoalAlarm(true, 5 * 60), window))
        assertEquals(emptySet<AlarmError>(), AlarmRules.validate(GoalAlarm(true, 6 * 60 + 30), window))
    }

    @Test
    fun theAppNeverPicksATime() {
        assertNull(GoalAlarm().minute)
        assertFalse(GoalAlarm().enabled)
        assertNull(AlarmRules.nextRing(goal(alarm = GoalAlarm(enabled = true, minute = null)), at(friday, 1), zone))
    }

    @Test
    fun theNextRingIsTodayBeforeTheTimeAndTomorrowAfterIt() {
        assertEquals(instant(friday, 5, 15), AlarmRules.nextRing(goal(), at(friday, 4, 0), zone)!!.at)
        assertEquals(instant(friday.plusDays(1), 5, 15), AlarmRules.nextRing(goal(), at(friday, 5, 15), zone)!!.at)
        assertEquals(friday.plusDays(1), AlarmRules.nextRing(goal(), at(friday, 9, 0), zone)!!.date)
    }

    @Test
    fun itRingsOnlyOnTheChosenDaysAndOnlyWhenTheGoalIsDue() {
        val weekdaysOnly = goal(alarm = GoalAlarm(true, 315, days = setOf(DayOfWeek.MONDAY)))
        assertEquals(LocalDate.of(2026, 11, 16), AlarmRules.nextRing(weekdaysOnly, at(friday, 9, 0), zone)!!.date)
        val everyOtherDay = goal(schedule = GoalSchedule(frequency = Frequency.EVERY_N_DAYS, intervalDays = 2, window = window, startDate = friday))
        assertEquals(friday.plusDays(2), AlarmRules.nextRing(everyOtherDay, at(friday, 9, 0), zone)!!.date)
    }

    @Test
    fun pausedAndRescuedDaysAreSkipped() {
        val skipTomorrow = { date: LocalDate -> date == friday.plusDays(1) }
        assertEquals(friday.plusDays(2), AlarmRules.nextRing(goal(), at(friday, 9, 0), zone, skipTomorrow)!!.date)
    }

    @Test
    fun noRingForAnArchivedGoalAQuotaGoalOrAGoalWithoutAWindow() {
        assertNull(AlarmRules.nextRing(goal().copy(state = GoalState.ARCHIVED), at(friday, 1), zone))
        val weekly = goal(schedule = GoalSchedule(frequency = Frequency.PER_WEEK, count = 3, window = window, startDate = friday.minusDays(30)))
        assertNull(AlarmRules.nextRing(weekly, at(friday, 1), zone))
        assertNull(AlarmRules.nextRing(goal(schedule = GoalSchedule(startDate = friday.minusDays(30))), at(friday, 1), zone))
    }

    @Test
    fun theEarliestAlarmAcrossGoalsIsTheOneScheduled() {
        val early = goal(id = 1, alarm = GoalAlarm(true, 5 * 60 + 15))
        val later = goal(id = 2, alarm = GoalAlarm(true, 6 * 60))
        assertEquals(1L, AlarmRules.earliest(listOf(later, early), at(friday, 4, 0), zone)!!.goalId)
        assertEquals(2L, AlarmRules.earliest(listOf(later, early), at(friday, 5, 30), zone)!!.goalId)
        assertEquals(2L, AlarmRules.earliest(listOf(later, early), at(friday, 4, 0), zone) { id, _ -> id == 1L }!!.goalId)
        assertNull(AlarmRules.earliest(emptyList(), at(friday, 4, 0), zone))
    }

    @Test
    fun snoozingStopsAtTheLimitAndAddsTheChosenMinutes() {
        val alarm = GoalAlarm(true, 300, snoozeMinutes = 5, snoozeLimit = 3)
        assertTrue(AlarmRules.canSnooze(alarm, 2))
        assertFalse(AlarmRules.canSnooze(alarm, 3))
        assertFalse(AlarmRules.canSnooze(alarm.copy(snoozeLimit = 0), 0))
        assertEquals(1_000L + 5 * 60_000L, AlarmRules.snoozeAt(1_000L, alarm))
        assertEquals(1_000L + 60_000L, AlarmRules.snoozeAt(1_000L, alarm.copy(snoozeMinutes = 0)))
    }
}
