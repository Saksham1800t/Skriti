package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Goal
import java.time.LocalDate

enum class SuggestionKind { LOWER_TARGET, RAISE }

/** [days] is how many recent days the advice rests on: days at the minimum, or the run at the target. */
data class Suggestion(val goalId: Long, val kind: SuggestionKind, val days: Int)

/**
 * Adaptive suggestions: advice only, never a change to a goal. Each kind is offered once per goal, so a goal
 * that was dismissed is not nagged about again.
 */
object SuggestionRules {
    const val WINDOW_DAYS = 14
    const val MINIMUM_DAYS_TO_LOWER = 10
    const val RUN_TO_RAISE = 14

    fun key(goalId: Long, kind: SuggestionKind) = "$goalId:${kind.name}"

    /**
     * [records] are the goal's due days up to yesterday or today. Lowering needs a smaller step to exist, since
     * otherwise the goal cannot end at the minimum.
     */
    fun forGoal(goal: Goal, records: List<DayRecord>, today: LocalDate, dismissed: Set<String>): Suggestion? {
        if (goal.schedule.isQuota) return null
        val recent = records.filter { it.date > today.minusDays(WINDOW_DAYS.toLong()) && it.date <= today }
        if (goal.measurement.hasMinimumStep) {
            val atMinimum = recent.count { it.status == DayStatus.MINIMUM }
            if (atMinimum >= MINIMUM_DAYS_TO_LOWER && key(goal.id, SuggestionKind.LOWER_TARGET) !in dismissed) {
                return Suggestion(goal.id, SuggestionKind.LOWER_TARGET, atMinimum)
            }
        }
        val run = records.sortedByDescending { it.date }.takeWhile { it.status == DayStatus.DONE }.size
        if (run >= RUN_TO_RAISE && key(goal.id, SuggestionKind.RAISE) !in dismissed) {
            return Suggestion(goal.id, SuggestionKind.RAISE, run)
        }
        return null
    }
}
