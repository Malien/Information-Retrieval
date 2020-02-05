package dict

import kotlinx.serialization.*
import util.KeySet
import util.TreeMapArraySerializer
import java.util.*

typealias Documents = KeySet<DocumentID>
fun emptyDocuments() = Documents(iterator {})

data class SpacedWord(val word: String, val spaced: Int)

@Serializable
class Dictionary(
    val singleWord: Boolean = true,
    val doubleWord: Boolean = true,
    val position: Boolean = true
) {

    init {
        if (!singleWord && !doubleWord && !position) {
            throw NoDictionarySpecifiedError("Expected to enable at least one dictionary")
        }
    }

    val singleWordDict: SingleWordDict? = if (singleWord) SingleWordDict() else null
    val doubleWordDict: DoubleWordDict? = if (doubleWord) DoubleWordDict() else null
    val positionedDict: PositionedDict? = if (position)   PositionedDict() else null

    private var _total = 0

    val totalWords: Int get() = _total
    val uniqueWords: Int
        get() =
            singleWordDict?.uniqueWords ?:
            positionedDict?.uniqueWords ?:
            doubleWordDict?.uniqueWords ?:
            throw UnsupportedOperationError("How did you manage to create dictionary with no dictionaries")

    @Serializable(with = TreeMapArraySerializer::class)
    private val _documents: TreeMap<DocumentID, String> = TreeMap()
    private var documentCount = 0

    fun add(word: String, from: DocumentID) {
        _total++
        singleWordDict?.add(word, from)
    }

    fun add(first: String, second: String, from: DocumentID) =
        doubleWordDict?.add(first, second, from)

    fun add(word: String, position: Int, from: DocumentID) {
        _total++
        singleWordDict?.add(word, from)
        positionedDict?.add(word, position, from)
    }

    fun get(word: String): Documents =
        singleWordDict?.get(word) ?:
        positionedDict?.get(word) ?:
        doubleWordDict?.get(word) ?:
        throw UnsupportedOperationError("How did you manage to create dictionary with no dictionaries")

    fun get(first: String, second: String): Documents =
        doubleWordDict?.get(first, second) ?:
        positionedDict?.get(first, second) ?:
        singleWordDict?.get(first, second) ?:
        throw UnsupportedOperationError("How did you manage to create dictionary with no dictionaries")

    fun get(vararg words: String): Documents =
        positionedDict?.get(*words) ?:
        doubleWordDict?.get(*words) ?:
        singleWordDict?.get(*words) ?:
        throw UnsupportedOperationError("How did you manage to create dictionary with no dictionaries")

    fun get(vararg spacedWord: SpacedWord): Documents =
        positionedDict?.get(*spacedWord) ?:
        throw UnsupportedOperationError("Spaced search requires positioned dictionary to be enabled " +
                "(remove disable-position flag)")

    @Transient
    val nearRegex = Regex("/\\d+")
    fun eval(query: String): Documents {
        val words = query.split(Regex(" +")).filter { it.isNotBlank() }
        return when {
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

    companion object {
        //TODO: version validation
        const val _VERSION = "0.3.0"
    }
}

//TODO: Return inline classes and make them serializable
@Serializable
data class DictionaryEntry(val counts: TreeMap<DocumentID, Int> = TreeMap()) {
    override fun toString() =
        buildString {
            append("DictionaryEntry(counts=[")
            for ((document, count) in counts) {
                append(document.id)
                append(':')
                append(count)
                append(", ")
            }
            append("])")
        }

    @Serializer(forClass = DictionaryEntry::class)
    companion object : KSerializer<DictionaryEntry> {
        private val serializer = TreeMapArraySerializer(DocumentID.serializer(), Int.serializer())
        override val descriptor: SerialDescriptor = serializer.descriptor
        override fun deserialize(decoder: Decoder) =
            DictionaryEntry(decoder.decodeSerializableValue(serializer))

        override fun serialize(encoder: Encoder, obj: DictionaryEntry) {
            encoder.encodeSerializableValue(serializer, obj.counts)
        }
    }
}
