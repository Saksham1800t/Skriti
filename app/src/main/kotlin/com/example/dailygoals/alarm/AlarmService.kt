package com.example.dailygoals.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.AlarmRules
import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Goal
import com.example.dailygoals.notify.NotificationChannels
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Plays the alarm. It is the one place a foreground service is used, and only while the alarm rings: Android 17
 * stops apps from starting sound in the background, but allows it from a service started by an alarm clock.
 * It stops when the alarm is dismissed or snoozed, or after ten minutes.
 */
class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var goal: Goal? = null
    private var date: LocalDate = LocalDate.now()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> dismiss()
            else -> ring(intent)
        }
        return START_NOT_STICKY
    }

    private fun ring(intent: Intent?) {
        val goalId = intent?.getLongExtra(GoalAlarmScheduler.EXTRA_GOAL_ID, 0L) ?: 0L
        date = intent?.getStringExtra(GoalAlarmScheduler.EXTRA_DATE)?.let(LocalDate::parse) ?: LocalDate.now()
        val app = application as SkritiApp
        // A foreground service must show its notification at once; the goal's name is filled in just after.
        showNotification(null)
        app.scope.launch {
            val loaded = app.repository.goal(goalId)
            handler.post {
                if (loaded == null || !loaded.alarm.enabled) {
                    stopRinging()
                    return@post
                }
                goal = loaded
                showNotification(loaded)
                startSound(loaded)
                handler.removeCallbacks(timeout)
                handler.postDelayed(timeout, TIMEOUT_MILLIS)
                startActivity(alarmScreen(loaded).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private val timeout = Runnable { stopRinging() }

    private fun startSound(goal: Goal) {
        val uri = goal.alarm.soundUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        stopSound()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        if (goal.alarm.vibrate) {
            runCatching {
                getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 500), 0))
            }
        }
    }

    private fun stopSound() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { getSystemService(VibratorManager::class.java).defaultVibrator.cancel() }
    }

    private fun snooze() {
        val current = goal
        val app = application as SkritiApp
        if (current != null) {
            val used = app.alarmStore.snoozesUsed(current.id, date)
            if (AlarmRules.canSnooze(current.alarm, used)) {
                app.alarmStore.countSnooze(current.id, date)
                GoalAlarmScheduler(this).snooze(current.id, date.toString(), AlarmRules.snoozeAt(System.currentTimeMillis(), current.alarm))
            }
        }
        stopRinging()
    }

    private fun dismiss() {
        val current = goal
        val app = application as SkritiApp
        if (current != null) {
            app.alarmStore.clearSnoozes(current.id, date)
            if (current.alarm.dismissTicksGoal) {
                app.scope.launch {
                    if (app.repository.statusOn(current.id, date) == DayStatus.PENDING) app.repository.applyAction(current.id, date, CheckAction.TOGGLE_DONE)
                    app.warnings.run()
                }
            }
        }
        stopRinging()
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeout)
        stopSound()
        AlarmScreenState.close()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        // The next alarm is worked out again whatever happened to this one.
        val app = application as SkritiApp
        app.scope.launch { app.warnings.run() }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        super.onDestroy()
    }

    private fun alarmScreen(goal: Goal): Intent = Intent(this, AlarmActivity::class.java)
        .putExtra(AlarmActivity.EXTRA_NAME, goal.name)
        .putExtra(AlarmActivity.EXTRA_CAN_SNOOZE, AlarmRules.canSnooze(goal.alarm, (application as SkritiApp).alarmStore.snoozesUsed(goal.id, date)))

    private fun action(name: String, code: Int): PendingIntent = PendingIntent.getService(
        this,
        code,
        Intent(this, AlarmService::class.java).setAction(name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showNotification(goal: Goal?) {
        val screen = goal?.let {
            PendingIntent.getActivity(this, 31, alarmScreen(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val builder = NotificationCompat.Builder(this, NotificationChannels.ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(goal?.name ?: getString(R.string.alarm_title))
            .setContentText(getString(R.string.alarm_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .addAction(0, getString(R.string.alarm_dismiss), action(ACTION_DISMISS, 32))
        if (goal != null && AlarmRules.canSnooze(goal.alarm, (application as SkritiApp).alarmStore.snoozesUsed(goal.id, date))) {
            builder.addAction(0, getString(R.string.alarm_snooze), action(ACTION_SNOOZE, 33))
        }
        if (screen != null) builder.setContentIntent(screen).setFullScreenIntent(screen, true)
        ServiceCompat.startForeground(this, NOTICE_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    companion object {
        const val ACTION_SNOOZE = "com.example.dailygoals.ALARM_SNOOZE"
        const val ACTION_DISMISS = "com.example.dailygoals.ALARM_DISMISS"
        private const val NOTICE_ID = 7730
        private const val TIMEOUT_MILLIS = 10 * 60_000L
    }
}

/** Starts the ringing service when an alarm clock fires. */
class GoalAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GoalAlarmScheduler.ACTION_RING) return
        val service = Intent(context, AlarmService::class.java)
            .putExtra(GoalAlarmScheduler.EXTRA_GOAL_ID, intent.getLongExtra(GoalAlarmScheduler.EXTRA_GOAL_ID, 0L))
            .putExtra(GoalAlarmScheduler.EXTRA_DATE, intent.getStringExtra(GoalAlarmScheduler.EXTRA_DATE))
        androidx.core.content.ContextCompat.startForegroundService(context, service)
    }
}

/** What the open alarm screen needs to know, so the service can close it. */
object AlarmScreenState {
    @Volatile
    var activity: java.lang.ref.WeakReference<AlarmActivity>? = null

    fun close() {
        activity?.get()?.finish()
        activity = null
    }
}
