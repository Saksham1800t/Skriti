package com.example.dailygoals.model

/** The goal's own words about why it matters, what a failure means, a self-set penalty, and the way back. */
data class GoalMeaning(
    val why: String? = null,
    val ifFail: String? = null,
    val stake: String? = null,
    val recoveryAllowed: Boolean = true,
) {
    val hasWords: Boolean get() = !why.isNullOrBlank() || !ifFail.isNullOrBlank()

    companion object {
        const val MAX_LENGTH = 200
    }
}
