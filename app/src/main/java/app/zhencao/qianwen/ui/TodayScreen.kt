package app.zhencao.qianwen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.TodayChar
import app.zhencao.qianwen.model.Edition
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.ui.theme.PaperDeep

@Composable
fun TodayScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onOpen: (Int) -> Unit,
) {
    val home by vm.home.collectAsState()
    val script = home.settings.script ?: ScriptStyle.CAO
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("智永真草千字文", style = MaterialTheme.typography.headlineMedium)
            val verse = home.today.firstOrNull()?.let { " · 第 ${it.group + 1} 句" }.orEmpty()
            Text(
                "今日${script.bookLabel}$verse",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
            )
        }
        when {
            !home.ready -> item { QuietLine("正在准备。") }
            home.sampleExhausted -> item { QuietLine("千字已经轮过一遍，可以从头再练。") }
            home.today.isEmpty() -> item { QuietLine("字还在准备。") }
            else -> {
                val groups = home.today.groupBy { it.group }
                groups.forEach { (group, chars) ->
                    item(key = "group-$group") {
                        VerseGroup(
                            verse = vm.corpus.groupText(group),
                            chars = chars,
                            edition = home.settings.edition,
                            script = script,
                            vm = vm,
                            onOpen = onOpen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuietLine(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun VerseGroup(
    verse: String,
    chars: List<TodayChar>,
    edition: Edition,
    script: ScriptStyle,
    vm: AppViewModel,
    onOpen: (Int) -> Unit,
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            verse.toList().joinToString("\u2002"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            chars.forEach { char ->
                GlyphTile(
                    char = char,
                    asset = vm.corpus[char.index].glyphAsset(edition, script),
                    modifier = Modifier.weight(1f),
                    onClick = { onOpen(char.index) },
                )
            }
            repeat(4 - chars.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun GlyphTile(
    char: TodayChar,
    asset: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val photo = rememberGlyphBitmap(asset)
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
            if (photo != null) drawGlyphPhoto(photo, invert = false)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            char.char,
            fontFamily = FontFamily.Serif,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
