package dict

import dict.joker.PrefixDict
import dict.joker.RelocationDict
import dict.joker.TriGramDict
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import util.*
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

typealias Documents = KeySet<DocumentID>

fun emptyDocuments() = Documents(iterator {})

data class SpacedWord(val word: String, val spaced: Int)

enum class JokerDictType {
    PrefixTree,
    TriGram,
    Relocation;

    companion object {
        class UnknownTypeError(msg: String) : Error(msg)

        fun fromArgument(argument: String): JokerDictType = when (argument) {
            "prefix-tree" -> PrefixTree
            "trigram" -> TriGram
            "relocation" -> Relocation
            else -> throw UnknownTypeError("Unknown joker dict type $argument")
        }
    }
}
//
//@Serializable
//data class TriGram(val first: Char, val second: Char, val third: Char): Comparable<TriGram> {
//    constructor(from: String) : this(from[0], from[1], from[2])
//
//    override fun compareTo(other: TriGram): Int {
//        var cmp = first.compareTo(other.first)
//        if (cmp == 0) cmp = second.compareTo(other.second)
//        if (cmp == 0) cmp = third.compareTo(other.third)
//        return cmp
//    }
//
//    operator fun get(idx: Int): Char = when (idx) {
//        0 -> first
//        1 -> second
//        2 -> third
//        else -> throw IndexOutOfBoundsException("Trigrams can only be indexed from 0 to 2")
//    }
//}

