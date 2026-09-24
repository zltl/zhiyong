package app.zhencao.qianwen

import androidx.compose.ui.geometry.Offset
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.ui.GlyphTurn
import app.zhencao.qianwen.ui.glyphTurn
import app.zhencao.qianwen.ui.incomingIndex
import app.zhencao.qianwen.ui.incomingOffset
import app.zhencao.qianwen.ui.resistedSlide
import app.zhencao.qianwen.ui.settleTarget
import app.zhencao.qianwen.ui.slideResistance
import app.zhencao.qianwen.ui.unwindSlide
import app.zhencao.qianwen.model.GlyphBook
import app.zhencao.qianwen.model.ScriptStyle
import app.zhencao.qianwen.model.parseScriptStyle
import app.zhencao.qianwen.model.shiftLabel
import app.zhencao.qianwen.model.sizeLabel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QianwenLogicTest {
    @Test
    fun swipeUpOrLeftSelectsTheNextGlyph() {
        val threshold = 48f
        assertEquals(GlyphTurn.Next, glyphTurn(0f, -80f, threshold))
        assertEquals(GlyphTurn.Next, glyphTurn(-80f, 10f, threshold))
        assertEquals(GlyphTurn.Previous, glyphTurn(0f, 80f, threshold))
        assertEquals(GlyphTurn.Previous, glyphTurn(80f, -10f, threshold))
        assertEquals(null, glyphTurn(20f, -20f, threshold))
    }

    @Test
    fun slideKeepsTheIncomingPageOneViewportAway() {
        val width = 400f
        val height = 800f
        val right = resistedSlide(120f, 10f, index = 3, lastIndex = 10)
        assertEquals(Offset(120f, 0f), right)
        assertEquals(2, incomingIndex(3, right, 10))
        assertEquals(Offset(120f - width, 0f), incomingOffset(right, width, height))
        assertEquals(Offset(width, 0f), settleTarget(right, width, height))

        val left = resistedSlide(-80f, 10f, index = 3, lastIndex = 10)
        assertEquals(Offset(-80f, 0f), left)
        assertEquals(4, incomingIndex(3, left, 10))
        assertEquals(Offset(-80f + width, 0f), incomingOffset(left, width, height))
        assertEquals(Offset(-width, 0f), settleTarget(left, width, height))

        val up = resistedSlide(5f, -100f, index = 3, lastIndex = 10)
        assertEquals(Offset(0f, -100f), up)
        assertEquals(4, incomingIndex(3, up, 10))
        assertEquals(Offset(0f, -100f + height), incomingOffset(up, width, height))
        assertEquals(Offset(0f, -height), settleTarget(up, width, height))

        assertEquals(Offset(width, 0f), incomingOffset(Offset.Zero, width, height, forwardHint = true))
        assertEquals(Offset(-width, 0f), incomingOffset(Offset.Zero, width, height, forwardHint = false))
    }

    @Test
    fun slideResistsAtTheEndsWithoutCompounding() {
        val blocked = resistedSlide(-100f, 0f, index = 10, lastIndex = 10)
        assertEquals(-100f * slideResistance, blocked.x, 0.01f)
        assertEquals(0f, blocked.y, 0.01f)
        assertEquals(null, incomingIndex(10, blocked, 10))
        assertEquals(-100f, unwindSlide(blocked, 10, 10).x, 0.05f)

        val open = resistedSlide(100f, 0f, index = 3, lastIndex = 10)
        assertEquals(open, unwindSlide(open, 3, 10))
        assertEquals(null, incomingIndex(0, resistedSlide(0f, 80f, index = 0, lastIndex = 10), 10))
    }

    @Test
    fun shiftLabelNamesTheDirection() {
        assertTrue(shiftLabel(0.1f, -0.08f).contains("偏右"))
        assertTrue(shiftLabel(0.1f, -0.08f).contains("偏上"))
        assertEquals("左右接近，上下接近", shiftLabel(0.01f, -0.01f))
    }

    @Test
    fun sizeLabelNamesTheProportion() {
        assertEquals("偏宽 20%，高度接近", sizeLabel(1.2f, 1.0f))
        assertTrue(sizeLabel(1.0f, 0.8f).contains("偏矮"))
    }

    @Test
    fun scriptChoiceStaysUntilChanged() {
        assertEquals(null, parseScriptStyle(""))
        assertEquals(null, parseScriptStyle("INK"))
        assertEquals(ScriptStyle.ZHEN, parseScriptStyle("ZHEN"))
        assertEquals(ScriptStyle.CAO, parseScriptStyle("CAO"))
        assertEquals("真书", ScriptStyle.ZHEN.bookLabel)
        assertEquals("草书", ScriptStyle.CAO.bookLabel)
    }

    @Test
    fun fallbackReplacesOnlyTheListedKinds() {
        val entry = CharacterEntry(
            index = 500,
            char = "x",
            group = 125,
            ink = "glyphs/ogawa/500",
            inkFallback = "glyphs/guanzhong/500",
            inkFallbackKinds = setOf("cao"),
        )
        assertEquals("glyphs/guanzhong/500_cao.webp", entry.glyphAsset(ScriptStyle.CAO))
        assertEquals("glyphs/ogawa/500_zhen.webp", entry.glyphAsset(ScriptStyle.ZHEN))
        assertEquals("glyphs/ogawa/500_cao.webp", entry.glyphAsset(ScriptStyle.CAO, GlyphBook.OGAWA))
        assertEquals("glyphs/guanzhong/500_zhen.webp", entry.glyphAsset(ScriptStyle.ZHEN, GlyphBook.GUANZHONG))
        assertTrue(entry.usesFallback(ScriptStyle.CAO))
        assertFalse(entry.usesFallback(ScriptStyle.ZHEN))
        assertEquals(GlyphBook.GUANZHONG, entry.shownBook(ScriptStyle.CAO))
        assertEquals(GlyphBook.OGAWA, entry.shownBook(ScriptStyle.ZHEN))
    }

    @Test
    fun corpusFileHasTheFullPracticeText() {
        val text = File("src/main/assets/corpus.json").readText()
        val indexes = Regex(""""index": (\d+)""").findAll(text).map { it.groupValues[1].toInt() }.toList()
        assertEquals((0 until 1000).toList(), indexes)
        assertFalse(text.contains("caoStrokes"))
        assertFalse(text.contains("\"available\""))
    }

    @Test
    fun everyVerseHasSimplifiedTextAndMeaning() {
        val text = File("src/main/assets/corpus.json").readText()
        val verse = Regex(""""group": (\d+),\s*"simplified": "([^"]*)",\s*"meaning": "([^"]*)"""")
        val verses = verse.findAll(text).toList()
        assertEquals((0 until 250).toList(), verses.map { it.groupValues[1].toInt() })
        for (match in verses) {
            assertEquals(match.value, 4, match.groupValues[2].length)
            assertTrue(match.value, match.groupValues[3].isNotBlank())
        }
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
        for (index in 0 until 1000) {
            val stem = "glyphs/ogawa/%03d".format(index)
            assertTrue("missing ink stem $stem", text.contains(""""ink": "$stem""""))
            assertTrue(File(dir, "%03d_zhen.webp".format(index)).isFile)
            assertTrue(File(dir, "%03d_cao.webp".format(index)).isFile)
        }
    }

    @Test
    fun allCharactersHaveGuanzhongCrops() {
        val text = File("src/main/assets/corpus.json").readText()
        val dir = File("src/main/assets/glyphs/guanzhong")
        assertEquals(2000, dir.list().orEmpty().size)
        for (index in 0 until 1000) {
            val stem = "glyphs/guanzhong/%03d".format(index)
            assertTrue("missing rubbing stem $stem", text.contains(""""rubbing": "$stem""""))
            assertTrue(File(dir, "%03d_zhen.webp".format(index)).isFile)
            assertTrue(File(dir, "%03d_cao.webp".format(index)).isFile)
        }
    }
}
