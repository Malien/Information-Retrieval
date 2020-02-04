package dict

import TreeMapArraySerializer
import kotlinx.serialization.*
import util.KeySet
import java.util.*

typealias Documents = KeySet<DocumentID>

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

    private var _total = 0

    val totalWords: Int get() = _total
    val uniqueWords: Int
        get() =
            singleWordDict?.uniqueWords ?:
            //TODO: positioned uniqueness
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
        //TODO: Positioned
    }

    fun get(word: String): Documents =
        singleWordDict?.get(word) ?:
        //TODO: Positioned
        doubleWordDict?.get(word) ?: Documents(iterator {})

    fun get(first: String, second: String): Documents =
        doubleWordDict?.get(first, second) ?:
        //TODO: Positioned
        singleWordDict?.get(first, second) ?:
        throw UnsupportedOperationError("How did you manage to create dictionary with no dictionaries")

    fun get(vararg words: String): Documents =
        //TODO: Positioned
        doubleWordDict?.get(*words) ?:
        singleWordDict?.get(*words) ?:
        throw UnsupportedOperationError("How did you manage to create dictionary with no dictionaries")

    fun registerDocument(path: String): DocumentID {
        val id = DocumentID(documentCount++)
        _documents[id] = path
        return DocumentID(_documents.size - 1)
    }

    fun deregisterDocument(id: DocumentID) =
        if (id in _documents) {
            _documents.remove(id)
            true
        } else false

    fun documentPath(id: DocumentID) = _documents[id]

    val documents: Iterator<DocumentID> get() = _documents.keys.iterator()

    companion object {
        //TODO: version validation
        val __version = "0.3.0"
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
