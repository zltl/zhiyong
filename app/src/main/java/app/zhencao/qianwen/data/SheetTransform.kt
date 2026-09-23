package app.zhencao.qianwen.data

import kotlin.math.cos
import kotlin.math.sin

/**
 * Places a sheet photo relative to the crop frame. A photo pixel p lands at
 * scale * R(rotation) * p + (tx, ty), in frame units where the frame is [0, 1]².
 */
data class SheetTransform(
    val scale: Float,
    val rotation: Float,
    val tx: Float,
    val ty: Float,
) {
    fun map(x: Float, y: Float): Point {
        val r = Math.toRadians(rotation.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return Point(scale * (c * x - s * y) + tx, scale * (s * x + c * y) + ty)
    }

    fun pan(dx: Float, dy: Float): SheetTransform = copy(tx = tx + dx, ty = ty + dy)

    fun zoom(factor: Float, cx: Float, cy: Float): SheetTransform =
        copy(scale = scale * factor, tx = factor * (tx - cx) + cx, ty = factor * (ty - cy) + cy)

    fun rotate(degrees: Float, cx: Float, cy: Float): SheetTransform {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val x = tx - cx
        val y = ty - cy
        return copy(rotation = rotation + degrees, tx = c * x - s * y + cx, ty = s * x + c * y + cy)
    }

    fun apply(fit: Fit): SheetTransform = copy(
        scale = scale * fit.scale,
        tx = fit.scale * (tx - fit.from.x) + fit.to.x,
        ty = fit.scale * (ty - fit.from.y) + fit.to.y,
    )

    companion object {
        /** Whole photo visible, centred on the frame, with [span] frames across its longer side. */
        fun fitting(width: Int, height: Int, span: Float): SheetTransform {
            val scale = span / maxOf(width, height)
            return SheetTransform(scale, 0f, 0.5f - scale * width / 2f, 0.5f - scale * height / 2f)
        }
    }
}
