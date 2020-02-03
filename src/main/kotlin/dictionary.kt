import kotlinx.serialization.*
import kotlinx.serialization.internal.IntDescriptor
import kotlinx.serialization.internal.SerialClassDescImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import parser.*
import util.*
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*
import kotlin.system.measureTimeMillis

//TODO: Return inline classes and make them serializable
@Serializable
data class DocumentID internal constructor(val id: Int) : Comparable<DocumentID> {
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

//TODO: add document lookup table
@Serializable(with = Dictionary.Companion.DictionarySerializer::class)
class Dictionary : Iterable<Dictionary.Companion.WordWithEntry> {
    private val entries: TreeMap<String, DictionaryEntry>
    private val _documents: TreeMap<DocumentID, String>
    private var documentCount = 0
    private var _total = 0
    private var _unique = 0

    val totalWords: Int get() = _total
    val uniqueWords: Int get() = _unique

    constructor() {
        this.entries = TreeMap()
        this._documents = TreeMap()
    }

    private constructor(
        entries: TreeMap<String, DictionaryEntry>,
        _total: Int,
        _unique: Int,
        documentCount: Int,
        _documents: TreeMap<DocumentID, String>
    ) {
        this.entries = entries
        this._total = _total
        this._unique = _unique
        this.documentCount = documentCount
        this._documents = _documents
    }

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
        private const val __version = "0.2.0"
        class VersionMismatchError(msg: String): Error(msg)

        @Serializer(forClass = Dictionary::class)
        class DictionarySerializer : KSerializer<Dictionary> {
            private val mapSerializer =
                TreeMapArraySerializer(String.serializer(), DictionaryEntry.serializer())
            private val docSerializer =
                TreeMapArraySerializer(DocumentID.serializer(), String.serializer())

            override val descriptor: SerialDescriptor = object: SerialClassDescImpl("Dictionary") {
                init {
                    addElement("__version")
                    addElement("_total")
                    addElement("_unique")
                    addElement("documentCount")
                    addElement("documents")
                    addElement("entries")
                }
            }

            override fun deserialize(decoder: Decoder): Dictionary {
                val struct = decoder.beginStructure(descriptor)
                var total: Int? = null
                var unique: Int? = null
                var count: Int? = null
                var documents: TreeMap<DocumentID, String>? = null
                var entries: TreeMap<String, DictionaryEntry>? = null
                loop@while (true) {
                    when (val i = struct.decodeElementIndex(descriptor)) {
                        CompositeDecoder.READ_DONE -> break@loop
                        0 -> {
                            val ver = struct.decodeStringElement(descriptor, i)
                            if (ver != __version) throw VersionMismatchError(
                                "Tried to read dictionary version $ver, but expected $__version."
                            )
                        }
                        1 -> total = struct.decodeIntElement(descriptor, i)
                        2 -> unique = struct.decodeIntElement(descriptor, i)
                        3 -> count = struct.decodeIntElement(descriptor, i)
                        4 -> documents = struct.decodeSerializableElement(
                            descriptor, i, docSerializer
                        )
                        5 -> entries = struct.decodeSerializableElement(
                            descriptor, i, mapSerializer)
                        else -> throw SerializationException("Unknown index $i")
                    }
                }
                struct.endStructure(descriptor)
                return Dictionary(entries!!, total!!, unique!!, count!!, documents!!)
            }

            override fun serialize(encoder: Encoder, obj: Dictionary) {
                val struct = encoder.beginStructure(descriptor)
                struct.encodeStringElement(descriptor, 0, __version)
                struct.encodeIntElement(descriptor, 1, obj._total)
                struct.encodeIntElement(descriptor, 2, obj._unique)
                struct.encodeSerializableElement(descriptor, 3,mapSerializer, obj.entries)
                struct.endStructure(descriptor)
            }
        }
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

//TODO: add parallel vs sequential indexing
//TODO: add execute param
val boolArguments = hashSetOf("i", "interactive", "s", "sequential", "d", "stat", "n", "verbose", "v")
val stringArguments
        = hashMapOf<String, String?>("execute" to null, "find" to null, "o" to null, "from" to null)

val json = Json(JsonConfiguration.Stable)

fun main(args: Array<String>) {
    // Parse arguments
    val parsed = parseArgs(
        DefaultArguments(
            booleans = boolArguments,
            strings = stringArguments
        ), args
    )

    val shouldNegate = "n" in parsed.booleans
    val verbose = "v" in parsed.booleans || "verbose" in parsed.booleans

    // Load dict
    val from = parsed.strings["from"]
    val dict = if (from != null) {
        val buffer = CharArray(4096)
        val input = FileReader(from)
        val string = buildString {
            while (true) {
                val charsRead = input.read(buffer)
                if (charsRead == -1) break
                append(buffer, 0, charsRead)
            }
        }
        json.parse(Dictionary.serializer(), string)
    } else Dictionary()

    // List of files to index
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

    // Indexing
    val syncTime = measureTimeMillis {
        for (file in files) {
            val id = dict.registerDocument(file.path)
            if (verbose) println("${id.id} -> $file")
            val br = BufferedReader(FileReader(file))
            br.lineSequence()
                .flatMap { it.split(Regex("\\W+")).asSequence() }
                .filter { it.isNotBlank() }
                .map { it.toLowerCase() }
                .forEach { dict.add(it, id) }
            br.close()
        }
    }

    // Save
    val saveLocation = parsed.strings["o"]
    if (saveLocation != null) {
        val out = FileWriter(saveLocation)
        val strData = json.stringify(Dictionary.serializer(), dict)
        out.write(strData)
        out.close()
    }

    // Stats
    if ("stat" in parsed.booleans) {
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

    // REPL
    if ("i" in parsed.booleans || "interactive" in parsed.booleans) {
        val eval = EvalContext<KeySet<DocumentID>>(
            fromID = {
                val entry = dict.get(it)
                if (entry != null) KeySet(entry.counts.keys.iterator())
                else KeySet(iterator {})
            },
            unite = ::unite,
            cross = ::cross,
            negate = ::negate
        )

        println("Started interactive REPL session.")
        print(">>> ")
        var query = readLine()
        while (query != null && query != ".q") {
            try {
                val tokens = tokenize(query)
                val tree = parse(tokens)
                val res = eval(tree)
                if (res.negated) {
                    if (shouldNegate) {
                        cross(
                            KeySet(dict.documents),
                            res
                        ).forEach { println(dict.documentPath(it)) }
                    } else {
                        println("Warning. Got negated result. Evaluation of such can take a lot of resources." +
                                "If you want to enable negation evaluation launch program with '-n' argument")
                    }
                } else  {
                    res.forEach { println(dict.documentPath(it)) }
                }
            } catch (e: InterpretationError) {
                println("Interpretation Error: ${e.message}")
            } catch (e: SyntaxError) {
                println("Syntax Error: ${e.message}")
            }
            print(">>> ")
            query = readLine()
        }
   }

}
