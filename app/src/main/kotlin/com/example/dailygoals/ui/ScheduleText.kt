package com.example.dailygoals.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.dailygoals.R
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.GoalSchedule
import com.example.dailygoals.model.MonthlyNth
import com.example.dailygoals.model.TimeWindow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val MIN_RANGE_LENGTH = 3

fun formatMinute(minute: Int): String = "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60)

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun DayOfWeek.narrowName(): String = getDisplayName(TextStyle.NARROW, Locale.getDefault())

@Composable
fun windowText(window: TimeWindow?): String = if (window == null) {
    stringResource(R.string.goal_all_day)
} else {
    stringResource(R.string.goal_window, formatMinute(window.startMinute), formatMinute(window.endMinute))
}

/** "Mon to Fri" for an unbroken run of three or more days, otherwise the days listed. */
@Composable
fun dayListText(days: Set<DayOfWeek>): String {
    val sorted = days.sorted()
    val isRun = sorted.size >= MIN_RANGE_LENGTH && sorted.last().value - sorted.first().value == sorted.size - 1
    return if (isRun) {
        stringResource(R.string.sentence_day_range, sorted.first().shortName(), sorted.last().shortName())
    } else {
        sorted.joinToString { it.shortName() }
    }
}

@Composable
fun daysPhrase(frequency: Frequency, days: Set<DayOfWeek>): String = when {
    frequency == Frequency.DAILY || days.size == DayOfWeek.entries.size -> stringResource(R.string.sentence_every_day)
    days.isEmpty() -> stringResource(R.string.sentence_no_days)
    else -> stringResource(R.string.sentence_on_days, dayListText(days))
}

@Composable
fun windowPhrase(window: TimeWindow?): String = if (window == null) {
    stringResource(R.string.sentence_all_day)
} else {
    stringResource(R.string.sentence_between, formatMinute(window.startMinute), formatMinute(window.endMinute))
}

/** The "how often" part of a goal's sentence for every frequency type. */
@Composable
fun schedulePhrase(schedule: GoalSchedule): String = when (schedule.frequency) {
    Frequency.PER_WEEK -> pluralStringResource(R.plurals.sentence_per_week, schedule.count, schedule.count)
    Frequency.PER_MONTH -> pluralStringResource(R.plurals.sentence_per_month, schedule.count, schedule.count)
    Frequency.EVERY_N_DAYS -> stringResource(R.string.sentence_every_n_days, schedule.intervalDays)
    Frequency.MONTHLY_PATTERN -> stringResource(
        R.string.sentence_monthly,
        stringResource(
            when (schedule.monthlyNth) {
                MonthlyNth.FIRST -> R.string.nth_first
                MonthlyNth.SECOND -> R.string.nth_second
                MonthlyNth.THIRD -> R.string.nth_third
                MonthlyNth.FOURTH -> R.string.nth_fourth
                MonthlyNth.LAST -> R.string.nth_last
            },
        ).lowercase(),
        schedule.monthlyWeekday.getDisplayName(TextStyle.FULL, Locale.getDefault()),
    )
    Frequency.DAILY, Frequency.WEEKDAYS -> daysPhrase(schedule.frequency, schedule.activeDays)
}
