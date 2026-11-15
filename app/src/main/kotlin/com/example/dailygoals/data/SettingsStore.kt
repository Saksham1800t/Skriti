package com.example.dailygoals.data

import android.content.SharedPreferences
import com.example.dailygoals.engine.NotificationCap
import com.example.dailygoals.engine.ReviewSchedule
import com.example.dailygoals.engine.QuietHours
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val dayEndMinute: Int = DEFAULT_DAY_END_MINUTE,
    val minimumWeightPercent: Int = DEFAULT_MINIMUM_WEIGHT_PERCENT,
    val timeShiftMinutes: Long = 0,
    val askFailNote: Boolean = true,
    val reviewMinute: Int = ReviewSchedule.DEFAULT_MINUTE,
    val maxNotificationsPerGoalPerDay: Int = NotificationCap.DEFAULT_MAX_PER_GOAL_PER_DAY,
    val quietHours: QuietHours = QuietHours(),
) {
    companion object {
        const val DEFAULT_DAY_END_MINUTE = 23 * 60 + 59
        const val DEFAULT_MINIMUM_WEIGHT_PERCENT = 60
    }
}

/** What the last evaluation run knew, so closed dates stay closed and zone jumps are noticed. */
data class EvalState(val finalisedThrough: LocalDate?, val lastToday: LocalDate?, val zoneId: String?, val neutralDates: Set<LocalDate>)

/** Small settings and bookkeeping values kept in the framework preferences, so no library is needed. */
class SettingsStore(private val prefs: SharedPreferences) {
    private val mutableSettings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = mutableSettings.asStateFlow()

    private val mutableTimers = MutableStateFlow(readTimers())
    val timers: StateFlow<Map<Long, Long>> = mutableTimers.asStateFlow()

    fun update(change: (Settings) -> Settings) {
        val next = change(mutableSettings.value)
        prefs.edit()
            .putInt(KEY_DAY_END, next.dayEndMinute)
            .putInt(KEY_MINIMUM_WEIGHT, next.minimumWeightPercent)
            .putLong(KEY_TIME_SHIFT, next.timeShiftMinutes)
            .putBoolean(KEY_ASK_NOTE, next.askFailNote)
            .putInt(KEY_REVIEW_MINUTE, next.reviewMinute)
            .putInt(KEY_MAX_NOTIFICATIONS, next.maxNotificationsPerGoalPerDay)
            .putBoolean(KEY_QUIET_ON, next.quietHours.enabled)
            .putInt(KEY_QUIET_START, next.quietHours.startMinute)
            .putInt(KEY_QUIET_END, next.quietHours.endMinute)
            .apply()
        mutableSettings.value = next
    }

    fun evalState(): EvalState = EvalState(
        finalisedThrough = prefs.getString(KEY_FINALISED, null)?.let(LocalDate::parse),
        lastToday = prefs.getString(KEY_LAST_TODAY, null)?.let(LocalDate::parse),
        zoneId = prefs.getString(KEY_ZONE, null),
        neutralDates = prefs.getString(KEY_NEUTRAL, "").orEmpty().split(',').filter { it.isNotBlank() }.map(LocalDate::parse).toSet(),
    )

    fun saveEvalState(state: EvalState) {
        prefs.edit()
            .putString(KEY_FINALISED, state.finalisedThrough?.toString())
            .putString(KEY_LAST_TODAY, state.lastToday?.toString())
            .putString(KEY_ZONE, state.zoneId)
            .putString(KEY_NEUTRAL, state.neutralDates.sorted().joinToString(","))
            .apply()
    }

    /** Whether the first-run permission walkthrough has been shown, so it is offered only once. */
    fun setupSeen(): Boolean = prefs.getBoolean(KEY_SETUP_SEEN, false)

    fun markSetupSeen() = prefs.edit().putBoolean(KEY_SETUP_SEEN, true).apply()

    private val mutableSeen = MutableStateFlow(prefs.getStringSet(KEY_SEEN_FAILURES, emptySet()).orEmpty())

    /** Failures whose "what happened" step was already shown, as goal id and date, so it appears once. */
    val seenFailures: StateFlow<Set<String>> = mutableSeen.asStateFlow()

    fun markFailureSeen(key: String) {
        val next = (mutableSeen.value + key).sortedDescending().take(200).toSet()
        prefs.edit().putStringSet(KEY_SEEN_FAILURES, next).apply()
        mutableSeen.value = next
    }

