package com.example.dailygoals.engine

import com.example.dailygoals.model.CheckIn
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Goal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class InsightRange(val days: Int?) { WEEK(7), MONTH(30), ALL(null) }

/** One failure as the Mistakes view lists it. */
data class Mistake(
    val goalId: Long,
    val goalName: String,
    val date: LocalDate,
    val minuteOfDay: Int?,
    val tag: String?,
    val note: String?,
    val trigger: String?,
    val mood: Int?,
    val before: String?,
    val place: String?,
    val detected: Boolean,
)

/** Turns a goal's stored results into the records the pattern and review rules read. */
object GoalHistory {
    /**
     * Every due day from the start up to today. A past due day with no result counts as missed, the same way
     * consistency does. Days that were paused, rescued or skipped by a time zone change are left out.
     */
    fun records(goal: Goal, checkIns: List<CheckIn>, today: LocalDate, neutral: Set<LocalDate>, dayEndMinute: Int, zone: ZoneId): List<DayRecord> {
        if (goal.schedule.isQuota) return emptyList()
        val byDate = checkIns.filter { it.goalId == goal.id }.associateBy { it.date }
        return generateSequence(goal.schedule.startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.mapNotNull { date ->
            if (date in neutral || !DueDates.isDue(goal.schedule, date)) return@mapNotNull null
            val row = byDate[date]
            val status = row?.status ?: DayStatus.PENDING
            if (date == today && status == DayStatus.PENDING) return@mapNotNull null
            if (status == DayStatus.PENDING) return@mapNotNull DayRecord(date, DayStatus.FAILED)
            DayRecord(
                date = date,
                status = status,
                failedAtMinute = if (status == DayStatus.FAILED) failedAt(goal, row, dayEndMinute, zone) else null,
                reason = row?.failTag.takeIf { status == DayStatus.FAILED },
                trigger = row?.failTrigger.takeIf { status == DayStatus.FAILED },
                lateMinutes = row?.lateMinutes,
                source = row?.failSource ?: com.example.dailygoals.model.FailSource.SELF_REPORTED,
            )
        }.toList()
    }

    /** A rule fails a goal at its deadline; a manual or detected failure happened when it was recorded. */
    private fun failedAt(goal: Goal, row: CheckIn?, dayEndMinute: Int, zone: ZoneId): Int? = when (row?.failReason) {
        FailReason.WINDOW_END -> goal.schedule.window?.let { (it.endMinute + goal.schedule.graceMinutes) % MINUTES_PER_DAY }
        FailReason.DAY_END -> dayEndMinute % MINUTES_PER_DAY
        FailReason.MANUAL, null -> row?.updatedAtMillis?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).let { t -> t.hour * 60 + t.minute } }
    }

    fun within(records: List<DayRecord>, range: InsightRange, today: LocalDate): List<DayRecord> =
        range.days?.let { days -> records.filter { it.date > today.minusDays(days.toLong()) } } ?: records

    /** Failures newest first, with the optional filters of the Mistakes view. */
    fun mistakes(
        goals: List<Goal>,
        checkIns: List<CheckIn>,
        zone: ZoneId,
        goalId: Long? = null,
        trigger: String? = null,
        search: String = "",
    ): List<Mistake> {
        val names = goals.associate { it.id to it.name }
        val query = search.trim().lowercase()
        return checkIns.filter { it.status == DayStatus.FAILED && it.goalId in names }
            .filter { goalId == null || it.goalId == goalId }
            .filter { trigger == null || it.failTrigger == trigger || it.failTag == trigger }
            .filter { query.isEmpty() || it.failNote.orEmpty().lowercase().contains(query) || it.failTag.orEmpty().lowercase().contains(query) || names.getValue(it.goalId).lowercase().contains(query) }
            .sortedWith(compareByDescending<CheckIn> { it.date }.thenByDescending { it.updatedAtMillis })
            .map {
                Mistake(
                    goalId = it.goalId,
                    goalName = names.getValue(it.goalId),
                    date = it.date,
                    minuteOfDay = it.updatedAtMillis.takeIf { m -> m > 0 }?.let { m -> Instant.ofEpochMilli(m).atZone(zone).let { t -> t.hour * 60 + t.minute } },
                    tag = it.failTag,
                    note = it.failNote,
                    trigger = it.failTrigger,
                    mood = it.failMood,
                    before = it.failPrevActivity,
                    place = it.failPlace,
                    detected = it.failSource == com.example.dailygoals.model.FailSource.DETECTED,
                )
            }
    }

    /** The most common reason or trigger per goal, for the line at the bottom of Mistakes. */
    fun commonCause(mistakes: List<Mistake>): Map<String, String> =
        mistakes.groupBy { it.goalName }.mapNotNull { (name, list) ->
            val labels = list.flatMap { listOfNotNull(it.tag, it.trigger) }
            labels.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).firstOrNull()?.let { name to it.key }
        }.toMap()

    private const val MINUTES_PER_DAY = 24 * 60
}
