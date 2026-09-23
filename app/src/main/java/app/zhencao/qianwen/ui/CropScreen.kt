package app.zhencao.qianwen.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.zhencao.qianwen.AppViewModel
import app.zhencao.qianwen.data.InkAnalysis
import app.zhencao.qianwen.data.MASK_SIZE
import app.zhencao.qianwen.data.SheetTransform
import app.zhencao.qianwen.data.inkBitmap
import app.zhencao.qianwen.data.maskOf
import app.zhencao.qianwen.data.renderCrop
import app.zhencao.qianwen.data.toMatrix
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.ui.theme.Zhu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FRAME_SHARE = 0.72f

@Composable
fun CropScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    KeepScreenOn()
    val index by vm.selected.collectAsState()
    val home by vm.home.collectAsState()
    val sheet by vm.sheet.collectAsState()
    val script = home.script ?: ScriptStyle.CAO
    val entry = vm.corpus[index]
    val current = sheet
    if (current == null) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("还没有临作照片。先拍一张，或从相册选。")
        }
        return
    }
    val photo = current.bitmap
    val fresh = remember(photo) { SheetTransform.fitting(photo.width, photo.height, span = 3f) }
    var transform by remember(photo) { mutableStateOf(current.transform ?: fresh) }
    var ghostOn by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var ghost by remember(index, script) { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(index, script) {
        ghost = withContext(Dispatchers.Default) {
            vm.modelMask(index, script)?.let { inkBitmap(it, Zhu.toArgb()).asImageBitmap() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                "框「${entry.char}」",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = ghostOn,
                onClick = { ghostOn = !ghostOn },
                label = { Text("朱色底帖") },
            )
        }
        Text(
            "单指拖动照片，双指缩放、旋转，把纸上的「${entry.char}」对准方框里的朱色底帖。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF2A2621)),
        ) {
            val density = LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            val side = minOf(width, height) * FRAME_SHARE
            val left = (width - side) / 2f
            val top = (height - side) / 2f
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(photo, side) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            val cx = (centroid.x - left) / side
                            val cy = (centroid.y - top) / side
                            transform = transform
                                .pan(pan.x / side, pan.y / side)
                                .zoom(zoom, cx, cy)
                                .rotate(rotation, cx, cy)
                        }
                    },
            ) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                drawContext.canvas.nativeCanvas.drawBitmap(photo, transform.toMatrix(left, top, side), paint)
                val shade = Color.Black.copy(alpha = 0.5f)
                drawRect(shade, Offset.Zero, Size(size.width, top))
                drawRect(shade, Offset(0f, top + side), Size(size.width, size.height - top - side))
                drawRect(shade, Offset(0f, top), Size(left, side))
                drawRect(shade, Offset(left + side, top), Size(size.width - left - side, side))
                translate(left, top) {
                    val frame = Size(side, side)
                    drawRect(Color.White, Offset.Zero, frame, style = Stroke(width = 3f))
                    val g = ghost
                    if (ghostOn && g != null) {
                        val dest = android.graphics.Rect(0, 0, side.toInt(), side.toInt())
                        val ghostPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 120 }
                        drawContext.canvas.nativeCanvas.drawBitmap(
                            g.asAndroidBitmap(),
                            null,
                            dest,
                            ghostPaint,
                        )
                    }
                }
            }
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    transform = fresh
                    message = null
                },
                enabled = !busy,
            ) { Text("重来") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        busy = true
                        val aligned = withContext(Dispatchers.Default) { autoAlign(vm, photo, transform, index, script) }
                        if (aligned == null) {
                            message = "方框里没找到墨迹，先把字大致挪进来。"
                        } else {
                            transform = aligned
                            message = "已按墨迹外框和重心对齐，可以再手动微调。"
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("自动对齐") }
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        val crop = withContext(Dispatchers.Default) { renderCrop(photo, transform) }
                        vm.rememberSheetTransform(transform)
                        val id = vm.savePractice(index, script, crop)
                        busy = false
                        onSaved(id)
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("保存") }
        }
    }
}

/** Two passes: the first fit can pull neighbouring characters in or out of the frame. */
private fun autoAlign(
    vm: AppViewModel,
    photo: android.graphics.Bitmap,
    start: SheetTransform,
    index: Int,
    script: ScriptStyle,
): SheetTransform? {
    val model = vm.modelMask(index, script) ?: return null
    var transform = start
    repeat(2) {
        val user = maskOf(renderCrop(photo, transform, MASK_SIZE), dropBorder = true)
        val fit = InkAnalysis.fit(model, user) ?: return if (it == 0) null else transform
        transform = transform.apply(fit)
    }
    return transform
}