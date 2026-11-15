package com.example.dailygoals.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.dailygoals.model.GoalState
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY sortOrder, id")
    suspend fun all(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun find(id: Long): GoalEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM goals")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: GoalState)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins")
    fun observeAll(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins")
    suspend fun all(): List<CheckInEntity>

    @Query("SELECT * FROM check_ins WHERE goalId = :goalId AND date = :date")
    suspend fun find(goalId: Long, date: String): CheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(checkIn: CheckInEntity)

    @Query("DELETE FROM check_ins")
    suspend fun deleteAll()
}

@Dao
interface AmountLogDao {
    @Query("SELECT * FROM amount_logs")
    suspend fun all(): List<AmountLogEntity>

    @Query("SELECT COALESCE(SUM(delta), 0) FROM amount_logs WHERE goalId = :goalId AND date = :date")
    suspend fun sum(goalId: Long, date: String): Int

    @Query("SELECT * FROM amount_logs WHERE goalId = :goalId AND date = :date ORDER BY id DESC LIMIT 1")
    suspend fun latest(goalId: Long, date: String): AmountLogEntity?

    @Insert
    suspend fun insert(log: AmountLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(log: AmountLogEntity): Long

    @Query("DELETE FROM amount_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM amount_logs WHERE goalId = :goalId AND date = :date")
    suspend fun deleteFor(goalId: Long, date: String)

    @Query("DELETE FROM amount_logs")
    suspend fun deleteAll()
}

@Dao
interface WarningMarkDao {
    @Query("SELECT * FROM warning_marks WHERE date = :date")
    suspend fun forDate(date: String): List<WarningMarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mark: WarningMarkEntity)

    @Query("DELETE FROM warning_marks WHERE date < :date")
    suspend fun deleteBefore(date: String)
}

@Dao
interface PeriodResultDao {
    @Query("SELECT * FROM period_results WHERE goalId = :goalId AND periodStart = :periodStart")
    suspend fun find(goalId: Long, periodStart: String): PeriodResultEntity?

    @Query("SELECT * FROM period_results")
    suspend fun all(): List<PeriodResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: PeriodResultEntity)

    @Query("DELETE FROM period_results WHERE goalId = :goalId AND periodStart = :periodStart")
    suspend fun deleteFor(goalId: Long, periodStart: String)

    @Query("DELETE FROM period_results")
    suspend fun deleteAll()
}

@Dao
interface BlockAttemptDao {
    @Insert
    suspend fun insert(attempt: BlockAttemptEntity): Long

    @Query("SELECT COUNT(*) FROM block_attempts WHERE goalId = :goalId AND attemptedAt >= :from AND attemptedAt < :until")
    suspend fun count(goalId: Long, from: Long, until: Long): Int

    @Query("DELETE FROM block_attempts")
    suspend fun deleteAll()
}

@Dao
interface TempAccessDao {
    @Insert
    suspend fun insert(use: TempAccessEntity): Long

    @Query("UPDATE temp_access SET endedEarly = 1, endsAt = :endedAt WHERE startedAt = :startedAt AND packageName = :packageName")
    suspend fun markEndedEarly(packageName: String, startedAt: Long, endedAt: Long)

    @Query("SELECT * FROM temp_access ORDER BY startedAt DESC")
    suspend fun all(): List<TempAccessEntity>

    @Query("DELETE FROM temp_access")
    suspend fun deleteAll()
}

@Dao
interface PendingChangeDao {
    @Query("SELECT * FROM pending_changes ORDER BY appliesAt, id")
    fun observeAll(): Flow<List<PendingChangeEntity>>

    @Query("SELECT * FROM pending_changes ORDER BY appliesAt, id")
    suspend fun all(): List<PendingChangeEntity>

    @Insert
    suspend fun insert(change: PendingChangeEntity): Long

    @Query("DELETE FROM pending_changes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_changes WHERE goalId = :goalId")
    suspend fun deleteForGoal(goalId: Long)

    @Query("DELETE FROM pending_changes WHERE settingKey = :key")
    suspend fun deleteForSetting(key: String)

    @Query("DELETE FROM pending_changes")
    suspend fun deleteAll()
}

@Dao
interface PauseEventDao {
    @Query("SELECT * FROM pause_events ORDER BY startDate")
    fun observeAll(): Flow<List<PauseEventEntity>>

    @Query("SELECT * FROM pause_events ORDER BY startDate")
    suspend fun all(): List<PauseEventEntity>

    @Insert
    suspend fun insert(event: PauseEventEntity): Long

    @Query("UPDATE pause_events SET endDate = :endDate WHERE id = :id")
    suspend fun setEnd(id: Long, endDate: String?)

    @Query("DELETE FROM pause_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pause_events")
    suspend fun deleteAll()
}

@Dao
interface RescueDayDao {
    @Query("SELECT * FROM rescue_days ORDER BY date")
    fun observeAll(): Flow<List<RescueDayEntity>>

    @Query("SELECT * FROM rescue_days ORDER BY date")
    suspend fun all(): List<RescueDayEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(day: RescueDayEntity): Long

    @Query("DELETE FROM rescue_days")
    suspend fun deleteAll()
}

@Dao
interface StakeDao {
    @Query("SELECT * FROM stakes ORDER BY dueDate, id")
    fun observeAll(): Flow<List<StakeEntity>>

    @Query("SELECT * FROM stakes ORDER BY dueDate, id")
    suspend fun all(): List<StakeEntity>

    @Query("SELECT * FROM stakes WHERE goalId = :goalId AND sourceDate = :sourceDate")
    suspend fun find(goalId: Long, sourceDate: String): StakeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(stake: StakeEntity): Long

    @Query("UPDATE stakes SET status = :status, resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, resolvedAt: Long?)

    @Query("DELETE FROM stakes")
    suspend fun deleteAll()
}

@Dao
interface ViolationDao {
    @Insert
    suspend fun insert(event: ViolationEventEntity): Long

    @Query("SELECT * FROM violation_events ORDER BY openedAt DESC")
    fun observeAll(): Flow<List<ViolationEventEntity>>

    @Query("SELECT * FROM violation_events ORDER BY openedAt DESC")
    suspend fun all(): List<ViolationEventEntity>

    @Query("UPDATE violation_events SET answer = :answer WHERE id = :id")
    suspend fun setAnswer(id: Long, answer: String)

    @Query("DELETE FROM violation_events")
    suspend fun deleteAll()
}

@Dao
interface WeeklyReviewDao {
    @Query("SELECT * FROM weekly_reviews ORDER BY weekStart DESC")
    fun observeAll(): Flow<List<WeeklyReviewEntity>>

    @Query("SELECT * FROM weekly_reviews WHERE weekStart = :weekStart")
    suspend fun find(weekStart: String): WeeklyReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(review: WeeklyReviewEntity)

    @Query("DELETE FROM weekly_reviews")
    suspend fun deleteAll()
}
