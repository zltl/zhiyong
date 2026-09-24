package app.zhencao.qianwen.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhencao.qianwen.model.PracticeGrid
import app.zhencao.qianwen.model.practiceGridGeometry
import app.zhencao.qianwen.ui.theme.GridRed
import app.zhencao.qianwen.ui.theme.Ink
import app.zhencao.qianwen.ui.theme.SheetPaper
import kotlin.math.roundToInt

private val glyphBitmapCache = object : LinkedHashMap<String, ImageBitmap?>(128, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?): Boolean = size > 240
}

fun loadGlyphBitmap(context: Context, asset: String?): ImageBitmap? {
    if (asset.isNullOrBlank()) return null
    synchronized(glyphBitmapCache) {
        if (glyphBitmapCache.containsKey(asset)) return glyphBitmapCache[asset]
        val decoded = runCatching {
            context.assets.open(asset).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
        glyphBitmapCache[asset] = decoded
        return decoded
    }
}

@Composable
fun rememberGlyphBitmap(asset: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(asset) { loadGlyphBitmap(context, asset) }
}

/** Draws [bitmap] centred and fitted inside the scope, never stretched. */
fun DrawScope.drawFitted(bitmap: ImageBitmap, alpha: Float = 1f) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    }
    val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    val dest = android.graphics.Rect(
        left.roundToInt(),
        top.roundToInt(),
        (left + w).roundToInt(),
        (top + h).roundToInt(),
    )
    drawContext.canvas.nativeCanvas.drawBitmap(bitmap.asAndroidBitmap(), null, dest, paint)
}

fun DrawScope.drawPracticeGrid(
    kind: PracticeGrid,
    width: Float = size.width,
    height: Float = size.height,
    border: Boolean = true,
    color: Color = GridRed,
    alpha: Float = 0.72f,
) {
    val ink = color.copy(alpha = alpha)
    val unit = 1.dp.toPx()
    val geometry = practiceGridGeometry(kind, width, height, border)
    geometry.boxes.forEach { box ->
        drawRect(
            ink,
            topLeft = Offset(box.left, box.top),
            size = Size(box.right - box.left, box.bottom - box.top),
            style = Stroke(width = box.width * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    geometry.lines.forEach { line ->
        drawLine(
            ink,
            Offset(line.startX, line.startY),
            Offset(line.endX, line.endY),
            strokeWidth = line.width * unit,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun PracticeGridPicker(selected: PracticeGrid, onSelect: (PracticeGrid) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PracticeGrid.entries.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelect(item) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
fun GlyphFrame(
    label: String,
    modifier: Modifier = Modifier,
    paper: Color = SheetPaper,
    draw: DrawScope.() -> Unit,
) {
    Box(modifier.background(paper)) {
        Canvas(Modifier.fillMaxSize()) { draw() }
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = Ink.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
    }
}

/** Keeps the display awake while the calling screen is shown; the phone sits beside the paper. */
@Composable
fun KeepScreenOn() {
    val view: View = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
