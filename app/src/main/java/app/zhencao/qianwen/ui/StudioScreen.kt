package app.zhencao.qianwen.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.data.Corpus
import app.zhencao.qianwen.data.db.PracticeEntity
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.GlyphBook
import app.zhencao.qianwen.model.PracticeGrid
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.ui.theme.PaperDeep
import app.zhencao.qianwen.ui.theme.Zhu
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun StudioScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onFrame: () -> Unit,
    onOpenPractice: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()
    val index by vm.selected.collectAsState()
    val home by vm.home.collectAsState()
    val sheet by vm.sheet.collectAsState()
    val script = home.script ?: ScriptStyle.CAO
    val entry = vm.corpus[index]
    val practices by remember(index, script) { vm.practices(index, script) }.collectAsState(emptyList())
    val picker = rememberSheetPicker(vm, onReady = onFrame)
    var compare by remember { mutableStateOf(false) }
    val otherBook = if (entry.shownBook(script) == GlyphBook.OGAWA) GlyphBook.GUANZHONG else GlyphBook.OGAWA
    val scope = rememberCoroutineScope()
    val pager = remember {
        GlyphPager(
            initial = index,
            lastIndex = vm.corpus.characters.lastIndex,
            scope = scope,
            onCommit = vm::select,
        )
    }
    LaunchedEffect(index) { pager.followSelection(index) }
    val shown = vm.corpus[pager.shown]
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        StudioHeader(
            vm,
            shown,
            script,
            onBack,
            canPrevious = pager.focus > 0,
            canNext = pager.focus < vm.corpus.characters.lastIndex,
            onPrevious = { pager.go(pager.focus - 1) },
            onNext = { pager.go(pager.focus + 1) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilterChip(
                selected = compare,
                onClick = { compare = !compare },
                label = { Text("对照${otherBook.label}") },
            )
        }
        ModelView(
            vm.corpus,
            pager,
            script,
            compare,
            otherBook,
            home.grid,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        PracticeStrip(vm, practices, onOpenPractice)
        picker.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = picker.camera, modifier = Modifier.weight(1f)) { Text("拍临作") }
            OutlinedButton(onClick = picker.gallery, modifier = Modifier.weight(1f)) { Text("相册") }
            if (sheet != null) {
                OutlinedButton(onClick = onFrame, modifier = Modifier.weight(1f)) { Text("接着框") }
            }
        }
    }
}

@Composable
private fun StudioHeader(
    vm: AppViewModel,
    entry: CharacterEntry,
    script: ScriptStyle,
    onBack: () -> Unit,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) { Text("返回") }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = canPrevious) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一个字")
                }
                Text(entry.char, fontFamily = FontFamily.Serif, fontSize = 36.sp)
                IconButton(
                    onClick = onNext,
                    enabled = canNext,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一个字")
                }
            }
            Text(
                "${script.bookLabel} · ${vm.corpus.groupText(entry.group)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelView(
    corpus: Corpus,
    pager: GlyphPager,
    script: ScriptStyle,
    compare: Boolean,
    otherBook: GlyphBook,
    grid: PracticeGrid,
    modifier: Modifier,
) {
    var scale by remember(pager.shown) { mutableFloatStateOf(1f) }
    var zoomPan by remember(pager.shown) { mutableStateOf(Offset.Zero) }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { pager.viewport = it }
                .pointerInput(compare) {
                    detectGlyphGestures(
                        onTransform = if (compare) {
                            null
                        } else {
                            { pan, zoom ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                zoomPan += pan
                            }
                        },
                        onGrab = { pager.grab() },
                        onDrag = pager::drag,
                        onRelease = { pager.release(glyphSwipeThreshold.toPx()) },
                        onCancel = pager::cancelDrag,
                    )
                },
        ) {
            val last = corpus.characters.lastIndex
            val neighbor = pager.incoming ?: incomingIndex(pager.shown, pager.slide, last)
            val width = pager.viewport.width.toFloat()
            val height = pager.viewport.height.toFloat()
            val turning = pager.slide != Offset.Zero || pager.incoming != null
            val sheet = if (turning) Modifier.background(MaterialTheme.colorScheme.background) else Modifier
            if (neighbor != null && width > 0f && height > 0f) {
                GlyphPage(
                    corpus[neighbor],
                    script,
                    compare,
                    otherBook,
                    grid,
                    Modifier.fillMaxSize().graphicsLayer {
                        val place = incomingOffset(
                            pager.slide,
                            width,
                            height,
                            pager.incoming?.let { it > pager.shown },
                        )
                        translationX = place.x
                        translationY = place.y
                    }.then(sheet),
                )
            }
            val pageScale = if (compare) 1f else scale
            val pagePan = if (compare) Offset.Zero else zoomPan
            GlyphPage(
                corpus[pager.shown],
                script,
                compare,
                otherBook,
                grid,
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = pageScale
                    scaleY = pageScale
                    translationX = pager.slide.x + pagePan.x
                    translationY = pager.slide.y + pagePan.y
                    if (turning) {
                        shadowElevation = 10.dp.toPx()
                        shape = RectangleShape
                        clip = false
                    }
                }.then(sheet),
            )
        }
        if (!compare && (scale != 1f || zoomPan != Offset.Zero)) {
            TextButton(
                onClick = {
                    scale = 1f
                    zoomPan = Offset.Zero
                },
                modifier = Modifier.align(Alignment.TopEnd),
            ) { Text("还原") }
        }
    }
}

