package com.example.dailygoals.ui.goals

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.dailygoals.R
import com.example.dailygoals.engine.GoalError
import com.example.dailygoals.model.GoalAlarm
import com.example.dailygoals.ui.components.SegmentedChoice
import com.example.dailygoals.ui.components.StepperRow

/** An optional real alarm for a goal that has a time window. The app never picks the time. */
@Composable
internal fun AlarmField(form: GoalFormState, viewModel: GoalFormViewModel) {
    if (!form.hasWindow) return
    val context = LocalContext.current
    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.edit { it.copy(alarmSound = picked?.toString()) }
        }
    }
    Field(R.string.form_alarm) {
        CheckRow(form.alarmOn, R.string.alarm_switch, R.string.alarm_switch_help) { on -> viewModel.edit { it.copy(alarmOn = on) } }
        if (!form.alarmOn) return@Field
        TimeBox(R.string.alarm_time, R.string.alarm_time_pick, form.alarmMinute ?: form.startMinute, Modifier.padding(top = 12.dp)) { minute ->
            viewModel.edit { it.copy(alarmMinute = minute) }
        }
        if (form.alarmMinute == null) HelpText(R.string.alarm_time_unset)
        ErrorText(form.errors, GoalError.ALARM_NEEDS_TIME, R.string.error_alarm_needs_time)
        ErrorText(form.errors, GoalError.ALARM_OUTSIDE_WINDOW, R.string.error_alarm_outside_window)
        DayPicker(form.alarmDays) { day ->
            viewModel.edit { it.copy(alarmDays = if (day in it.alarmDays) it.alarmDays - day else it.alarmDays + day) }
        }
        ErrorText(form.errors, GoalError.ALARM_NO_DAYS, R.string.error_alarm_no_days)
        TextButton(onClick = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, form.alarmSound?.let(Uri::parse))
            ringtone.launch(intent)
        }) {
            Text(
                stringResource(
                    R.string.alarm_sound,
                    form.alarmSound?.let { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) } ?: stringResource(R.string.alarm_sound_default),
                ),
            )
        }
        CheckRow(form.alarmVibrate, R.string.alarm_vibrate, R.string.alarm_vibrate_help) { on -> viewModel.edit { it.copy(alarmVibrate = on) } }
        Text(stringResource(R.string.alarm_snooze_length), modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            SegmentedChoice(
                options = GoalAlarm.SNOOZE_MINUTE_CHOICES.map { it to stringResource(R.string.form_grace_minutes, it) },
                selected = form.snoozeMinutes,
                onSelect = { minutes -> viewModel.edit { it.copy(snoozeMinutes = minutes) } },
            )
        }
        StepperRow(
            label = stringResource(R.string.alarm_snooze_limit),
            shown = form.snoozeLimit.toString(),
            range = GoalAlarm.SNOOZE_LIMIT_RANGE,
            value = form.snoozeLimit,
        ) { limit -> viewModel.edit { it.copy(snoozeLimit = limit) } }
        CheckRow(form.alarmDismissTicks, R.string.alarm_dismiss_ticks, R.string.alarm_dismiss_ticks_help) { on ->
            viewModel.edit { it.copy(alarmDismissTicks = on) }
        }
    }
}

internal fun alarmFromForm(form: GoalFormState): GoalAlarm = GoalAlarm(
    enabled = form.alarmOn && form.hasWindow,
    minute = form.alarmMinute,
    days = form.alarmDays,
    soundUri = form.alarmSound,
    vibrate = form.alarmVibrate,
    snoozeMinutes = form.snoozeMinutes,
    snoozeLimit = form.snoozeLimit,
    dismissTicksGoal = form.alarmDismissTicks,
)
