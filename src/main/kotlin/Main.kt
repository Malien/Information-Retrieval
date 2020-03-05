import dict.*
import dict.spimi.ENTRIES_COUNT
import dict.spimi.SPIMIFile
import dict.spimi.SPIMIMapper
import dict.spimi.reduce
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import parser.*
import util.*
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.streams.asSequence
import kotlin.system.measureTimeMillis

fun Double.round(digits: Int = 0) = (10.0.pow(digits) * this).roundToInt() / 10.0.pow(digits)

val Long.megabytes get() = (this / 1024 / 1024.0).round(2)

fun getFiles(path: String, extension: String? = null): Sequence<File> {
    val directory = File(path)
    if (!directory.exists() && !directory.isDirectory) return emptySequence()
    val files = directory.list { dir, name ->
        val file = File(dir, name)
        file.exists() && file.isFile && (extension == null || file.extension == extension)
    } ?: emptyArray()
    return files.asSequence().map { File(directory, it) }
}

fun getFilesRecursively(path: String): Sequence<File> =
    Files.walk(Paths.get(path)).filter{ Files.isRegularFile(it) }.map { it.toFile() }.asSequence()

inline fun <R> measureReturnTimeMillis(block: () -> R): Pair<R, Long> {
    val start = System.currentTimeMillis()
    val value = block()
    return value to System.currentTimeMillis() - start
}

fun <T> fromJSONFile(path: String, serializer: KSerializer<T>): T {
    val readBuffer = CharArray(4096)
    val reader = FileReader(path)
    val serializedObject = buildString {
        while (true) {
            val charsRead = reader.read(readBuffer)
            if (charsRead == -1) break
            append(readBuffer, 0, charsRead)
        }
    }
    return json.parse(serializer, serializedObject)
}

val runtime: Runtime = Runtime.getRuntime()

//TODO: add execute param
//TODO: add find param
val boolArguments = hashSetOf(
    "i",
    "interactive",
    "s",
    "sequential",
    "stat",
    "n",
    "verbose",
    "v",
    "disable-double-word",
    "disable-position",
    "map-reduce",
    "r",
    "pretty-print"
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
    val processingThreads = if (sequential) 1 else parsed.numbers["p"]!!.toInt()
    val recursive = "r" in parsed.booleans
    val prettyPrint = "pretty-print" in parsed.booleans

    val console = if (prettyPrint) FancyConsole(System.out) else PlainConsole(System.out)

    // List of files to index
    val filesSequence = parsed.unspecified.asSequence()
        .map { File(it) }
        .filter {
            if (!it.exists()) {
                System.err.println("$it does not exist")
                false
            } else true
        }
    val fromDirs = filesSequence
        .filter { it.isDirectory }
        .map { it.path }
        .flatMap { if (recursive) getFilesRecursively(it) else getFiles(it) }
    val fromFiles = filesSequence.filter { it.isFile }
    val files = (fromDirs + fromFiles).toList()

    val size = files.asSequence()
        .map { it.length() }
        .sum()

    if (mapReduce) {
        // Map-reduce version of dict.
        if (verbose) {
            if (doubleWord) println("Double-word dict is disabled to support map-reduce indexing")
            if (positioned) println("Positioned dict is disabled to support map-reduce indexing") // TODO
            if (jokerType != null) println("Joker is disabled to support map-reduce indexing")
            if (from != null) println("Cannot load dict when map-reduce is enables")
            if (interactive) println("Interactive sessions are not supported in map-reduce mode")
        }

        data class IndexingResult(val file: SPIMIFile, val documents: DocumentRegistry, val timeTook: Long = 0)

        val (dict, documents, timeTook) = if (from != null) {
            if (files.isNotEmpty() && verbose) println("Specified dict load location. Indexing won't be done")
            IndexingResult(
                SPIMIFile("$from/dictionary.spimi"),
                fromJSONFile("$from/registry.json", DocumentRegistry.serializer())
            )
        } else {
            val dictPath = saveLocation ?: "./chunks.sppkg"
            val dictDirFile = File(dictPath)
            if (!dictDirFile.exists()) dictDirFile.mkdirs()
            val documents = DocumentRegistry()

            val splits = sequence {
                val splitSize = files.size.toDouble() / processingThreads
                for (splitno in 0 until processingThreads) {
                    val start = (splitno * splitSize).toInt()
                    val end = ((splitno + 1) * splitSize).toInt()
                    yield(files.slice(start until end))
                }
            }

            val filesMapped = AtomicInteger(0)
            val bytesMapped = AtomicLong(0)
            val pastBytesMapped = AtomicLong(0)
            val prevTimeStamp = AtomicLong(System.currentTimeMillis())

            val spimiFiles = splits.mapIndexed { idx, split ->
                async {
                    val mapper = SPIMIMapper()
                    val list = ArrayList<SPIMIFile>()
                    for (file in split) {
                        val id: DocumentID
                        synchronized(documents) {
                            id = documents.register(file.path)
                        }
                        val br = BufferedReader(FileReader(file))
                        br.lineSequence()
                            .flatMap { it.split(Regex("\\W+")).asSequence() }
                            .filter { it.isNotBlank() }
                            .map { it.toLowerCase() }
                            .forEach {
                                val hasMoreSpace = mapper.add(it, id)
                                if (!hasMoreSpace) {
                                    if (verbose) console.println("Mapper #$idx: done mapping chunk of $ENTRIES_COUNT elements")
                                    mapper.unify()
                                    if (verbose) console.println("Mapper #$idx: unified chunk down to ${mapper.size}")
                                    val dumpFile = mapper.dumpToDir(dictPath)
                                    if (verbose) console.println("Mapper #$idx: dumped chunk ${dumpFile.filename}")
                                    list.add(dumpFile)
                                    mapper.clear()
                                }
                            }
                        br.close()
                        val currentlyMappedFiles = filesMapped.incrementAndGet()
                        val currentlyMappedBytes = bytesMapped.addAndGet(file.length())
                        if (verbose && currentlyMappedFiles % 50 == 0) {
                            val percentage = ((currentlyMappedFiles.toDouble() / files.size) * 100).round(digits = 2)
                            val currentTime = System.currentTimeMillis()
                            val timeDelta = currentTime - prevTimeStamp.getAndSet(currentTime)
                            val byteDelta = currentlyMappedBytes - pastBytesMapped.getAndSet(currentlyMappedBytes)
                            val indexingSpeed = (byteDelta.megabytes / (timeDelta * 1_000_000)).round(digits = 2)
                            console.statusLine = "Mapped $currentlyMappedFiles files out of ${files.size} ($percentage%)." +
                                    "Indexed ${currentlyMappedBytes.megabytes}Mb ($indexingSpeed Mb/s)"
                        }
                    }
                    if (verbose) console.println("Mapper #$idx: final mapping done")
                    mapper.unify()
                    if (verbose) console.println("Mapper #$idx: final unification done")
                    val file = mapper.dumpToDir(dictPath)
                    if (verbose) console.println("Mapper #$idx: dumped final chunk ${file.filename}")
                    list.add(file)
                    list
                }
            }.constrainOnce()

            val (fileList, mapTime) = measureReturnTimeMillis {
                spimiFiles.toMutableList().flatMap { it.get() }.toTypedArray()
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
        if (from == null && saveLocation == null) dict.delete()

    } else {
        // Legacy dict

        // Load dict
        val dict = if (from != null) {
            val dict = fromJSONFile(from, Dictionary.serializer())
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
