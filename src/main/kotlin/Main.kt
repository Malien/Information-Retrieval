import dict.*
import dict.spimi.SPIMIFile
import dict.spimi.SPIMIMapper
import dict.spimi.reduce
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import parser.*
import util.*
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

fun Double.round(digits: Int = 0) = (10.0.pow(digits) * this).roundToInt() / 10.0.pow(digits)

val Long.megabytes get() = (this / 1024 / 1024.0).round(2)

fun getFiles(path: String, extension: String? = null): List<File> {
    val directory = File(path)
    if (!directory.exists() && !directory.isDirectory) return emptyList()
    val files = directory.list { dir, name ->
        val file = File(dir, name)
        file.exists() && file.isFile && (extension == null || file.extension == extension)
    } ?: emptyArray()
    return files.map { File(directory, it) }
}

val runtime: Runtime = Runtime.getRuntime()

inline fun <R> measureReturnTimeMillis(block: () -> R): Pair<R, Long> {
    val start = System.currentTimeMillis()
    val value = block()
    return value to System.currentTimeMillis() - start
}

//TODO: add parallel vs sequential indexing
//TODO: add execute param
//TODO: add find param
val boolArguments = hashSetOf(
    "i",
    "interactive",
    "s",
    "sequential",
    "d",
    "stat",
    "n",
    "verbose",
    "v",
    "disable-double-word",
    "disable-position",
    "map-reduce"
)
val numberArguments: HashMap<String, Double?> = hashMapOf(
    "p" to runtime.availableProcessors().toDouble()
)
val stringArguments =
    hashMapOf<String, String?>("execute" to null, "find" to null, "o" to null, "from" to null, "joker" to null)

val json = Json(JsonConfiguration.Stable)

fun startReplSession(context: EvalContext<Documents>, documents: DocumentRegistry, shouldNegate: Boolean = false) {
    println("Started interactive REPL session.")
    print(">>> ")
    var query = readLine()
    while (query != null && query != ".q") {
        try {
            val tokens = tokenize(query)
            val tree = parse(tokens)
            val res = context.eval(tree)
            if (res.negated) {
                if (shouldNegate) {
                    cross(documents.keySet, res)
                        .forEach { println(documents.path(it)) }
                } else {
                    println(
                        "Warning. Got negated result. Evaluation of such can take a lot of resources." +
                                "If you want to enable negation evaluation launch program with '-n' argument"
                    )
                }
            } else {
                res.forEach { println(documents.path(it)) }
            }
        } catch (e: InterpretationError) {
            println("Interpretation Error: ${e.message}")
        } catch (e: SyntaxError) {
            println("Syntax Error: ${e.message}")
        } catch (e: UnsupportedOperationException) {
            println("Unsupported operation: ${e.message}")
        }
        print(">>> ")
        query = readLine()
    }
}

