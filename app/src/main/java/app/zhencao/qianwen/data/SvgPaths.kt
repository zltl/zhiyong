package app.zhencao.qianwen.data

import app.zhencao.qianwen.model.Box
import app.zhencao.qianwen.model.GlyphStroke
import java.time.LocalDate
import kotlin.math.min

sealed class PathOp {
    data class Move(val x: Float, val y: Float) : PathOp()
    data class Line(val x: Float, val y: Float) : PathOp()
    data class Cubic(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
    ) : PathOp()
    data class Quad(
        val x1: Float,
        val y1: Float,
        val x: Float,
        val y: Float,
    ) : PathOp()
    data object Close : PathOp()
}

object SvgPaths {
    private val token = Regex("""[A-Za-z]|-?\d*\.?\d+""")

    fun parse(path: String): List<PathOp> {
        val tokens = token.findAll(path).map { it.value }.toList()
        if (tokens.isEmpty()) return emptyList()
        val ops = ArrayList<PathOp>()
        var index = 0
        var command = 'M'
        var currentX = 0f
        var currentY = 0f
        var startX = 0f
        var startY = 0f

        fun number(): Float {
            if (index >= tokens.size) error("路径参数不足")
            return tokens[index++].toFloat()
        }

        while (index < tokens.size) {
            val token = tokens[index]
            if (token[0].isLetter()) {
                command = token[0]
                index++
            }
            val relative = command.isLowerCase()
            when (command.uppercaseChar()) {
                'M' -> {
                    val x = number()
                    val y = number()
                    currentX = if (relative) currentX + x else x
                    currentY = if (relative) currentY + y else y
                    startX = currentX
                    startY = currentY
                    ops += PathOp.Move(currentX, currentY)
                    command = if (relative) 'l' else 'L'
                }
                'L' -> {
                    val x = number()
                    val y = number()
                    currentX = if (relative) currentX + x else x
                    currentY = if (relative) currentY + y else y
                    ops += PathOp.Line(currentX, currentY)
                }
                'C' -> {
                    val x1 = number()
                    val y1 = number()
                    val x2 = number()
                    val y2 = number()
                    val x = number()
                    val y = number()
                    val abs = AbsoluteCubic(currentX, currentY, relative, x1, y1, x2, y2, x, y)
                    ops += abs.op
                    currentX = abs.x
                    currentY = abs.y
                }
                'Q' -> {
                    val x1 = number()
                    val y1 = number()
                    val x = number()
                    val y = number()
                    val cx = if (relative) currentX + x1 else x1
                    val cy = if (relative) currentY + y1 else y1
                    val ex = if (relative) currentX + x else x
                    val ey = if (relative) currentY + y else y
                    ops += PathOp.Quad(cx, cy, ex, ey)
                    currentX = ex
                    currentY = ey
                }
                'Z' -> {
                    ops += PathOp.Close
                    currentX = startX
                    currentY = startY
                }
                else -> error("不支持的路径命令 $command")
            }
        }
        return ops
    }

    fun parseOrEmpty(path: String): List<PathOp> = runCatching { parse(path) }.getOrDefault(emptyList())

    fun bounds(ops: List<PathOp>, steps: Int = 16): Box? {
        val points = ArrayList<Pair<Float, Float>>()
        var currentX = 0f
        var currentY = 0f
        for (op in ops) {
            when (op) {
                is PathOp.Move -> {
                    currentX = op.x
                    currentY = op.y
                    points += currentX to currentY
                }
                is PathOp.Line -> {
                    currentX = op.x
                    currentY = op.y
                    points += currentX to currentY
                }
                is PathOp.Cubic -> {
                    val x0 = currentX
                    val y0 = currentY
                    for (step in 1..steps) {
                        val t = step / steps.toFloat()
                        points += cubic(x0, op.x1, op.x2, op.x, t) to cubic(y0, op.y1, op.y2, op.y, t)
                    }
                    currentX = op.x
                    currentY = op.y
                }
                is PathOp.Quad -> {
                    val x0 = currentX
                    val y0 = currentY
                    for (step in 1..steps) {
                        val t = step / steps.toFloat()
                        val u = 1f - t
                        val x = u * u * x0 + 2 * u * t * op.x1 + t * t * op.x
                        val y = u * u * y0 + 2 * u * t * op.y1 + t * t * op.y
                        points += x to y
                    }
                    currentX = op.x
                    currentY = op.y
                }
                PathOp.Close -> Unit
            }
        }
        if (points.isEmpty()) return null
        return Box(
            left = points.minOf { it.first },
            top = points.minOf { it.second },
            right = points.maxOf { it.first },
            bottom = points.maxOf { it.second },
        )
    }

    fun modelBox(strokes: List<GlyphStroke>): Box? {
        val boxes = strokes.mapNotNull { bounds(parseOrEmpty(it.path)) }
        if (boxes.isEmpty()) return null
        return Box(
            left = boxes.minOf { it.left },
            top = boxes.minOf { it.top },
            right = boxes.maxOf { it.right },
            bottom = boxes.maxOf { it.bottom },
        )
    }
}

private class AbsoluteCubic(
    currentX: Float,
    currentY: Float,
    relative: Boolean,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    x: Float,
    y: Float,
) {
    val x: Float = if (relative) currentX + x else x
    val y: Float = if (relative) currentY + y else y
    val op = PathOp.Cubic(
        x1 = if (relative) currentX + x1 else x1,
        y1 = if (relative) currentY + y1 else y1,
        x2 = if (relative) currentX + x2 else x2,
        y2 = if (relative) currentY + y2 else y2,
        x = this.x,
        y = this.y,
    )
}

private fun cubic(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val u = 1f - t
    return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
}

object DailyPlanner {
    fun assign(availableCount: Int, cursor: Int, dailyCount: Int): List<Int> {
        if (dailyCount <= 0 || cursor >= availableCount) return emptyList()
        val end = min(availableCount, cursor + dailyCount)
        return (cursor until end).toList()
    }

    fun nextCursor(
        lastPlanDate: String,
        today: LocalDate,
        cursor: Int,
        lastDailyCount: Int,
    ): Int {
        if (lastPlanDate.isEmpty()) return cursor
        val last = runCatching { LocalDate.parse(lastPlanDate) }.getOrNull() ?: return cursor
        if (last >= today) return cursor
        return cursor + lastDailyCount.coerceAtLeast(0)
    }
}
