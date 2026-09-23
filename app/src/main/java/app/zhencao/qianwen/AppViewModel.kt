package app.zhencao.qianwen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.zhencao.qianwen.data.Corpus
import app.zhencao.qianwen.data.DailyPlanner
import app.zhencao.qianwen.data.db.DailyTaskEntity
import app.zhencao.qianwen.data.db.QianwenDao
import app.zhencao.qianwen.data.db.QuizAnswerEntity
import app.zhencao.qianwen.data.db.QuizStats
import app.zhencao.qianwen.data.db.SettingsEntity
import app.zhencao.qianwen.model.Edition
import app.zhencao.qianwen.model.GridType
import app.zhencao.qianwen.model.InkStroke
import app.zhencao.qianwen.model.LayoutMode
import app.zhencao.qianwen.model.QuizKind
import app.zhencao.qianwen.model.QuizQuestion
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.model.parseScriptStyle
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

data class UserSettings(
    val dailyCount: Int = 4,
    val edition: Edition = Edition.INK,
    val script: ScriptStyle? = null,
    val grid: GridType = GridType.MI,
    val invert: Boolean = false,
    val layout: LayoutMode = LayoutMode.SIDE,
    val cursor: Int = 0,
)

data class TodayChar(
    val index: Int,
    val char: String,
    val group: Int,
)

data class HomeState(
    val ready: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val today: List<TodayChar> = emptyList(),
    val quizTotal: Int = 0,
    val quizCorrect: Int = 0,
    val sampleExhausted: Boolean = false,
)

