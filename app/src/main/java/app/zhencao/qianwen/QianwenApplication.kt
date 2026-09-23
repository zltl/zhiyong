package app.zhencao.qianwen

import android.app.Application
import androidx.room.Room
import app.zhencao.qianwen.data.Corpus
import app.zhencao.qianwen.data.CorpusLoader
import app.zhencao.qianwen.data.db.QianwenDatabase

class QianwenApplication : Application() {
    lateinit var corpus: Corpus
        private set
    lateinit var database: QianwenDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        corpus = CorpusLoader.load(this)
        database = Room.databaseBuilder(
            this,
            QianwenDatabase::class.java,
            "qianwen.db",
        ).addMigrations(QianwenDatabase.MIGRATION_1_2, QianwenDatabase.MIGRATION_2_3).build()
    }
}
