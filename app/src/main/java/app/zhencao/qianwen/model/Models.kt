package app.zhencao.qianwen.model

enum class Edition { INK, RUBBING }

enum class ScriptStyle {
    ZHEN,
    CAO,
    ;

    val glyphKind: String
        get() = when (this) {
            ZHEN -> "zhen"
            CAO -> "cao"
        }

    val label: String
        get() = when (this) {
            ZHEN -> "真"
            CAO -> "草"
        }

    val bookLabel: String
        get() = when (this) {
            ZHEN -> "真书"
            CAO -> "草书"
        }
}

fun parseScriptStyle(raw: String?): ScriptStyle? {
    if (raw.isNullOrBlank()) return null
    return runCatching { ScriptStyle.valueOf(raw) }.getOrNull()
}

enum class GridType { NONE, MI, JIU, HUI }

enum class LayoutMode { SIDE, STACK }

enum class StrokeKind { SOLID, SILK }

enum class StudioMode { COMPARE, STROKE, WRITE, OVERLAY }

enum class QuizKind { RECOGNIZE_CAO, PICK_CAO }

data class GlyphStroke(
    val order: Int,
    val kind: StrokeKind,
    val path: String,
)

data class CharacterEntry(
    val index: Int,
    val char: String,
    val group: Int,
    val slot: Int,
    val available: Boolean,
    val ink: String?,
    val rubbing: String?,
    val caoStrokes: List<GlyphStroke>,
    /** Stem of 关中本 glyphs standing in for lost or damaged 小川本 ones. */
    val inkFallback: String? = null,
    /** Glyph kinds ("zhen", "cao") that the ink edition takes from [inkFallback]. */
    val inkFallbackKinds: Set<String> = emptySet(),
) {
    fun glyphStem(edition: Edition): String? = when (edition) {
        Edition.INK -> ink
        Edition.RUBBING -> rubbing
    }

    fun usesFallback(edition: Edition, kind: String): Boolean =
        edition == Edition.INK && inkFallback != null && kind in inkFallbackKinds

    fun glyphAsset(edition: Edition, kind: String): String? {
        val stem = if (usesFallback(edition, kind)) inkFallback else glyphStem(edition)
        return stem?.let { "${it}_${kind}.webp" }
    }

    fun glyphAsset(edition: Edition, script: ScriptStyle): String? = glyphAsset(edition, script.glyphKind)
}

data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
)

data class InkStroke(
    val points: List<InkPoint>,
)

data class QuizQuestion(
    val kind: QuizKind,
    val answerIndex: Int,
    val options: List<Int>,
)

data class Box(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun area(): Float = width * height
}

fun iou(first: Box, second: Box): Float {
    val left = maxOf(first.left, second.left)
    val top = maxOf(first.top, second.top)
    val right = minOf(first.right, second.right)
    val bottom = minOf(first.bottom, second.bottom)
    val overlap = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
    val union = first.area() + second.area() - overlap
    if (union <= 0f) return 0f
    return overlap / union
}

fun inkBox(strokes: List<InkStroke>): Box? {
    val points = strokes.flatMap { it.points }
    if (points.isEmpty()) return null
    return Box(
        left = points.minOf { it.x },
        top = points.minOf { it.y },
        right = points.maxOf { it.x },
        bottom = points.maxOf { it.y },
    )
}

fun shiftLabel(dx: Float, dy: Float): String {
    val horizontal = when {
        dx > 0.03f -> "偏右 ${percent(dx)}"
        dx < -0.03f -> "偏左 ${percent(-dx)}"
        else -> "左右接近"
    }
    val vertical = when {
        dy > 0.03f -> "偏下 ${percent(dy)}"
        dy < -0.03f -> "偏上 ${percent(-dy)}"
        else -> "上下接近"
    }
    return "$horizontal，$vertical"
}

private fun percent(value: Float): String = "${(value * 100).toInt()}%"
