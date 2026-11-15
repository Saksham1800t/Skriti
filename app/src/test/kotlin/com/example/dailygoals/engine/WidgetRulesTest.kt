package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalSchedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetRulesTest {
    private val start = LocalDate.of(2026, 11, 1)

    private fun item(id: Long, name: String, status: DayStatus) = DayItem(
        goal = Goal(id = id, name = name, schedule = GoalSchedule(startDate = start)),
        status = status,
        section = DaySection.ANY_TIME,
        minutesLeft = null,
    )

    @Test
    fun openGoalsComeFirstThenMissedThenKept() {
        val model = WidgetRules.model(
            listOf(item(1, "Run", DayStatus.DONE), item(2, "No social", DayStatus.FAILED), item(3, "Study", DayStatus.PENDING), item(4, "Read", DayStatus.MINIMUM)),
            maxRows = 4,
            nextDeadlineMinute = null,
        )
        assertEquals(listOf("Study", "No social", "Run", "Read"), model.rows.map { it.name })
        assertEquals(listOf(WidgetState.PENDING, WidgetState.MISSED, WidgetState.KEPT, WidgetState.KEPT), model.rows.map { it.state })
    }

    @Test
    fun theRingCountsKeptGoalsIncludingMinimumAndLongListsSayHowManyAreHidden() {
        val items = (1L..6L).map { item(it, "Goal $it", if (it <= 2) DayStatus.DONE else DayStatus.PENDING) }
        val model = WidgetRules.model(items, maxRows = 4, nextDeadlineMinute = 18 * 60)
        assertEquals(2, model.kept)
        assertEquals(6, model.total)
        assertEquals(4, model.rows.size)
        assertEquals(2, model.more)
        assertEquals("18:00", model.nextText)
    }

    @Test
    fun anEmptyDayHasNoRowsAndNoNextDeadline() {
        val model = WidgetRules.model(emptyList(), 4, null)
        assertEquals(0, model.total)
        assertEquals(emptyList<WidgetRow>(), model.rows)
        assertNull(model.nextText)
    }
}