@Composable
private fun GlyphPage(
    entry: CharacterEntry,
    script: ScriptStyle,
    compare: Boolean,
    otherBook: GlyphBook,
    grid: PracticeGrid,
    modifier: Modifier,
) {
    if (compare) {
        BoxWithConstraints(modifier) {
            val cell = minOf((maxWidth - 8.dp) / 2, maxHeight)
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditionFrame(entry, script, entry.shownBook(script), grid, Modifier.size(cell))
                EditionFrame(entry, script, otherBook, grid, Modifier.size(cell))
            }
        }
        return
    }
    val photo = rememberGlyphBitmap(entry.glyphAsset(script))
    val label = frameLabel(entry, script, entry.shownBook(script))
    BoxWithConstraints(modifier) {
        val cell = minOf(maxWidth, maxHeight) - 8.dp
        GlyphFrame(
            label,
            Modifier.align(Alignment.Center).size(cell).clip(RoundedCornerShape(14.dp)),
        ) {
            if (photo != null) drawFitted(photo)
            drawPracticeGrid(grid)
        }
    }
}

@Composable
private fun EditionFrame(
    entry: CharacterEntry,
    script: ScriptStyle,
    book: GlyphBook,
    grid: PracticeGrid,
    modifier: Modifier,
) {
    val photo = rememberGlyphBitmap(entry.glyphAsset(script, book))
    GlyphFrame(frameLabel(entry, script, book), modifier.clip(RoundedCornerShape(14.dp))) {
        if (photo != null) drawFitted(photo)
        drawPracticeGrid(grid)
    }
}

internal val glyphSwipeThreshold = 48.dp

/** How far a swipe still follows the finger when there is no further glyph. */
internal const val slideResistance = 0.28f

internal enum class GlyphTurn { Next, Previous }

/** Up selects the next glyph; down selects the previous one. A sideways swipe does not turn. */
internal fun glyphTurn(dx: Float, dy: Float, threshold: Float): GlyphTurn? {
    if (abs(dy) < threshold || abs(dx) >= abs(dy)) return null
    return if (dy < 0f) GlyphTurn.Next else GlyphTurn.Previous
}

/** Finger position before resistance, so a second grab does not rubber-band twice. */
internal fun unwindSlide(slide: Offset, index: Int, lastIndex: Int): Offset {
    if (slide == Offset.Zero) return Offset.Zero
    if (incomingIndex(index, slide, lastIndex) != null) return slide
    return Offset(slide.x / slideResistance, slide.y / slideResistance)
}

