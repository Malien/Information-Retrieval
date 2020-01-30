import kotlinx.serialization.*
import kotlinx.serialization.internal.IntDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import util.DefaultArguments
import util.parseArgs
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*
import kotlin.system.measureTimeMillis

//TODO: Return inline classes and make them serializable
@Serializable
data class DocumentID(val id: Int) : Comparable<DocumentID> {
    override fun compareTo(other: DocumentID) =
        id.compareTo(other.id)

    @Serializer(forClass = DocumentID::class)
    companion object : KSerializer<DocumentID> {
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

@Serializable
class Dictionary : Iterable<Dictionary.Companion.WordWithEntry> {
    @Serializable(with = TreeMapArraySerializer::class)
    private val entries = TreeMap<String, DictionaryEntry>()
    private var _total = 0
    private var _unique = 0

    val totalWords: Int get() = _total
    val uniqueWords: Int get() = _unique

    fun add(word: String, document: DocumentID) {
        _total++
        val entry = entries.getOrPut(word) {
            _unique++
            DictionaryEntry()
        }
        val count = entry.counts[document]
        if (count != null) entry.counts[document] = count + 1
        else entry.counts[document] = 1
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

fun getFiles(path: String, extension: String? = null): List<File> {
    val directory = File(path)
    if (!directory.exists() && !directory.isDirectory) return emptyList()
    val files = directory.list { dir, name ->
        val file = File(dir, name)
        file.exists() && file.isFile && (extension == null || file.extension == extension)
    } ?: emptyArray()
    return files.map { File(directory, it) }
}

val boolArguments = hashSetOf("i", "interactive", "s", "sequential", "d")
val stringArguments = hashMapOf<String, String?>("execute" to null, "find" to null, "o" to null, "from" to null)

fun main(args: Array<String>) {
    val parsed = parseArgs(
        DefaultArguments(
            booleans = boolArguments,
            strings = stringArguments
        ), args
    )

    val files = (
        if ("d" in parsed.booleans) {
            parsed.unspecified.asSequence()
                .flatMap { getFiles(it).asSequence() }
        } else parsed.unspecified.asSequence().map { File(it) }
    )
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
        for ((idx, file) in files.withIndex()) {
            println("$idx -> $file")
            val br = BufferedReader(FileReader(file))
            br.lineSequence()
                .flatMap { it.split(Regex("\\W+")).asSequence() }
                .filter { it.isNotBlank() }
                .map { it.toLowerCase() }
                .forEach { dict.add(it, DocumentID(idx)) }
            br.close()
        }
    }

    val out = FileWriter("out.json")
    val JSON = Json(JsonConfiguration.Stable)
    val strData = JSON.stringify(Dictionary.serializer(), dict)
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
    val diskDict = JSON.parse(Dictionary.serializer(), string)
    diskDict.forEach { println(it) }

    val runtime = Runtime.getRuntime()
    val memoryUsage = (runtime.totalMemory() - runtime.freeMemory()).megabytes
    val total = dict.totalWords
    val unique = dict.uniqueWords
    val count = files.count()
    val mbs = size.megabytes
    println(
        "Indexed $count files ($mbs MB total). " +
                "Took $syncTime ms to index. " +
                "Total words: $total, unique: $unique. " +
                "Memory usage: $memoryUsage MB."
    )
}
