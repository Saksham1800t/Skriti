package com.example.dailygoals.data

import androidx.room.withTransaction
import com.example.dailygoals.data.backup.BackupCodec
import com.example.dailygoals.engine.ActiveBlock
import com.example.dailygoals.engine.AmountRules
import com.example.dailygoals.engine.BlockKind
import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.engine.CheckInRules
import com.example.dailygoals.engine.CheckResult
import com.example.dailygoals.engine.Clock
import com.example.dailygoals.engine.CoolingOff
import com.example.dailygoals.engine.DeliveryLevel
import com.example.dailygoals.engine.FailureEvaluator
import com.example.dailygoals.engine.KeepOutcome
import com.example.dailygoals.engine.Warning
import com.example.dailygoals.engine.WarningPlan
import com.example.dailygoals.engine.WarningPlanner
import com.example.dailygoals.engine.GoalPause
import com.example.dailygoals.engine.HabitPauseLength
import com.example.dailygoals.engine.NeutralDays
import com.example.dailygoals.engine.PauseRules
import com.example.dailygoals.engine.PeriodPunishment
import com.example.dailygoals.engine.RescueCheck
import com.example.dailygoals.engine.RescueRules
import com.example.dailygoals.engine.PeriodStatus
import com.example.dailygoals.engine.Periods
import com.example.dailygoals.engine.QuotaRules
import com.example.dailygoals.engine.ScheduledBlocks
import com.example.dailygoals.engine.TempAccessUse
import com.example.dailygoals.engine.TimerRules
import com.example.dailygoals.engine.ZoneChange
import com.example.dailygoals.model.CheckIn
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.model.FailReason
import com.example.dailygoals.model.Goal
import com.example.dailygoals.model.GoalState
import com.example.dailygoals.model.isMet
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ScheduledBatch(val blocks: List<ActiveBlock>, val nextChangeIn: Duration?)

data class WarningBatch(val plan: WarningPlan, val goals: Map<Long, Goal>, val resolvedToday: Set<Long>, val now: java.time.Instant)

