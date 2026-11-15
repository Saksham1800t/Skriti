package com.example.dailygoals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRulesTest {
    private val now = 1_000_000L
    private val midnight = 5_000_000L

    private fun failed(name: String, stake: String? = null, blockEnd: Long? = null) = FailedGoal(name, stake, blockEnd)

    @Test
    fun onlyAStrongFailureOutsideThePauseChangesTheWallpaper() {
        assertTrue(WallpaperRules.triggers(Intensity.STRONG, skippedByPause = false))
        assertFalse(WallpaperRules.triggers(Intensity.STRONG, skippedByPause = true))
        assertFalse(WallpaperRules.triggers(Intensity.MODERATE, skippedByPause = false))
        assertFalse(WallpaperRules.triggers(Intensity.NONE, skippedByPause = false))
    }

    @Test
    fun noFailedGoalMeansNoWarning() {
        assertNull(WallpaperRules.state(emptyList(), WallpaperRestore.MIDNIGHT, now, midnight))
    }

    @Test
    fun oneWallpaperListsEveryFailedGoalOnceAndEndsAtMidnightByDefault() {
        val state = WallpaperRules.state(listOf(failed("Study"), failed("No social"), failed("Study")), WallpaperRestore.MIDNIGHT, now, midnight)!!
        assertEquals(listOf("Study", "No social"), state.goalNames)
        assertEquals(midnight, state.endsAtMillis)
    }

    @Test
    fun theBlockEndOptionUsesTheLongestRunningBlockAndFallsBackToMidnight() {
        val goals = listOf(failed("A", blockEnd = 2_000_000), failed("B", blockEnd = 3_000_000), failed("C", blockEnd = 500_000))
        assertEquals(3_000_000L, WallpaperRules.state(goals, WallpaperRestore.BLOCK_END, now, midnight)!!.endsAtMillis)
        assertEquals(midnight, WallpaperRules.state(listOf(failed("A")), WallpaperRestore.BLOCK_END, now, midnight)!!.endsAtMillis)
        assertEquals(midnight, WallpaperRules.state(listOf(failed("A", blockEnd = 500_000)), WallpaperRestore.BLOCK_END, now, midnight)!!.endsAtMillis)
    }

    @Test
    fun aWarningThatAlreadyEndedShowsNothing() {
        assertNull(WallpaperRules.state(listOf(failed("A")), WallpaperRestore.MIDNIGHT, midnight, midnight))
    }

    @Test
    fun theFirstNonBlankPenaltyIsShown() {
        val state = WallpaperRules.state(listOf(failed("A", stake = "  "), failed("B", stake = "No phone tomorrow")), WallpaperRestore.MIDNIGHT, now, midnight)!!
        assertEquals("No phone tomorrow", state.stake)
        assertNull(WallpaperRules.state(listOf(failed("A")), WallpaperRestore.MIDNIGHT, now, midnight)!!.stake)
    }

    @Test
    fun longListsAreShortenedWithAMoreLine() {
        val more = { n: Int -> "+$n more" }
        assertEquals(listOf("A", "B", "C"), WallpaperRules.displayNames(listOf("A", "B", "C"), more))
        assertEquals(listOf("A", "B", "+3 more"), WallpaperRules.displayNames(listOf("A", "B", "C", "D", "E"), more))
    }

    @Test
    fun theCountdownReadsMinutesAndSecondsThenHours() {
        assertEquals("00:00", WallpaperRules.countdown(0))
        assertEquals("00:01", WallpaperRules.countdown(1))
        assertEquals("59:59", WallpaperRules.countdown(59 * 60_000 + 59_000))
        assertEquals("1:00:00", WallpaperRules.countdown(3_600_000))
        assertEquals("14:05:09", WallpaperRules.countdown(((14 * 60 + 5) * 60 + 9) * 1000L))
        assertEquals("00:00", WallpaperRules.countdown(-5))
    }

    @Test
    fun theRingFractionRunsFromOneToZero() {
        assertEquals(1f, WallpaperRules.fractionLeft(0, 100, 0), 0.001f)
        assertEquals(0.5f, WallpaperRules.fractionLeft(0, 100, 50), 0.001f)
        assertEquals(0f, WallpaperRules.fractionLeft(0, 100, 200), 0.001f)
        assertEquals(0f, WallpaperRules.fractionLeft(100, 100, 100), 0.001f)
    }
}
