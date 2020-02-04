package dict

import TreeMapArraySerializer
import kotlinx.serialization.*
import java.util.*

@Serializable
class Dictionary (
    val singleWord: Boolean = true,
    val doubleWord: Boolean = true,
    val position: Boolean = true
) {
    val singleWordDict: SingleWordDict? = if (singleWord) SingleWordDict() else null

    private var _total = 0

    val totalWords: Int get() = _total
    val uniqueWords: Int get() = singleWordDict?.uniqueWords ?:
        //TODO: positioned uniqueness
        throw UnsupportedOperation("To get unique word count enable single-word or position dictionaries")

    @Serializable(with = TreeMapArraySerializer::class)
    private val _documents: TreeMap<DocumentID, String> = TreeMap()
    private var documentCount = 0

    fun add(word: String, from: DocumentID) {
        _total++
        singleWordDict?.add(word, from)
    }

    fun get(word: String): DictionaryEntry =
        singleWordDict?.get(word) ?: DictionaryEntry()

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
