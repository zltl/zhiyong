package app.zhencao.qianwen.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val MASK_SIZE = 256

data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class Point(val x: Float, val y: Float)

/** Square ink mask; coordinates in [Bounds] and [Point] are fractions of the side. */
class InkMask(val size: Int, val ink: BooleanArray) {
    init {
        require(ink.size == size * size)
    }

    val count: Int = ink.count { it }

    fun bounds(): Bounds? {
        var left = size
        var top = size
        var right = -1
        var bottom = -1
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!ink[y * size + x]) continue
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
            }
        }
        if (right < 0) return null
        val s = size.toFloat()
        return Bounds(left / s, top / s, (right + 1) / s, (bottom + 1) / s)
    }

    fun centroid(): Point? {
        if (count == 0) return null
        var sx = 0.0
        var sy = 0.0
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (ink[y * size + x]) {
                    sx += x + 0.5
                    sy += y + 0.5
                }
            }
        }
        return Point((sx / count / size).toFloat(), (sy / count / size).toFloat())
    }
}

data class Comparison(
    /** Share of the model's ink the practice reaches, within tolerance. */
    val recall: Float,
    /** Share of the practice's ink that lands on the model, within tolerance. */
    val precision: Float,
    val dx: Float,
    val dy: Float,
    val widthRatio: Float,
    val heightRatio: Float,
) {
    val overlap: Float
        get() = if (recall + precision <= 0f) 0f else 2f * recall * precision / (recall + precision)
}

enum class DiffCell { NONE, SHARED, MISSING, EXTRA }

/** Similarity that maps practice-ink point p to s * (p - from) + to. */
data class Fit(val scale: Float, val from: Point, val to: Point)

object InkAnalysis {
    /** [lightInk]: light strokes on a dark ground, as in the 关中本 rubbing. */
    fun extract(argb: IntArray, size: Int, dropBorder: Boolean = false, lightInk: Boolean = false): InkMask {
        require(argb.size == size * size)
        val value = FloatArray(argb.size) { i ->
            val c = argb[i]
            // Brightest channel: red grid lines stay light, black ink stays dark.
            val v = max((c shr 16) and 0xFF, max((c shr 8) and 0xFF, c and 0xFF))
            (if (lightInk) 255 - v else v).toFloat()
        }
        // Paper brightness: the max filter lifts strokes up to a quarter of the side wide,
        // the blur then smooths lighting; ink never darkens its own background.
        val radius = max(1, size / 8)
        val background = boxBlur(maxFilter(value, size, radius), size, radius)
        val norm = IntArray(value.size) { i ->
            (value[i] / max(background[i], 1f) * 200f).toInt().coerceIn(0, 255)
        }
        val threshold = min(otsu(norm), 180)
        var mask = BooleanArray(norm.size) { norm[it] < threshold }
        mask = dropSmall(mask, size, max(4, size * size / 1600))
        mask = fillHoles(mask, size, size * size / 400)
        if (dropBorder) mask = dropTouchingBorder(mask, size)
        return InkMask(size, mask)
    }

    fun otsu(values: IntArray): Int {
        val hist = IntArray(256)
        for (v in values) hist[v.coerceIn(0, 255)]++
        val total = values.size.toDouble()
        var sumAll = 0.0
        for (t in 0 until 256) sumAll += t * hist[t].toDouble()
        var weightBack = 0.0
        var sumBack = 0.0
        var best = -1.0
        var threshold = 0
        for (t in 0 until 256) {
            weightBack += hist[t]
            if (weightBack == 0.0) continue
            val weightFore = total - weightBack
            if (weightFore == 0.0) break
            sumBack += t * hist[t].toDouble()
            val meanBack = sumBack / weightBack
            val meanFore = (sumAll - sumBack) / weightFore
            val between = weightBack * weightFore * (meanBack - meanFore) * (meanBack - meanFore)
            if (between > best) {
                best = between
                threshold = t + 1
            }
        }
        return threshold
    }

    fun compare(model: InkMask, user: InkMask, tolerance: Int = model.size * 6 / MASK_SIZE): Comparison? {
        require(model.size == user.size)
        if (model.count == 0 || user.count == 0) return null
        val nearUser = dilate(user.ink, user.size, tolerance)
        val nearModel = dilate(model.ink, model.size, tolerance)
        var reached = 0
        var landed = 0
        for (i in model.ink.indices) {
            if (model.ink[i] && nearUser[i]) reached++
            if (user.ink[i] && nearModel[i]) landed++
        }
        val mc = model.centroid()!!
        val uc = user.centroid()!!
        val mb = model.bounds()!!
        val ub = user.bounds()!!
        return Comparison(
            recall = reached.toFloat() / model.count,
            precision = landed.toFloat() / user.count,
            dx = uc.x - mc.x,
            dy = uc.y - mc.y,
            widthRatio = ub.width / mb.width,
            heightRatio = ub.height / mb.height,
        )
    }

    fun diff(model: InkMask, user: InkMask, tolerance: Int = model.size * 6 / MASK_SIZE): Array<DiffCell> {
        require(model.size == user.size)
        val nearUser = dilate(user.ink, user.size, tolerance)
        val nearModel = dilate(model.ink, model.size, tolerance)
        return Array(model.ink.size) { i ->
            when {
                model.ink[i] && !nearUser[i] -> DiffCell.MISSING
                user.ink[i] && !nearModel[i] -> DiffCell.EXTRA
                model.ink[i] || user.ink[i] -> DiffCell.SHARED
                else -> DiffCell.NONE
            }
        }
    }

