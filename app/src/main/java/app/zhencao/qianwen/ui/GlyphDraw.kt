package app.zhencao.qianwen.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhencao.qianwen.data.PathOp
import app.zhencao.qianwen.data.SvgPaths
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.Edition
import app.zhencao.qianwen.model.GlyphStroke
import app.zhencao.qianwen.model.GridType
import app.zhencao.qianwen.model.InkPoint
import app.zhencao.qianwen.model.InkStroke
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.model.StrokeKind
import kotlin.math.roundToInt

data class GlyphPalette(
    val paper: Color,
    val ink: Color,
    val grid: Color,
)

fun glyphPalette(edition: Edition, invert: Boolean): GlyphPalette {
    return if (!invert) {
        when (edition) {
            Edition.INK -> GlyphPalette(Color(0xFFF7F1E6), Color(0xFF2A241C), Color(0xFFC46A5A))
            Edition.RUBBING -> GlyphPalette(Color(0xFFE4DFD4), Color(0xFF111111), Color(0xFFC46A5A))
        }
    } else {
        when (edition) {
            Edition.INK -> GlyphPalette(Color(0xFF1A1814), Color(0xFFF4EFE4), Color(0xFFD7A394))
            Edition.RUBBING -> GlyphPalette(Color(0xFF101010), Color(0xFFF3F3F3), Color(0xFFD7A394))
        }
    }
}

fun DrawScope.drawGrid(grid: GridType, color: Color) {
    if (grid == GridType.NONE) return
    val line = color.copy(alpha = 0.55f)
    drawRect(line, style = Stroke(width = 2f))
    when (grid) {
        GridType.NONE -> Unit
        GridType.JIU, GridType.MI -> {
            val stepX = size.width / 3f
            val stepY = size.height / 3f
            for (step in 1..2) {
                drawLine(line, Offset(stepX * step, 0f), Offset(stepX * step, size.height), strokeWidth = 1.5f)
                drawLine(line, Offset(0f, stepY * step), Offset(size.width, stepY * step), strokeWidth = 1.5f)
            }
            if (grid == GridType.MI) {
                drawLine(line, Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.5f)
                drawLine(line, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f)
            }
        }
        GridType.HUI -> {
            val inset = 0.22f
            drawRect(
                line,
                topLeft = Offset(size.width * inset, size.height * inset),
                size = Size(size.width * (1f - inset * 2), size.height * (1f - inset * 2)),
                style = Stroke(width = 1.5f),
            )
            drawLine(line, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 1.5f)
            drawLine(line, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.5f)
        }
    }
}

private val glyphBitmapCache = LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true)

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
        while (glyphBitmapCache.size > 80) {
            val oldest = glyphBitmapCache.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        return decoded
    }
}

@Composable
fun rememberGlyphBitmap(asset: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(asset) { loadGlyphBitmap(context, asset) }
}

fun DrawScope.drawGlyphPhoto(bitmap: ImageBitmap, invert: Boolean, alpha: Float = 1f) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        if (invert) {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
    }
    // fit, never stretch: a wide frame shows the square glyph centred
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

fun DrawScope.drawTruth(char: String, color: Color) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = size.minDimension * 0.62f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
    }
    val metrics = paint.fontMetrics
    drawContext.canvas.nativeCanvas.drawText(
        char,
        size.width / 2f,
        size.height / 2f - (metrics.ascent + metrics.descent) / 2f,
        paint,
    )
}

fun DrawScope.drawScriptModel(
    entry: CharacterEntry,
    script: ScriptStyle,
    edition: Edition,
    ink: Color,
    photo: ImageBitmap?,
    invert: Boolean,
    alpha: Float = 1f,
    progress: Float? = null,
) {
    if (photo != null) {
        drawGlyphPhoto(photo, invert, alpha)
        return
    }
    when (script) {
        ScriptStyle.ZHEN -> drawTruth(entry.char, ink.copy(alpha = alpha))
        ScriptStyle.CAO -> drawCursive(entry.caoStrokes, edition, ink.copy(alpha = alpha), progress)
    }
}

