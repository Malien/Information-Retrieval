import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlinx.serialization.*
import kotlinx.serialization.internal.IntDescriptor
import kotlinx.serialization.json.Json

//TODO: Return inline classes and make them serializable
@Serializable
data class DocumentID(val id: Int) : Comparable<DocumentID> {
    override fun compareTo(other: DocumentID) =
        id.compareTo(other.id)

    @Serializer(forClass = DocumentID::class)
    companion object: KSerializer<DocumentID> {
        override val descriptor: SerialDescriptor =
            IntDescriptor.withName("id")
        override fun deserialize(decoder: Decoder) =
            DocumentID(decoder.decodeInt())
        override fun serialize(encoder: Encoder, obj: DocumentID) {
            encoder.encodeInt(obj.id)
        }
    }
}

//TODO: Return inline classes and make them serializable
@Serializable
data class DictionaryEntry(val counts: ArrayMap<DocumentID, Int> = ArrayMap()) {
    override fun toString() =
        buildString {
            append("DictionaryEntry(counts=[")
            for ((document, count) in counts) {
                append(document)
                append(':')
                append(count)
            }
            append("])")
        }

    @Serializer(forClass = DictionaryEntry::class)
    companion object: KSerializer<DictionaryEntry> {
        private val serializer = ArrayMap.serializer(DocumentID.serializer(), Int.serializer())
        override val descriptor: SerialDescriptor = serializer.descriptor
        override fun deserialize(decoder: Decoder) =
            DictionaryEntry(decoder.decodeSerializableValue(serializer))
        override fun serialize(encoder: Encoder, obj: DictionaryEntry) {
            encoder.encodeSerializableValue(serializer, obj.counts)
        }
    }
}

@Serializable
class Dictionary : Iterable<Dictionary.Companion.WordWithEntry> {
    private val entries = ArrayMap<String, DictionaryEntry>()
    private val requestQueue: ArrayBlockingQueue<InsertionRequest> by lazy {
        ArrayBlockingQueue<InsertionRequest>(1000)
    }
    private var _total = 0
    private var _unique = 0

    val totalWords: Int get() = _total
    val uniqueWords: Int get() = _unique

    fun add(word: String, document: DocumentID) {
        _total++
        val entry = entries.getOrSet(word) {
            _unique++
            DictionaryEntry()
        }
        entry.counts.mutateOrSet(document, { it + 1 }, { 0 })
    }

    override fun toString(): String {
        return "Dictionary(entries=$entries)"
    }

    override fun iterator() =
        entries.iterator().asSequence().map { (word, entry) -> WordWithEntry(word, entry) }.iterator()

    companion object {
        data class InsertionRequest(val word: String?, val from: DocumentID)
        data class WordWithEntry(val word: String, val entry: DictionaryEntry)
    }

}

val boolArguments = hashSetOf("i", "interactive", "s", "sequential")
val stringArguments = hashMapOf<String, String?>("execute" to null)

@UnstableDefault
@ExperimentalCoroutinesApi
fun main(args: Array<String>) {
    val parsed = Arguments(DefaultArguments(booleans = boolArguments, strings = stringArguments), args)

    val files = parsed.unspecified.asSequence()
        .map { File(it) }
        .filter {
            if (!it.exists()) {
                System.err.println("$it does not exist")
                false
            } else if (!it.isFile) {
                System.err.println("$it is not a file")
                false
            } else true
        }
        .toList()
    val size = files.asSequence()
        .map { it.length() }
        .sum()

    val dict = Dictionary()
    val syncTime = measureTimeMillis {
        for (file in files) {
            val br = BufferedReader(FileReader(file))
            br.lineSequence()
                .flatMap { it.split(Regex("\\W+")).asSequence() }
                .filter { it.isNotBlank() }
                .map { it.toLowerCase() }
                .forEach { dict.add(it, DocumentID(0)) }
            br.close()
        }
    }

    val out = FileWriter("out.json")
    val strData = Json.stringify(Dictionary.serializer(), dict)
    out.write(strData)
    out.close()

    val buffer = CharArray(4096)
    val input = FileReader("out.json")
    val string = buildString {
        while (true) {
            val charsRead = input.read(buffer)
            if (charsRead == -1) break
            append(buffer, 0, charsRead)
        }
    }
    val diskDict = Json.parse(Dictionary.serializer(), string)
    diskDict.forEach{ println(it) }

    val runtime = Runtime.getRuntime()
    val memoryUsage = (runtime.totalMemory() - runtime.freeMemory()).megabytes
    val total = dict.totalWords
    val unique = dict.uniqueWords
    val count = files.count()
    val mbs = size.megabytes
    println("Indexed $count files ($mbs MB total). " +
            "Took $syncTime ms to index. " +
            "Total words: $total, unique: $unique. " +
            "Memory usage: $memoryUsage MB.")
}
