package app.zhencao.qianwen.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SettingsEntity::class,
        DailyTaskEntity::class,
        CheckInEntity::class,
        QuizAnswerEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class QianwenDatabase : RoomDatabase() {
    abstract fun dao(): QianwenDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN script TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
