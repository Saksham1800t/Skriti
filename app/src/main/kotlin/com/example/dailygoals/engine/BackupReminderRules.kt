package com.example.dailygoals.engine

/**
 * The backup reminder: a notification every [days] days until a backup file is exported. An export restarts the
 * count, so a phone that exports regularly is never nagged. Times are real epoch millis.
 */
object BackupReminderRules {
    const val DEFAULT_DAYS = 30
    val DAYS_RANGE = 7..90
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** When the next reminder is due, counted from the last export (or first use) and the last reminder. */
    fun nextDueAt(firstSeenMillis: Long, lastExportMillis: Long?, lastReminderMillis: Long?, days: Int): Long {
        val reference = maxOf(lastExportMillis ?: firstSeenMillis, lastReminderMillis ?: 0L)
        return reference + days * DAY_MILLIS
    }

    fun isDue(enabled: Boolean, nowMillis: Long, firstSeenMillis: Long, lastExportMillis: Long?, lastReminderMillis: Long?, days: Int): Boolean =
        enabled && nowMillis >= nextDueAt(firstSeenMillis, lastExportMillis, lastReminderMillis, days)

    /** Milliseconds until the next reminder, or null when the reminder is off. Zero or less means due now. */
    fun waitMillis(enabled: Boolean, nowMillis: Long, firstSeenMillis: Long, lastExportMillis: Long?, lastReminderMillis: Long?, days: Int): Long? =
        if (enabled) nextDueAt(firstSeenMillis, lastExportMillis, lastReminderMillis, days) - nowMillis else null
}