    /** How many warnings were posted for the goal on the date, for the daily cap. */
    fun sentCount(goalId: Long, date: LocalDate): Int = prefs.getInt("$KEY_SENT_PREFIX$goalId:$date", 0)

    fun countSent(goalId: Long, date: LocalDate) {
        val editor = prefs.edit().putInt("$KEY_SENT_PREFIX$goalId:$date", sentCount(goalId, date) + 1)
        val old = date.minusDays(KEEP_SENT_DAYS).toString()
        prefs.all.keys.filter { it.startsWith(KEY_SENT_PREFIX) && it.substringAfterLast(':') < old }.forEach { editor.remove(it) }
        editor.apply()
    }

    /** Pauses whose end was already announced, as goal id and resume date, so the notice appears once. */
    fun announcedResumes(): Set<String> = prefs.getStringSet(KEY_RESUMES, emptySet()).orEmpty()

    fun saveAnnouncedResumes(keys: Set<String>) = prefs.edit().putStringSet(KEY_RESUMES, keys.sortedDescending().take(MAX_RESUMES).toSet()).apply()


    fun startTimer(goalId: Long, startMillis: Long) = writeTimers(mutableTimers.value + (goalId to startMillis))

    fun stopTimer(goalId: Long): Long? {
        val start = mutableTimers.value[goalId] ?: return null
        writeTimers(mutableTimers.value - goalId)
        return start
    }

    private fun writeTimers(timers: Map<Long, Long>) {
        prefs.edit().putString(KEY_TIMERS, timers.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
        mutableTimers.value = timers
    }

    private fun readTimers(): Map<Long, Long> = prefs.getString(KEY_TIMERS, "").orEmpty().split(',').filter { it.contains(':') }
        .associate { entry -> entry.substringBefore(':').toLong() to entry.substringAfter(':').toLong() }

    private fun read() = Settings(
        dayEndMinute = prefs.getInt(KEY_DAY_END, Settings.DEFAULT_DAY_END_MINUTE),
        minimumWeightPercent = prefs.getInt(KEY_MINIMUM_WEIGHT, Settings.DEFAULT_MINIMUM_WEIGHT_PERCENT),
        timeShiftMinutes = prefs.getLong(KEY_TIME_SHIFT, 0),
        askFailNote = prefs.getBoolean(KEY_ASK_NOTE, true),
        reviewMinute = prefs.getInt(KEY_REVIEW_MINUTE, ReviewSchedule.DEFAULT_MINUTE),
        maxNotificationsPerGoalPerDay = prefs.getInt(KEY_MAX_NOTIFICATIONS, NotificationCap.DEFAULT_MAX_PER_GOAL_PER_DAY),
        quietHours = QuietHours(
            enabled = prefs.getBoolean(KEY_QUIET_ON, false),
            startMinute = prefs.getInt(KEY_QUIET_START, QuietHours().startMinute),
            endMinute = prefs.getInt(KEY_QUIET_END, QuietHours().endMinute),
        ),
    )

    private companion object {
        const val KEY_DAY_END = "day_end_minute"
        const val KEY_MINIMUM_WEIGHT = "minimum_weight_percent"
        const val KEY_TIME_SHIFT = "time_shift_minutes"
        const val KEY_FINALISED = "finalised_through"
        const val KEY_LAST_TODAY = "last_evaluated_today"
        const val KEY_ZONE = "last_zone_id"
        const val KEY_NEUTRAL = "neutral_dates"
        const val KEY_TIMERS = "timers"
        const val KEY_SETUP_SEEN = "setup_seen"
        const val KEY_RESUMES = "announced_resumes"
        const val KEY_ASK_NOTE = "ask_fail_note"
        const val KEY_REVIEW_MINUTE = "review_minute"
        const val KEY_MAX_NOTIFICATIONS = "max_notifications_per_goal"
        const val KEY_QUIET_ON = "quiet_hours_on"
        const val KEY_QUIET_START = "quiet_hours_start"
        const val KEY_QUIET_END = "quiet_hours_end"
        const val KEY_SENT_PREFIX = "sent:"
        const val KEY_SEEN_FAILURES = "seen_failures"
        const val MAX_RESUMES = 100
        const val KEEP_SENT_DAYS = 3L
    }
}
