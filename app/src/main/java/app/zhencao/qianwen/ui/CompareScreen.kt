package app.zhencao.qianwen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.zhencao.qianwen.Analysis
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.data.diffBitmap
import app.zhencao.qianwen.data.inkBitmap
import app.zhencao.qianwen.data.db.PracticeEntity
import app.zhencao.qianwen.model.PracticeGrid
import app.zhencao.qianwen.model.parseScriptStyle
import app.zhencao.qianwen.model.shiftLabel
import app.zhencao.qianwen.model.sizeLabel
import app.zhencao.qianwen.ui.theme.Ink
import app.zhencao.qianwen.ui.theme.Qing
import app.zhencao.qianwen.ui.theme.Zhu
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CompareMode(val label: String) { OVERLAY("叠影"), DIFF("差异"), SIDE("并排") }

private class CompareImages(
    val analysis: Analysis,
    val crop: ImageBitmap,
    val model: ImageBitmap,
    val user: ImageBitmap,
    val diff: ImageBitmap,
)

private val timeFormat = DateTimeFormatter.ofPattern("M月d日 HH:mm")

@Composable
fun CompareScreen(
    vm: AppViewModel,
    id: Long,
    onBack: () -> Unit,
    onNext: (charIndex: Int, framing: Boolean) -> Unit,
) {
    KeepScreenOn()
    val practice by remember(id) { vm.practice(id) }.collectAsState(null)
    val home by vm.home.collectAsState()
    val sheet by vm.sheet.collectAsState()
    val record = practice
    if (record == null) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("这条临作不在了。")
        }
        return
    }
    val entry = vm.corpus[record.charIndex]
    val script = parseScriptStyle(record.script)
    var images by remember(record.id) { mutableStateOf<CompareImages?>(null) }
    var mode by remember { mutableStateOf(CompareMode.OVERLAY) }
    var opacity by remember { mutableFloatStateOf(0.75f) }
    var failed by remember(record.id) { mutableStateOf(false) }
    LaunchedEffect(record.id) {
        images = buildImages(vm, record)
        failed = images == null
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            val time = Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).format(timeFormat)
            Text(
                "「${entry.char}」${script?.bookLabel.orEmpty()} · $time",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompareMode.entries.forEach { item ->
                FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item.label) })
            }
        }
        val ready = images
        if (ready == null) {
            Text(
                if (failed) "这次临作的图片读不出来了。" else "正在比对。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ComparePanel(ready, mode, opacity, script?.let { entry.glyphAsset(it) }, home.grid)
            when (mode) {
                CompareMode.OVERLAY -> {
                    Text("临作 ${(opacity * 100).roundToInt()}%")
                    Slider(value = opacity, onValueChange = { opacity = it })
                }
                CompareMode.DIFF -> Legend()
                CompareMode.SIDE -> Unit
            }
            Hints(ready.analysis)
        }
        val next = vm.corpus.characters.getOrNull(record.charIndex + 1)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                vm.deletePractice(record)
                onBack()
            }) { Text("删掉这次") }
            if (next != null) {
                Button(
                    onClick = { onNext(record.charIndex, sheet != null) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (sheet != null) "同一张纸框「${next.char}」" else "下一个字「${next.char}」")
                }
            }
        }
    }
}

@Composable
private fun ComparePanel(
    images: CompareImages,
    mode: CompareMode,
    opacity: Float,
    modelAsset: String?,
    grid: PracticeGrid,
) {
    when (mode) {
        CompareMode.OVERLAY -> GlyphFrame("", squareModifier()) {
            drawFitted(images.model, alpha = 0.7f)
            drawFitted(images.user, alpha = opacity)
            drawPracticeGrid(grid)
        }
        CompareMode.DIFF -> GlyphFrame("", squareModifier()) {
            drawFitted(images.diff)
            drawPracticeGrid(grid)
        }
        CompareMode.SIDE -> BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - 8.dp) / 2
            val modelPhoto = rememberGlyphBitmap(modelAsset)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlyphFrame("底帖", Modifier.size(cell).clip(RoundedCornerShape(10.dp))) {
                    modelPhoto?.let { drawFitted(it) }
                    drawPracticeGrid(grid)
                }
                GlyphFrame("临作", Modifier.size(cell).clip(RoundedCornerShape(10.dp))) {
                    drawFitted(images.crop)
                    drawPracticeGrid(grid)
                }
            }
        }
    }
}

private fun squareModifier(): Modifier =
    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))

@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("朱：底帖有，临作没写到", color = Zhu, style = MaterialTheme.typography.bodyMedium)
        Text("青：临作多写出来的", color = Qing, style = MaterialTheme.typography.bodyMedium)
        Text("墨：两者重合", color = Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Hints(analysis: Analysis) {
    val comparison = analysis.comparison
    if (comparison == null) {
        Text(
            "临作里没找到墨迹。",
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }
    Text("重合 ${(comparison.overlap * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
    Text(
        "写到了底帖笔画的 ${(comparison.recall * 100).roundToInt()}%，" +
            "临作有 ${(comparison.precision * 100).roundToInt()}% 落在底帖上。",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text("重心${shiftLabel(comparison.dx, comparison.dy)}", style = MaterialTheme.typography.bodyMedium)
    Text("字形${sizeLabel(comparison.widthRatio, comparison.heightRatio)}", style = MaterialTheme.typography.bodyMedium)
}

private suspend fun buildImages(vm: AppViewModel, practice: PracticeEntity): CompareImages? {
    val analysis = vm.analyze(practice) ?: return null
    return withContext(Dispatchers.Default) {
        CompareImages(
            analysis = analysis,
            crop = analysis.crop.asImageBitmap(),
            model = inkBitmap(analysis.model, Zhu.toArgb()).asImageBitmap(),
            user = inkBitmap(analysis.user, Ink.toArgb()).asImageBitmap(),
            diff = diffBitmap(
                analysis.diff,
                analysis.model.size,
                shared = Ink.copy(alpha = 0.55f).toArgb(),
                missing = Zhu.toArgb(),
                extra = Qing.toArgb(),
            ).asImageBitmap(),
        )
    }
}