@Serializable
class Dictionary(
    var doubleWord: Boolean = true,
    var position: Boolean = true,
    val jokerType: JokerDictType? = null
) {
    @Serializable(with = TreeMapArraySerializer::class)
    private val entries = TreeMap<String, DictionaryEntry>()

    private val prefix = if (jokerType == JokerDictType.PrefixTree) PrefixDict() else null

    @Serializable(with = TreeMapArraySerializer::class)
    private val trigram = if (jokerType == JokerDictType.TriGram) TriGramDict() else null

    private val relocation = if (jokerType == JokerDictType.Relocation) RelocationDict() else null

    var totalWords: Int = 0
        private set
    val uniqueWords: Int get() = entries.size

    @Serializable(with = TreeMapArraySerializer::class)
    private val _documents: TreeMap<DocumentID, String> = TreeMap()
    private var documentCount = 0

    /**
     * Positions should be inserted only in sorted order
     */
    fun add(word: String, from: DocumentID, position: Int = -1, prev: String? = null) {
        totalWords++
        val entry = entries.getOrPut(word) { DictionaryEntry() }

        if (this.doubleWord && position != -1) {
            val positions = entry.documents.getOrPut(from) { ArrayList() }
            if (positions == null) {
                entry.documents[from] = arrayListOf(position)
            } else positions.add(position)
        } else entry.documents.putIfAbsent(from, null)

        if (this.position && prev != null) {
            if (entry.previous == null) entry.previous = TreeMap()
            val docs = entry.previous!!.getOrPut(prev) { PriorityQueue() }
            docs.add(from)
        }

        when (jokerType) {
            JokerDictType.PrefixTree -> prefix
            JokerDictType.TriGram -> trigram
            JokerDictType.Relocation -> relocation
            else -> null
        }?.add(word)
    }

    fun get(word: String): Documents =
        //TODO: temporary place here
        if ('*' in word) getStar(word).asSequence().map { getSingle(it)?.keySet ?: emptyDocuments() }.reduce(::unite)
        else getSingle(word) ?: emptyDocuments()

    fun get(first: String, second: String): Documents =
        (if (doubleWord) getDouble(first, second) else null)
            ?: (if (position) getPositioned(first, second) else null)
            ?: getSingle(first, second)
            ?: emptyDocuments()

    fun get(vararg words: String): Documents =
        (if (position) getPositioned(*words) else null)
            ?: (if (doubleWord) getDouble(*words) else null)
            ?: getSingle(*words)
            ?: emptyDocuments()

    fun get(vararg spacedWords: SpacedWord): Documents =
        if (position) getPositioned(*spacedWords) ?: emptyDocuments()
        else throw UnsupportedOperationException(
            "Spaced search requires positioned dictionary to be enabled (remove disable-position flag)"
        )

    private fun <T> containsWithin(left: Iterable<Int>, right: T, offset: Int)
            where T : List<Int>,
                  T : RandomAccess = containsWithin(left.iterator(), right, offset)

    private fun <T> containsWithin(left: Iterator<Int>, right: T, offset: Int): Boolean
            where T : List<Int>,
                  T : RandomAccess {
        if (right.isEmpty()) return false
        var idx = 0
        leftLoop@ for (leftElement in left) {
            while (idx < right.size) {
                val rightElement = right[idx] - offset
                when {
                    leftElement == rightElement -> return true
                    leftElement > rightElement -> idx++
                    leftElement < rightElement -> continue@leftLoop
                }
            }
        }
        return false
    }

    private fun getStar(word: String): Iterator<String> =
        prefix?.getStar(word)
            ?: trigram?.getStar(word)
            ?: relocation?.getStar(word)
            ?: if (jokerType == null)
                throw UnsupportedOperationException(
                    "Joker search requires joker dict to be provided." +
                            " See joker commandline argument"
                )
            else iterator {}

    private fun getPositioned(vararg words: String): Documents? {
        val wordEntries = words.map {
            entries[it]?.documents ?: return@getPositioned null
        }
        val crossed = wordEntries.asSequence()
            .map {
                it.filter { (_, value) -> value != null }.map { entry -> entry.key }
            }.map { it.keySet }
            .reduce(::cross)
        return Documents(iterator {
            documentLoop@ for (document in crossed) {
                for ((prevWordDocs, nextWordDocs) in wordEntries.zipWithNext()) {
                    val prevDoc = prevWordDocs[document]!!
                    val nextDoc = nextWordDocs[document]!!
                    if (!containsWithin(prevDoc, nextDoc, 1)) continue@documentLoop
                }
                yield(document)
            }
        })
    }

    private fun getPositioned(vararg spacedWords: SpacedWord): Documents? {
        val wordEntries = spacedWords.map { (word, space) ->
            (entries[word]?.documents ?: return@getPositioned null) to space
        }
        val crossed = wordEntries.asSequence()
            .map { it.first.keys.keySet }
            .reduce(::cross)
        return Documents(iterator {
            documentLoop@ for (document in crossed) {
                for ((prev, next) in wordEntries.zipWithNext()) {
                    val (prevWordDocs) = prev
                    val (nextWordDocs, spaced) = next
                    val prevDoc = prevWordDocs[document]!!
                    val nextDoc = nextWordDocs[document]!!
                    if (!containsWithin(prevDoc, nextDoc, spaced)) continue@documentLoop
                }
                yield(document)
            }
        })
    }

    private fun getDouble(first: String, second: String): Documents? =
        entries[second]?.previous?.get(first)?.keySet

    private fun getDouble(vararg words: String): Documents? =
        words.asSequence()
            .zipWithNext()
            .map { (first, second) -> getDouble(first, second) }
            .let { docs ->
                if (docs.any { it == null }) null
                else docs.filterNotNull().reduce(::cross)
            }

    private fun getSingle(word: String): Documents? =
        entries[word]?.documents?.keys?.keySet

    private fun getSingle(vararg words: String): Documents? =
        words.asSequence().map(::getSingle).let { docs ->
            if (docs.any { it == null }) null
            else docs.filterNotNull().reduce(::cross)
        }

    @Transient
    val nearRegex = Regex("/\\d+")

    fun eval(query: String): Documents {
        val words = query.split(Regex(" +")).filter { it.isNotBlank() }
        return eval(words)
    }

    private fun eval(words: List<String>): Documents = when {
        words.isEmpty() -> emptyDocuments()
        words.size == 1 -> get(words[0])
        words.size == 2 -> get(words[0], words[1])
        words.any { nearRegex.matches(it) } -> {
            val s = sequence<SpacedWord> {
                var space = 0
                for (word in words) {
                    space = if (nearRegex.matches(word)) {
                        word.drop(1).toInt()
                    } else {
                        yield(SpacedWord(word, space + 1))
                        0
                    }
                }
            }
            get(*s.toMutableList().toTypedArray())
        }
        else -> get(*words.toTypedArray())
    }

    fun registerDocument(path: String): DocumentID =
        DocumentID(documentCount++).also { _documents[it] = path }

    fun deregisterDocument(id: DocumentID) =
        if (id in _documents) {
            _documents.remove(id)
            true
        } else false

    fun documentPath(id: DocumentID) = _documents[id]

    val documents: Iterator<DocumentID> get() = _documents.keys.iterator()

}

@Serializable
data class DictionaryEntry(
    @Serializable(with = TreeMapArraySerializer::class)
    val documents: TreeMap<DocumentID, ArrayList<Int>?> = TreeMap(),

    @Serializable(with = TreeMapArraySerializer::class)
    var previous: TreeMap<String, @Serializable(with = PriorityQueueSerializer::class) PriorityQueue<DocumentID>>? = null
)
