package app.zhencao.qianwen.model

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

enum class GlyphBook(val label: String) {
    OGAWA("小川本"),
    GUANZHONG("关中本"),
}

data class CharacterEntry(
    val index: Int,
    val char: String,
    val group: Int,
    val ink: String,
    /** Stem of the 关中本 crops for this character. */
    val rubbing: String = "glyphs/guanzhong/%03d".format(index),
    /** Stem of 关中本 glyphs standing in for lost or damaged 小川本 ones. */
    val inkFallback: String? = null,
    /** Glyph kinds ("zhen", "cao") taken from [inkFallback]. */
    val inkFallbackKinds: Set<String> = emptySet(),
) {
    fun usesFallback(script: ScriptStyle): Boolean =
        inkFallback != null && script.glyphKind in inkFallbackKinds

    /** The edition shown until the reader opens the other one. */
    fun shownBook(script: ScriptStyle): GlyphBook =
        if (usesFallback(script)) GlyphBook.GUANZHONG else GlyphBook.OGAWA

    fun glyphAsset(script: ScriptStyle): String = glyphAsset(script, shownBook(script))

    fun glyphAsset(script: ScriptStyle, book: GlyphBook): String {
        val stem = when (book) {
            GlyphBook.OGAWA -> ink
            GlyphBook.GUANZHONG -> rubbing
        }
        return "${stem}_${script.glyphKind}.webp"
    }
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

fun sizeLabel(widthRatio: Float, heightRatio: Float): String {
    fun part(ratio: Float, big: String, small: String, near: String) = when {
        ratio > 1.08f -> "$big ${percent(ratio - 1f)}"
        ratio < 0.92f -> "$small ${percent(1f - ratio)}"
        else -> near
    }
    return "${part(widthRatio, "偏宽", "偏窄", "宽度接近")}，${part(heightRatio, "偏高", "偏矮", "高度接近")}"
}

private fun percent(value: Float): String = "${(value * 100).toInt()}%"
