package com.example.dailygoals.engine

import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.GoalType

enum class ViolationAnswer { YES, NO, UNANSWERED }

/** The last prompt shown for one app: when, and which goal it was for. */
data class PromptMark(val packageName: String, val goalId: Long, val shownAtMillis: Long)

/**
 * Violation detection for avoid goals. The service only knows which app came to the front, so the question is
 * always asked and the user decides: Yes fails the goal as DETECTED, No dismisses, and an ignored prompt is stored
 * as UNANSWERED and never fails a goal by itself.
 */
object ViolationRules {
    const val MIN_GAP_MILLIS = 10 * 60_000L

    /** Scheduled blocking already stops the app, so the prompt is only for goals that do not block it. */
    fun watches(goal: Goal, packageName: String): Boolean =
        goal.type == GoalType.AVOID && goal.state == GoalState.ACTIVE &&
            packageName in goal.avoidance.temptingPackages && !ScheduledBlocks.applies(goal)

    /** Inside the watch time, or all day when none is set. A range may cross midnight. */
    fun inWatchTime(goal: Goal, minuteOfDay: Int): Boolean {
        val start = goal.avoidance.watchStart ?: return true
        val end = goal.avoidance.watchEnd ?: return true
        return if (start <= end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end
    }

    /**
     * One prompt per app session and at least ten minutes between prompts for the same app.
     * [sessionStart] is when the app came to the front this time; a prompt already shown during it counts.
     */
    fun shouldPrompt(goal: Goal, packageName: String, minuteOfDay: Int, nowMillis: Long, sessionStartMillis: Long, last: PromptMark?): Boolean {
        if (!watches(goal, packageName) || !inWatchTime(goal, minuteOfDay)) return false
        val previous = last?.takeIf { it.packageName == packageName } ?: return true
        if (previous.shownAtMillis >= sessionStartMillis) return false
        return nowMillis - previous.shownAtMillis >= MIN_GAP_MILLIS
    }

    /** The goals a prompt for [packageName] would be about right now. */
    fun goalsToAsk(goals: List<Goal>, packageName: String, minuteOfDay: Int): List<Goal> =
        goals.filter { watches(it, packageName) && inWatchTime(it, minuteOfDay) }

    /** A failure marked by this answer: only a confirmed Yes fails the goal. */
    fun failsGoal(answer: ViolationAnswer): Boolean = answer == ViolationAnswer.YES
}
