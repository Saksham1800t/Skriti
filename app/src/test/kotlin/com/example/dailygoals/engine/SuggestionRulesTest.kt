package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalMeasurement
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.MeasureType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionRulesTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val goal = Goal(
        id = 7,
        name = "Study",
        schedule = GoalSchedule(startDate = LocalDate.of(2026, 9, 1)),
        measurement = GoalMeasurement(type = MeasureType.AMOUNT, unit = "min", target = 120, minimum = 20),
    )

    /** One record per day, newest last, ending today. */
    private fun days(vararg statuses: DayStatus): List<DayRecord> =
        statuses.mapIndexed { i, s -> DayRecord(today.minusDays((statuses.size - 1 - i).toLong()), s) }

    private fun repeat(n: Int, status: DayStatus) = Array(n) { status }

    @Test
    fun tenMinimumDaysInTheLastFourteenSuggestLoweringTheTarget() {
        val records = days(*repeat(4, DayStatus.DONE), *repeat(10, DayStatus.MINIMUM))
        val found = SuggestionRules.forGoal(goal, records, today, emptySet())
        assertEquals(Suggestion(7, SuggestionKind.LOWER_TARGET, 10), found)
    }

    @Test
    fun nineMinimumDaysIsNotEnough() {
        val records = days(*repeat(5, DayStatus.DONE), *repeat(9, DayStatus.MINIMUM))
        assertNull(SuggestionRules.forGoal(goal, records, today, emptySet()))
    }

    @Test
    fun daysOlderThanFourteenDoNotCount() {
        val records = days(*repeat(10, DayStatus.MINIMUM), *repeat(14, DayStatus.FAILED))
        assertNull(SuggestionRules.forGoal(goal, records, today, emptySet()))
    }

    @Test
    fun aGoalWithNoSmallerStepIsNeverToldToLowerIt() {
        val noMinimum = goal.copy(measurement = GoalMeasurement())
        val records = days(*repeat(14, DayStatus.MINIMUM))
        assertNull(SuggestionRules.forGoal(noMinimum, records, today, emptySet()))
    }

    @Test
    fun fourteenDaysAtTheTargetInARowSuggestRaisingIt() {
        val records = days(DayStatus.FAILED, *repeat(14, DayStatus.DONE))
        assertEquals(Suggestion(7, SuggestionKind.RAISE, 14), SuggestionRules.forGoal(goal, records, today, emptySet()))
    }

    @Test
    fun aMinimumDayBreaksTheRunAtTheTarget() {
        val records = days(*repeat(10, DayStatus.DONE), DayStatus.MINIMUM, *repeat(3, DayStatus.DONE))
        assertNull(SuggestionRules.forGoal(goal, records, today, emptySet()))
    }

    @Test
    fun aDismissedSuggestionIsNotOfferedAgain() {
        val records = days(*repeat(14, DayStatus.DONE))
        val dismissed = setOf(SuggestionRules.key(7, SuggestionKind.RAISE))
        assertNull(SuggestionRules.forGoal(goal, records, today, dismissed))
    }

    @Test
    fun quotaGoalsGetNoSuggestions() {
        val quota = goal.copy(schedule = goal.schedule.copy(frequency = com.example.dailygoals.model.Frequency.PER_WEEK))
        assertNull(SuggestionRules.forGoal(quota, days(*repeat(14, DayStatus.DONE)), today, emptySet()))
    }
}
