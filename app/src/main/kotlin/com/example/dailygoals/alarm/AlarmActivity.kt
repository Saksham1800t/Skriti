package com.example.dailygoals.alarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.ui.components.AccentButton
import com.example.dailygoals.ui.theme.SkritiTheme
import java.lang.ref.WeakReference

/** The full-screen alarm, shown over the lock screen, with a large Snooze and Dismiss. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        AlarmScreenState.activity = WeakReference(this)
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val canSnooze = intent.getBooleanExtra(EXTRA_CAN_SNOOZE, true)
        setContent {
            SkritiTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.alarm_title), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(name, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(48.dp))
                    if (canSnooze) {
                        AccentButton(stringResource(R.string.alarm_snooze), onClick = { send(AlarmService.ACTION_SNOOZE) })
                        Spacer(Modifier.height(20.dp))
                    }
                    AccentButton(stringResource(R.string.alarm_dismiss), onClick = { send(AlarmService.ACTION_DISMISS) })
                }
            }
        }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
        finish()
    }

    override fun onDestroy() {
        if (AlarmScreenState.activity?.get() === this) AlarmScreenState.activity = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_CAN_SNOOZE = "can_snooze"
    }
}
