package app.zhencao.qianwen.model

enum class PracticeGrid(val label: String) {
    NONE("无"),
    TIAN("田字"),
    MI("米字"),
    JIU("九宫"),
    HUI("回宫"),
}

fun parsePracticeGrid(raw: String?): PracticeGrid {
    if (raw.isNullOrBlank()) return PracticeGrid.MI
    return runCatching { PracticeGrid.valueOf(raw) }.getOrDefault(PracticeGrid.MI)
}

data class GridBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val width: Float,
)

data class GridLine(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val width: Float,
)

data class PracticeGridGeometry(
    val boxes: List<GridBox> = emptyList(),
    val lines: List<GridLine> = emptyList(),
)

/** Stroke widths in dp. Drawn scaled to screen density. */
const val GRID_FRAME = 2.75f
const val GRID_CROSS = 2f
const val GRID_DIAGONAL = 1.5f

/** Guide lines for a cell of [width] by [height]. [border] is the outer frame; crop already has one. */
fun practiceGridGeometry(
    kind: PracticeGrid,
    width: Float,
    height: Float,
    border: Boolean = true,
): PracticeGridGeometry {
    if (kind == PracticeGrid.NONE || width <= 0f || height <= 0f) return PracticeGridGeometry()
    val boxes = mutableListOf<GridBox>()
    val lines = mutableListOf<GridLine>()
    if (border) boxes += GridBox(0f, 0f, width, height, GRID_FRAME)
    when (kind) {
        PracticeGrid.NONE -> Unit
        PracticeGrid.TIAN -> lines += cross(width, height)
        PracticeGrid.MI -> {
            lines += cross(width, height)
            lines += GridLine(0f, 0f, width, height, GRID_DIAGONAL)
            lines += GridLine(width, 0f, 0f, height, GRID_DIAGONAL)
        }
        PracticeGrid.JIU -> {
            val x1 = width / 3f
            val x2 = width * 2f / 3f
            val y1 = height / 3f
            val y2 = height * 2f / 3f
            lines += GridLine(x1, 0f, x1, height, GRID_CROSS)
            lines += GridLine(x2, 0f, x2, height, GRID_CROSS)
            lines += GridLine(0f, y1, width, y1, GRID_CROSS)
            lines += GridLine(0f, y2, width, y2, GRID_CROSS)
        }
        PracticeGrid.HUI -> {
            val insetX = width / 6f
            val insetY = height / 6f
            boxes += GridBox(insetX, insetY, width - insetX, height - insetY, GRID_CROSS)
        }
    }
    return PracticeGridGeometry(boxes, lines)
}

private fun cross(width: Float, height: Float) = listOf(
    GridLine(width / 2f, 0f, width / 2f, height, GRID_CROSS),
    GridLine(0f, height / 2f, width, height / 2f, GRID_CROSS),
)
