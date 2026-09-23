package app.zhencao.qianwen.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val dailyCount: Int = 4,
    val edition: String = "INK",
    val grid: String = "MI",
    val invert: Boolean = false,
    val layout: String = "SIDE",
    val cursor: Int = 0,
    val lastPlanDate: String = "",
    val streak: Int = 0,
    val lastCheckInDate: String = "",
    val script: String = "",
)

@Entity(
    tableName = "daily_tasks",
    primaryKeys = ["date", "position"],
)
data class DailyTaskEntity(
    val date: String,
    val position: Int,
    val charIndex: Int,
    val viewed: Boolean = false,
    val strokePlayed: Boolean = false,
    val wrote: Boolean = false,
    val overlay: Boolean = false,
)

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey val date: String,
    val charCount: Int,
    val createdAt: Long,
)

@Entity(tableName = "quiz_answers")
data class QuizAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val charIndex: Int,
    val correct: Boolean,
    val createdAt: Long,
)

data class QuizStats(val total: Int, val correct: Int)

@Dao
abstract class QianwenDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    abstract fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    abstract suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSettings(entity: SettingsEntity)

    @Query("SELECT * FROM daily_tasks WHERE date = :date ORDER BY position")
    abstract fun observeTasks(date: String): Flow<List<DailyTaskEntity>>

    @Query("SELECT * FROM daily_tasks WHERE date = :date ORDER BY position")
    abstract suspend fun getTasks(date: String): List<DailyTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTasks(tasks: List<DailyTaskEntity>)

    @Query("DELETE FROM daily_tasks WHERE date = :date")
    abstract suspend fun deleteTasks(date: String)

    @Transaction
    open suspend fun replaceTasks(date: String, tasks: List<DailyTaskEntity>) {
        deleteTasks(date)
        if (tasks.isNotEmpty()) upsertTasks(tasks)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertCheckIn(entity: CheckInEntity)

    @Query("SELECT * FROM check_ins WHERE date = :date")
    abstract suspend fun getCheckIn(date: String): CheckInEntity?

    @Query("SELECT * FROM check_ins WHERE date = :date")
    abstract fun observeCheckIn(date: String): Flow<CheckInEntity?>

    @Query("SELECT COUNT(*) FROM check_ins")
    abstract fun observeCheckInCount(): Flow<Int>

    @Insert
    abstract suspend fun insertQuiz(entity: QuizAnswerEntity)

    @Query("SELECT COUNT(*) FROM quiz_answers")
    abstract fun observeQuizCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM quiz_answers WHERE correct = 1")
    abstract fun observeQuizCorrect(): Flow<Int>

    fun observeQuizStats(): Flow<QuizStats> = combine(
        observeQuizCount(),
        observeQuizCorrect(),
    ) { total, correct ->
        QuizStats(total, correct)
    }
}
