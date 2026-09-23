package app.zhencao.qianwen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.UserSettings
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.InkPoint
import app.zhencao.qianwen.model.InkStroke
import app.zhencao.qianwen.model.ScriptStyle

@Composable
fun WritingPane(vm: AppViewModel, entry: CharacterEntry, settings: UserSettings) {
    val inkMap by vm.ink.collectAsState()
    val strokes = inkMap[entry.index].orEmpty()
    var showModel by remember(entry.index) { mutableStateOf(true) }
    var active by remember(entry.index) { mutableStateOf<List<InkPoint>>(emptyList()) }
    val script = settings.script ?: ScriptStyle.CAO
    val palette = glyphPalette(settings.edition, settings.invert)
    val modelPhoto = rememberGlyphBitmap(entry.glyphAsset(settings.edition, script))
    val commit = rememberUpdatedState<(List<InkPoint>) -> Unit> { points ->
        if (points.size < 2) return@rememberUpdatedState
        val next = strokes + InkStroke(points)
        vm.setInk(entry.index, next)
        vm.markWrote(entry.index)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "有压感时线会变粗，没有压感就用均匀线宽。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(palette.paper)
                .pointerInput(entry.index) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val points = mutableListOf<InkPoint>()
                        fun add(x: Float, y: Float, pressure: Float) {
                            val safe = if (pressure <= 0.01f) 0.55f else pressure.coerceIn(0.12f, 1f)
                            points += InkPoint(
                                x = (x / size.width).coerceIn(0f, 1f),
                                y = (y / size.height).coerceIn(0f, 1f),
                                pressure = safe,
                            )
                            active = points.toList()
                        }
                        add(down.position.x, down.position.y, down.pressure)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            add(change.position.x, change.position.y, change.pressure)
                            if (change.positionChange() != androidx.compose.ui.geometry.Offset.Zero) {
                                change.consume()
                            }
                            if (!change.pressed) break
                        }
                        val finished = points.toList()
                        active = emptyList()
                        commit.value(finished)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawGrid(settings.grid, palette.grid)
                if (showModel) {
                    drawScriptModel(
                        entry,
                        script,
                        settings.edition,
                        palette.ink,
                        modelPhoto,
                        settings.invert,
                        alpha = if (modelPhoto != null) 0.28f else 0.18f,
                    )
                }
                drawInk(strokes, active, palette.ink)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = showModel,
                onClick = { showModel = !showModel },
                label = { Text(if (showModel) "底帖开" else "底帖关") },
            )
            TextButton(
                onClick = {
                    if (strokes.isNotEmpty()) vm.setInk(entry.index, strokes.dropLast(1))
                },
                enabled = strokes.isNotEmpty(),
            ) { Text("撤销") }
            TextButton(
                onClick = { vm.setInk(entry.index, emptyList()) },
                enabled = strokes.isNotEmpty(),
            ) { Text("清空") }
        }
    }
}
