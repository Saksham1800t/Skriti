package com.example.dailygoals.engine

import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.isMet

enum class WidgetState { PENDING, MISSED, KEPT }

data class WidgetRow(val goalId: Long, val name: String, val state: WidgetState)

data class WidgetModel(val rows: List<WidgetRow>, val kept: Int, val total: Int, val more: Int, val nextText: String?)

/**
 * What the home screen widget shows: today's ring and the goals still worth a tap. Goals that are open come
 * first, then missed ones, then kept ones, so a short widget still shows what needs attention. Paused goals
 * are not in today's plan, so they never appear.
 */
object WidgetRules {
    fun model(items: List<DayItem>, maxRows: Int, nextDeadlineMinute: Int?): WidgetModel {
        val rows = items.map { WidgetRow(it.goal.id, it.goal.name, stateOf(it.status)) }
        val ordered = rows.sortedBy { it.state.ordinal }
        val shown = ordered.take(maxRows)
        val next = nextDeadlineMinute?.let { "%02d:%02d".format(it / 60, it % 60) }
        return WidgetModel(shown, kept = items.count { it.status.isMet }, total = items.size, more = ordered.size - shown.size, nextText = next)
    }

    private fun stateOf(status: DayStatus) = when {
        status.isMet -> WidgetState.KEPT
        status == DayStatus.FAILED -> WidgetState.MISSED
        else -> WidgetState.PENDING
    }
}
