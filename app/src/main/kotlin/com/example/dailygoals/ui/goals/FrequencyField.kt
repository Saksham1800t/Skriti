package com.example.dailygoals.ui.goals

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.engine.GoalError
import com.example.dailygoals.engine.GoalValidator
import com.example.dailygoals.model.Frequency
import com.example.dailygoals.model.MonthlyNth
import com.example.dailygoals.model.QuotaFailureMode
import com.example.dailygoals.ui.components.SegmentedChoice
import com.example.dailygoals.ui.components.StepperRow

@Composable
internal fun FrequencyField(form: GoalFormState, viewModel: GoalFormViewModel) {
    val choose: (Frequency) -> Unit = { frequency -> viewModel.edit { it.copy(frequency = frequency) } }
    Field(R.string.form_frequency) {
        SegmentedChoice(
            options = listOf(
                Frequency.DAILY to stringResource(R.string.form_every_day),
                Frequency.WEEKDAYS to stringResource(R.string.form_specific_days),
                Frequency.EVERY_N_DAYS to stringResource(R.string.freq_interval),
            ),
            selected = form.frequency,
            onSelect = choose,
        )
        Spacer(Modifier.height(6.dp))
        SegmentedChoice(
            options = listOf(
                Frequency.PER_WEEK to stringResource(R.string.freq_per_week),
                Frequency.PER_MONTH to stringResource(R.string.freq_per_month),
                Frequency.MONTHLY_PATTERN to stringResource(R.string.freq_monthly),
            ),
            selected = form.frequency,
            onSelect = choose,
        )
        when (form.frequency) {
            Frequency.DAILY -> Unit
            Frequency.WEEKDAYS -> {
                DayPicker(form.days) { day ->
                    viewModel.edit { it.copy(days = if (day in it.days) it.days - day else it.days + day) }
                }
                ErrorText(form.errors, GoalError.NO_DAYS, R.string.error_no_days)
            }
            Frequency.EVERY_N_DAYS -> {
                StepperRow(
                    label = stringResource(R.string.freq_interval_label),
                    shown = stringResource(R.string.freq_interval_days, form.intervalDays),
                    range = GoalValidator.INTERVAL_RANGE,
                    value = form.intervalDays,
                ) { days -> viewModel.edit { it.copy(intervalDays = days) } }
                HelpText(R.string.freq_interval_help)
            }
            Frequency.PER_WEEK, Frequency.PER_MONTH -> QuotaControls(form, viewModel)
            Frequency.MONTHLY_PATTERN -> {
                Spacer(Modifier.height(8.dp))
                SegmentedChoice(
                    options = listOf(
                        MonthlyNth.FIRST to stringResource(R.string.nth_first),
                        MonthlyNth.SECOND to stringResource(R.string.nth_second),
                        MonthlyNth.THIRD to stringResource(R.string.nth_third),
                        MonthlyNth.FOURTH to stringResource(R.string.nth_fourth),
                        MonthlyNth.LAST to stringResource(R.string.nth_last),
                    ),
                    selected = form.monthlyNth,
                    onSelect = { nth -> viewModel.edit { it.copy(monthlyNth = nth) } },
                )
                DayPicker(setOf(form.monthlyWeekday)) { day -> viewModel.edit { it.copy(monthlyWeekday = day) } }
            }
        }
    }
}

@Composable
private fun QuotaControls(form: GoalFormState, viewModel: GoalFormViewModel) {
    val perWeek = form.frequency == Frequency.PER_WEEK
    val range = if (perWeek) GoalValidator.WEEK_COUNT_RANGE else GoalValidator.MONTH_COUNT_RANGE
    StepperRow(
        label = stringResource(if (perWeek) R.string.freq_times_week else R.string.freq_times_month),
        shown = form.count.coerceIn(range).toString(),
        range = range,
        value = form.count.coerceIn(range),
    ) { count -> viewModel.edit { it.copy(count = count) } }
    HelpText(R.string.freq_quota_help)
    Text(
        stringResource(R.string.freq_failure_title),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
    SegmentedChoice(
        options = listOf(
            QuotaFailureMode.WHEN_UNREACHABLE to stringResource(R.string.freq_failure_unreachable),
            QuotaFailureMode.END_OF_PERIOD to stringResource(R.string.freq_failure_end),
        ),
        selected = form.quotaFailureMode,
        onSelect = { mode -> viewModel.edit { it.copy(quotaFailureMode = mode) } },
    )
    HelpText(if (form.quotaFailureMode == QuotaFailureMode.WHEN_UNREACHABLE) R.string.freq_failure_unreachable_help else R.string.freq_failure_end_help)
}
