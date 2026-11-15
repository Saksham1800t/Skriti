package com.example.dailygoals.model

/** The apps a goal blocks, for a punishment now and for scheduled blocking later. */
/** The apps an avoid goal watches or blocks, and the time of day to watch them; no times means all day. */
data class GoalAvoidance(
    val temptingPackages: Set<String> = emptySet(),
    val watchStart: Int? = null,
    val watchEnd: Int? = null,
)
