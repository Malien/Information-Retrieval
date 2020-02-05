package dict

import kotlinx.serialization.Serializable
import util.TreeMapArraySerializer
import util.cross
import util.keySet
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

//TODO: add document lookup table
@Serializable
class SingleWordDict : Iterable<SingleWordDict.Companion.WordWithEntry> {
    @Serializable(with = TreeMapArraySerializer::class)
    private val entries: TreeMap<String, DictionaryEntry> = TreeMap()

    private var _unique = 0
    val uniqueWords: Int get() = _unique

    fun add(word: String, document: DocumentID) {
        val entry = entries.getOrPut(word) {
            _unique++
            DictionaryEntry()
        }
        val count = entry.counts[document]
        if (count != null) entry.counts[document] = count + 1
        else entry.counts[document] = 1
    }

    fun get(word: String): Documents =
        entries[word]?.counts?.keys?.iterator()?.keySet ?: emptyDocuments()

    fun get(vararg words: String): Documents {
        var set: Documents? = null
        for (word in words) {
            set = if (set == null) get(word)
            else cross(set, get(word))
        }
        return set ?: emptyDocuments()
    }

    override fun toString(): String {
        return "Dictionary(entries=$entries)"
    }

    override fun iterator() =
        entries.iterator().asSequence().map { (word, entry) -> WordWithEntry(word, entry) }.iterator()

    companion object {
        data class WordWithEntry(val word: String, val entry: DictionaryEntry)
   }

}