/** Follows a vertical swipe, and shortens it at the first or last glyph. Sideways motion stays put. */
internal fun resistedSlide(dx: Float, dy: Float, index: Int, lastIndex: Int): Offset {
    if (abs(dx) >= abs(dy) || dy == 0f) return Offset.Zero
    val slide = Offset(0f, dy)
    if (slide == Offset.Zero) return Offset.Zero
    return if (incomingIndex(index, slide, lastIndex) == null) {
        Offset(slide.x * slideResistance, slide.y * slideResistance)
    } else {
        slide
    }
}

/** Glyph that should slide in beside [index], or null at the ends of the text. */
internal fun incomingIndex(index: Int, drag: Offset, lastIndex: Int): Int? {
    val turn = glyphTurn(drag.x, drag.y, 0.5f) ?: return null
    val next = when (turn) {
        GlyphTurn.Next -> index + 1
        GlyphTurn.Previous -> index - 1
    }
    return next.takeIf { it in 0..lastIndex }
}

/**
 * Where the incoming page sits so it stays one viewport away and moves with [drag].
 * [forwardHint] covers the moment a button turn has started but the page has not moved yet.
 */
internal fun incomingOffset(
    drag: Offset,
    width: Float,
    height: Float,
    forwardHint: Boolean? = null,
): Offset {
    val forward = forwardHint ?: when {
        drag.y != 0f -> drag.y < 0f
        else -> return Offset.Zero
    }
    val aimY = if (forward) -1f else 1f
    return Offset(0f, drag.y - aimY * height)
}

/** Resting place once the finger has committed to turning the page. */
internal fun settleTarget(drag: Offset, width: Float, height: Float): Offset {
    val aim = if (drag.y < 0f) -1f else 1f
    return Offset(0f, aim * height)
}

/**
 * Slides the shown glyph with the finger. [shown] changes only after the page has finished moving,
 * so the picture eases into place instead of swapping under the finger.
 */
internal class GlyphPager(
    initial: Int,
    private val lastIndex: Int,
    private val scope: CoroutineScope,
    private val onCommit: (Int) -> Unit,
) {
    var shown by mutableIntStateOf(initial)
    var slide by mutableStateOf(Offset.Zero)
    var incoming by mutableStateOf<Int?>(null)
    var viewport by mutableStateOf(IntSize.Zero)
    private var job: Job? = null
    private var epoch = 0

    /** Index the arrows act on, including a turn that is already in flight. */
    val focus: Int get() = incoming ?: shown

    fun followSelection(index: Int) {
        if (job?.isActive == true || slide != Offset.Zero || incoming != null) return
        val next = index.coerceIn(0, lastIndex)
        if (shown != next) shown = next
    }

    fun grab(): Offset {
        epoch++
        job?.cancel()
        job = null
        return unwindSlide(slide, shown, lastIndex)
    }

    fun drag(pan: Offset) {
        incoming = null
        slide = resistedSlide(pan.x, pan.y, shown, lastIndex)
    }

    fun cancelDrag() {
        epoch++
        job?.cancel()
        job = null
        incoming = null
        slide = Offset.Zero
    }

    fun release(thresholdPx: Float) {
        val landed = incomingIndex(shown, slide, lastIndex)
        val turn = glyphTurn(slide.x, slide.y, thresholdPx)
        if (turn == null || landed == null || viewport.width == 0 || viewport.height == 0) {
            if (slide != Offset.Zero) animate(Offset.Zero) { incoming = null }
            return
        }
        val target = settleTarget(slide, viewport.width.toFloat(), viewport.height.toFloat())
        animate(target) {
            incoming = null
            slide = Offset.Zero
            shown = landed
            onCommit(landed)
        }
    }

    fun go(index: Int) {
        val next = index.coerceIn(0, lastIndex)
        if (next == incoming) return
        if (next == shown && incoming == null) {
            if (slide != Offset.Zero) animate(Offset.Zero) { }
            return
        }
        if (viewport.width == 0 || viewport.height == 0) {
            incoming = null
            slide = Offset.Zero
            shown = next
            onCommit(next)
            return
        }
        incoming = next
        val target = Offset(
            0f,
            if (next > shown) -viewport.height.toFloat() else viewport.height.toFloat(),
        )
        animate(target) {
            incoming = null
            slide = Offset.Zero
            shown = next
            onCommit(next)
        }
    }

    private fun animate(target: Offset, end: () -> Unit) {
        val ticket = ++epoch
        val start = slide
        job?.cancel()
        job = scope.launch {
            if (start != target) {
                val distance = (target - start).getDistance()
                val span = max(viewport.width, viewport.height).toFloat().coerceAtLeast(1f)
                val duration = (260f * distance / span).toInt().coerceIn(160, 280)
                animate(
                    typeConverter = Offset.VectorConverter,
                    initialValue = start,
                    targetValue = target,
                    animationSpec = tween(duration, easing = FastOutSlowInEasing),
                ) { value, _ ->
                    if (epoch == ticket) slide = value
                }
            }
            if (epoch == ticket) end()
        }
    }
}