fun DrawScope.drawCursive(
    strokes: List<GlyphStroke>,
    edition: Edition,
    color: Color,
    progress: Float? = null,
) {
    val drawn = progress ?: strokes.size.toFloat()
    strokes.forEachIndexed { index, stroke ->
        val fraction = (drawn - index).coerceIn(0f, 1f)
        if (fraction > 0f) {
            drawStroke(stroke, fraction, edition, color)
        }
    }
}

fun DrawScope.drawInk(strokes: List<InkStroke>, active: List<InkPoint>, color: Color) {
    strokes.forEach { drawInkPoints(it.points, color) }
    drawInkPoints(active, color)
}

private fun DrawScope.drawStroke(
    stroke: GlyphStroke,
    fraction: Float,
    edition: Edition,
    color: Color,
) {
    val ops = SvgPaths.parseOrEmpty(stroke.path)
    if (ops.isEmpty()) return
    val source = ops.toAndroidPath({ it * size.width }, { it * size.height })
    val path = if (fraction >= 0.999f) {
        source
    } else {
        val measure = PathMeasure(source, false)
        val segment = android.graphics.Path()
        measure.getSegment(0f, measure.length * fraction, segment, true)
        segment
    }
    val width = size.minDimension * when {
        edition == Edition.INK && stroke.kind == StrokeKind.SOLID -> 0.034f
        edition == Edition.INK -> 0.018f
        stroke.kind == StrokeKind.SOLID -> 0.026f
        else -> 0.014f
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = if (edition == Edition.INK) Paint.Cap.ROUND else Paint.Cap.SQUARE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = width
        this.color = color.copy(alpha = if (stroke.kind == StrokeKind.SILK) 0.4f else 1f).toArgb()
    }
    drawContext.canvas.nativeCanvas.drawPath(path, paint)
}

private fun DrawScope.drawInkPoints(points: List<InkPoint>, color: Color) {
    if (points.size < 2) return
    val scale = size.minDimension / 280f
    for (index in 0 until points.lastIndex) {
        val start = points[index]
        val end = points[index + 1]
        val pressure = ((start.pressure + end.pressure) / 2f).coerceIn(0.15f, 1f)
        drawLine(
            color = color,
            start = Offset(start.x * size.width, start.y * size.height),
            end = Offset(end.x * size.width, end.y * size.height),
            strokeWidth = (3.5f + pressure * 14f) * scale,
            cap = StrokeCap.Round,
        )
    }
}

private fun List<PathOp>.toAndroidPath(
    mapX: (Float) -> Float,
    mapY: (Float) -> Float,
): android.graphics.Path {
    val path = android.graphics.Path()
    for (op in this) {
        when (op) {
            is PathOp.Move -> path.moveTo(mapX(op.x), mapY(op.y))
            is PathOp.Line -> path.lineTo(mapX(op.x), mapY(op.y))
            is PathOp.Cubic -> path.cubicTo(
                mapX(op.x1),
                mapY(op.y1),
                mapX(op.x2),
                mapY(op.y2),
                mapX(op.x),
                mapY(op.y),
            )
            is PathOp.Quad -> path.quadTo(mapX(op.x1), mapY(op.y1), mapX(op.x), mapY(op.y))
            PathOp.Close -> path.close()
        }
    }
    return path
}

@Composable
fun GlyphFrame(
    palette: GlyphPalette,
    grid: GridType,
    label: String,
    modifier: Modifier = Modifier,
    draw: DrawScope.() -> Unit,
) {
    Box(modifier.background(palette.paper)) {
        Canvas(Modifier.fillMaxSize()) {
            drawGrid(grid, palette.grid)
            draw()
        }
        Text(
            text = label,
            color = palette.ink.copy(alpha = 0.55f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}
