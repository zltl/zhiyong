package app.zhencao.qianwen

import app.zhencao.qianwen.data.DailyPlanner
import app.zhencao.qianwen.data.SvgPaths
import app.zhencao.qianwen.model.Box
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.model.iou
import app.zhencao.qianwen.model.parseScriptStyle
import app.zhencao.qianwen.model.shiftLabel
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QianwenLogicTest {
    @Test
    fun cubicBoundsContainMidpoint() {
        val ops = SvgPaths.parse("M 0 0 C 0 1 1 1 1 0")
        val box = SvgPaths.bounds(ops)
        assertNotNull(box)
        assertTrue(box!!.left <= 0.01f)
        assertTrue(box.right >= 0.99f)
        assertTrue(box.bottom >= 0.7f)
    }

    @Test
    fun identicalBoxesFullyOverlap() {
        val box = Box(0f, 0f, 1f, 1f)
        assertEquals(1f, iou(box, box), 0.001f)
    }

    @Test
    fun disjointBoxesDoNotOverlap() {
        val first = Box(0f, 0f, 0.2f, 0.2f)
        val second = Box(0.8f, 0.8f, 1f, 1f)
        assertEquals(0f, iou(first, second), 0.001f)
    }

    @Test
    fun shiftLabelNamesTheDirection() {
        assertTrue(shiftLabel(0.1f, -0.08f).contains("偏右"))
        assertTrue(shiftLabel(0.1f, -0.08f).contains("偏上"))
    }

    @Test
    fun dailyPlanStopsAtTheSampleEnd() {
        assertEquals(listOf(0, 1, 2, 3), DailyPlanner.assign(40, 0, 4))
        assertEquals(listOf(38, 39), DailyPlanner.assign(40, 38, 4))
        assertTrue(DailyPlanner.assign(40, 40, 4).isEmpty())
    }

    @Test
    fun newDayMovesTheCursorOnce() {
        val today = LocalDate.of(2026, 9, 21)
        assertEquals(0, DailyPlanner.nextCursor("", today, 0, 4))
        assertEquals(0, DailyPlanner.nextCursor("2026-09-21", today, 0, 4))
        assertEquals(4, DailyPlanner.nextCursor("2026-09-20", today, 0, 4))
        assertEquals(8, DailyPlanner.nextCursor("2026-09-18", today, 4, 4))
    }

    @Test
    fun scriptChoiceStaysUntilChanged() {
        assertEquals(null, parseScriptStyle(""))
        assertEquals(null, parseScriptStyle("INK"))
        assertEquals(ScriptStyle.ZHEN, parseScriptStyle("ZHEN"))
        assertEquals(ScriptStyle.CAO, parseScriptStyle("CAO"))
        assertEquals("zhen", ScriptStyle.ZHEN.glyphKind)
        assertEquals("cao", ScriptStyle.CAO.glyphKind)
        assertEquals("真书", ScriptStyle.ZHEN.bookLabel)
        assertEquals("草书", ScriptStyle.CAO.bookLabel)
    }

    @Test
    fun corpusFileHasTheFullPracticeText() {
        val text = File("src/main/assets/corpus.json").readText()
        val indexes = Regex(""""index": (\d+)""").findAll(text).map { it.groupValues[1].toInt() }.toList()
        assertEquals(1000, indexes.size)
        assertEquals((0 until 1000).toList(), indexes)
        assertEquals(1000, Regex(""""available": true""").findAll(text).count())
        assertEquals(0, Regex(""""available": false""").findAll(text).count())
        val path = Regex(""""path": "([^"]+)"""").find(text)!!.groupValues[1]
        assertTrue(SvgPaths.parse(path).isNotEmpty())
        assertTrue(text.contains("草书底帖为小川本墨迹切图；笔顺仍是示意图。"))
    }

    @Test
    fun ogawaPagesCoverOneThousandIndexes() {
        fun range(page: Int): IntRange = when (page) {
            2 -> 0 until 10
            52 -> 990 until 1000
            else -> {
                val start = 10 + 20 * (page - 3)
                start until (start + 20)
            }
        }
        assertEquals(0 until 10, range(2))
        assertEquals(10 until 30, range(3))
        assertEquals(970 until 990, range(51))
        assertEquals(990 until 1000, range(52))
        val covered = (2..52).flatMap { range(it).toList() }
        assertEquals((0 until 1000).toList(), covered)
    }

    @Test
    fun inkFallbacksPointAtRubbingGlyphs() {
        val text = File("src/main/assets/corpus.json").readText()
        val entry = Regex(""""inkFallback": "([^"]+)",\s*"inkFallbackKinds": \[([^\]]*)\]""")
        var patched = 0
        for (match in entry.findAll(text)) {
            val stem = match.groupValues[1]
            for (kind in Regex(""""(\w+)"""").findAll(match.groupValues[2])) {
                val file = File("src/main/assets/${stem}_${kind.groupValues[1]}.webp")
                assertTrue("missing $file", file.isFile)
                patched++
            }
        }
        assertEquals(11, patched)
    }

    @Test
    fun allCharactersHaveOgawaInkCrops() {
        val text = File("src/main/assets/corpus.json").readText()
        val dir = File("src/main/assets/glyphs/ogawa")
        assertEquals(2000, dir.list().orEmpty().size)
        assertEquals(0, Regex(""""ink": null""").findAll(text).count())
        for (index in 0 until 1000) {
            val stem = "glyphs/ogawa/%03d".format(index)
            assertTrue("missing ink stem $stem", text.contains(""""ink": "$stem""""))
            assertTrue(File(dir, "%03d_zhen.webp".format(index)).isFile)
            assertTrue(File(dir, "%03d_cao.webp".format(index)).isFile)
        }
    }
}