data class QuizUi(
    val kind: QuizKind = QuizKind.RECOGNIZE_CAO,
    val question: QuizQuestion? = null,
    val revealed: Boolean = false,
    val pickedIndex: Int? = null,
    val sessionCorrect: Int = 0,
    val sessionTotal: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    application: Application,
    val corpus: Corpus,
    private val dao: QianwenDao,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : AndroidViewModel(application) {
    private val planMutex = Mutex()
    private val todayDate = MutableStateFlow(clock().toString())
    private val random = Random(System.currentTimeMillis())

    private val _selected = MutableStateFlow(0)
    val selected: StateFlow<Int> = _selected.asStateFlow()

    private val _ink = MutableStateFlow<Map<Int, List<InkStroke>>>(emptyMap())
    val ink: StateFlow<Map<Int, List<InkStroke>>> = _ink.asStateFlow()

    private val _photos = MutableStateFlow<Map<Int, String>>(emptyMap())
    val photos: StateFlow<Map<Int, String>> = _photos.asStateFlow()

    private val _quiz = MutableStateFlow(QuizUi(question = makeQuestion(QuizKind.RECOGNIZE_CAO)))
    val quiz: StateFlow<QuizUi> = _quiz.asStateFlow()

    val home: StateFlow<HomeState> = combine(
        dao.observeSettings(),
        todayDate.flatMapLatest { dao.observeTasks(it) },
        dao.observeQuizStats(),
    ) { settings, tasks, quizStats ->
        toHome(settings, tasks, quizStats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        ensureToday()
    }

    fun ensureToday() {
        viewModelScope.launch { refreshPlan() }
    }

    fun select(index: Int) {
        _selected.value = index
    }

    fun setInk(index: Int, strokes: List<InkStroke>) {
        _ink.value = _ink.value + (index to strokes)
    }

    fun setPhoto(index: Int, uri: String?) {
        _photos.value = if (uri == null) _photos.value - index else _photos.value + (index to uri)
    }

    fun setDailyCount(count: Int) {
        if (count != 4 && count != 8) return
        viewModelScope.launch {
            refreshPlan(allowReplace = true) { current ->
                if (current.dailyCount == count) current else current.copy(dailyCount = count)
            }
        }
    }

    fun setEdition(edition: Edition) = saveSettings { it.copy(edition = edition.name) }

    fun setScript(script: ScriptStyle) = saveSettings { it.copy(script = script.name) }

    fun setGrid(grid: GridType) = saveSettings { it.copy(grid = grid.name) }

    fun setInvert(invert: Boolean) = saveSettings { it.copy(invert = invert) }

    fun setLayout(layout: LayoutMode) = saveSettings { it.copy(layout = layout.name) }

    fun markViewed(index: Int) = patchTask(index) { it.copy(viewed = true) }

    fun markStrokePlayed(index: Int) = patchTask(index) { it.copy(strokePlayed = true) }

    fun markWrote(index: Int) = patchTask(index) { it.copy(wrote = true) }

    fun markOverlay(index: Int) = patchTask(index) { it.copy(overlay = true) }

    fun setQuizKind(kind: QuizKind) {
        val state = _quiz.value
        if (state.kind == kind) return
        _quiz.value = state.copy(
            kind = kind,
            question = makeQuestion(kind),
            revealed = false,
            pickedIndex = null,
        )
    }

    fun answer(optionIndex: Int) {
        val state = _quiz.value
        val question = state.question ?: return
        if (state.revealed) return
        val correct = optionIndex == question.answerIndex
        _quiz.value = state.copy(
            revealed = true,
            pickedIndex = optionIndex,
            sessionCorrect = state.sessionCorrect + if (correct) 1 else 0,
            sessionTotal = state.sessionTotal + 1,
        )
        viewModelScope.launch {
            dao.insertQuiz(
                QuizAnswerEntity(
                    kind = question.kind.name,
                    charIndex = question.answerIndex,
                    correct = correct,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun nextQuestion() {
        val state = _quiz.value
        _quiz.value = state.copy(
            question = makeQuestion(state.kind),
            revealed = false,
            pickedIndex = null,
        )
    }

    private fun saveSettings(transform: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { refreshPlan(allowReplace = false, transform = transform) }
    }

    private fun patchTask(index: Int, block: (DailyTaskEntity) -> DailyTaskEntity) {
        viewModelScope.launch {
            val task = dao.getTasks(todayDate.value).find { it.charIndex == index } ?: return@launch
            val next = block(task)
            if (next != task) dao.upsertTasks(listOf(next))
        }
    }

    private suspend fun refreshPlan(
        allowReplace: Boolean = false,
        transform: (SettingsEntity) -> SettingsEntity = { it },
    ) {
        planMutex.withLock {
            val today = clock()
            val date = today.toString()
            todayDate.value = date
            val current = dao.getSettings() ?: SettingsEntity()
            val settings = transform(current)
            val tasks = dao.getTasks(date)
            val countChanged = settings.dailyCount != current.dailyCount
            val cursor = DailyPlanner.nextCursor(
                current.lastPlanDate,
                today,
                settings.cursor,
                current.dailyCount,
            )
            if (tasks.isEmpty() || (allowReplace && countChanged)) {
                val offsets = DailyPlanner.assign(
                    corpus.available.size,
                    cursor,
                    settings.dailyCount,
                )
                val built = offsets.mapIndexed { position, offset ->
                    DailyTaskEntity(
                        date = date,
                        position = position,
                        charIndex = corpus.available[offset].index,
                    )
                }
                dao.replaceTasks(date, built)
            }
            dao.upsertSettings(settings.copy(cursor = cursor, lastPlanDate = date))
        }
    }

    private fun toHome(
        settings: SettingsEntity?,
        tasks: List<DailyTaskEntity>,
        quizStats: QuizStats,
    ): HomeState {
        if (settings == null) return HomeState()
        val user = UserSettings(
            dailyCount = settings.dailyCount,
            edition = runCatching { Edition.valueOf(settings.edition) }.getOrDefault(Edition.INK),
            script = parseScriptStyle(settings.script),
            grid = runCatching { GridType.valueOf(settings.grid) }.getOrDefault(GridType.MI),
            invert = settings.invert,
            layout = runCatching { LayoutMode.valueOf(settings.layout) }.getOrDefault(LayoutMode.SIDE),
            cursor = settings.cursor,
        )
        val today = tasks.map { task ->
            val entry = corpus.characters.getOrNull(task.charIndex)
            TodayChar(
                index = task.charIndex,
                char = entry?.char.orEmpty(),
                group = entry?.group ?: 0,
            )
        }
        return HomeState(
            ready = true,
            settings = user,
            today = today,
            quizTotal = quizStats.total,
            quizCorrect = quizStats.correct,
            sampleExhausted = settings.cursor >= corpus.available.size && today.isEmpty(),
        )
    }

    private fun makeQuestion(kind: QuizKind): QuizQuestion? {
        val pool = corpus.available
        if (pool.size < 4) return null
        val answer = pool.random(random)
        val options = (pool.filter { it.index != answer.index }.shuffled(random).take(3) + answer)
            .shuffled(random)
            .map { it.index }
        return QuizQuestion(kind, answer.index, options)
    }
}

class AppViewModelFactory(
    private val app: QianwenApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(app, app.corpus, app.database.dao()) as T
    }
}
