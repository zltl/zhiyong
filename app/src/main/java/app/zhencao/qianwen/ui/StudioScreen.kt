package app.zhencao.qianwen.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.data.db.PracticeEntity
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.GlyphBook
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.ui.theme.PaperDeep
import app.zhencao.qianwen.ui.theme.Zhu
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

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
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        StudioHeader(vm, entry, script, onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilterChip(
                selected = compare,
                onClick = { compare = !compare },
                label = { Text("对照${otherBook.label}") },
            )
        }
        ModelView(
            entry,
            script,
            compare,
            otherBook,
            onPrevious = { if (entry.index > 0) vm.select(entry.index - 1) },
            onNext = { if (entry.index < vm.corpus.characters.lastIndex) vm.select(entry.index + 1) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        PracticeStrip(vm, practices, onOpenPractice)
        picker.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = picker.camera, modifier = Modifier.weight(1f)) { Text("拍临作") }
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
                IconButton(onClick = { vm.select(entry.index - 1) }, enabled = entry.index > 0) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一个字")
                }
                Text(entry.char, fontFamily = FontFamily.Serif, fontSize = 36.sp)
                IconButton(
                    onClick = { vm.select(entry.index + 1) },
                    enabled = entry.index < vm.corpus.characters.lastIndex,
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
    entry: CharacterEntry,
    script: ScriptStyle,
    compare: Boolean,
    otherBook: GlyphBook,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    if (compare) {
        BoxWithConstraints(
            modifier.pointerInput(entry.index) {
                detectGlyphGestures(onTransform = null, onTurn = { turn ->
                    when (turn) {
                        GlyphTurn.Next -> onNext()
                        GlyphTurn.Previous -> onPrevious()
                    }
                })
            },
        ) {
            val cell = minOf((maxWidth - 8.dp) / 2, maxHeight)
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditionFrame(entry, script, entry.shownBook(script), Modifier.size(cell))
                EditionFrame(entry, script, otherBook, Modifier.size(cell))
            }
        }
        return
    }
    var scale by remember(entry.index) { mutableFloatStateOf(1f) }
    var offset by remember(entry.index) { mutableStateOf(Offset.Zero) }
    val photo = rememberGlyphBitmap(entry.glyphAsset(script))
    val label = frameLabel(entry, script, entry.shownBook(script))
    Box(modifier) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(entry.index) {
                    detectGlyphGestures(
                        onTransform = { pan, zoom ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            offset += pan
                        },
                        onTurn = { turn ->
                            when (turn) {
                                GlyphTurn.Next -> onNext()
                                GlyphTurn.Previous -> onPrevious()
                            }
                        },
                    )
                },
        ) {
            val cell = minOf(maxWidth, maxHeight) - 8.dp
            GlyphFrame(
                label,
                Modifier.align(Alignment.Center).size(cell).clip(RoundedCornerShape(14.dp)),
            ) {
                if (photo != null) drawFitted(photo)
            }
        }
        if (scale != 1f || offset != Offset.Zero) {
            TextButton(
                onClick = {
                    scale = 1f
                    offset = Offset.Zero
                },
                modifier = Modifier.align(Alignment.TopEnd),
            ) { Text("还原") }
        }
    }
}

@Composable
private fun EditionFrame(
    entry: CharacterEntry,
    script: ScriptStyle,
    book: GlyphBook,
    modifier: Modifier,
) {
    val photo = rememberGlyphBitmap(entry.glyphAsset(script, book))
    GlyphFrame(frameLabel(entry, script, book), modifier.clip(RoundedCornerShape(14.dp))) {
        if (photo != null) drawFitted(photo)
    }
}

private val glyphSwipeThreshold = 48.dp

internal enum class GlyphTurn { Next, Previous }

/** Up or right selects the next glyph; down or left selects the previous one. */
internal fun glyphTurn(dx: Float, dy: Float, threshold: Float): GlyphTurn? {
    val horizontal = abs(dx)
    val vertical = abs(dy)
    if (max(horizontal, vertical) < threshold) return null
    return if (horizontal >= vertical) {
        if (dx > 0f) GlyphTurn.Next else GlyphTurn.Previous
    } else {
        if (dy < 0f) GlyphTurn.Next else GlyphTurn.Previous
    }
}

private suspend fun PointerInputScope.detectGlyphGestures(
    onTransform: ((pan: Offset, zoom: Float) -> Unit)?,
    onTurn: (GlyphTurn) -> Unit,
) {
    val threshold = glyphSwipeThreshold.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var multiTouch = false
        var pan = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) multiTouch = true
            if (multiTouch && onTransform != null && pressed >= 2) {
                val zoom = event.calculateZoom()
                val drag = event.calculatePan()
                if (zoom != 1f || drag != Offset.Zero) onTransform(drag, zoom)
            } else if (!multiTouch) {
                pan += event.calculatePan()
            }
            event.changes.forEach { if (it.positionChanged()) it.consume() }
            if (pressed == 0) break
        }
        if (!multiTouch) glyphTurn(pan.x, pan.y, threshold)?.let(onTurn)
    }
}

private fun frameLabel(entry: CharacterEntry, script: ScriptStyle, book: GlyphBook): String =
    when (book) {
        GlyphBook.OGAWA -> if (entry.usesFallback(script)) "小川本" else script.label
        GlyphBook.GUANZHONG -> if (entry.usesFallback(script)) "${script.label} · 关中本补" else "关中本"
    }

@Composable
private fun PracticeStrip(
    vm: AppViewModel,
    practices: List<PracticeEntity>,
    onOpen: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            if (practices.isEmpty()) "还没有临作。在纸上写好，拍下来框进格子。" else "临作 ${practices.size} 次",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (practices.isNotEmpty()) {
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
