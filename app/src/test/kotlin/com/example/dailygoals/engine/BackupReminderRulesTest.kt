package com.example.dailygoals.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderRulesTest {
    private val day = 24 * 60 * 60 * 1000L
    private val start = 1_000 * day

    @Test
    fun theFirstReminderComesThirtyDaysAfterFirstUseWhenNothingWasExported() {
        assertFalse(BackupReminderRules.isDue(true, start + 29 * day, start, null, null, 30))
        assertTrue(BackupReminderRules.isDue(true, start + 30 * day, start, null, null, 30))
    }

    @Test
    fun aReminderRepeatsEveryPeriodUntilAnExportIsMade() {
        val reminded = start + 30 * day
        assertFalse(BackupReminderRules.isDue(true, reminded + 29 * day, start, null, reminded, 30))
        assertTrue(BackupReminderRules.isDue(true, reminded + 30 * day, start, null, reminded, 30))
    }

    @Test
    fun anExportRestartsTheCount() {
        val exported = start + 40 * day
        assertEquals(exported + 30 * day, BackupReminderRules.nextDueAt(start, exported, start + 30 * day, 30))
        assertFalse(BackupReminderRules.isDue(true, exported + 29 * day, start, exported, start + 30 * day, 30))
    }

    @Test
    fun aSwitchedOffReminderIsNeverDueAndNeverWakesTheApp() {
        assertFalse(BackupReminderRules.isDue(false, start + 500 * day, start, null, null, 30))
        assertNull(BackupReminderRules.waitMillis(false, start, start, null, null, 30))
    }

    @Test
    fun theWaitIsTheTimeLeftAndNotPositiveOnceDue() {
        assertEquals(10 * day, BackupReminderRules.waitMillis(true, start + 20 * day, start, null, null, 30))
        assertTrue(BackupReminderRules.waitMillis(true, start + 31 * day, start, null, null, 30)!! <= 0)
    }
}
