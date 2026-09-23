package app.zhencao.qianwen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.UserSettings
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.Edition
import app.zhencao.qianwen.model.GridType
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.model.StrokeKind
import app.zhencao.qianwen.model.StudioMode
import kotlin.math.floor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun StudioScreen(
    vm: AppViewModel,
    index: Int,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val entry = vm.corpus.characters.getOrNull(index)
    if (entry == null || !entry.available) {
        Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("返回") }
            }
            Text("这个字还不能看", style = MaterialTheme.typography.headlineSmall)
            Text("这个字的底帖还没就绪。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LaunchedEffect(index) { vm.markViewed(index) }
    val home by vm.home.collectAsState()
    val script = home.settings.script ?: ScriptStyle.CAO
    var mode by remember { mutableStateOf(StudioMode.COMPARE) }
    LaunchedEffect(script, mode) {
        if (script == ScriptStyle.ZHEN && mode == StudioMode.STROKE) {
            mode = StudioMode.COMPARE
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        StudioHeader(vm, entry, script, onBack)
        if (script == ScriptStyle.CAO) {
            ModeRow(mode) { mode = it }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                StudioMode.COMPARE -> ComparePane(vm, entry, home.settings, script)
                StudioMode.STROKE -> StrokePane(vm, entry)
                StudioMode.WRITE, StudioMode.OVERLAY -> ComparePane(vm, entry, home.settings, script)
            }
        }
    }
}

@Composable
private fun StudioHeader(
    vm: AppViewModel,
    entry: CharacterEntry,
    script: ScriptStyle,
    onBack: (() -> Unit)?,
) {
    val available = vm.corpus.available
    val position = available.indexOfFirst { it.index == entry.index }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("返回") }
        }
        IconButton(
            onClick = { vm.select(available[(position - 1).coerceAtLeast(0)].index) },
            enabled = position > 0,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一个字")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(entry.char, fontFamily = FontFamily.Serif, fontSize = 40.sp)
            Text(
                "${script.bookLabel} · ${vm.corpus.groupText(entry.group)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = {
                vm.select(available[(position + 1).coerceAtMost(available.lastIndex)].index)
            },
            enabled = position < available.lastIndex,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一个字")
        }
    }
}

@Composable
private fun ModeRow(mode: StudioMode, onChange: (StudioMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = mode == StudioMode.COMPARE,
            onClick = { onChange(StudioMode.COMPARE) },
            label = { Text("看帖") },
        )
        FilterChip(
            selected = mode == StudioMode.STROKE,
            onClick = { onChange(StudioMode.STROKE) },
            label = { Text("笔顺") },
        )
    }
}

@Composable
private fun ComparePane(
    vm: AppViewModel,
    entry: CharacterEntry,
    settings: UserSettings,
    script: ScriptStyle,
) {
    var scale by remember(entry.index) { mutableFloatStateOf(1f) }
    var offset by remember(entry.index) { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxSize()) {
        if (scale != 1f || offset != Offset.Zero) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    scale = 1f
                    offset = Offset.Zero
                }) { Text("还原") }
            }
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(entry.index) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset += pan
                    }
                },
        ) {
            val palette = glyphPalette(Edition.INK, invert = false)
            val cell = minOf(maxWidth, maxHeight) - 8.dp
            val cellModifier = Modifier.align(Alignment.Center).size(cell).clip(RoundedCornerShape(14.dp))
            when (script) {
                ScriptStyle.ZHEN -> TruthCell(entry, palette, cellModifier)
                ScriptStyle.CAO -> CursiveCell(entry, palette, null, cellModifier)
            }
        }
    }
}

@Composable
private fun StrokePane(vm: AppViewModel, entry: CharacterEntry) {
    val strokes = entry.caoStrokes
    val progress = remember(entry.index) { Animatable(0f) }
    var playing by remember(entry.index) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val indexState = rememberUpdatedState(entry.index)
    LaunchedEffect(progress, entry.index) {
        snapshotFlow { progress.value }.collectLatest { value ->
            if (value >= 1f) vm.markStrokePlayed(indexState.value)
        }
    }
    LaunchedEffect(playing, entry.index) {
        if (!playing) return@LaunchedEffect
        val target = strokes.size.toFloat()
        if (progress.value >= target) progress.snapTo(0f)
        val duration = ((target - progress.value) * 700f).toInt().coerceAtLeast(240)
        progress.animateTo(target, tween(duration))
        playing = false
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val palette = glyphPalette(Edition.INK, invert = false)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cell = minOf(maxWidth, maxHeight)
            CursiveCell(
                entry,
                palette,
                progress.value,
                Modifier.align(Alignment.Center).size(cell),
            )
        }
        val active = when {
            strokes.isEmpty() -> null
            progress.value <= 0f -> strokes.first()
            progress.value >= strokes.size -> strokes.last()
            else -> strokes[floor(progress.value).toInt().coerceIn(0, strokes.lastIndex)]
        }
        Text(
            active?.let { "第 ${it.order} 笔 · ${if (it.kind == StrokeKind.SILK) "牵丝" else "实笔"}" }
                ?: "这个字的笔顺还没拆开",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { playing = !playing }) { Text(if (playing) "暂停" else "播放") }
            TextButton(onClick = {
                playing = false
                scope.launch {
                    val next = floor(progress.value + 1f).coerceAtMost(strokes.size.toFloat())
                    progress.snapTo(next)
                }
            }) { Text("下一步") }
            TextButton(onClick = {
                scope.launch {
                    progress.snapTo(0f)
                    playing = true
                }
            }) { Text("重放") }
        }
    }
}

@Composable
private fun TruthCell(
    entry: CharacterEntry,
    palette: GlyphPalette,
    modifier: Modifier,
) {
    val photo = rememberGlyphBitmap(entry.glyphAsset(Edition.INK, "zhen"))
    val label = if (photo != null && entry.usesFallback(Edition.INK, "zhen")) "真 · 关中本补" else "真"
    GlyphFrame(palette, GridType.MI, label, modifier) {
        if (photo != null) {
            drawGlyphPhoto(photo, invert = false)
        } else {
            drawTruth(entry.char, palette.ink)
        }
    }
}

@Composable
private fun CursiveCell(
    entry: CharacterEntry,
    palette: GlyphPalette,
    progress: Float?,
    modifier: Modifier,
) {
    val usePhoto = progress == null
    val photo = rememberGlyphBitmap(if (usePhoto) entry.glyphAsset(Edition.INK, "cao") else null)
    val label = when {
        !usePhoto || photo == null -> "草 · 示意图"
        entry.usesFallback(Edition.INK, "cao") -> "草 · 关中本补"
        else -> "草"
    }
    GlyphFrame(palette, GridType.MI, label, modifier) {
        if (usePhoto && photo != null) {
            drawGlyphPhoto(photo, invert = false)
        } else {
            drawCursive(entry.caoStrokes, Edition.INK, palette.ink, progress)
        }
    }
}
