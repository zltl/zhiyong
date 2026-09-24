package app.zhencao.qianwen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val chars = vm.corpus.verse(verse)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text("智永真草千字文", style = MaterialTheme.typography.headlineMedium)
        Text(
            "接着写 · ${script.bookLabel} · 第 ${verse + 1} 句，共 $VERSE_COUNT 句",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.setVerse(verse - 1) }, enabled = verse > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一句")
            }
            Text(
                vm.corpus.groupText(verse).toList().joinToString("\u2002"),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.setVerse(verse + 1) }, enabled = verse < VERSE_COUNT - 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一句")
            }
        }
        val note = vm.corpus.note(verse)
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
        chars.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                pair.forEach { entry ->
                    GlyphTile(
                        entry = entry,
                        script = script,
                        count = home.counts[entry.index] ?: 0,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpen(entry.index) },
                    )
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
