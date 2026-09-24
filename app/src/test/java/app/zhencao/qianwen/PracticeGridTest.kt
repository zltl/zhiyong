package app.zhencao.qianwen

import app.zhencao.qianwen.model.GRID_CROSS
import app.zhencao.qianwen.model.GRID_DIAGONAL
import app.zhencao.qianwen.model.GRID_FRAME
import app.zhencao.qianwen.model.GridBox
import app.zhencao.qianwen.model.GridLine
import app.zhencao.qianwen.model.PracticeGrid
import app.zhencao.qianwen.model.parsePracticeGrid
import app.zhencao.qianwen.model.practiceGridGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeGridTest {
    @Test
    fun tianCrossesTheMiddle() {
        val geometry = practiceGridGeometry(PracticeGrid.TIAN, 6f, 6f)
        assertEquals(listOf(GridBox(0f, 0f, 6f, 6f, GRID_FRAME)), geometry.boxes)
        assertEquals(
            listOf(
                GridLine(3f, 0f, 3f, 6f, GRID_CROSS),
                GridLine(0f, 3f, 6f, 3f, GRID_CROSS),
            ),
            geometry.lines,
        )
    }

    @Test
    fun miAddsBothDiagonals() {
        val lines = practiceGridGeometry(PracticeGrid.MI, 6f, 6f).lines
        assertEquals(GridLine(0f, 0f, 6f, 6f, GRID_DIAGONAL), lines[2])
        assertEquals(GridLine(6f, 0f, 0f, 6f, GRID_DIAGONAL), lines[3])
    }

    @Test
    fun jiuDividesIntoThirds() {
        val lines = practiceGridGeometry(PracticeGrid.JIU, 6f, 6f, border = false).lines
        assertEquals(
            listOf(
                GridLine(2f, 0f, 2f, 6f, GRID_CROSS),
                GridLine(4f, 0f, 4f, 6f, GRID_CROSS),
                GridLine(0f, 2f, 6f, 2f, GRID_CROSS),
                GridLine(0f, 4f, 6f, 4f, GRID_CROSS),
            ),
            lines,
        )
    }

    @Test
    fun huiInsetsByOneSixth() {
        val boxes = practiceGridGeometry(PracticeGrid.HUI, 6f, 6f).boxes
        assertEquals(GridBox(1f, 1f, 5f, 5f, GRID_CROSS), boxes[1])
    }

    @Test
    fun noneAndCropBorderDrawNoFrame() {
        assertEquals(0, practiceGridGeometry(PracticeGrid.NONE, 6f, 6f).boxes.size)
        assertEquals(0, practiceGridGeometry(PracticeGrid.TIAN, 6f, 6f, border = false).boxes.size)
        assertEquals(2, practiceGridGeometry(PracticeGrid.TIAN, 6f, 6f, border = false).lines.size)
    }

    @Test
    fun unknownGridFallsBackToMi() {
        assertEquals(PracticeGrid.MI, parsePracticeGrid(null))
        assertEquals(PracticeGrid.MI, parsePracticeGrid("方格"))
        assertEquals(PracticeGrid.HUI, parsePracticeGrid("HUI"))
    }
}
