package app.zhencao.qianwen

import app.zhencao.qianwen.model.CharacterEntry
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
