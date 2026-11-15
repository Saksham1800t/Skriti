package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.isMet

/** One goal's days in the review week and the week before it. */
data class GoalWeek(
    val goalId: Long,
    val name: String,
    val thisWeek: List<DayRecord>,
    val previousWeek: List<DayRecord>,
    val hasMinimum: Boolean,
)

enum class NextWeek { KEEP, MODIFY, PAUSE, DELETE }

sealed interface Recommendation {
    val goalId: Long

    /** Most completions are late, so the window may be too early. */
    data class MoveWindow(override val goalId: Long, val averageLateMinutes: Int) : Recommendation

    /** A goal that fails often has no smaller version to fall back on. */
    data class AddMinimum(override val goalId: Long) : Recommendation

    /** A goal with a minimum still fails almost every time. */
    data class PauseIt(override val goalId: Long) : Recommendation
}

data class GoalChange(val goalId: Long, val name: String, val deltaPoints: Int)

data class GoalFailureRate(val goalId: Long, val name: String, val failed: Int, val due: Int)

data class WeeklyReview(
    val habits: Int,
    val completionPercent: Int,
    val failures: Int,
    val rescues: Int,
    val emergencyPauses: Int,
    val bestStreak: Int,
    /** Null when there was no earlier week to compare with. */
    val consistencyChangePoints: Int?,
    val improved: GoalChange?,
    val worst: GoalFailureRate?,
    val reasons: List<Pair<String, Int>>,
    val triggers: List<Pair<String, Int>>,
    val recommendation: Recommendation?,
    /** Every goal starts as Keep; the user changes it on the review. */
    val decisions: Map<Long, NextWeek>,
)

/** The Sunday review, worked out from the same records as the pattern figures. */
object WeeklyReviewRules {
    const val MIN_DUE_FOR_STATEMENT = 3
    const val WORST_RATE_PERCENT = 60
    const val PAUSE_RATE_PERCENT = 80
    const val IMPROVEMENT_POINTS = 5
    private const val LATE_MINUTES = 15

    private fun percent(part: Int, whole: Int) = if (whole == 0) 0 else (part * 100 + whole / 2) / whole

    private fun due(records: List<DayRecord>) = records.filter { it.status != DayStatus.PENDING }

    private fun rate(records: List<DayRecord>): Int = due(records).let { percent(it.count { r -> r.status.isMet }, it.size) }

    fun review(goals: List<GoalWeek>, rescuesUsed: Int, emergencyPausesUsed: Int): WeeklyReview {
        val thisDue = goals.flatMap { due(it.thisWeek) }
        val previousDue = goals.flatMap { due(it.previousWeek) }
        val completion = percent(thisDue.count { it.status.isMet }, thisDue.size)
        val previousCompletion = percent(previousDue.count { it.status.isMet }, previousDue.size)
        val failed = thisDue.filter { it.status == DayStatus.FAILED }
        val improved = goals.mapNotNull { goal ->
            val now = due(goal.thisWeek)
            val before = due(goal.previousWeek)
            if (now.size < MIN_DUE_FOR_STATEMENT || before.size < MIN_DUE_FOR_STATEMENT) return@mapNotNull null
            GoalChange(goal.goalId, goal.name, rate(goal.thisWeek) - rate(goal.previousWeek))
        }.filter { it.deltaPoints >= IMPROVEMENT_POINTS }.maxByOrNull { it.deltaPoints }
        val worst = goals.map { goal ->
            val records = due(goal.thisWeek)
            GoalFailureRate(goal.goalId, goal.name, records.count { it.status == DayStatus.FAILED }, records.size)
        }.filter { it.due >= MIN_DUE_FOR_STATEMENT && it.failed > 0 }
            .maxWithOrNull(compareBy({ percent(it.failed, it.due) }, { it.failed }, { -it.goalId }))
            ?.takeIf { percent(it.failed, it.due) >= WORST_RATE_PERCENT }
        return WeeklyReview(
            habits = goals.size,
            completionPercent = completion,
            failures = failed.size,
            rescues = rescuesUsed,
            emergencyPauses = emergencyPausesUsed,
            bestStreak = goals.maxOfOrNull { streak(it.thisWeek) } ?: 0,
            consistencyChangePoints = if (previousDue.isEmpty()) null else completion - previousCompletion,
            improved = improved,
            worst = worst,
            reasons = counts(failed.mapNotNull { it.reason }),
            triggers = counts(failed.mapNotNull { it.trigger }),
            recommendation = recommend(goals, worst),
            decisions = goals.associate { it.goalId to NextWeek.KEEP },
        )
    }

    private fun counts(labels: List<String>): List<Pair<String, Int>> =
        labels.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }.take(3)

    private fun streak(records: List<DayRecord>): Int {
        var run = 0
        var best = 0
        due(records).sortedBy { it.date }.forEach { record ->
            run = if (record.status.isMet) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }

    /** One suggestion at most: lateness first, because it is the cheapest change, then the worst goal. */
    private fun recommend(goals: List<GoalWeek>, worst: GoalFailureRate?): Recommendation? {
        goals.forEach { goal ->
            val lates = due(goal.thisWeek).filter { it.status.isMet }.mapNotNull { it.lateMinutes }
            if (lates.size >= MIN_DUE_FOR_STATEMENT) {
                val late = lates.filter { it > 0 }
                if (late.size * 2 > lates.size && late.average() >= LATE_MINUTES) return Recommendation.MoveWindow(goal.goalId, late.average().toInt())
            }
        }
        val target = worst ?: return null
        val goal = goals.first { it.goalId == target.goalId }
        return when {
            percent(target.failed, target.due) >= PAUSE_RATE_PERCENT && goal.hasMinimum -> Recommendation.PauseIt(target.goalId)
            !goal.hasMinimum -> Recommendation.AddMinimum(target.goalId)
            else -> null
        }
    }

    /** The user's choice for each goal replaces the default Keep. */
    fun withDecision(review: WeeklyReview, goalId: Long, choice: NextWeek): WeeklyReview =
        if (goalId !in review.decisions) review else review.copy(decisions = review.decisions + (goalId to choice))
}
