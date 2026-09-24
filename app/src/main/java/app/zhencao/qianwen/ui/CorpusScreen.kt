package app.zhencao.qianwen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.data.VERSE_COUNT
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.ui.theme.PaperDeep
import app.zhencao.qianwen.ui.theme.SheetPaper
import app.zhencao.qianwen.ui.theme.Zhu

@Composable
fun CorpusScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onOpen: (Int) -> Unit,
    onPickVerse: (Int) -> Unit,
) {
    val home by vm.home.collectAsState()
    val script = home.script ?: return
    val groups = remember { (0 until VERSE_COUNT).toList() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (home.verse - 3).coerceAtLeast(0))
    Column(modifier.fillMaxSize()) {
        Text(
            "千文",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 8.dp),
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(groups, key = { it }) { group ->
                val chars = vm.corpus.verse(group)
                val practiced = chars.count { (home.counts[it.index] ?: 0) > 0 }
                val current = group == home.verse
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (current) PaperDeep else MaterialTheme.colorScheme.background)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "%03d".format(group + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (current) Zhu else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { onPickVerse(group) }
                            .padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
                    )
                    val note = vm.corpus.note(group)
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            chars.forEach { entry ->
                                CorpusGlyphThumb(
                                    entry = entry,
                                    script = script,
                                    modifier = Modifier.clickable { onOpen(entry.index) },
                                )
                            }
                        }
                        Text(
                            note.simplified,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        Text(
                            note.meaning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                        )
                    }
                    Text(
                        if (practiced > 0) "已临 $practiced/4" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = Zhu,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CorpusGlyphThumb(
    entry: CharacterEntry,
    script: ScriptStyle,
    modifier: Modifier = Modifier,
) {
    val photo = rememberGlyphBitmap(entry.glyphAsset(script))
    Box(
        modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(SheetPaper),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Canvas(Modifier.fillMaxSize().padding(2.dp)) { drawFitted(photo) }
        } else {
            Text(entry.char, style = MaterialTheme.typography.titleMedium)
        }
    }
}