class GoalRepository(
    private val database: AppDatabase,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val punishments: BlockingHooks? = null,
) {
    private val goalDao = database.goalDao()
    private val checkInDao = database.checkInDao()
    private val amountLogDao = database.amountLogDao()
    private val periodDao = database.periodResultDao()
    private val warningDao = database.warningMarkDao()
    private val attemptDao = database.blockAttemptDao()
    private val tempAccessDao = database.tempAccessDao()
    private val pendingDao = database.pendingChangeDao()
    private val pauseDao = database.pauseEventDao()
    private val rescueDao = database.rescueDayDao()
    private val evaluationLock = Mutex()

    val goals: Flow<List<Goal>> = goalDao.observeAll().map { list -> list.map(GoalEntity::toModel) }

    val checkIns: Flow<List<CheckIn>> = checkInDao.observeAll().map { list -> list.map(CheckInEntity::toModel) }

    /** Goal id to the epoch millis at which its running timer started. */
    val timers: StateFlow<Map<Long, Long>> = settings.timers

    val pendingChanges: Flow<List<PendingChangeEntity>> = pendingDao.observeAll()

    val pauses: Flow<List<GoalPause>> = pauseDao.observeAll().map { rows -> rows.map(PauseEventEntity::toPause) }

    val rescueDays: Flow<Set<LocalDate>> = rescueDao.observeAll().map { rows -> rows.map { LocalDate.parse(it.date) }.toSet() }

    /** Pauses the goal from today for the chosen length; false when a chosen end date is before today. */
    suspend fun pauseGoal(goalId: Long, length: HabitPauseLength, chosen: LocalDate?): Boolean = database.withTransaction {
        val today = clock.now().toLocalDate()
        if (!PauseRules.isValid(length, today, chosen)) return@withTransaction false
        val rows = pauseDao.all()
        val current = PauseRules.current(rows.map(PauseEventEntity::toPause), goalId, today)
        if (current != null) {
            rows.firstOrNull { it.goalId == goalId && it.startDate == current.start.toString() }?.let { pauseDao.delete(it.id) }
        }
        pauseDao.insert(
            PauseEventEntity(goalId = goalId, startDate = today.toString(), endDate = PauseRules.lastDay(length, today, chosen)?.toString(), createdAt = epochMillis()),
        )
        true
    }

    suspend fun resumeGoal(goalId: Long) = database.withTransaction {
        val today = clock.now().toLocalDate()
        val rows = pauseDao.all()
        val current = PauseRules.current(rows.map(PauseEventEntity::toPause), goalId, today) ?: return@withTransaction
        val row = rows.firstOrNull { it.goalId == goalId && it.startDate == current.start.toString() } ?: return@withTransaction
        val resumed = PauseRules.resumedToday(current, today)
        if (resumed == null) pauseDao.delete(row.id) else pauseDao.setEnd(row.id, resumed.end?.toString())
    }

    /** Uses this week's rescue day on [date]: that date becomes neutral for every goal and its failures are cleared. */
    suspend fun useRescueDay(date: LocalDate): RescueCheck = database.withTransaction {
        val today = clock.now().toLocalDate()
        val used = rescueDao.all().map { LocalDate.parse(it.date) }
        val check = RescueRules.check(date, today, used)
        if (check != RescueCheck.OK) return@withTransaction check
        rescueDao.insert(RescueDayEntity(date = date.toString(), weekStart = RescueRules.weekStart(date).toString(), usedAt = epochMillis()))
        checkInDao.all().filter { it.date == date.toString() && it.status == DayStatus.FAILED }.forEach { row ->
            write(row.goalId, date, DayStatus.PENDING, null, row.amount, null, row)
        }
        check
    }

    /** Paused goals that came back today and were not announced yet, so a notice can say so once. */
    suspend fun takeResumedGoals(): List<Goal> {
        val today = clock.now().toLocalDate()
        val resumed = PauseRules.justResumed(pauseDao.all().map(PauseEventEntity::toPause), today)
        val announced = settings.announcedResumes()
        val fresh = resumed.filter { "${it.goalId}:${it.resumesOn}" !in announced }
        if (fresh.isEmpty()) return emptyList()
        settings.saveAnnouncedResumes(announced + fresh.map { "${it.goalId}:${it.resumesOn}" })
        return fresh.mapNotNull { goalDao.find(it.goalId)?.toModel() }.filter { it.state == GoalState.ACTIVE }
    }

    private suspend fun pausedGoalIds(date: LocalDate): Set<Long> {
        val rows = pauseDao.all().map(PauseEventEntity::toPause)
        return goalDao.all().map { it.id }.filter { PauseRules.isPaused(rows, it, date) }.toSet()
    }

    private suspend fun rescuedDates(): Set<LocalDate> = rescueDao.all().map { LocalDate.parse(it.date) }.toSet()

    suspend fun goal(id: Long): Goal? = goalDao.find(id)?.toModel()

    /**
     * Saves the goal. An edit that would weaken one of the goal's running blocks is held until that block
     * ends; the return value is then the real time it will apply, and null means it applied now. The
     * latest save always replaces an edit that was still waiting.
     */
    suspend fun save(goal: Goal): Long? {
        if (goal.id == 0L) {
            goalDao.insert(goal.copy(sortOrder = goalDao.nextSortOrder()).toEntity())
            return null
        }
        val appliesAt = editAppliesAt(goal)
        pendingDao.deleteForGoal(goal.id)
        if (appliesAt == null) {
            goalDao.update(goal.toEntity())
        } else {
            pendingDao.insert(
                PendingChangeEntity(goalId = goal.id, settingKey = null, newValue = BackupCodec.encodeGoal(goal.toEntity()), appliesAt = appliesAt, createdAt = System.currentTimeMillis()),
            )
        }
        return appliesAt
    }

    private suspend fun editAppliesAt(new: Goal): Long? {
        val old = goalDao.find(new.id)?.toModel() ?: return null
        val now = clock.now()
        val instant = now.atZone(clock.zone).toInstant()
        val weakenings = CoolingOff.weakenings(old, new, now.toLocalDate())
        val scheduledEnd = ScheduledBlocks.span(old, now.toLocalDate(), clock.zone)
            ?.takeIf { instant >= it.first && instant < it.second }
            ?.let { it.second.toEpochMilli() - clockOffsetMillis() }
        val punishmentEnds = punishments?.activeBlocks().orEmpty()
            .filter { it.goalId == old.id && it.kind == BlockKind.PUNISHMENT }
            .map { it.endsAtMillis }
        return CoolingOff.goalEditAppliesAt(weakenings, punishmentEnds + listOfNotNull(scheduledEnd), System.currentTimeMillis())
    }

    /** Archiving during one of the goal's blocks waits like any other weakening edit. */
    suspend fun archive(id: Long): Long? {
        val goal = goalDao.find(id)?.toModel() ?: return null
        return save(goal.copy(state = GoalState.ARCHIVED))
    }

    suspend fun pendingFor(goalId: Long): PendingChangeEntity? = pendingDao.all().firstOrNull { it.goalId == goalId }

    fun isArchive(change: PendingChangeEntity): Boolean =
        runCatching { BackupCodec.decodeGoal(change.newValue).state == GoalState.ARCHIVED }.getOrDefault(false)

    suspend fun queueSetting(key: String, value: String, appliesAt: Long) {
        pendingDao.deleteForSetting(key)
        pendingDao.insert(PendingChangeEntity(goalId = null, settingKey = key, newValue = value, appliesAt = appliesAt, createdAt = System.currentTimeMillis()))
    }

    suspend fun cancelPendingSetting(key: String) = pendingDao.deleteForSetting(key)

    suspend fun cancelPending(id: Long) = pendingDao.delete(id)

    /** Applies every held change whose block has ended. */
    suspend fun applyDuePending() {
        val now = System.currentTimeMillis()
        pendingDao.all().filter { it.appliesAt <= now }.forEach { change ->
            if (change.goalId != null) {
                runCatching { BackupCodec.decodeGoal(change.newValue) }.getOrNull()?.let { goalDao.update(it) }
            } else {
                change.settingKey?.let { punishments?.applySetting(it, change.newValue) }
            }
            pendingDao.delete(change.id)
        }
    }


    suspend fun applyAction(goalId: Long, date: LocalDate, action: CheckAction): CheckResult = database.withTransaction {
        val goal = goalDao.find(goalId)?.toModel() ?: return@withTransaction CheckResult.NoChange
        val today = clock.now().toLocalDate()
        if (!CheckInRules.canEdit(date, today)) return@withTransaction CheckResult.FutureDate
        val existing = checkInDao.find(goalId, date.toString())
        if (goal.measurement.isAmount) amountAction(goal, date, action, existing) else checkAction(goal, date, action, existing)
    }

    private suspend fun checkAction(goal: Goal, date: LocalDate, action: CheckAction, existing: CheckInEntity?): CheckResult {
        if (action == CheckAction.MARK_MINIMUM && !goal.measurement.hasMinimumStep) return CheckResult.NoChange
        val now = clock.now()
        val result = CheckInRules.apply(existing?.status ?: DayStatus.PENDING, action, date, now.toLocalDate())
        if (result is CheckResult.Changed) {
            val late = CheckInRules.lateMinutes(goal.schedule.window, date, now, clock.zone).takeIf { result.status.isMet }
            write(goal.id, date, result.status, result.failReason, existing?.amount ?: 0, late, existing)
        }
        return result
    }

    /** Ticking an amount goal logs what is left to the target; clearing it removes the day's logs. */
    private suspend fun amountAction(goal: Goal, date: LocalDate, action: CheckAction, existing: CheckInEntity?): CheckResult {
        val status = existing?.status ?: DayStatus.PENDING
        val amount = existing?.amount ?: 0
        when (action) {
            CheckAction.TOGGLE_DONE ->
                if (status == DayStatus.DONE) clearDay(goal, date, existing) else logInternal(goal, date, (goal.measurement.target - amount).coerceAtLeast(1))
            CheckAction.CLEAR -> clearDay(goal, date, existing)
            CheckAction.MARK_FAILED -> write(goal.id, date, DayStatus.FAILED, FailReason.MANUAL, amount, null, existing)
            CheckAction.MARK_MINIMUM -> return CheckResult.NoChange
        }
        val after = checkInDao.find(goal.id, date.toString())
        return CheckResult.Changed(after?.status ?: DayStatus.PENDING, after?.failedReason)
    }

    private suspend fun clearDay(goal: Goal, date: LocalDate, existing: CheckInEntity?) {
        amountLogDao.deleteFor(goal.id, date.toString())
        write(goal.id, date, DayStatus.PENDING, null, 0, null, existing)
    }

    suspend fun logAmount(goalId: Long, date: LocalDate, delta: Int) = database.withTransaction {
        val goal = goalDao.find(goalId)?.toModel() ?: return@withTransaction
        if (!goal.measurement.isAmount || delta <= 0) return@withTransaction
        if (!CheckInRules.canEdit(date, clock.now().toLocalDate())) return@withTransaction
        logInternal(goal, date, delta)
    }

    suspend fun undoLastLog(goalId: Long, date: LocalDate) = database.withTransaction {
        val goal = goalDao.find(goalId)?.toModel() ?: return@withTransaction
        val latest = amountLogDao.latest(goalId, date.toString()) ?: return@withTransaction
        amountLogDao.delete(latest.id)
        recalculate(goal, date)
    }

    private suspend fun logInternal(goal: Goal, date: LocalDate, delta: Int) {
        amountLogDao.insert(AmountLogEntity(goalId = goal.id, date = date.toString(), delta = delta, loggedAt = epochMillis()))
        recalculate(goal, date)
    }

    private suspend fun recalculate(goal: Goal, date: LocalDate) {
        val now = clock.now()
        val amount = AmountRules.add(0, amountLogDao.sum(goal.id, date.toString()))
        val existing = checkInDao.find(goal.id, date.toString())
        val fired = FailureEvaluator.firedReason(goal, date, now, clock.zone, settings.settings.value.dayEndMinute)
        val resolved = AmountRules.resolve(goal.measurement, amount, existing?.status ?: DayStatus.PENDING, existing?.failedReason, fired)
        val wasMet = existing?.status?.isMet == true
        val late = when {
            !resolved.status.isMet -> null
            wasMet -> existing?.lateMinutes
            else -> CheckInRules.lateMinutes(goal.schedule.window, date, now, clock.zone)
        }
        write(goal.id, date, resolved.status, resolved.failReason, amount, late, existing)
    }

    private suspend fun write(
        goalId: Long,
        date: LocalDate,
        status: DayStatus,
        reason: FailReason?,
        amount: Int,
        lateMinutes: Int?,
        existing: CheckInEntity?,
    ) {
        val millis = epochMillis()
        val punishmentSkipped = applyPunishment(goalId, date, status, existing, millis)
        checkInDao.save(
            CheckInEntity(
                id = existing?.id ?: 0,
                goalId = goalId,
                date = date.toString(),
                status = status,
                failedReason = reason,
                completedAt = millis.takeIf { status.isMet },
                updatedAt = millis,
                amount = amount,
                lateMinutes = lateMinutes,
                punishmentSkipped = punishmentSkipped,
            ),
        )
    }

    /** A new failure today starts its punishment once; correcting a failed day ends what that failure caused. */
    private suspend fun applyPunishment(goalId: Long, date: LocalDate, status: DayStatus, existing: CheckInEntity?, millis: Long): Boolean {
        val sink = punishments ?: return existing?.punishmentSkipped ?: false
        val before = existing?.status
        if (status == DayStatus.FAILED && before != DayStatus.FAILED && date == clock.now().toLocalDate()) {
            val goal = goalDao.find(goalId)?.toModel() ?: return false
            return sink.onFailed(goal, date, millis)
        }
        if (status != DayStatus.FAILED && before == DayStatus.FAILED) {
            sink.onCorrected(goalId, date)
            return false
        }
        return existing?.punishmentSkipped ?: false
    }

    /** The answer "I did not use it": keeps the day unless the user already marked it failed by hand. */
    suspend fun answerKept(goalId: Long, date: LocalDate) = database.withTransaction {
        val existing = checkInDao.find(goalId, date.toString())
        val status = existing?.status ?: DayStatus.PENDING
        val failedByRule = status == DayStatus.FAILED && existing?.failedReason != FailReason.MANUAL
        if (status == DayStatus.PENDING || failedByRule) write(goalId, date, DayStatus.DONE, null, existing?.amount ?: 0, null, existing)
    }

    /** The scheduled blocks in force now, with end times on the real clock, and how long until one changes. */
    suspend fun scheduledBatch(): ScheduledBatch {
        val now = clock.now()
        val offset = clockOffsetMillis()
        val resting = pausedGoalIds(now.toLocalDate())
        val plan = ScheduledBlocks.plan(goalDao.all().map(GoalEntity::toModel).filter { it.id !in resting }, now, clock.zone)
        val wait = plan.nextChange?.let { Duration.between(now.atZone(clock.zone).toInstant(), it) }
        val pendingWait = pendingDao.all().minOfOrNull { it.appliesAt }?.let { Duration.ofMillis((it - System.currentTimeMillis()).coerceAtLeast(0)) }
        return ScheduledBatch(plan.active.map { it.copy(endsAtMillis = it.endsAtMillis - offset) }, listOfNotNull(wait, pendingWait).minOrNull())
    }

    suspend fun recordAttempts(blocks: List<ActiveBlock>, atMillis: Long) {
        blocks.filter { it.goalId > 0 }.forEach {
            attemptDao.insert(BlockAttemptEntity(goalId = it.goalId, packageName = it.packageName, kind = it.kind, attemptedAt = atMillis))
        }
    }

    suspend fun logTempAccess(use: TempAccessUse, goalIds: List<Long>, reason: String, minutes: Int) {
        tempAccessDao.insert(
            TempAccessEntity(
                packageName = use.packageName,
                goalIds = goalIds.filter { it > 0 }.joinToString(","),
                reason = reason,
                startedAt = use.startedAtMillis,
                endsAt = use.endsAtMillis,
                minutes = minutes,
            ),
        )
    }

    suspend fun markTempAccessEndedEarly(use: TempAccessUse, endedAtMillis: Long) =
        tempAccessDao.markEndedEarly(use.packageName, use.startedAtMillis, endedAtMillis)

    suspend fun attemptsToday(goalId: Long): Int {
        val dayStart = LocalDate.now(clock.zone).atStartOfDay(clock.zone)
        return attemptDao.count(goalId, dayStart.toInstant().toEpochMilli(), dayStart.plusDays(1).toInstant().toEpochMilli())
    }

    /** How far the app's clock is ahead of the real one; only a debug time shift makes this non-zero. */
    private fun clockOffsetMillis(): Long = epochMillis() - System.currentTimeMillis()

    fun startTimer(goalId: Long) = settings.startTimer(goalId, epochMillis())

    /** Stops the timer and logs the whole minutes it ran for today; returns the minutes logged. */
    suspend fun stopTimer(goalId: Long): Int {
        val start = settings.stopTimer(goalId) ?: return 0
        val minutes = TimerRules.elapsedMinutes(start, epochMillis())
        if (minutes > 0) logAmount(goalId, clock.now().toLocalDate(), minutes)
        return minutes
    }

    /**
     * Applies the window-end and day-end rules and judges quota periods. Safe to run often: dates that
     * were already closed are never reopened, and results the user set are never overwritten.
     */
    suspend fun evaluate() = evaluationLock.withLock {
        val now = clock.now()
        val zone = clock.zone
        val state = settings.evalState()
        val oldZone = state.zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
        val neutral = state.neutralDates + ZoneChange.skippedDates(state.lastToday, now.atZone(zone).toInstant(), oldZone, zone)
        val dayEnd = settings.settings.value.dayEndMinute
        val offset = clockOffsetMillis()
        val lifts = punishments?.lifts().orEmpty().map { it.copy(startMillis = it.startMillis + offset, endMillis = it.endMillis + offset) }
        val questions = mutableListOf<Pair<Goal, LocalDate>>()
        database.withTransaction {
            val models = goalDao.all().map(GoalEntity::toModel)
            val rows = checkInDao.all()
            val pauseRows = pauseDao.all().map(PauseEventEntity::toPause)
            val rescued = rescuedDates()
            val isNeutral = { goalId: Long, date: LocalDate -> date in rescued || PauseRules.isPaused(pauseRows, goalId, date) }
            val byKey = rows.associateBy { it.goalId to LocalDate.parse(it.date) }
            val recorded = { goalId: Long, date: LocalDate -> byKey[goalId to date]?.status ?: DayStatus.PENDING }
            val keeps = ScheduledBlocks.autoKeeps(models, recorded, now, zone, dayEnd, state.finalisedThrough, neutral, lifts, isNeutral)
            keeps.forEach { keep ->
                if (keep.outcome == KeepOutcome.KEEP) {
                    write(keep.goalId, keep.date, DayStatus.DONE, null, 0, null, byKey[keep.goalId to keep.date])
                } else {
                    models.firstOrNull { it.id == keep.goalId }?.let { questions += it to keep.date }
                }
            }
            val kept = keeps.filter { it.outcome == KeepOutcome.KEEP }.map { it.goalId to it.date }.toSet()
            val failures = FailureEvaluator.newFailures(
                goals = models,
                statusOf = { goalId, date -> if (goalId to date in kept) DayStatus.DONE else recorded(goalId, date) },
                now = now,
                zone = zone,
                dayEndMinute = dayEnd,
                finalisedThrough = state.finalisedThrough,
                neutralDates = neutral,
                isNeutral = isNeutral,
            )
            val unanswered = questions.map { (goal, date) -> goal.id to date }.toSet()
            // A question about a lifted block is still open, so the window ending must not decide the day for the user.
            failures.filterNot { it.reason == FailReason.WINDOW_END && (it.goalId to it.date) in unanswered }.forEach { failure ->
                val existing = byKey[failure.goalId to failure.date]
                write(failure.goalId, failure.date, DayStatus.FAILED, failure.reason, existing?.amount ?: 0, null, existing)
            }
            judgeQuotaPeriods(models, rows, neutral, state.finalisedThrough, rescued, pauseRows)
        }
        questions.forEach { (goal, date) -> punishments?.askWhetherUsed(goal, date) }
        settings.saveEvalState(
            state.copy(
                finalisedThrough = FailureEvaluator.finalisedAfter(now, state.finalisedThrough),
                lastToday = now.toLocalDate(),
                zoneId = zone.id,
                neutralDates = neutral,
            ),
        )
    }

    private suspend fun judgeQuotaPeriods(
        goals: List<Goal>,
        rows: List<CheckInEntity>,
        neutral: Set<LocalDate>,
        finalisedThrough: LocalDate?,
        rescued: Set<LocalDate>,
        pauseRows: List<GoalPause>,
    ) {
        val now = clock.now()
        val dayEnd = settings.settings.value.dayEndMinute
        goals.filter { it.state == GoalState.ACTIVE && it.schedule.isQuota }.forEach { goal ->
            val frequency = goal.schedule.frequency
            val metDates = rows.filter { it.goalId == goal.id && it.status.isMet }.map { LocalDate.parse(it.date) }.toSet()
            val first = Periods.of(frequency, goal.schedule.startDate) ?: return@forEach
            generateSequence(first) { Periods.of(frequency, it.end.plusDays(1)) }
                .takeWhile { it.start <= now.toLocalDate() }
                .filter { finalisedThrough == null || it.end > finalisedThrough }
                .forEach { period ->
                    val goalNeutral = NeutralDays.forGoal(goal.id, neutral, rescued, pauseRows, period.start, period.end)
                    val outcome = QuotaRules.evaluate(goal.schedule, period, metDates, goalNeutral, now, dayEnd)
                    val before = periodDao.find(goal.id, period.start.toString())
                    when (QuotaRules.punishmentChange(before?.status, outcome.status, outcome.failedOn, now.toLocalDate())) {
                        PeriodPunishment.START -> outcome.failedOn?.let { punishments?.onFailed(goal, it, epochMillis()) }
                        PeriodPunishment.CANCEL -> before?.failedAt?.let { punishments?.onCorrected(goal.id, LocalDate.parse(it)) }
                        PeriodPunishment.NONE -> Unit
                    }
                    if (outcome.status == PeriodStatus.OPEN) {
                        periodDao.deleteFor(goal.id, period.start.toString())
                    } else {
                        periodDao.upsert(
                            PeriodResultEntity(
                                goalId = goal.id,
                                periodStart = period.start.toString(),
                                periodEnd = period.end.toString(),
                                originalRequired = outcome.originalRequired,
                                adjustedRequired = outcome.adjustedRequired,
                                eligibleDays = outcome.eligibleDays,
                                pausedDays = 0,
                                rescuedDays = 0,
                                completed = outcome.completed,
                                status = outcome.status,
                                failedAt = outcome.failedOn?.toString(),
                                recalculatedAt = epochMillis(),
                            ),
                        )
                    }
                }
        }
    }

    /** Fires whenever goals or results change, so alarms can be rebuilt from the database. */
    val changes: Flow<Unit> = kotlinx.coroutines.flow.combine(goals, checkIns, pendingChanges, pauses, rescueDays) { _, _, _, _, _ -> }

    /** What to warn about right now and when the app next needs to wake up. */
    suspend fun warningBatch(): WarningBatch {
        val now = clock.now()
        val today = now.toLocalDate()
        val resting = pausedGoalIds(today)
        val rescuedToday = today in rescuedDates()
        val models = goalDao.all().map(GoalEntity::toModel).filter { it.id !in resting && !rescuedToday }
        val statuses = checkInDao.all().associate { (it.goalId to LocalDate.parse(it.date)) to it.status }
        val marks = warningDao.forDate(today.toString()).associate { (it.goalId to LocalDate.parse(it.date)) to it.lastIndex }
        val plan = WarningPlanner.plan(
            goals = models,
            statusOf = { goalId, date -> statuses[goalId to date] ?: DayStatus.PENDING },
            marks = marks,
            now = now,
            zone = clock.zone,
            dayEndMinute = settings.settings.value.dayEndMinute,
        )
        val resolved = models.map { it.id }.filter { (statuses[it to today] ?: DayStatus.PENDING) != DayStatus.PENDING }.toSet()
        return WarningBatch(plan, models.associateBy { it.id }, resolved, now.atZone(clock.zone).toInstant())
    }

    suspend fun statusOn(goalId: Long, date: LocalDate): DayStatus =
        checkInDao.find(goalId, date.toString())?.status ?: DayStatus.PENDING

    suspend fun markWarningShown(warning: Warning, level: DeliveryLevel) {
        warningDao.upsert(
            WarningMarkEntity(goalId = warning.goalId, date = warning.date.toString(), lastIndex = warning.index, level = level, firedAt = epochMillis()),
        )
        warningDao.deleteBefore(clock.now().toLocalDate().minusDays(KEEP_MARK_DAYS).toString())
    }

    fun neutralDates(): Set<LocalDate> = settings.evalState().neutralDates

    private companion object {
        const val KEEP_MARK_DAYS = 7L
    }

    private fun epochMillis(): Long = clock.now().atZone(clock.zone).toInstant().toEpochMilli()
}