    fun fit(model: InkMask, user: InkMask): Fit? {
        val mb = model.bounds() ?: return null
        val ub = user.bounds() ?: return null
        val mc = model.centroid() ?: return null
        val uc = user.centroid() ?: return null
        val userArea = ub.width * ub.height
        if (userArea <= 0f) return null
        val scale = sqrt(mb.width * mb.height / userArea).coerceIn(0.4f, 2.5f)
        return Fit(scale, uc, mc)
    }

    internal fun boxBlur(values: FloatArray, size: Int, radius: Int): FloatArray {
        val stride = size + 1
        val integral = DoubleArray(stride * stride)
        for (y in 0 until size) {
            var row = 0.0
            for (x in 0 until size) {
                row += values[y * size + x]
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + row
            }
        }
        return FloatArray(values.size) { i ->
            val x = i % size
            val y = i / size
            val x0 = max(0, x - radius)
            val y0 = max(0, y - radius)
            val x1 = min(size, x + radius + 1)
            val y1 = min(size, y + radius + 1)
            val sum = integral[y1 * stride + x1] - integral[y0 * stride + x1] -
                integral[y1 * stride + x0] + integral[y0 * stride + x0]
            (sum / ((x1 - x0) * (y1 - y0))).toFloat()
        }
    }

    internal fun maxFilter(values: FloatArray, size: Int, radius: Int): FloatArray {
        val horizontal = FloatArray(values.size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                var best = 0f
                for (k in max(0, x - radius)..min(size - 1, x + radius)) best = max(best, values[y * size + k])
                horizontal[y * size + x] = best
            }
        }
        val out = FloatArray(values.size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                var best = 0f
                for (k in max(0, y - radius)..min(size - 1, y + radius)) best = max(best, horizontal[k * size + x])
                out[y * size + x] = best
            }
        }
        return out
    }

    internal fun dilate(mask: BooleanArray, size: Int, radius: Int): BooleanArray {
        if (radius <= 0) return mask.copyOf()
        val horizontal = BooleanArray(mask.size)
        for (y in 0 until size) {
            var run = 0
            for (x in -radius until size) {
                val enter = x + radius
                if (enter < size && mask[y * size + enter]) run++
                val leave = x - radius - 1
                if (leave >= 0 && mask[y * size + leave]) run--
                if (x >= 0) horizontal[y * size + x] = run > 0
            }
        }
        val out = BooleanArray(mask.size)
        for (x in 0 until size) {
            var run = 0
            for (y in -radius until size) {
                val enter = y + radius
                if (enter < size && horizontal[enter * size + x]) run++
                val leave = y - radius - 1
                if (leave >= 0 && horizontal[leave * size + x]) run--
                if (y >= 0) out[y * size + x] = run > 0
            }
        }
        return out
    }

    private fun dropSmall(mask: BooleanArray, size: Int, minArea: Int): BooleanArray {
        val out = mask.copyOf()
        forEachComponent(mask, size, target = true) { pixels, _ ->
            if (pixels.size < minArea) pixels.forEach { out[it] = false }
        }
        return out
    }

    private fun fillHoles(mask: BooleanArray, size: Int, maxArea: Int): BooleanArray {
        val out = mask.copyOf()
        forEachComponent(mask, size, target = false) { pixels, touchesBorder ->
            if (!touchesBorder && pixels.size <= maxArea) pixels.forEach { out[it] = true }
        }
        return out
    }

    private fun dropTouchingBorder(mask: BooleanArray, size: Int): BooleanArray {
        val out = mask.copyOf()
        forEachComponent(mask, size, target = true) { pixels, touchesBorder ->
            if (touchesBorder) pixels.forEach { out[it] = false }
        }
        return out
    }

    private inline fun forEachComponent(
        mask: BooleanArray,
        size: Int,
        target: Boolean,
        block: (pixels: IntArray, touchesBorder: Boolean) -> Unit,
    ) {
        val seen = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        val pixels = IntArray(mask.size)
        for (start in mask.indices) {
            if (mask[start] != target || seen[start]) continue
            var top = 0
            var count = 0
            var border = false
            stack[top++] = start
            seen[start] = true
            while (top > 0) {
                val p = stack[--top]
                pixels[count++] = p
                val x = p % size
                val y = p / size
                if (x == 0 || y == 0 || x == size - 1 || y == size - 1) border = true
                if (x > 0) top = push(mask, seen, stack, top, p - 1, target)
                if (x < size - 1) top = push(mask, seen, stack, top, p + 1, target)
                if (y > 0) top = push(mask, seen, stack, top, p - size, target)
                if (y < size - 1) top = push(mask, seen, stack, top, p + size, target)
            }
            block(pixels.copyOf(count), border)
        }
    }

    private fun push(
        mask: BooleanArray,
        seen: BooleanArray,
        stack: IntArray,
        top: Int,
        p: Int,
        target: Boolean,
    ): Int {
        if (seen[p] || mask[p] != target) return top
        seen[p] = true
        stack[top] = p
        return top + 1
    }
}
