package app.zhencao.qianwen

import app.zhencao.qianwen.data.DiffCell
import app.zhencao.qianwen.data.InkAnalysis
import app.zhencao.qianwen.data.InkMask
import app.zhencao.qianwen.data.SheetTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InkAnalysisTest {
    private val side = 64
    private val white = 0xFFF4F1EA.toInt()
    private val black = 0xFF1E1C1A.toInt()
    private val red = 0xFFDC4A3C.toInt()

    private fun sheet(block: (IntArray) -> Unit): IntArray = IntArray(side * side) { white }.also(block)

    private fun IntArray.rect(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        for (y in y0 until y1) for (x in x0 until x1) this[y * side + x] = color
    }

    private fun mask(x0: Int, y0: Int, x1: Int, y1: Int): InkMask {
        val ink = BooleanArray(side * side)
        for (y in y0 until y1) for (x in x0 until x1) ink[y * side + x] = true
        return InkMask(side, ink)
    }

    @Test
    fun otsuSplitsTwoClusters() {
        val values = IntArray(100) { if (it < 30) 40 else 210 }
        val t = InkAnalysis.otsu(values)
        assertTrue(t in 41..210)
    }

    @Test
    fun extractKeepsInkAndIgnoresRedGridLines() {
        val pixels = sheet {
            it.rect(0, 31, side, 33, red)
            it.rect(31, 0, 33, side, red)
            it.rect(20, 24, 44, 40, black)
        }
        val mask = InkAnalysis.extract(pixels, side)
        assertEquals(24 * 16, mask.count)
        val bounds = mask.bounds()!!
        assertEquals(20f / side, bounds.left, 0.001f)
        assertEquals(44f / side, bounds.right, 0.001f)
    }

    @Test
    fun extractDropsSpecksAndFillsPinholes() {
        val pixels = sheet {
            it.rect(24, 24, 40, 40, black)
            it.rect(31, 31, 33, 33, white)
            it.rect(4, 4, 5, 5, black)
        }
        val mask = InkAnalysis.extract(pixels, side)
        assertEquals(16 * 16, mask.count)
    }

    @Test
    fun extractCanDropNeighboursCutByTheFrame() {
        val pixels = sheet {
            it.rect(24, 24, 40, 40, black)
            it.rect(0, 10, 6, 50, black)
        }
        assertEquals(256 + 6 * 40, InkAnalysis.extract(pixels, side).count)
        assertEquals(256, InkAnalysis.extract(pixels, side, dropBorder = true).count)
    }

    @Test
    fun rubbingStrokesAreLightOnDark() {
        val stone = 0xFF3A3632.toInt()
        val chalk = 0xFFD8CBB0.toInt()
        val pixels = IntArray(side * side) { stone }.also { it.rect(24, 20, 40, 44, chalk) }
        assertEquals(16 * 24, InkAnalysis.extract(pixels, side, lightInk = true).count)
    }

    @Test
    fun blankPaperHasNoInk() {
        assertEquals(0, InkAnalysis.extract(sheet { }, side).count)
    }

    @Test
    fun identicalMasksFullyOverlap() {
        val model = mask(20, 20, 40, 40)
        val result = InkAnalysis.compare(model, model, tolerance = 1)!!
        assertEquals(1f, result.overlap, 0.001f)
        assertEquals(0f, result.dx, 0.001f)
        assertEquals(1f, result.widthRatio, 0.001f)
    }

    @Test
    fun shiftedPracticeReportsDirectionAndLowerOverlap() {
        val model = mask(20, 20, 40, 40)
        val user = mask(30, 20, 50, 40)
        val result = InkAnalysis.compare(model, user, tolerance = 1)!!
        assertTrue(result.dx > 0.1f)
        assertEquals(0f, result.dy, 0.001f)
        assertTrue(result.overlap in 0.4f..0.7f)
    }

    @Test
    fun emptyPracticeHasNoComparison() {
        assertNull(InkAnalysis.compare(mask(20, 20, 40, 40), InkMask(side, BooleanArray(side * side))))
    }

    @Test
    fun diffMarksMissingAndExtraInk() {
        val model = mask(10, 10, 20, 20)
        val user = mask(40, 40, 50, 50)
        val cells = InkAnalysis.diff(model, user, tolerance = 1)
        assertEquals(DiffCell.MISSING, cells[15 * side + 15])
        assertEquals(DiffCell.EXTRA, cells[45 * side + 45])
        assertEquals(DiffCell.NONE, cells[30 * side + 30])
    }

    @Test
    fun fitScalesAndMovesPracticeOntoModel() {
        val model = mask(16, 16, 48, 48)
        val user = mask(4, 4, 20, 20)
        val fit = InkAnalysis.fit(model, user)!!
        assertEquals(2f, fit.scale, 0.01f)
        val moved = SheetTransform(1f, 0f, 0f, 0f).apply(fit).map(fit.from.x, fit.from.y)
        assertEquals(fit.to.x, moved.x, 0.001f)
        assertEquals(fit.to.y, moved.y, 0.001f)
    }

    @Test
    fun zoomAndRotateKeepTheirCentre() {
        val start = SheetTransform(0.01f, 0f, 0.1f, 0.2f)
        val photoPoint = 30f to 40f
        val before = start.map(photoPoint.first, photoPoint.second)
        val zoomed = start.zoom(1.7f, before.x, before.y).map(photoPoint.first, photoPoint.second)
        assertEquals(before.x, zoomed.x, 0.0001f)
        assertEquals(before.y, zoomed.y, 0.0001f)
        val turned = start.rotate(33f, before.x, before.y).map(photoPoint.first, photoPoint.second)
        assertEquals(before.x, turned.x, 0.0001f)
        assertEquals(before.y, turned.y, 0.0001f)
    }

    @Test
    fun fittingCentresThePhotoOnTheFrame() {
        val t = SheetTransform.fitting(3000, 2000, span = 3f)
        val centre = t.map(1500f, 1000f)
        assertEquals(0.5f, centre.x, 0.0001f)
        assertEquals(0.5f, centre.y, 0.0001f)
        val right = t.map(3000f, 1000f)
        assertNotNull(right)
        assertEquals(3f, (right.x - centre.x) * 2f, 0.0001f)
    }
}
