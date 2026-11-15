package com.example.dailygoals.model

import java.time.LocalDate

enum class DayStatus { PENDING, MINIMUM, DONE, FAILED }

val DayStatus.isMet: Boolean get() = this == DayStatus.DONE || this == DayStatus.MINIMUM

enum class FailReason { MANUAL, WINDOW_END, DAY_END }

/** How a failure was found: the user said so, the app saw a tempting app open and the user confirmed, or the user changed the result. */
enum class FailSource { SELF_REPORTED, DETECTED, CORRECTED }

data class CheckIn(
    val goalId: Long,
    val date: LocalDate,
    val status: DayStatus,
    val failReason: FailReason? = null,
    val amount: Int = 0,
    val lateMinutes: Int? = null,
    val punishmentSkipped: Boolean = false,
    val failTag: String? = null,
    val failNote: String? = null,
    val recovered: Boolean = false,
    val failTrigger: String? = null,
    val failMood: Int? = null,
    val failPrevActivity: String? = null,
    val failPlace: String? = null,
    val failSource: FailSource = FailSource.SELF_REPORTED,
)
