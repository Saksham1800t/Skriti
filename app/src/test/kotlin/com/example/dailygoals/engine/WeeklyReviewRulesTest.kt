package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReviewRulesTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val lastMonday = monday.minusDays(7)

    private fun record(base: LocalDate, offset: Int, status: DayStatus, reason: String? = null, trigger: String? = null, late: Int? = null) =
        DayRecord(base.plusDays(offset.toLong()), status, reason = reason, trigger = trigger, lateMinutes = late)

    private fun week(base: LocalDate, vararg statuses: DayStatus) = statuses.mapIndexed { i, s -> record(base, i, s) }

    private val done = DayStatus.DONE
    private val failed = DayStatus.FAILED

    @Test
    fun theSummaryCountsHabitsCompletionFailuresAndChange() {
        val run = GoalWeek(1, "Run", week(monday, done, done, failed, done), week(lastMonday, failed, failed, done, done), hasMinimum = true)
        val read = GoalWeek(2, "Read", week(monday, done, done, done, done), week(lastMonday, done, done, done, done), hasMinimum = false)
        val review = WeeklyReviewRules.review(listOf(run, read), rescuesUsed = 1, emergencyPausesUsed = 2)
        assertEquals(2, review.habits)
        assertEquals(88, review.completionPercent)
        assertEquals(1, review.failures)
        assertEquals(1, review.rescues)
        assertEquals(2, review.emergencyPauses)
        assertEquals(4, review.bestStreak)
        assertEquals(88 - 75, review.consistencyChangePoints)
    }

    @Test
    fun theFirstWeekHasNothingToCompareWith() {
        val run = GoalWeek(1, "Run", week(monday, done, done, failed), emptyList(), hasMinimum = false)
        assertNull(WeeklyReviewRules.review(listOf(run), 0, 0).consistencyChangePoints)
    }

    @Test
    fun theBiggestImprovementNeedsAtLeastFivePointsAndEnoughDays() {
        val better = GoalWeek(1, "Mornings", week(monday, done, done, done, done), week(lastMonday, done, failed, failed, done), true)
        val same = GoalWeek(2, "Read", week(monday, done, done, done, done), week(lastMonday, done, done, done, done), true)
        val review = WeeklyReviewRules.review(listOf(better, same), 0, 0)
        assertEquals(GoalChange(1, "Mornings", 50), review.improved)
        val tooShort = GoalWeek(3, "New", week(monday, done, done), week(lastMonday, failed, failed), true)
        assertNull(WeeklyReviewRules.review(listOf(tooShort), 0, 0).improved)
    }

    @Test
    fun theWorstGoalNeedsThreeDueDaysAndSixtyPercentFailures() {
        val bad = GoalWeek(1, "Exercise", week(monday, failed, failed, failed, done), emptyList(), true)
        val fine = GoalWeek(2, "Read", week(monday, done, done, failed, done), emptyList(), true)
        assertEquals(GoalFailureRate(1, "Exercise", 3, 4), WeeklyReviewRules.review(listOf(bad, fine), 0, 0).worst)
        assertNull(WeeklyReviewRules.review(listOf(fine), 0, 0).worst)
        val twoDays = GoalWeek(3, "New", week(monday, failed, failed), emptyList(), true)
        assertNull(WeeklyReviewRules.review(listOf(twoDays), 0, 0).worst)
    }

    @Test
    fun reasonsAndTriggersAreCountedFromTheWeeksFailures() {
        val records = listOf(
            record(monday, 0, failed, reason = "Busy", trigger = "Bored"),
            record(monday, 1, failed, reason = "Busy", trigger = "Tired"),
            record(monday, 2, failed, reason = "Forgot", trigger = "Bored"),
            record(monday, 3, done),
        )
        val review = WeeklyReviewRules.review(listOf(GoalWeek(1, "Study", records, emptyList(), true)), 0, 0)
        assertEquals(listOf("Busy" to 2, "Forgot" to 1), review.reasons)
        assertEquals(listOf("Bored" to 2, "Tired" to 1), review.triggers)
    }

    @Test
    fun aFailingGoalWithNoMinimumIsToldToAddOneAndOneWithAMinimumToPause() {
        val noMinimum = GoalWeek(1, "Exercise", week(monday, failed, failed, failed, done), emptyList(), hasMinimum = false)
        assertEquals(Recommendation.AddMinimum(1), WeeklyReviewRules.review(listOf(noMinimum), 0, 0).recommendation)
        val withMinimum = GoalWeek(2, "Exercise", week(monday, failed, failed, failed, failed, done), emptyList(), hasMinimum = true)
        assertEquals(Recommendation.PauseIt(2), WeeklyReviewRules.review(listOf(withMinimum), 0, 0).recommendation)
        val sixtyFive = GoalWeek(3, "Exercise", week(monday, failed, failed, failed, done, done), emptyList(), hasMinimum = true)
        assertNull(WeeklyReviewRules.review(listOf(sixtyFive), 0, 0).recommendation)
    }

    @Test
    fun lateCompletionsSuggestMovingTheWindowAheadOfOtherAdvice() {
        val late = (0 until 4).map { record(monday, it, done, late = 30) }
        val goals = listOf(
            GoalWeek(1, "Wake up", late, emptyList(), true),
            GoalWeek(2, "Exercise", week(monday, failed, failed, failed, failed), emptyList(), false),
        )
        assertEquals(Recommendation.MoveWindow(1, 30), WeeklyReviewRules.review(goals, 0, 0).recommendation)
    }

    @Test
    fun everyGoalStartsAsKeepAndTheUsersChoiceIsSaved() {
        val goals = listOf(GoalWeek(1, "A", week(monday, done), emptyList(), true), GoalWeek(2, "B", week(monday, done), emptyList(), true))
        val review = WeeklyReviewRules.review(goals, 0, 0)
        assertTrue(review.decisions.values.all { it == NextWeek.KEEP })
        val changed = WeeklyReviewRules.withDecision(review, 2, NextWeek.PAUSE)
        assertEquals(mapOf(1L to NextWeek.KEEP, 2L to NextWeek.PAUSE), changed.decisions)
        assertEquals(review, WeeklyReviewRules.withDecision(review, 99, NextWeek.DELETE))
    }

    @Test
    fun anEmptyWeekGivesZerosAndNoStatements() {
        val review = WeeklyReviewRules.review(emptyList(), 0, 0)
        assertEquals(0, review.habits)
        assertEquals(0, review.completionPercent)
        assertNull(review.worst)
        assertNull(review.improved)
        assertNull(review.recommendation)
    }
}
