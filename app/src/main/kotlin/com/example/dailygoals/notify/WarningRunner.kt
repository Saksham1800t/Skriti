package com.example.dailygoals.notify

import com.example.dailygoals.blocker.ScheduledBlockSync
import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.wallpaper.WallpaperController
import com.example.dailygoals.engine.DeliveryLevel
import com.example.dailygoals.engine.DeliveryLevels
import com.example.dailygoals.engine.NotificationCap
import java.time.Duration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single place that turns the database and the clock into warnings and the next alarm. It runs
 * when an alarm fires, after a reboot or a time change, when the app opens, and whenever goals or
 * results change, so a missed alarm is repaired by the next run.
 */
class WarningRunner(
    private val repository: GoalRepository,
    private val scheduler: AlarmScheduler,
    private val permissions: PermissionChecker,
    private val notifier: WarningNotifier,
    private val blockSync: ScheduledBlockSync,
    private val wallpaper: WallpaperController,
) {
    private val lock = Mutex()

    suspend fun run() = lock.withLock {
        repository.applyDuePending()
        repository.evaluate()
        repository.takeResumedGoals().forEach(notifier::postResumed)
        val zone = java.time.ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()
        val midnight = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        wallpaper.refresh(repository.failedForWallpaper(), nowMillis, midnight)
        val scheduled = repository.scheduledBatch()
        blockSync.apply(scheduled.blocks)
        val batch = repository.warningBatch()
        batch.resolvedToday.forEach(notifier::cancel)
        val state = permissions.deliveryState()
        batch.plan.toFire.forEach { warning ->
            val goal = batch.goals[warning.goalId] ?: return@forEach
            val level = DeliveryLevels.choose(state, goal.reminder.strongAlert)
            val settings = repository.currentSettings()
            val minute = batch.now.atZone(java.time.ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
            val sent = repository.sentCount(goal.id, warning.date)
            val allowed = NotificationCap.allows(warning.stage, sent, settings.maxNotificationsPerGoalPerDay, settings.quietHours, minute)
            if (level != DeliveryLevel.IN_APP && allowed) {
                notifier.post(goal, warning, level)
                repository.countSent(goal.id, warning.date)
            }
            repository.markWarningShown(warning, level)
        }
        val warningWait = Duration.between(batch.now, batch.plan.nextWakeup)
        scheduler.scheduleIn(listOfNotNull(warningWait, scheduled.nextChangeIn).min())
    }
}
