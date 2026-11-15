package com.example.dailygoals.engine

import com.example.dailygoals.model.CheckIn
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.FailSource
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalDiscipline
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.TimeWindow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalHistoryTest {
    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2026, 11, 1)
    private val today = LocalDate.of(2026, 11, 8)
    private val dayEnd = 23 * 60 + 59

    private val goal = Goal(id = 1, name = "Study", schedule = GoalSchedule(window = TimeWindow(300, 420), startDate = start, graceMinutes = 5), discipline = GoalDiscipline(failOnWindowEnd = true))

    private fun millis(date: LocalDate, hour: Int, minute: Int) = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).toInstant(zone).toEpochMilli()

    private fun row(offset: Int, status: DayStatus, reason: FailReason? = null, tag: String? = null, at: Long = 0, source: FailSource = FailSource.SELF_REPORTED) =
        CheckIn(1, start.plusDays(offset.toLong()), status, failReason = reason, failTag = tag, failSource = source, updatedAtMillis = at)

    @Test
    fun everyPastDueDayIsJudgedAndAnUntickedOneCountsAsMissed() {
        val records = GoalHistory.records(goal, listOf(row(0, DayStatus.DONE)), today, emptySet(), dayEnd, zone)
        assertEquals(7, records.size)
        assertEquals(DayStatus.DONE, records.first().status)
        assertTrue(records.drop(1).all { it.status == DayStatus.FAILED })
    }

    @Test
    fun todayIsLeftOutWhileStillOpenAndNeutralDaysAreSkipped() {
        val records = GoalHistory.records(goal, emptyList(), today, setOf(start, start.plusDays(1)), dayEnd, zone)
        assertEquals(5, records.size)
        assertEquals(start.plusDays(2), records.first().date)
        assertTrue(records.none { it.date == today })
    }

    @Test
    fun theFailureTimeComesFromTheRuleOrFromWhenItWasRecorded() {
        val rows = listOf(
            row(0, DayStatus.FAILED, FailReason.WINDOW_END),
            row(1, DayStatus.FAILED, FailReason.DAY_END),
            row(2, DayStatus.FAILED, FailReason.MANUAL, at = millis(start.plusDays(2), 22, 15)),
        )
        val records = GoalHistory.records(goal, rows, today, emptySet(), dayEnd, zone)
        assertEquals(7 * 60 + 5, records[0].failedAtMinute)
        assertEquals(dayEnd, records[1].failedAtMinute)
        assertEquals(22 * 60 + 15, records[2].failedAtMinute)
    }

    @Test
    fun reasonTriggerAndSourceTravelWithAFailureButNotWithAKeptDay() {
        val rows = listOf(row(0, DayStatus.FAILED, FailReason.MANUAL, tag = "Tired", source = FailSource.DETECTED), row(1, DayStatus.DONE, tag = "Tired"))
        val records = GoalHistory.records(goal, rows, today, emptySet(), dayEnd, zone)
        assertEquals("Tired", records[0].reason)
        assertEquals(FailSource.DETECTED, records[0].source)
        assertNull(records[1].reason)
    }

    @Test
    fun quotaGoalsHaveNoDailyRecords() {
        val weekly = goal.copy(schedule = goal.schedule.copy(frequency = com.example.dailygoals.model.Frequency.PER_WEEK, count = 3))
        assertTrue(GoalHistory.records(weekly, emptyList(), today, emptySet(), dayEnd, zone).isEmpty())
    }

    @Test
    fun rangesKeepTheLastSevenOrThirtyDays() {
        val records = (0 until 20).map { DayRecord(start.plusDays(it.toLong()), DayStatus.DONE) }
        assertEquals(7, GoalHistory.within(records, InsightRange.WEEK, start.plusDays(19)).size)
        assertEquals(20, GoalHistory.within(records, InsightRange.MONTH, start.plusDays(19)).size)
        assertEquals(20, GoalHistory.within(records, InsightRange.ALL, start.plusDays(19)).size)
    }

    @Test
    fun mistakesAreNewestFirstAndFilterByGoalTriggerAndText() {
        val other = goal.copy(id = 2, name = "Run")
        val rows = listOf(
            CheckIn(1, start, DayStatus.FAILED, failNote = "Stayed up late", failTag = "Tired", updatedAtMillis = 1),
            CheckIn(2, start.plusDays(1), DayStatus.FAILED, failTrigger = "Bored", updatedAtMillis = 2),
            CheckIn(1, start.plusDays(2), DayStatus.FAILED, failTrigger = "Bored", failSource = FailSource.DETECTED, updatedAtMillis = 3),
            CheckIn(1, start.plusDays(3), DayStatus.DONE),
        )
        val all = GoalHistory.mistakes(listOf(goal, other), rows, zone)
        assertEquals(listOf(start.plusDays(2), start.plusDays(1), start), all.map { it.date })
        assertTrue(all.first().detected)
        assertEquals(listOf(1L), GoalHistory.mistakes(listOf(goal, other), rows, zone, goalId = 1).map { it.goalId }.distinct())
        assertEquals(2, GoalHistory.mistakes(listOf(goal, other), rows, zone, trigger = "Bored").size)
        assertEquals(listOf("Study"), GoalHistory.mistakes(listOf(goal, other), rows, zone, search = "LATE").map { it.goalName })
    }

    @Test
    fun theMostCommonCausePerGoalIsListed() {
        val rows = listOf(
            CheckIn(1, start, DayStatus.FAILED, failTag = "Tired", updatedAtMillis = 1),
            CheckIn(1, start.plusDays(1), DayStatus.FAILED, failTag = "Tired", updatedAtMillis = 2),
            CheckIn(1, start.plusDays(2), DayStatus.FAILED, failTrigger = "Bored", updatedAtMillis = 3),
        )
        val causes = GoalHistory.commonCause(GoalHistory.mistakes(listOf(goal), rows, zone))
        assertEquals(mapOf("Study" to "Tired"), causes)
    }
}
