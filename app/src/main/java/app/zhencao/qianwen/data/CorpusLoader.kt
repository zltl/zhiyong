package app.zhencao.qianwen.data

import android.content.Context
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.GlyphStroke
import app.zhencao.qianwen.model.StrokeKind
import org.json.JSONObject

class Corpus(
    val characters: List<CharacterEntry>,
) {
    val available: List<CharacterEntry> = characters.filter { it.available }

    fun groupText(group: Int): String {
        return characters.filter { it.group == group }.joinToString("") { it.char }
    }

    operator fun get(index: Int): CharacterEntry = characters[index]
}

object CorpusLoader {
    fun load(context: Context): Corpus {
        val text = context.assets.open("corpus.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val array = root.getJSONArray("characters")
        val characters = buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val strokes = item.getJSONArray("caoStrokes")
                val fallbackKinds = item.optJSONArray("inkFallbackKinds")
                add(
                    CharacterEntry(
                        index = item.getInt("index"),
                        char = item.getString("char"),
                        group = item.getInt("group"),
                        slot = item.getInt("slot"),
                        available = item.getBoolean("available"),
                        ink = item.optStringOrNull("ink"),
                        rubbing = item.optStringOrNull("rubbing"),
                        caoStrokes = buildList {
                            for (s in 0 until strokes.length()) {
                                val stroke = strokes.getJSONObject(s)
                                add(
                                    GlyphStroke(
                                        order = stroke.getInt("order"),
                                        kind = if (stroke.getString("kind") == "silk") {
                                            StrokeKind.SILK
                                        } else {
                                            StrokeKind.SOLID
                                        },
                                        path = stroke.getString("path"),
                                    ),
                                )
                            }
                        },
                        inkFallback = item.optStringOrNull("inkFallback"),
                        inkFallbackKinds = buildSet {
                            if (fallbackKinds != null) {
                                for (k in 0 until fallbackKinds.length()) add(fallbackKinds.getString(k))
                            }
                        },
                    ),
                )
            }
        }
        check(characters.size == 1000) { "字库应为 1000 字" }
        return Corpus(characters)
    }
}

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return getString(name)
}