@ExperimentalUnsignedTypes
fun main(args: Array<String>) {
    // Parse arguments
    val parsed = parseArgs(
        DefaultArguments(
            booleans = boolArguments,
            numbers = numberArguments,
            strings = stringArguments
        ), args
    )

    val shouldNegate = "n" in parsed.booleans
    val verbose = "v" in parsed.booleans || "verbose" in parsed.booleans
    val doubleWord = "disable-double-word" !in parsed.booleans
    val positioned = "disable-position" !in parsed.booleans
    val jokerType = parsed.strings["joker"]?.let { JokerDictType.fromArgument(it) }
    val mapReduce = "map-reduce" in parsed.booleans
    val interactive = "i" in parsed.booleans || "interactive" in parsed.booleans
    val sequential = "sequential" in parsed.booleans || "s" in parsed.booleans
    val stat = "stat" in parsed.booleans
    val from = parsed.strings["from"]
    val saveLocation = parsed.strings["o"]
    val loadLocation = parsed.strings["from"]
    val processingThreads = if (sequential) 1 else parsed.numbers["p"]!!.toInt()

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

    if (mapReduce) {
        // Map-reduce version of dict.
        if (verbose) {
            if (doubleWord) println("Double-word dict is disabled to support map-reduce indexing")
            if (positioned) println("Positioned dict is disabled to support map-reduce indexing") // TODO
            if (jokerType != null) println("Joker is disabled to support map-reduce indexing")
            if (from != null) println("Cannot load dict when map-reduce is enables") // TODO
            if (interactive) println("Interactive sessions are not supported in map-reduce mode") //TODO
        }
        // TODO: stat
        // TODO: save
        // TODO: Thread affinity

        data class IndexingResult(val file: SPIMIFile, val documents: DocumentRegistry, val timeTook: Long = 0)

        val (dict, documents, timeTook) = if (loadLocation != null) {
            if (files.isNotEmpty() && verbose) println("Specified dict load location. Indexing won't be done")
            val readBuffer = CharArray(4096)
            val registryReader = FileReader("$loadLocation/registry.json")
            val serializedRegistry = buildString {
                while (true) {
                    val charsRead = registryReader.read(readBuffer)
                    if (charsRead == -1) break
                    append(readBuffer, 0, charsRead)
                }
            }
            val documents = json.parse(DocumentRegistry.serializer(), serializedRegistry)
            IndexingResult(SPIMIFile("$loadLocation/dictionary.spimi"), documents)
        } else {
            val dictPath = saveLocation ?: "./chunks.sppkg"
            val dictDirFile = File(dictPath)
            if (!dictDirFile.exists()) dictDirFile.mkdirs()

            val mappers = Array(processingThreads) { SPIMIMapper() }
            val documents = DocumentRegistry()

            val splits = sequence {
                val splitSize = files.size.toDouble() / mappers.size
                for (splitno in mappers.indices) {
                    val start = (splitno * splitSize).toInt()
                    val end = ((splitno + 1) * splitSize).toInt()
                    yield(files.slice(start until end))
                }
            }

            val spimiFiles = splits.zip(mappers.asSequence()).mapIndexed { idx, (split, mapper) ->
                async {
                    for (file in split) {
                        val id: DocumentID
                        synchronized(documents) {
                            id = documents.register(file.path)
                        }
//                    if (verbose) println("${id.id} -> $file")
                        val br = BufferedReader(FileReader(file))
                        br.lineSequence()
                            .flatMap { it.split(Regex("\\W+")).asSequence() }
                            .filter { it.isNotBlank() }
                            .map { it.toLowerCase() }
                            .forEach { mapper.add(it, id) }
                        br.close()
                    }
                    if (verbose) println("Mapper #$idx: mapping done")
                    mapper.unify()
                    if (verbose) println("Mapper #$idx: unification done")
                    val file = mapper.dumpToDir(dictPath)
                    if (verbose) println("Mapper #$idx: dumped final chunk ${file.filename}")
                    file
                }
            }.constrainOnce()

            val (fileList, mapTime) = measureReturnTimeMillis {
                spimiFiles.toMutableList().toTypedArray().mapArray { it.get() }
            }
            if (verbose) println("Mapping done in $mapTime ms")

            val (reduced, reduceTime) = measureReturnTimeMillis {
                reduce(
                    fileList,
                    to = "$dictPath/dictionary.spimi",
                    externalDocuments = "$dictPath/documents.sstr"
                )
            }
            if (verbose) println("Reduction done in $reduceTime ms")

            for (file in fileList) file.delete()
            IndexingResult(reduced, documents, mapTime + reduceTime)
        }

        if (saveLocation != null) {
            val registry = json.stringify(DocumentRegistry.serializer(), documents)
            val out = FileWriter("$saveLocation/registry.json")
            out.write(registry)
            out.close()
        }

        if (stat) {
            val memoryUsage = (runtime.totalMemory() - runtime.freeMemory()).megabytes
            val unique = dict.entries
            val count = files.count()
            val mbs = size.megabytes
            println(
                "Indexed $count files ($mbs MB total). " +
                        "Took $timeTook ms to index. " +
                        "Unique words: $unique. " +
                        "Memory usage: $memoryUsage MB."
            )
        }

        if (interactive) {
            val context = EvalContext(
                fromID = dict::find,
                unite = ::unite,
                cross = ::cross,
                negate = ::negate
            )
            startReplSession(context, documents, shouldNegate = shouldNegate)
        }

        dict.close()
        if (saveLocation == null) dict.delete()

    } else {
        // Legacy dict

        // Load dict
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
            val dict = json.parse(Dictionary.serializer(), string)
            if (verbose) {
                if (doubleWord != dict.doubleWord) {
                    println(
                        """"WARNING: Mismatch in read dict and arguments: 
                       |disable-double-word is set to ${!doubleWord} in args and to ${!dict.doubleWord} in read dictionary.
                       |May result in worse performance and/or worse accuracy.
                       |Changed dict type to respect current settings. This will introduce inconsistency with newly indexed data"""
                    )
                }
                if (positioned != dict.position) {
                    println(
                        """WARNING: Mismatch in read dict and arguments: 
                      |disable-position is set to ${!positioned} in args and to ${!dict.position} in read dictionary.
                      |May result in worse performance and/or worse accuracy.
                      |Changed dict type to respect current settings. This will introduce inconsistency with newly indexed data"""
                    )
                }
                if (jokerType != dict.jokerType) {
                    println(
                        """WARNING: Mismatch in read dict and arguments: 
                      |joker is set to $jokerType in args and to ${dict.jokerType} in read dictionary.
                      |Changed option to respect dictionary's settings"""
                    )
                }
            }
            dict.also {
                it.doubleWord = doubleWord
                it.position = positioned
            }
        } else Dictionary(
            doubleWord = doubleWord,
            position = positioned,
            jokerType = jokerType
        )

        // Indexing
        val syncTime = measureTimeMillis {
            for (file in files) {
                val id = dict.documents.register(file.path)
                if (verbose) println("${id.id} -> $file")
                val br = BufferedReader(FileReader(file))
                var prev: String? = null
                br.lineSequence()
                    .flatMap { it.split(Regex("\\W+")).asSequence() }
                    .filter { it.isNotBlank() }
                    .map { it.toLowerCase() }
                    .forEachIndexed { idx, word ->
                        dict.add(word, position = idx, from = id, prev = prev)
                        prev = word
                    }
                br.close()
            }
        }

        // Save
        if (saveLocation != null) {
            val out = FileWriter(saveLocation)
            val strData = json.stringify(Dictionary.serializer(), dict)
            out.write(strData)
            out.close()
        }

        // Stats
        if (stat) {
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
        if (interactive) {
            val eval = EvalContext(
                fromID = dict::eval,
                unite = ::unite,
                cross = ::cross,
                negate = ::negate
            )
            startReplSession(eval, dict.documents, shouldNegate = shouldNegate)
        }
    }

}
