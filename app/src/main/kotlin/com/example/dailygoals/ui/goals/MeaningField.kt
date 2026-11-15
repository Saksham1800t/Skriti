package com.example.dailygoals.ui.goals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.dailygoals.R
import com.example.dailygoals.model.GoalMeaning

/** The goal's own words: why it matters, what a failure means, a penalty for itself, and whether recovery is allowed. */
@Composable
internal fun MeaningField(form: GoalFormState, viewModel: GoalFormViewModel) {
    Field(R.string.form_meaning) {
        OutlinedTextField(
            value = form.why,
            onValueChange = { text -> viewModel.edit { it.copy(why = text.take(GoalMeaning.MAX_LENGTH)) } },
            label = { Text(stringResource(R.string.meaning_why)) },
            placeholder = { Text(stringResource(R.string.meaning_why_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.ifFail,
            onValueChange = { text -> viewModel.edit { it.copy(ifFail = text.take(GoalMeaning.MAX_LENGTH)) } },
            label = { Text(stringResource(R.string.meaning_if_fail)) },
            placeholder = { Text(stringResource(R.string.meaning_if_fail_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = form.stake,
            onValueChange = { text -> viewModel.edit { it.copy(stake = text.take(GoalMeaning.MAX_LENGTH)) } },
            label = { Text(stringResource(R.string.meaning_stake)) },
            placeholder = { Text(stringResource(R.string.meaning_stake_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        HelpText(R.string.meaning_stake_help)
        OutlinedTextField(
            value = form.ifText,
            onValueChange = { text -> viewModel.edit { it.copy(ifText = text.take(GoalMeaning.MAX_LENGTH)) } },
            label = { Text(stringResource(R.string.plan_when)) },
            placeholder = { Text(stringResource(R.string.plan_when_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = form.thenText,
            onValueChange = { text -> viewModel.edit { it.copy(thenText = text.take(GoalMeaning.MAX_LENGTH)) } },
            label = { Text(stringResource(R.string.plan_then)) },
            placeholder = { Text(stringResource(R.string.plan_then_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        HelpText(R.string.plan_help)
        CheckRow(form.recoveryAllowed, R.string.meaning_recovery, R.string.meaning_recovery_help) { on ->
            viewModel.edit { it.copy(recoveryAllowed = on) }
        }
    }
}
