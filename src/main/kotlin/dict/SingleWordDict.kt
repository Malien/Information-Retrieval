package dict

import TreeMapArraySerializer
import kotlinx.serialization.Serializable
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

    fun get(word: String): DictionaryEntry? {
        return entries[word]
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