/** Turns the page after the finger passes touch slop, so a tap can still hit a child. */
internal suspend fun PointerInputScope.detectPageSwipe(
    onGrab: () -> Offset,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var pan = Offset.Zero
        var dragging = false
        var origin = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) {
                if (dragging) onRelease()
                break
            }
            if (pressed >= 2) {
                if (dragging) onCancel()
                break
            }
            pan += event.calculatePan()
            if (!dragging) {
                if (pan.getDistance() < slop) continue
                dragging = true
                origin = onGrab()
            }
            onDrag(origin + pan)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
}

private suspend fun PointerInputScope.detectGlyphGestures(
    onTransform: ((pan: Offset, zoom: Float) -> Unit)?,
    onGrab: () -> Offset,
    onDrag: (Offset) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var pan = Offset.Zero
        var dragging = false
        var zooming = false
        var origin = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) {
                if (dragging) onRelease()
                break
            }
            if (pressed >= 2 && onTransform != null) {
                if (!zooming && dragging) onCancel()
                dragging = false
                zooming = true
                val zoom = event.calculateZoom()
                val drag = event.calculatePan()
                if (zoom != 1f || drag != Offset.Zero) onTransform(drag, zoom)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
                continue
            }
            if (zooming) continue
            pan += event.calculatePan()
            if (!dragging) {
                if (pan.getDistance() < slop) continue
                dragging = true
                origin = onGrab()
            }
            onDrag(origin + pan)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
}

private fun frameLabel(entry: CharacterEntry, script: ScriptStyle, book: GlyphBook): String =
    when (book) {
        GlyphBook.OGAWA -> if (entry.usesFallback(script)) "小川本" else ""
        GlyphBook.GUANZHONG -> if (entry.usesFallback(script)) "关中本补" else "关中本"
    }

@Composable
private fun PracticeStrip(
    vm: AppViewModel,
    practices: List<PracticeEntity>,
    onOpen: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (practices.isNotEmpty()) {
            Text(
                "临作 ${practices.size} 次",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(practices, key = { it.id }) { practice ->
                    PracticeThumb(vm, practice, onClick = { onOpen(practice.id) })
                }
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("M/d")

@Composable
private fun PracticeThumb(vm: AppViewModel, practice: PracticeEntity, onClick: () -> Unit) {
    var bitmap by remember(practice.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(practice.id) { bitmap = vm.loadCrop(practice)?.asImageBitmap() }
    Column(
        Modifier.width(72.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphFrame(
            "",
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
            paper = PaperDeep,
        ) {
            bitmap?.let { drawFitted(it) }
        }
        val day = Instant.ofEpochMilli(practice.createdAt).atZone(ZoneId.systemDefault()).format(dayFormat)
        Text(
            "$day · ${(practice.overlap * 100).roundToInt()}%",
            fontSize = 11.sp,
            color = Zhu,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}
