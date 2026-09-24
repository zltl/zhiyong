package app.zhencao.qianwen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.data.VERSE_COUNT
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.ui.theme.PaperDeep
import app.zhencao.qianwen.ui.theme.Zhu

@Composable
fun TodayScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onOpen: (Int) -> Unit,
) {
    val home by vm.home.collectAsState()
    val script = home.script ?: ScriptStyle.CAO
    val verse = home.verse
    val scope = rememberCoroutineScope()
    val pager = remember {
        GlyphPager(verse, VERSE_COUNT - 1, scope, vm::setVerse)
    }
    LaunchedEffect(verse) { pager.followSelection(verse) }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
        Text("智永真草千字文", style = MaterialTheme.typography.headlineMedium)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clipToBounds()
                .onSizeChanged { pager.viewport = it }
                .pointerInput(Unit) {
                    detectPageSwipe(
                        onGrab = { pager.grab() },
                        onDrag = pager::drag,
                        onRelease = { pager.release(glyphSwipeThreshold.toPx()) },
                        onCancel = pager::cancelDrag,
                    )
                },
        ) {
            val last = VERSE_COUNT - 1
            val neighbor = pager.incoming ?: incomingIndex(pager.shown, pager.slide, last)
            val width = pager.viewport.width.toFloat()
            val height = pager.viewport.height.toFloat()
            val turning = pager.slide != Offset.Zero || pager.incoming != null
            val sheet = if (turning) Modifier.background(MaterialTheme.colorScheme.background) else Modifier
            if (neighbor != null && width > 0f && height > 0f) {
                VersePage(
                    vm,
                    neighbor,
                    script,
                    home.counts,
                    onOpen,
                    onTurn = pager::go,
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
            VersePage(
                vm,
                pager.shown,
                script,
                home.counts,
                onOpen,
                onTurn = pager::go,
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = pager.slide.x
                    translationY = pager.slide.y
                    if (turning) {
                        shadowElevation = 10.dp.toPx()
                        shape = RectangleShape
                        clip = false
                    }
                }.then(sheet),
            )
        }
    }
}

@Composable
private fun VersePage(
    vm: AppViewModel,
    verse: Int,
    script: ScriptStyle,
    counts: Map<Int, Int>,
    onOpen: (Int) -> Unit,
    onTurn: (Int) -> Unit,
    modifier: Modifier,
) {
    val chars = vm.corpus.verse(verse)
    val note = vm.corpus.note(verse)
    Column(modifier) {
        Text(
            "接着写 · ${script.bookLabel} · 第 ${verse + 1} 句，共 $VERSE_COUNT 句",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onTurn(verse - 1) }, enabled = verse > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一句")
            }
            Text(
                vm.corpus.groupText(verse).toList().joinToString("\u2002"),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onTurn(verse + 1) }, enabled = verse < VERSE_COUNT - 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一句")
            }
        }
        Text(
            "简体　${note.simplified}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Text(
            "释义　${note.meaning}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        VerseGrid(chars, script, counts, onOpen, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun VerseGrid(
    chars: List<CharacterEntry>,
    script: ScriptStyle,
    counts: Map<Int, Int>,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val gap = 16.dp
        val label = 28.dp
        val cell = minOf((maxWidth - gap) / 2, (maxHeight - label * 2 - gap) / 2).coerceAtLeast(0.dp)
        Column(Modifier.align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(gap)) {
            chars.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    pair.forEach { entry ->
                        GlyphTile(
                            entry = entry,
                            script = script,
                            count = counts[entry.index] ?: 0,
                            modifier = Modifier.size(cell),
                            onClick = { onOpen(entry.index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlyphTile(
    entry: CharacterEntry,
    script: ScriptStyle,
    count: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val photo = rememberGlyphBitmap(entry.glyphAsset(script))
    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(PaperDeep),
        ) {
            if (photo != null) drawFitted(photo)
        }
        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                entry.char,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (count > 0) {
                Text("临 $count", fontSize = 12.sp, color = Zhu)
            }
        }
    }
}
