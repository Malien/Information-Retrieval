package dict.legacy.joker

import kotlinx.serialization.Serializable
import util.KeySet
import util.cross
import util.keySet
import util.serialization.TreeMapArraySerializer
import util.serialization.TreeSetSerializer
import java.util.*

@Serializable
class TriGramDict : JokerDict {
    @Serializable(with = TreeMapArraySerializer::class)
    private val entries =
        TreeMap<String, @Serializable(with = TreeSetSerializer::class) TreeSet<String>>()

    override fun add(word: String) {
        for (trigram in toTriGrams(padded(word))) {
            val entry = entries.getOrPut(trigram) { TreeSet() }
            entry.add(word)
        }
    }

    fun get(closeTo: String): KeySet<String>? {
        val grams = toTriGrams(closeTo).asSequence().map { entries[it] }.toList()
        if (grams.any { it == null }) return null
        return grams.filterNotNull().map { it.iterator().keySet }.reduce(::cross)
    }

    override fun getRough(query: String): Iterator<String>? {
        val split = query.split('*')
        val chunks =
            split.filterIndexed { idx, s -> if (idx == 0 || idx == split.size - 1) s.length > 1 else s.length > 2 }
        return if (chunks.any()) {
            chunks.mapIndexed { index, s ->
                val word = if (index == 0) "$$s" else if (index == chunks.size - 1) "$s$" else s
                get(word) ?: return@getRough null
            }.reduce(::cross).iterator
        } else throw UnsupportedOperationException("Not enough characters to start joker search on TriGramDict")
    }

    companion object {
        fun padded(str: String) = "$$str$"

        fun toTriGrams(word: String): Iterator<String> =
            iterator {
                for (end in 3..word.length) {
                    yield(word.substring(end - 3, end))
                }
            }

    }
}