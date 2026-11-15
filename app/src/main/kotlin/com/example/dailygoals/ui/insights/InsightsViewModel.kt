package com.example.dailygoals.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.data.SettingsStore
import com.example.dailygoals.engine.Clock
import com.example.dailygoals.engine.DayRecord
import com.example.dailygoals.engine.GoalHistory
import com.example.dailygoals.engine.InsightRange
import com.example.dailygoals.engine.Mistake
import com.example.dailygoals.engine.NeutralDays
import com.example.dailygoals.engine.PatternReport
import com.example.dailygoals.engine.PatternRules
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalState
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GoalInsight(val goal: Goal, val report: PatternReport)

data class InsightsState(
    val today: LocalDate,
    val goals: List<GoalInsight>,
    val mistakes: List<Mistake>,
    val emergencyPausesThisMonth: Int,
    val recoveries: Int,
    val rescues: Int,
)

class InsightsViewModel(private val repository: GoalRepository, private val clock: Clock, private val store: SettingsStore, private val pauseUses: () -> Int) : ViewModel() {
    private val zone get() = clock.zone

    private val inputs = combine(repository.goals, repository.checkIns, repository.pauses, repository.rescueDays) { goals, checkIns, pauses, rescued ->
        Quad(goals, checkIns, pauses, rescued)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun state(range: InsightRange): StateFlow<InsightsState?> = inputs.let { flow ->
        combine(flow, kotlinx.coroutines.flow.flowOf(range)) { q, r ->
            val today = clock.now().toLocalDate()
            val dayEnd = store.settings.value.dayEndMinute
            val neutral = store.evalState().neutralDates + q.d
            val active = q.a.filter { it.state == GoalState.ACTIVE && !it.schedule.isQuota }
            val insights = active.map { goal ->
                val goalNeutral = NeutralDays.forGoal(goal.id, neutral, emptySet(), q.c, goal.schedule.startDate, today)
                val all = GoalHistory.records(goal, q.b, today, goalNeutral, dayEnd, zone)
                GoalInsight(goal, PatternRules.analyse(GoalHistory.within(all, r, today)))
            }
            InsightsState(
                today = today,
                goals = insights,
                mistakes = GoalHistory.mistakes(q.a, q.b, zone),
                emergencyPausesThisMonth = pauseUses(),
                recoveries = q.b.count { it.recovered },
                rescues = q.d.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), null)
    }

    /** The full history of one goal, so the goal page never depends on the range chosen on the list. */
    fun goalReport(goalId: Long, range: InsightRange): StateFlow<GoalInsight?> =
        state(range).let { flow ->
            combine(flow, kotlinx.coroutines.flow.flowOf(goalId)) { s, id -> s?.goals?.firstOrNull { it.goal.id == id } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), null)
        }

    companion object {
        private const val STOP_MILLIS = 5000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SkritiApp
                InsightsViewModel(app.repository, app.clock, app.settings) {
                    val now = System.currentTimeMillis()
                    val start = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    app.blocks.pauseUses.count { it.startedAtMillis in start..now }
                }
            }
        }
    }
}
