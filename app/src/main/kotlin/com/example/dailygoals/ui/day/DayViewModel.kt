package com.example.dailygoals.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.data.GoalRepository
import com.example.dailygoals.data.SettingsStore
import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.engine.Clock
import com.example.dailygoals.data.StakeEntity
import com.example.dailygoals.engine.Consistency
import com.example.dailygoals.engine.NeutralDays
import com.example.dailygoals.engine.PauseRules
import com.example.dailygoals.engine.RescueCheck
import com.example.dailygoals.engine.RescueRules
import com.example.dailygoals.engine.ConsistencyStats
import com.example.dailygoals.engine.DayPlan
import com.example.dailygoals.engine.DayPlanner
import com.example.dailygoals.engine.DaySummary
import com.example.dailygoals.engine.Weeks
import com.example.dailygoals.model.GoalState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayUiState(
    val now: LocalDateTime,
    val plan: DayPlan,
    val week: List<LocalDate>,
    val weekSummaries: Map<LocalDate, DaySummary>,
    val hasActiveGoals: Boolean,
    val stats: Map<Long, ConsistencyStats>,
    val timers: Map<Long, Long>,
    val minimumWeightPercent: Int,
    val rescued: Boolean = false,
    val rescueCheck: RescueCheck = RescueCheck.TOO_OLD,
    val rescueUsedOn: LocalDate? = null,
    val pausedCount: Int = 0,
) {
    val today: LocalDate get() = now.toLocalDate()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(
    private val repository: GoalRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) : ViewModel() {
    /** Null follows today, so the screen rolls over at midnight instead of staying on yesterday. */
    private val pickedDate = MutableStateFlow<LocalDate?>(null)

    private val now: Flow<LocalDateTime> = flow {
        while (true) {
            val current = clock.now()
            emit(current.withSecond(0).withNano(0))
            delay((SECONDS_PER_MINUTE - current.second) * MILLIS_PER_SECOND)
        }
    }.distinctUntilChanged()

    private val neutralFlow = combine(repository.pauses, repository.rescueDays) { pauses, rescueDays -> pauses to rescueDays }

    private val selection: Flow<Pair<LocalDateTime, LocalDate>> =
        combine(now, pickedDate) { time, picked -> time to (picked ?: time.toLocalDate()) }

    val state: StateFlow<DayUiState?> = selection
        .flatMapLatest { (time, date) ->
            val week = Weeks.weekOf(date)
            combine(repository.goals, repository.checkIns, repository.timers, settings.settings, neutralFlow) { goals, checkIns, timers, current, (pauses, rescueDays) ->
                val byGoal = checkIns.groupBy { it.goalId }
                val neutral = repository.neutralDates() + rescueDays
                val visible = if (date in rescueDays) goals.filter { goal -> byGoal[goal.id].orEmpty().any { it.date == date } } else goals
                val shown = visible.filterNot { goal -> PauseRules.isPaused(pauses, goal.id, date) && byGoal[goal.id].orEmpty().none { it.date == date } }
                DayUiState(
                    now = time,
                    plan = DayPlanner.plan(shown, checkIns, date, time),
                    week = week,
                    weekSummaries = DayPlanner.summaries(goals, checkIns, week),
                    hasActiveGoals = goals.any { it.state == GoalState.ACTIVE },
                    stats = goals.filter { it.state == GoalState.ACTIVE }.associate { goal ->
                        val statuses = byGoal[goal.id].orEmpty().associate { it.date to it.status }
                        val goalNeutral = NeutralDays.forGoal(goal.id, neutral, emptySet(), pauses, goal.schedule.startDate, time.toLocalDate())
                        goal.id to Consistency.forGoal(goal, statuses, time.toLocalDate(), goalNeutral)
                    },
                    timers = timers,
                    minimumWeightPercent = current.minimumWeightPercent,
                    rescued = date in rescueDays,
                    rescueCheck = RescueRules.check(date, time.toLocalDate(), rescueDays),
                    rescueUsedOn = RescueRules.usedThisWeek(time.toLocalDate(), rescueDays),
                    pausedCount = goals.count { it.state == GoalState.ACTIVE && PauseRules.isPaused(pauses, it.id, date) },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    init {
        viewModelScope.launch { now.collect { repository.evaluate() } }
    }

    fun monthSummaries(month: YearMonth): Flow<Map<LocalDate, DaySummary>> {
        val days = List(month.lengthOfMonth()) { month.atDay(it + 1) }
        return combine(repository.goals, repository.checkIns) { goals, checkIns -> DayPlanner.summaries(goals, checkIns, days) }
    }

    fun select(date: LocalDate) {
        pickedDate.value = date.takeIf { it != clock.now().toLocalDate() }
    }

    val seenFailures: StateFlow<Set<String>> = settings.seenFailures

    val stakes: StateFlow<List<StakeEntity>> = repository.stakes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun failureSeen(goalId: Long, date: LocalDate) = settings.markFailureSeen("$goalId:$date")

    fun saveNote(goalId: Long, date: LocalDate, tag: String?, note: String) {
        viewModelScope.launch { repository.saveFailNote(goalId, date, tag, note) }
    }

    fun recover(goalId: Long, date: LocalDate) {
        viewModelScope.launch { repository.recover(goalId, date) }
    }

    fun completeStake(id: Long) {
        viewModelScope.launch { repository.completeStake(id) }
    }

    fun dismissStake(id: Long) {
        viewModelScope.launch { repository.dismissStake(id) }
    }

    fun useRescueDay(date: LocalDate) {
        viewModelScope.launch { repository.useRescueDay(date) }
    }

    fun act(goalId: Long, date: LocalDate, action: CheckAction) {
        viewModelScope.launch { repository.applyAction(goalId, date, action) }
    }

    fun log(goalId: Long, date: LocalDate, delta: Int) {
        viewModelScope.launch { repository.logAmount(goalId, date, delta) }
    }

    fun undoLog(goalId: Long, date: LocalDate) {
        viewModelScope.launch { repository.undoLastLog(goalId, date) }
    }

    fun startTimer(goalId: Long) = repository.startTimer(goalId)

    fun stopTimer(goalId: Long) {
        viewModelScope.launch { repository.stopTimer(goalId) }
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val MILLIS_PER_SECOND = 1000L
        private const val STOP_TIMEOUT_MILLIS = 5000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SkritiApp
                DayViewModel(app.repository, app.settings, app.clock)
            }
        }
    }
}
