package app.zhencao.qianwen.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val script: String = "",
    val verse: Int = 0,
)

@Entity(tableName = "practices", indices = [Index("charIndex")])
data class PracticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val charIndex: Int,
    /** [app.zhencao.qianwen.model.ScriptStyle] name the practice was compared against. */
    val script: String,
    val createdAt: Long,
    /** Path of the 512px practice crop, relative to filesDir. */
    val file: String,
    val overlap: Float,
    val dx: Float,
    val dy: Float,
)

data class PracticeCount(val charIndex: Int, val count: Int)

@Dao
interface QianwenDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(entity: SettingsEntity)

    @Insert
    suspend fun insertPractice(entity: PracticeEntity): Long

    @Query("SELECT * FROM practices WHERE id = :id")
    fun observePractice(id: Long): Flow<PracticeEntity?>

    @Query("SELECT * FROM practices WHERE id = :id")
    suspend fun getPractice(id: Long): PracticeEntity?

    @Query("SELECT * FROM practices WHERE charIndex = :charIndex AND script = :script ORDER BY createdAt DESC")
    fun observePractices(charIndex: Int, script: String): Flow<List<PracticeEntity>>

    @Query("SELECT charIndex, COUNT(*) AS count FROM practices WHERE script = :script GROUP BY charIndex")
    fun observePracticeCounts(script: String): Flow<List<PracticeCount>>

    @Query("DELETE FROM practices WHERE id = :id")
    suspend fun deletePractice(id: Long)
}
