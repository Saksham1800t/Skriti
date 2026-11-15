package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalMeaning
import com.example.dailygoals.model.GoalMeasurement
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.MeasureType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryRulesTest {
    private val today = LocalDate.of(2026, 11, 13)

    private val study = Goal(
        id = 1,
        name = "Study",
        schedule = GoalSchedule(startDate = today.minusDays(10)),
        measurement = GoalMeasurement(minimumLabel = "Open the book"),
    )

    private fun check(goal: Goal = study, date: LocalDate = today, status: DayStatus = DayStatus.FAILED, used: Boolean = false) =
        RecoveryRules.check(goal, date, today, status, used)

    @Test
    fun aFailedDoGoalWithAMinimumCanRecoverOnTheSameDay() {
        assertEquals(RecoveryCheck.OK, check())
    }

    @Test
    fun recoveryNeedsAFailureAMinimumAndPermission() {
        assertEquals(RecoveryCheck.NOT_FAILED, check(status = DayStatus.PENDING))
        assertEquals(RecoveryCheck.NOT_FAILED, check(status = DayStatus.DONE))
        assertEquals(RecoveryCheck.NO_MINIMUM, check(study.copy(measurement = GoalMeasurement())))
        assertEquals(RecoveryCheck.NOT_ALLOWED, check(study.copy(meaning = GoalMeaning(recoveryAllowed = false))))
    }

    @Test
    fun recoveryIsRefusedForAnotherDayASecondTimeAndAFailedQuotaPeriod() {
        assertEquals(RecoveryCheck.OTHER_DAY, check(date = today.minusDays(1)))
        assertEquals(RecoveryCheck.ALREADY_USED, check(used = true))
        val weekly = study.copy(schedule = study.schedule.copy(frequency = Frequency.PER_WEEK, count = 3))
        assertEquals(RecoveryCheck.QUOTA_GOAL, check(weekly))
    }

    @Test
    fun avoidGoalsDefaultToNoRecoveryAndDoGoalsToRecovery() {
        assertFalse(RecoveryRules.defaultAllowed(isAvoid = true))
        assertTrue(RecoveryRules.defaultAllowed(isAvoid = false))
    }

    @Test
    fun anAmountGoalRecoversOnlyWhenTheMinimumAmountIsLogged() {
        val run = study.copy(measurement = GoalMeasurement(MeasureType.AMOUNT, "min", target = 30, minimum = 10))
        assertFalse(RecoveryRules.amountRecovers(run, 9))
        assertTrue(RecoveryRules.amountRecovers(run, 10))
        assertFalse(RecoveryRules.amountRecovers(study, 100))
        assertEquals(RecoveryCheck.OK, check(run))
    }

    @Test
    fun notesAreTrimmedLimitedAndOptional() {
        assertNull(FailNotes.clean("   "))
        assertEquals("Stayed up late", FailNotes.clean("  Stayed up late "))
        assertEquals(FailNotes.MAX_NOTE_LENGTH, FailNotes.clean("a".repeat(500))!!.length)
        assertEquals("Tired", FailNotes.cleanTag("Tired"))
        assertNull(FailNotes.cleanTag("Whatever"))
        assertNull(FailNotes.cleanTag(null))
    }

    private val failedOn = LocalDate.of(2026, 11, 13)

    @Test
    fun aFailureCreatesOneStakeDueTheNextDay() {
        val stake = StakeRules.onFailed("No social media tomorrow morning", Intensity.GENTLE, punishmentSkipped = false, paused = false, failedDate = failedOn)
        assertEquals(NewStake(failedOn.plusDays(1), "No social media tomorrow morning"), stake)
    }

    @Test
    fun noStakeWithoutTextOrPunishmentDuringThePauseOrForAPausedGoal() {
        assertNull(StakeRules.onFailed(null, Intensity.GENTLE, false, false, failedOn))
        assertNull(StakeRules.onFailed("  ", Intensity.GENTLE, false, false, failedOn))
        assertNull(StakeRules.onFailed("Walk", Intensity.NONE, false, false, failedOn))
        assertNull(StakeRules.onFailed("Walk", Intensity.STRONG, punishmentSkipped = true, paused = false, failedDate = failedOn))
        assertNull(StakeRules.onFailed("Walk", Intensity.STRONG, punishmentSkipped = false, paused = true, failedDate = failedOn))
    }

    @Test
    fun correctingAFailureCancelsOnlyAPendingStake() {
        assertEquals(StakeStatus.CANCELLED, StakeRules.onCorrected(StakeStatus.PENDING))
        assertNull(StakeRules.onCorrected(StakeStatus.DONE))
        assertNull(StakeRules.onCorrected(StakeStatus.DISMISSED))
        assertNull(StakeRules.onCorrected(StakeStatus.CANCELLED))
    }

    @Test
    fun unfinishedStakesCarryOverUntilTickedOrDismissed() {
        val stakes = listOf(
            StakeStatus.PENDING to failedOn.plusDays(1),
            StakeStatus.PENDING to failedOn.plusDays(5),
            StakeStatus.DONE to failedOn.plusDays(1),
            StakeStatus.CANCELLED to failedOn.plusDays(1),
        )
        assertEquals(emptyList<Int>(), StakeRules.due(stakes, failedOn))
        assertEquals(listOf(0), StakeRules.due(stakes, failedOn.plusDays(1)))
        assertEquals(listOf(0), StakeRules.due(stakes, failedOn.plusDays(4)))
        assertEquals(listOf(0, 1), StakeRules.due(stakes, failedOn.plusDays(5)))
    }

    @Test
    fun detailChoicesOutsideTheListAreDropped() {
        assertEquals("Bored", FailNotes.cleanChoice("Bored", FailNotes.TRIGGERS))
        assertNull(FailNotes.cleanChoice("Whatever", FailNotes.TRIGGERS))
        assertNull(FailNotes.cleanChoice(null, FailNotes.PLACES))
        assertEquals(3, FailNotes.cleanMood(3))
        assertNull(FailNotes.cleanMood(0))
        assertNull(FailNotes.cleanMood(6))
    }

    @Test
    fun aPlanNeedsBothHalves() {
        assertEquals("When I want to smoke, I will wait 10 minutes", GoalMeaning(ifText = "I want to smoke", thenText = "wait 10 minutes").plan)
        assertNull(GoalMeaning(ifText = "I want to smoke").plan)
        assertNull(GoalMeaning(thenText = "wait", ifText = " ").plan)
    }
}
