package com.example.dailygoals.engine

import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalDiscipline
import com.example.dailygoals.model.GoalReminder
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.TimeWindow
import com.example.dailygoals.model.WarningMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 11, 13)
    private val dayEnd = 23 * 60 + 59

    private fun at(hour: Int, minute: Int = 0): Instant = LocalTimes.resolve(date, hour * 60 + minute, zone).toInstant()

    private val wake = Goal(
        id = 1,
        name = "Wake up",
        schedule = GoalSchedule(window = TimeWindow(300, 390), startDate = date.minusDays(5), graceMinutes = 5),
        discipline = GoalDiscipline(failOnWindowEnd = true),
        reminder = GoalReminder(mode = WarningMode.REPEATING),
    )
    private val allDay = Goal(
        id = 2,
        name = "Read",
        schedule = GoalSchedule(startDate = date.minusDays(5)),
        discipline = GoalDiscipline(failOnDayEnd = true),
        reminder = GoalReminder(mode = WarningMode.REPEATING, allDayMinute = 20 * 60),
    )

    private fun stage(goal: Goal, at: Instant) = Escalation.stageFor(goal, date, at, zone, dayEnd)

    @Test
    fun aWindowGoalStartsWithAReminderBecomesStrongerHalfwayAndFinalBeforeTheGraceEnds() {
        assertEquals(EscalationStage.REMINDER, stage(wake, at(5, 0)))
        assertEquals(EscalationStage.REMINDER, stage(wake, at(5, 40)))
        assertEquals(EscalationStage.STRONGER, stage(wake, at(5, 45)))
        assertEquals(EscalationStage.STRONGER, stage(wake, at(6, 20)))
        assertEquals(EscalationStage.FINAL, stage(wake, at(6, 25)))
        assertEquals(EscalationStage.FINAL, stage(wake, at(6, 34)))
    }

    @Test
    fun anAllDayGoalBecomesStrongerThirtyMinutesLaterAndFinalBeforeTheDayEnd() {
        assertEquals(EscalationStage.REMINDER, stage(allDay, at(20, 0)))
        assertEquals(EscalationStage.REMINDER, stage(allDay, at(20, 29)))
        assertEquals(EscalationStage.STRONGER, stage(allDay, at(20, 30)))
        assertEquals(EscalationStage.STRONGER, stage(allDay, at(23, 40)))
        assertEquals(EscalationStage.FINAL, stage(allDay, at(23, 49)))
    }

    @Test
    fun aGoalThatCannotFailByItselfNeverGetsAFinalWarning() {
        val gentle = wake.copy(discipline = GoalDiscipline())
        assertEquals(EscalationStage.STRONGER, stage(gentle, at(6, 30)))
    }

    @Test
    fun theFirstWarningIsNeverFinalEvenWhenTheCheckTimeIsAlmostTheDayEnd() {
        val late = allDay.copy(reminder = GoalReminder(mode = WarningMode.REPEATING, allDayMinute = 23 * 60 + 55))
        assertEquals(EscalationStage.REMINDER, stage(late, at(23, 55)))
    }

    private val none = QuietHours()
    private val night = QuietHours(enabled = true, startMinute = 22 * 60, endMinute = 7 * 60)

    @Test
    fun theDailyCapStopsRemindersAfterTheLimit() {
        assertTrue(NotificationCap.allows(EscalationStage.REMINDER, 5, 6, none, 600))
        assertFalse(NotificationCap.allows(EscalationStage.REMINDER, 6, 6, none, 600))
        assertFalse(NotificationCap.allows(EscalationStage.STRONGER, 9, 6, none, 600))
    }

    @Test
    fun theFinalWarningIsNeverSilencedByTheCapOrQuietHours() {
        assertTrue(NotificationCap.allows(EscalationStage.FINAL, 50, 6, night, 23 * 60))
    }

    @Test
    fun quietHoursCrossMidnightAndAreOffUntilSwitchedOn() {
        assertTrue(night.contains(23 * 60))
        assertTrue(night.contains(3 * 60))
        assertFalse(night.contains(7 * 60))
        assertFalse(night.contains(12 * 60))
        assertFalse(none.contains(23 * 60))
        assertFalse(NotificationCap.allows(EscalationStage.REMINDER, 0, 6, night, 23 * 60))
        assertTrue(QuietHours(true, 13 * 60, 14 * 60).contains(13 * 60 + 30))
    }

    @Test
    fun theCapIsKeptInsideItsRange() {
        assertTrue(NotificationCap.allows(EscalationStage.REMINDER, 0, 0, none, 600))
        assertFalse(NotificationCap.allows(EscalationStage.REMINDER, 20, 99, none, 600))
    }
}
