package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailSource
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternRulesTest {
    private val monday = LocalDate.of(2026, 10, 5)

    private fun day(offset: Int, status: DayStatus, at: Int? = null, reason: String? = null, trigger: String? = null, late: Int? = null, source: FailSource = FailSource.SELF_REPORTED) =
        DayRecord(monday.plusDays(offset.toLong()), status, at, reason, trigger, late, source)

    private fun ok(offset: Int, late: Int? = null) = day(offset, DayStatus.DONE, late = late)

    private fun fail(offset: Int, at: Int? = null, reason: String? = null, trigger: String? = null, source: FailSource = FailSource.SELF_REPORTED) =
        day(offset, DayStatus.FAILED, at, reason, trigger, source = source)

    @Test
    fun fewerThanTenDueDaysGiveNoStatementsAtAll() {
        val records = (0 until 9).map { if (it % 2 == 0) ok(it) else fail(it, at = 23 * 60) }
        val report = PatternRules.analyse(records)
        assertFalse(report.hasEnoughData)
        assertTrue(report.observations.isEmpty())
        assertEquals(9, report.dueDays)
        assertEquals(56, report.successPercent)
    }

    @Test
    fun openDaysAreNotCounted() {
        val report = PatternRules.analyse(listOf(ok(0), day(1, DayStatus.PENDING)))
        assertEquals(1, report.dueDays)
    }

    @Test
    fun weekdayVersusWeekendShowsOnlyWhenTheGapIsTwentyPointsOrMore() {
        // Four weeks: weekdays all done, weekends all failed.
        val records = (0 until 28).map { if (monday.plusDays(it.toLong()).dayOfWeek.value >= 6) fail(it) else ok(it) }
        val seen = PatternRules.analyse(records).observations.filterIsInstance<Observation.WeekdayWeekend>().single()
        assertEquals(100, seen.weekdayPercent)
        assertEquals(0, seen.weekendPercent)
        val closeRecords = (0 until 28).map { if (it == 5) fail(it) else ok(it) }
        assertTrue(PatternRules.analyse(closeRecords).observations.none { it is Observation.WeekdayWeekend })
    }

    @Test
    fun weekdayVersusWeekendNeedsAtLeastThreeDaysInEachGroup() {
        val records = (0 until 12).map { ok(it) } + fail(5)
        val report = PatternRules.analyse((0 until 11).map { ok(it) })
        assertTrue(report.observations.none { it is Observation.WeekdayWeekend })
        assertTrue(records.isNotEmpty())
    }

    @Test
    fun aTwoHourBandWithFortyPercentOfFailuresIsReportedWithFiveFailures() {
        val failures = listOf(23 * 60, 23 * 60 + 30, 22 * 60 + 10, 9 * 60, 14 * 60).mapIndexed { i, m -> fail(i * 2, at = m) }
        val records = failures + (20 until 30).map { ok(it) }
        val band = PatternRules.analyse(records).observations.filterIsInstance<Observation.FailureBand>().single()
        assertEquals(22, band.startHour)
        assertEquals(60, band.percent)
    }

    @Test
    fun failureStatementsNeedFiveFailures() {
        val records = (0 until 4).map { fail(it, at = 23 * 60, reason = "Tired") } + (4 until 20).map { ok(it) }
        val report = PatternRules.analyse(records)
        assertTrue(report.hasEnoughData)
        assertTrue(report.observations.none { it is Observation.FailureBand || it is Observation.CommonCause })
    }

    @Test
    fun aReasonOrTriggerMakingUpAtLeastFortyPercentIsReported() {
        val records = listOf("Tired", "Tired", "Busy", "Forgot", "Stress").mapIndexed { i, r -> fail(i, reason = r, trigger = "Bored") } + (5 until 20).map { ok(it) }
        val causes = PatternRules.analyse(records).observations.filterIsInstance<Observation.CommonCause>()
        assertEquals(listOf(Observation.CommonCause("Tired", false, 40), Observation.CommonCause("Bored", true, 100)), causes)
        assertEquals("Tired", PatternRules.analyse(records).topReason)
    }

    @Test
    fun manyLateCompletionsSuggestTheWindowIsTooEarly() {
        val records = (0 until 10).map { ok(it, late = if (it < 7) 20 else 0) }
        val late = PatternRules.analyse(records).observations.filterIsInstance<Observation.LateCompletions>().single()
        assertEquals(70, late.percentLate)
        assertEquals(20, late.averageMinutes)
        assertNull(PatternRules.analyse((0 until 10).map { ok(it, late = 0) }).observations.filterIsInstance<Observation.LateCompletions>().firstOrNull())
    }

    @Test
    fun failuresAreSplitBySourceAndCorrectionsAreCounted() {
        val records = listOf(
            fail(0, source = FailSource.SELF_REPORTED), fail(1, source = FailSource.DETECTED), fail(2, source = FailSource.DETECTED),
            day(3, DayStatus.DONE, source = FailSource.CORRECTED),
        )
        val report = PatternRules.analyse(records)
        assertEquals(1, report.selfReported)
        assertEquals(2, report.detected)
        assertEquals(1, report.corrected)
    }

    @Test
    fun theWorstWeekdayIsTheOneWithMostFailures() {
        val records = listOf(fail(0), fail(7), fail(14), fail(2), ok(3))
        assertEquals(DayOfWeek.MONDAY, PatternRules.analyse(records).worstWeekday)
    }
}
