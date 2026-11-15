package com.example.dailygoals.engine

import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalAvoidance
import com.example.dailygoals.model.GoalDiscipline
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.GoalType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViolationRulesTest {
    private val insta = "com.instagram.android"
    private val work = Goal(
        id = 1,
        name = "No Instagram during work",
        type = GoalType.AVOID,
        schedule = GoalSchedule(startDate = LocalDate.of(2026, 11, 1)),
        avoidance = GoalAvoidance(setOf(insta), watchStart = 9 * 60, watchEnd = 18 * 60),
    )

    @Test
    fun onlyAnActiveAvoidGoalListingTheAppWatchesIt() {
        assertTrue(ViolationRules.watches(work, insta))
        assertFalse(ViolationRules.watches(work, "com.youtube"))
        assertFalse(ViolationRules.watches(work.copy(type = GoalType.DO), insta))
        assertFalse(ViolationRules.watches(work.copy(state = GoalState.ARCHIVED), insta))
    }

    @Test
    fun aGoalThatBlocksTheAppOnScheduleNeedsNoPrompt() {
        val blocking = work.copy(discipline = GoalDiscipline(scheduledBlockEnabled = true))
        assertFalse(ViolationRules.watches(blocking, insta))
    }

    @Test
    fun theWatchTimeIsAHalfOpenRangeOrAllDayWhenNoneIsSet() {
        assertFalse(ViolationRules.inWatchTime(work, 8 * 60 + 59))
        assertTrue(ViolationRules.inWatchTime(work, 9 * 60))
        assertTrue(ViolationRules.inWatchTime(work, 17 * 60 + 59))
        assertFalse(ViolationRules.inWatchTime(work, 18 * 60))
        assertTrue(ViolationRules.inWatchTime(work.copy(avoidance = GoalAvoidance(setOf(insta))), 3 * 60))
    }

    @Test
    fun aWatchTimeMayCrossMidnight() {
        val night = work.copy(avoidance = GoalAvoidance(setOf(insta), watchStart = 22 * 60, watchEnd = 6 * 60))
        assertTrue(ViolationRules.inWatchTime(night, 23 * 60))
        assertTrue(ViolationRules.inWatchTime(night, 2 * 60))
        assertFalse(ViolationRules.inWatchTime(night, 12 * 60))
    }

    @Test
    fun theFirstOpenInsideTheWatchTimeIsPromptedAndOutsideItIsNot() {
        assertTrue(ViolationRules.shouldPrompt(work, insta, 10 * 60, 1_000_000, 900_000, null))
        assertFalse(ViolationRules.shouldPrompt(work, insta, 20 * 60, 1_000_000, 900_000, null))
    }

    @Test
    fun oneAppSessionGetsOnePrompt() {
        val shown = PromptMark(insta, 1, 950_000)
        assertFalse(ViolationRules.shouldPrompt(work, insta, 10 * 60, 1_100_000, 900_000, shown))
    }

    @Test
    fun aNewSessionNeedsTenMinutesSinceThePreviousPrompt() {
        val shown = PromptMark(insta, 1, 0)
        val tenMinutes = ViolationRules.MIN_GAP_MILLIS
        assertFalse(ViolationRules.shouldPrompt(work, insta, 10 * 60, tenMinutes - 1, 1_000, shown))
        assertTrue(ViolationRules.shouldPrompt(work, insta, 10 * 60, tenMinutes, 1_000, shown))
        val otherApp = PromptMark("com.youtube", 1, 0)
        assertTrue(ViolationRules.shouldPrompt(work, insta, 10 * 60, 1_000, 500, otherApp))
    }

    @Test
    fun onlyAYesFailsTheGoal() {
        assertTrue(ViolationRules.failsGoal(ViolationAnswer.YES))
        assertFalse(ViolationRules.failsGoal(ViolationAnswer.NO))
        assertFalse(ViolationRules.failsGoal(ViolationAnswer.UNANSWERED))
    }

    @Test
    fun aPromptNamesEveryGoalWatchingThatAppAtThatTime() {
        val other = work.copy(id = 2, name = "No social", avoidance = GoalAvoidance(setOf(insta)))
        val quiet = work.copy(id = 3, avoidance = GoalAvoidance(setOf(insta), watchStart = 20 * 60, watchEnd = 22 * 60))
        assertEquals(listOf(1L, 2L), ViolationRules.goalsToAsk(listOf(work, other, quiet), insta, 10 * 60).map { it.id })
    }
}
