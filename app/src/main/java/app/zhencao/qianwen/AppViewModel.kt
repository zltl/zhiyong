package app.zhencao.qianwen

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.zhencao.qianwen.data.Comparison
import app.zhencao.qianwen.data.Corpus
import app.zhencao.qianwen.data.DiffCell
import app.zhencao.qianwen.data.InkAnalysis
import app.zhencao.qianwen.data.InkMask
import app.zhencao.qianwen.data.PracticeStore
import app.zhencao.qianwen.data.SheetTransform
import app.zhencao.qianwen.data.VERSE_COUNT
import app.zhencao.qianwen.data.db.PracticeEntity
import app.zhencao.qianwen.data.db.QianwenDao
import app.zhencao.qianwen.data.db.SettingsEntity
import app.zhencao.qianwen.data.maskOf
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.model.parseScriptStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class HomeState(
    val ready: Boolean = false,
    val script: ScriptStyle? = null,
    val verse: Int = 0,
    /** Practice count per character index, for the chosen script. */
    val counts: Map<Int, Int> = emptyMap(),
)

/** A photographed sheet of practice; several characters can be framed from it in turn. */
class Sheet(val bitmap: Bitmap, val transform: SheetTransform?)

class Analysis(
    val crop: Bitmap,
    val model: InkMask,
    val user: InkMask,
    val comparison: Comparison?,
    val diff: Array<DiffCell>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    application: Application,
    val corpus: Corpus,
    private val dao: QianwenDao,
    private val store: PracticeStore,
) : AndroidViewModel(application) {
    private val settingsMutex = Mutex()
    private val _selected = MutableStateFlow(0)
    val selected: StateFlow<Int> = _selected.asStateFlow()

    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet.asStateFlow()

    private val settings = dao.observeSettings()

    val home: StateFlow<HomeState> = settings.filterNotNull().flatMapLatest { entity ->
        val script = parseScriptStyle(entity.script)
        dao.observePracticeCounts(script?.name.orEmpty()).map { counts ->
            HomeState(
                ready = true,
                script = script,
                verse = entity.verse.coerceIn(0, VERSE_COUNT - 1),
                counts = counts.associate { it.charIndex to it.count },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    init {
        updateSettings { it }
    }

    fun select(index: Int) {
        _selected.value = index
        setVerse(corpus[index].group)
    }

    fun setVerse(verse: Int) = updateSettings { it.copy(verse = verse.coerceIn(0, VERSE_COUNT - 1)) }

    fun setScript(script: ScriptStyle) = updateSettings { it.copy(script = script.name) }

    fun practices(charIndex: Int, script: ScriptStyle): Flow<List<PracticeEntity>> =
        dao.observePractices(charIndex, script.name)

    fun practice(id: Long): Flow<PracticeEntity?> = dao.observePractice(id)

    fun captureUri(): Uri = store.captureUri()

    suspend fun loadCrop(practice: PracticeEntity): Bitmap? =
        withContext(Dispatchers.IO) { store.loadCrop(practice.file) }

    suspend fun openSheet(uri: Uri): Boolean {
        val bitmap = withContext(Dispatchers.IO) { runCatching { store.decodeSheet(uri) }.getOrNull() }
            ?: return false
        _sheet.value = Sheet(bitmap, transform = null)
        return true
    }

    fun rememberSheetTransform(transform: SheetTransform) {
        _sheet.value = _sheet.value?.let { Sheet(it.bitmap, transform) }
    }

    fun modelMask(charIndex: Int, script: ScriptStyle): InkMask? =
        store.modelMask(corpus[charIndex], script)

    suspend fun savePractice(charIndex: Int, script: ScriptStyle, crop: Bitmap): Long =
        withContext(Dispatchers.Default) {
            val model = store.modelMask(corpus[charIndex], script)
            val user = maskOf(crop, dropBorder = true)
            val comparison = model?.let { InkAnalysis.compare(it, user) }
            val createdAt = System.currentTimeMillis()
            val file = withContext(Dispatchers.IO) { store.saveCrop(crop, charIndex, createdAt) }
            dao.insertPractice(
                PracticeEntity(
                    charIndex = charIndex,
                    script = script.name,
                    createdAt = createdAt,
                    file = file,
                    overlap = comparison?.overlap ?: 0f,
                    dx = comparison?.dx ?: 0f,
                    dy = comparison?.dy ?: 0f,
                ),
            )
        }

    suspend fun analyze(practice: PracticeEntity): Analysis? = withContext(Dispatchers.Default) {
        val script = parseScriptStyle(practice.script) ?: return@withContext null
        val crop = withContext(Dispatchers.IO) { store.loadCrop(practice.file) } ?: return@withContext null
        val model = store.modelMask(corpus[practice.charIndex], script) ?: return@withContext null
        val user = maskOf(crop, dropBorder = true)
        Analysis(crop, model, user, InkAnalysis.compare(model, user), InkAnalysis.diff(model, user))
    }

    fun deletePractice(practice: PracticeEntity) {
        viewModelScope.launch {
            dao.deletePractice(practice.id)
            withContext(Dispatchers.IO) { store.deleteCrop(practice.file) }
        }
    }

    private fun updateSettings(transform: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch {
            settingsMutex.withLock {
                val stored = dao.getSettings()
                val current = stored ?: SettingsEntity()
                val next = transform(current)
                if (stored == null || next != current) dao.upsertSettings(next)
            }
        }
    }
}

class AppViewModelFactory(
    private val app: QianwenApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(app, app.corpus, app.database.dao(), PracticeStore(app)) as T
    }
}
