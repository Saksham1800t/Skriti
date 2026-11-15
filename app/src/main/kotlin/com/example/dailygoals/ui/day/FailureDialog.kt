package com.example.dailygoals.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.data.StakeEntity
import com.example.dailygoals.engine.DayItem
import com.example.dailygoals.engine.FailNotes
import com.example.dailygoals.engine.RecoveryCheck
import com.example.dailygoals.engine.RecoveryRules
import java.time.LocalDate

/**
 * Shown once when a goal becomes failed: the user's own reason, an optional note about what happened, and a
 * same-day way back for goals that have a minimum version.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FailureDialog(
    item: DayItem,
    date: LocalDate,
    today: LocalDate,
    askNote: Boolean,
    onSaveNote: (tag: String?, note: String) -> Unit,
    onRecover: () -> Unit,
    onClose: () -> Unit,
) {
    val meaning = item.goal.meaning
    val canRecover = RecoveryRules.check(item.goal, date, today, item.status, alreadyRecovered = false) == RecoveryCheck.OK
    if (!meaning.hasWords && !askNote && !canRecover) {
        LaunchedEffect(item.goal.id, date) { onClose() }
        return
    }
    var tag by remember(item.goal.id, date) { mutableStateOf<String?>(null) }
    var note by remember(item.goal.id, date) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.failure_screen_title, item.goal.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (meaning.hasWords) {
                    meaning.why?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.failure_screen_why), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    meaning.ifFail?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.failure_screen_if_fail), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                if (askNote) {
                    Text(stringResource(R.string.failure_screen_what_happened), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        FailNotes.TAGS.forEach { option ->
                            FilterChip(selected = tag == option, onClick = { tag = if (tag == option) null else option }, label = { Text(option) })
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(FailNotes.MAX_NOTE_LENGTH) },
                        label = { Text(stringResource(R.string.failure_screen_note)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (canRecover) {
                    Text(stringResource(R.string.failure_screen_recovery), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                    Row {
                        TextButton(onClick = {
                            if (askNote && (tag != null || note.isNotBlank())) onSaveNote(tag, note)
                            if (!item.goal.measurement.isAmount) onRecover()
                            onClose()
                        }) {
                            Text(
                                stringResource(
                                    if (item.goal.measurement.isAmount) R.string.failure_screen_log_minimum else R.string.failure_screen_do_minimum,
                                    item.goal.measurement.minimum,
                                    item.goal.measurement.unit,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (askNote && (tag != null || note.isNotBlank())) onSaveNote(tag, note)
                onClose()
            }) { Text(stringResource(if (canRecover) R.string.failure_screen_accept else R.string.failure_screen_save)) }
        },
        dismissButton = if (askNote) {
            { TextButton(onClick = onClose) { Text(stringResource(R.string.failure_screen_skip)) } }
        } else {
            null
        },
    )
}

/** A stake that is due: the penalty the goal set for itself, with a tick for doing it or a way to let it go. */
@Composable
internal fun StakeBanner(stake: StakeEntity, goalName: String, onDone: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(stringResource(R.string.stake_title, goalName.ifEmpty { stringResource(R.string.stake_unknown_goal) }), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stake.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row {
            TextButton(onClick = onDone) { Text(stringResource(R.string.stake_done)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.stake_dismiss)) }
        }
    }
}
