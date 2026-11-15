package com.example.dailygoals.data

import com.example.dailygoals.engine.GoalChange
import com.example.dailygoals.engine.GoalFailureRate
import com.example.dailygoals.engine.NextWeek
import com.example.dailygoals.engine.Recommendation
import com.example.dailygoals.engine.WeeklyReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewCodecTest {
    private val review = WeeklyReview(
        habits = 2, completionPercent = 68, failures = 5, rescues = 1, emergencyPauses = 2, bestStreak = 6, consistencyChangePoints = 7,
        improved = GoalChange(1, "Mornings", 18), worst = GoalFailureRate(2, "No social media", 4, 5),
        reasons = listOf("Busy" to 2, "Forgot" to 1), triggers = listOf("Bored" to 3),
        recommendation = Recommendation.AddMinimum(2),
        decisions = mapOf(1L to NextWeek.KEEP, 2L to NextWeek.PAUSE),
    )
    private val names = mapOf(1L to "Mornings", 2L to "No social media")

    @Test
    fun aReviewAndTheUsersChoicesSurviveStorage() {
        val (back, backNames) = ReviewCodec.decode(ReviewCodec.encode(review, names), ReviewCodec.encodeDecisions(review.decisions))!!
        assertEquals(review, back)
        assertEquals(names, backNames)
    }

    @Test
    fun everyKindOfRecommendationRoundTrips() {
        listOf(Recommendation.MoveWindow(1, 30), Recommendation.AddMinimum(2), Recommendation.PauseIt(1), null).forEach { r ->
            val source = review.copy(recommendation = r)
            val (back, _) = ReviewCodec.decode(ReviewCodec.encode(source, names), ReviewCodec.encodeDecisions(source.decisions))!!
            assertEquals(r, back.recommendation)
        }
    }

    @Test
    fun aReviewWithNothingToSayRoundTrips() {
        val empty = review.copy(improved = null, worst = null, reasons = emptyList(), triggers = emptyList(), recommendation = null)
        val (back, _) = ReviewCodec.decode(ReviewCodec.encode(empty, names), ReviewCodec.encodeDecisions(empty.decisions))!!
        assertNull(back.improved)
        assertEquals(empty, back)
    }

    @Test
    fun damagedTextGivesNothingInsteadOfACrash() {
        assertNull(ReviewCodec.decode("not json", "{}"))
        assertNull(ReviewCodec.decode("{}", "{}"))
    }

    @Test
    fun anUnknownChoiceFallsBackToKeep() {
        val (back, _) = ReviewCodec.decode(ReviewCodec.encode(review, names), "{\"1\":\"BOGUS\"}")!!
        assertEquals(NextWeek.KEEP, back.decisions.getValue(1))
    }
}
