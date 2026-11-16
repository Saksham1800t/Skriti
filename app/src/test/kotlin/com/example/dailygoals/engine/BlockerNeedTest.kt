package com.example.dailygoals.engine

import com.example.dailygoals.model.GoalType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerNeedTest {
    private val apps = setOf("com.instagram.android")

    @Test
    fun anAvoidGoalWithAppsNeedsTheService() {
        assertTrue(BlockerNeed.needed(GoalType.AVOID, Intensity.NONE, apps))
    }

    @Test
    fun aDoGoalNeedsItOnlyWhenAFailureBlocksApps() {
        assertFalse(BlockerNeed.needed(GoalType.DO, Intensity.NONE, apps))
        assertTrue(BlockerNeed.needed(GoalType.DO, Intensity.MODERATE, apps))
    }

    @Test
    fun noAppsMeansNoNeed() {
        assertFalse(BlockerNeed.needed(GoalType.AVOID, Intensity.STRONG, emptySet()))
    }
}
