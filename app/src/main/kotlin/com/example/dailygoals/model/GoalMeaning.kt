package com.example.dailygoals.model

/** The goal's own words about why it matters, what a failure means, a self-set penalty, and the way back. */
data class GoalMeaning(
    val why: String? = null,
    val ifFail: String? = null,
    val stake: String? = null,
    val recoveryAllowed: Boolean = true,
    /** "When ___, I will ___": a small plan shown when the goal is at risk. */
    val ifText: String? = null,
    val thenText: String? = null,
) {
    val plan: String? get() = if (ifText.isNullOrBlank() || thenText.isNullOrBlank()) null else "When ${ifText.trim()}, I will ${thenText.trim()}"

    val hasWords: Boolean get() = !why.isNullOrBlank() || !ifFail.isNullOrBlank()

    companion object {
        const val MAX_LENGTH = 200
    }
}
