package app.zhencao.qianwen.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SettingsEntity::class,
        PracticeEntity::class,
    ],
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE settings_new (id INTEGER NOT NULL, script TEXT NOT NULL, " +
                        "verse INTEGER NOT NULL, PRIMARY KEY(id))",
                )
                db.execSQL(
                    "INSERT INTO settings_new (id, script, verse) " +
                        "SELECT id, script, MIN(cursor / 4, 249) FROM settings",
                )
                db.execSQL("DROP TABLE settings")
                db.execSQL("ALTER TABLE settings_new RENAME TO settings")
                db.execSQL("DROP TABLE IF EXISTS daily_tasks")
                db.execSQL("DROP TABLE IF EXISTS check_ins")
                db.execSQL("DROP TABLE IF EXISTS quiz_answers")
                db.execSQL(
                    "CREATE TABLE practices (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "charIndex INTEGER NOT NULL, script TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "file TEXT NOT NULL, " +
                        "overlap REAL NOT NULL, dx REAL NOT NULL, dy REAL NOT NULL)",
                )
                db.execSQL("CREATE INDEX index_practices_charIndex ON practices (charIndex)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN grid TEXT NOT NULL DEFAULT 'MI'")
            }
        }
    }
}
