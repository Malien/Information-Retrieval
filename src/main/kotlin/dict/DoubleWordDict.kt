package dict

import TreeMapArraySerializer
import kotlinx.serialization.Serializable
import util.cross
import util.keySet
import util.unite
import java.util.*

@Serializable
class DoubleWordDict {
    @Serializable(with = TreeMapArraySerializer::class)
    private val entries =
        TreeMap<String, @Serializable(with = TreeMapArraySerializer::class) TreeMap<String, DictionaryEntry>>()

    private var _unique = 0
    val uniqueWords get() = _unique

    fun add(first: String, second: String, from: DocumentID) {
        val firstResolution = entries.getOrPut(first) {
            _unique++
            TreeMap()
        }
        val entry = firstResolution.getOrPut(second) { DictionaryEntry() }
        val count = entry.counts[from]
        if (count != null) entry.counts[from] = count + 1
        else entry.counts[from] = 1
    }

    fun get(word: String): Documents? =
        entries[word]?.values?.asSequence()?.map { it.counts.keys.iterator().keySet }?.reduce(::unite)

    fun get(first: String, second: String): Documents? =
        entries[first]?.get(second)?.counts?.keys?.iterator()?.keySet

    fun get(vararg words: String): Documents? {
        var set: Documents? = null
        for ((first, second) in words.asSequence().zipWithNext()) {
            set = if (set == null) get(first, second)
            else {
                val n = get(first, second)
                if (n == null) return null
                else cross(set, n)
            }
        }
        return set
    }
}