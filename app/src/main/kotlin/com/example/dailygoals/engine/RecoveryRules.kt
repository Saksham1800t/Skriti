package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Goal
import java.time.LocalDate

enum class RecoveryCheck { OK, NOT_FAILED, NOT_ALLOWED, NO_MINIMUM, OTHER_DAY, ALREADY_USED, QUOTA_GOAL }

/**
 * After a failure the user can still do the minimum version the same day. It is limited so it stays honest:
 * same date only, once per goal per day, never for a failed week or month, and only for goals that have a
 * minimum and allow recovery. A recovered day counts as met (MINIMUM), flagged as recovered.
 */
object RecoveryRules {
    fun check(goal: Goal, date: LocalDate, today: LocalDate, status: DayStatus, alreadyRecovered: Boolean): RecoveryCheck = when {
        status != DayStatus.FAILED -> RecoveryCheck.NOT_FAILED
        !goal.meaning.recoveryAllowed -> RecoveryCheck.NOT_ALLOWED
        goal.schedule.isQuota -> RecoveryCheck.QUOTA_GOAL
        !goal.measurement.hasMinimumStep -> RecoveryCheck.NO_MINIMUM
        date != today -> RecoveryCheck.OTHER_DAY
        alreadyRecovered -> RecoveryCheck.ALREADY_USED
        else -> RecoveryCheck.OK
    }

    /** A new DO goal allows recovery; an avoid goal does not, because a slip cannot be undone. */
    fun defaultAllowed(isAvoid: Boolean): Boolean = !isAvoid

    /** An amount goal recovers by logging at least its minimum on the same day. */
    fun amountRecovers(goal: Goal, amount: Int): Boolean = goal.measurement.isAmount && amount >= goal.measurement.minimum
}

/** The quick picks on the "What happened?" step. */
object FailNotes {
    val TAGS = listOf("Tired", "Forgot", "Stress", "Temptation", "Busy", "Other")
    const val MAX_NOTE_LENGTH = 280
    val TRIGGERS = listOf("Bored", "Stressed", "Tired", "Social", "Craving", "Habit", "Other")
    val BEFORE = listOf("Working", "Studying", "Scrolling", "Eating", "Resting")
    val PLACES = listOf("Home", "Work", "Commute", "Outside", "Other")
    val MOODS = 1..5

    fun cleanChoice(value: String?, allowed: List<String>): String? = value?.takeIf { it in allowed }

    fun cleanMood(value: Int?): Int? = value?.takeIf { it in MOODS }

    fun clean(note: String): String? = note.trim().take(MAX_NOTE_LENGTH).ifEmpty { null }

    fun cleanTag(tag: String?): String? = tag?.takeIf { it in TAGS }
}

enum class StakeStatus { PENDING, DONE, DISMISSED, CANCELLED }

data class NewStake(val dueDate: LocalDate, val text: String)

/**
 * A stake is the penalty a goal set for itself. A failure creates at most one, due the next day. It is
 * cancelled when the failure is corrected or rescued, never created during the emergency pause or for a
 * goal with no punishment, and a stake already done or dismissed stays in history.
 */
object StakeRules {
    fun onFailed(stakeText: String?, intensity: Intensity, punishmentSkipped: Boolean, paused: Boolean, failedDate: LocalDate): NewStake? {
        val text = stakeText?.trim().orEmpty()
        if (text.isEmpty() || intensity == Intensity.NONE || punishmentSkipped || paused) return null
        return NewStake(failedDate.plusDays(1), text)
    }

    /** What a pending stake becomes when its failure is corrected; null when nothing changes. */
    fun onCorrected(current: StakeStatus): StakeStatus? = if (current == StakeStatus.PENDING) StakeStatus.CANCELLED else null

    /** Pending stakes whose day has come; unfinished ones carry over until ticked or dismissed. */
    fun due(stakes: List<Pair<StakeStatus, LocalDate>>, today: LocalDate): List<Int> =
        stakes.mapIndexedNotNull { index, (status, dueDate) -> index.takeIf { status == StakeStatus.PENDING && !dueDate.isAfter(today) } }
}
