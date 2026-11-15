package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailSource
import com.example.dailygoals.model.isMet
import java.time.DayOfWeek
import java.time.LocalDate

/** One judged due day of a goal, with what is known about it. Times are minutes since midnight. */
data class DayRecord(
    val date: LocalDate,
    val status: DayStatus,
    val failedAtMinute: Int? = null,
    val reason: String? = null,
    val trigger: String? = null,
    val lateMinutes: Int? = null,
    val source: FailSource = FailSource.SELF_REPORTED,
)

sealed interface Observation {
    /** Weekday and weekend success rates differ by at least 20 points. */
    data class WeekdayWeekend(val weekdayPercent: Int, val weekendPercent: Int) : Observation

    /** At least 40% of failures fall in one two-hour band starting at [startHour]. */
    data class FailureBand(val startHour: Int, val percent: Int) : Observation

    /** One reason or trigger makes up at least 40% of failures. */
    data class CommonCause(val label: String, val isTrigger: Boolean, val percent: Int) : Observation

    /** Most completions come after the window opened long ago, so the window may be too early. */
    data class LateCompletions(val averageMinutes: Int, val percentLate: Int) : Observation
}

data class PatternReport(
    val dueDays: Int,
    val met: Int,
    val failures: Int,
    val successPercent: Int,
    val worstWeekday: DayOfWeek?,
    val topReason: String?,
    val topTrigger: String?,
    val averageLateMinutes: Int?,
    val detected: Int,
    val selfReported: Int,
    val corrected: Int,
    val observations: List<Observation>,
    /** Statements only appear with enough data: at least 10 due days, and 5 failures for failure statements. */
    val hasEnoughData: Boolean,
)

/** Plain statistics over a goal's own history. No model, no network. */
object PatternRules {
    const val MIN_DUE_DAYS = 10
    const val MIN_FAILURES = 5
    const val MIN_GROUP_DAYS = 3
    const val WEEKDAY_GAP_POINTS = 20
    const val SHARE_PERCENT = 40
    const val BAND_HOURS = 2
    const val LATE_MIN_COMPLETIONS = 5
    const val LATE_SHARE_PERCENT = 50

    private fun percent(part: Int, whole: Int) = if (whole == 0) 0 else (part * 100 + whole / 2) / whole

    fun analyse(records: List<DayRecord>): PatternReport {
        val due = records.filter { it.status != DayStatus.PENDING }
        val failed = due.filter { it.status == DayStatus.FAILED }
        val met = due.filter { it.status.isMet }
        val enough = due.size >= MIN_DUE_DAYS
        val enoughFailures = enough && failed.size >= MIN_FAILURES
        val observations = buildList {
            if (enough) {
                weekdayWeekend(due)?.let(::add)
                lateCompletions(met)?.let(::add)
            }
            if (enoughFailures) {
                failureBand(failed)?.let(::add)
                commonCause(failed.mapNotNull { it.reason }, isTrigger = false)?.let(::add)
                commonCause(failed.mapNotNull { it.trigger }, isTrigger = true)?.let(::add)
            }
        }
        val lates = met.mapNotNull { it.lateMinutes }
        return PatternReport(
            dueDays = due.size,
            met = met.size,
            failures = failed.size,
            successPercent = percent(met.size, due.size),
            worstWeekday = failed.groupingBy { it.date.dayOfWeek }.eachCount().maxByOrNull { it.value }?.key,
            topReason = top(failed.mapNotNull { it.reason }),
            topTrigger = top(failed.mapNotNull { it.trigger }),
            averageLateMinutes = lates.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            detected = failed.count { it.source == FailSource.DETECTED },
            selfReported = failed.count { it.source == FailSource.SELF_REPORTED },
            corrected = due.count { it.source == FailSource.CORRECTED },
            observations = observations,
            hasEnoughData = enough,
        )
    }

    private fun top(labels: List<String>): String? =
        labels.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).firstOrNull()?.key

    private fun isWeekend(day: DayOfWeek) = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY

    private fun weekdayWeekend(due: List<DayRecord>): Observation.WeekdayWeekend? {
        val (weekend, weekday) = due.partition { isWeekend(it.date.dayOfWeek) }
        if (weekend.size < MIN_GROUP_DAYS || weekday.size < MIN_GROUP_DAYS) return null
        val weekdayPercent = percent(weekday.count { it.status.isMet }, weekday.size)
        val weekendPercent = percent(weekend.count { it.status.isMet }, weekend.size)
        return if (kotlin.math.abs(weekdayPercent - weekendPercent) >= WEEKDAY_GAP_POINTS) Observation.WeekdayWeekend(weekdayPercent, weekendPercent) else null
    }

    private fun failureBand(failed: List<DayRecord>): Observation.FailureBand? {
        val minutes = failed.mapNotNull { it.failedAtMinute }
        if (minutes.size < MIN_FAILURES) return null
        val best = (0 until 24).map { start ->
            val count = minutes.count { m -> (0 until BAND_HOURS).any { (start + it) % 24 == m / 60 } }
            start to count
        }.maxWith(compareBy({ it.second }, { -it.first }))
        val share = percent(best.second, minutes.size)
        return if (share >= SHARE_PERCENT) Observation.FailureBand(best.first, share) else null
    }

    private fun commonCause(labels: List<String>, isTrigger: Boolean): Observation.CommonCause? {
        if (labels.size < MIN_FAILURES) return null
        val best = labels.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).first()
        val share = percent(best.value, labels.size)
        return if (share >= SHARE_PERCENT) Observation.CommonCause(best.key, isTrigger, share) else null
    }

    private fun lateCompletions(met: List<DayRecord>): Observation.LateCompletions? {
        val lates = met.mapNotNull { it.lateMinutes }
        if (lates.size < LATE_MIN_COMPLETIONS) return null
        val late = lates.filter { it > 0 }
        val share = percent(late.size, lates.size)
        return if (share >= LATE_SHARE_PERCENT) Observation.LateCompletions(late.average().toInt(), share) else null
    }
}
