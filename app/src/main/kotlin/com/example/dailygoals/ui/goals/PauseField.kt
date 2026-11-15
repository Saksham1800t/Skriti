package com.example.dailygoals.ui.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.engine.HabitPauseLength
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

/** Pausing a goal: it makes no check-ins, warnings, failures or new blocks, and its paused days do not count. */
@Composable
internal fun PauseField(form: GoalFormState, viewModel: GoalFormViewModel) {
    if (!form.isEdit) return
    var choosing by remember { mutableStateOf(false) }
    val pause = form.pause
    Field(R.string.form_pause) {
        if (pause == null) {
            HelpText(R.string.habit_pause_help)
            TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.pause_button)) }
        } else {
            Text(
                text = pause.resumesOn?.let { stringResource(R.string.pause_until, it.format(shortDate)) } ?: stringResource(R.string.pause_until_resumed),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            HelpText(R.string.pause_running_help)
            TextButton(onClick = viewModel::resume) { Text(stringResource(R.string.pause_resume)) }
        }
    }
    if (choosing) {
        PauseDialog(onDismiss = { choosing = false }) { length, chosen ->
            viewModel.pause(length, chosen)
            choosing = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseDialog(onDismiss: () -> Unit, onPause: (HabitPauseLength, LocalDate?) -> Unit) {
    var length by remember { mutableStateOf(HabitPauseLength.DAYS_7) }
    var pickingDate by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<LocalDate?>(null) }
    val options = listOf(
        HabitPauseLength.DAYS_3 to R.string.pause_3_days,
        HabitPauseLength.DAYS_7 to R.string.pause_7_days,
        HabitPauseLength.DAYS_14 to R.string.pause_14_days,
        HabitPauseLength.UNTIL_DATE to R.string.pause_until_date,
        HabitPauseLength.UNTIL_RESUMED to R.string.pause_until_resumed_option,
    )
    val needsDate = length == HabitPauseLength.UNTIL_DATE && chosen == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pause_button)) },
        text = {
            Column {
                options.forEach { (option, label) ->
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = length == option, role = Role.RadioButton, onClick = {
                                length = option
                                if (option == HabitPauseLength.UNTIL_DATE) pickingDate = true
                            }),
                    ) {
                        RadioButton(selected = length == option, onClick = null, modifier = Modifier.padding(end = 12.dp))
                        Text(
                            if (option == HabitPauseLength.UNTIL_DATE && chosen != null) {
                                stringResource(R.string.pause_until, chosen!!.plusDays(1).format(shortDate))
                            } else {
                                stringResource(label)
                            },
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !needsDate, onClick = { onPause(length, chosen) }) { Text(stringResource(R.string.pause_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.form_cancel)) } },
    )
    if (pickingDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { chosen = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text(stringResource(R.string.form_save)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.form_cancel)) } },
        ) { DatePicker(state = state) }
    }
}
