package app.zhencao.qianwen.data

import android.content.Context
import app.zhencao.qianwen.model.CharacterEntry
import app.zhencao.qianwen.model.VerseNote
import org.json.JSONObject

const val VERSE_COUNT = 250
const val VERSE_SIZE = 4

class Corpus(
    val characters: List<CharacterEntry>,
    val notes: List<VerseNote>,
) {
    fun verse(group: Int): List<CharacterEntry> =
        characters.subList(group * VERSE_SIZE, group * VERSE_SIZE + VERSE_SIZE)

    fun groupText(group: Int): String = verse(group).joinToString("") { it.char }

    fun note(group: Int): VerseNote = notes[group]

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
                val fallbackKinds = item.optJSONArray("inkFallbackKinds")
                add(
                    CharacterEntry(
                        index = item.getInt("index"),
                        char = item.getString("char"),
                        group = item.getInt("group"),
                        ink = item.getString("ink"),
                        rubbing = item.getString("rubbing"),
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
        check(characters.size == VERSE_COUNT * VERSE_SIZE) { "字库应为 1000 字" }
        val verses = root.getJSONArray("verses")
        val notes = List(verses.length()) { i ->
            val item = verses.getJSONObject(i)
            VerseNote(simplified = item.getString("simplified"), meaning = item.getString("meaning"))
        }
        check(notes.size == VERSE_COUNT) { "释义应为 250 句" }
        return Corpus(characters, notes)
    }
}

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return getString(name)
}
