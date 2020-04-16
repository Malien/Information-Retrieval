import dict.Document
import dict.DocumentID
import dict.DocumentRegistry
import dict.Documents
import dict.cluster.map
import dict.legacy.Dictionary
import dict.legacy.JokerDictType
import dict.spimi.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import parser.cfg.*
import util.*
import util.kotlinx.getFiles
import util.kotlinx.megabytes
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence
import kotlin.system.measureTimeMillis

/**
 * Retrieves the sequence of files from directory tree recursively
 * @param path path to a directory
 * @return sequence of files
 */
fun getFilesRecursively(path: String): Sequence<File> =
    Files.walk(Paths.get(path)).filter { Files.isRegularFile(it) }.map { it.toFile() }.asSequence()

/**
 * Deserialize object from a JSON file
 * @param path path to a JSON file
 * @param serializer a serializer used to deserialize file
 * @return deserialized object
 */
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

/**
 * Serializes object to JSON file
 * @param obj object to be serialized and saved
 * @param path path at which object is to be saved
 * @param serializer a serializer used to serialize object to JSON
 */
fun <T> toJSONFile(obj: T, path: String, serializer: KSerializer<T>) {
    val string = json.stringify(serializer, obj)
    val out = FileWriter(path)
    out.write(string)
    out.close()
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
    "cluster"
)
val numberArguments: HashMap<String, Double?> = hashMapOf(
    "p" to runtime.availableProcessors().toDouble()
)
val stringArguments =
    hashMapOf<String, String?>("execute" to null, "find" to null, "o" to null, "from" to null, "joker" to null)

val json = Json(JsonConfiguration.Stable)

enum class Mode { CLUSTER, MAP_REDUCE, LEGACY }

/**
 * Main application entrypoint. Processed commandline arguments, setups dicts, links everything together
 */
@ExperimentalUnsignedTypes
fun main(args: Array<String>) {
    // Parse arguments
    val parsed = parseArgs(
        args,
        booleans = boolArguments,
        numbers = numberArguments,
        strings = stringArguments
    )

    // Store argument parsing results in variables for better convenience
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
    val cluster = "cluster" in parsed.booleans

    /**
     * Starts an interactive REPL (Read Eval Print Loop) session to evaluate user specified queries.
     * Blocks execution until user types ".q" to the prompt
     * Captures shouldNegate from closure, which specifies whether boolean expression that results in negation
     * should be evaluated. If set to false, user would get warning, otherwise negation will proceed
     * @param context an evaluation context which is used to process request instructions.
     *                Such as retrieving documents from string, unifying, crossing and negating boolean expressions
     *                with those documents
     * @param documents a document registry used to correlate document ids
     *                  to their stats (in this case the stat is the path to a file)
     */
    fun startReplSession(context: EvalContext<Documents>, documents: DocumentRegistry) {
        println("Started interactive REPL session.")
        print(">>> ")
        val cross = defaultCross<DocumentID>()
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

    fun startRankedReplSession(context: EvalContext<RankedDocuments>, documents: DocumentRegistry) {
        println("Started interactive REPL session.")
        print(">>> ")
        var input = readLine()
        while (input != null && input != ".q") {
            try {
                val tokens = tokenize(input)
                val tree = parse(tokens)
                val res = context.eval(tree)
                if (res.negated) {
                    if (shouldNegate) {
                        TODO("Negation for ranked search is not implemented")
                    } else {
                        println(
                            "Warning. Got negated result. Evaluation of such can take a lot of resources." +
                                    "If you want to enable negation evaluation launch program with '-n' argument"
                        )
                    }
                } else {
                    res.sortedWith(RankedDocument.rankComparator)
                        .forEach { println("${documents.path(it.doc)} -- ${it.rating}") }
                }
            } catch (e: InterpretationError) {
                println("Interpretation Error: ${e.message}")
            } catch (e: SyntaxError) {
                println("Syntax Error: ${e.message}")
            } catch (e: UnsupportedOperationException) {
                println("Unsupported operation: ${e.message}")
            }
            print(">>> ")
            input = readLine()
        }
    }

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
    val files = (fromDirs + fromFiles).map { Document(it) }.toList()

    val size = files.asSequence()
        .map { it.file.length() }
        .sum()

    when(if (cluster) Mode.CLUSTER else if (mapReduce) Mode.MAP_REDUCE else Mode.LEGACY) {
        Mode.CLUSTER -> {
            val documents = DocumentRegistry()
            val collection = map(files, documents)

            if (interactive) {
                val uniteFunc = RankedDocument.Companion::unite
                val context = EvalContext(
                    fromID = collection::find,
                    unite = uniteFactory(uniteFunc, uniteFunc),
                    cross = uniteFactory(uniteFunc, uniteFunc),
                    negate = ::negate
                )
                startRankedReplSession(context, documents)
            }
        }
        Mode.MAP_REDUCE -> {
            // Map-reduce version of dict.
            if (verbose) {
                if (doubleWord) println("Double-word dict is disabled to support map-reduce indexing")
                if (positioned) println("Positioned dict is disabled to support map-reduce indexing") // TODO
                if (jokerType != null) println("Joker is disabled to support map-reduce indexing")
                if (files.isNotEmpty() && from != null)
                    println("Cannot extend already existing dictionary. Loading $from without changes")
            }

            data class IndexingResult(val file: SPIMIDict, val documents: DocumentRegistry)

            val (dict, documents) = if (from != null) {
                // Loading dict from disk
                if (files.isNotEmpty() && verbose) println("Specified dict load location. Indexing won't be done")
                val manifest = fromJSONFile("$from/manifest.json", Manifest.serializer())
                val registry = fromJSONFile("$from/registry.json", DocumentRegistry.serializer())
                IndexingResult(manifest.openDict(), registry)
            } else {
                // Indexing dict to a disk
                val dictPath = saveLocation ?: "./chunks.sppkg"
                val dictDirFile = File(dictPath)
                if (!dictDirFile.exists()) dictDirFile.mkdirs()
                val documents = DocumentRegistry()

                val delimiters = genDelimiters(processingThreads)

                if (verbose) println("\nMapping:")
                val mappingFunction = if (verbose) ::verboseMultiMap else ::multiMap
                val spimiFiles = mappingFunction(
                    files,
                    dictDirFile,
                    documents,
                    processingThreads,
                    delimiters
                )

                if (verbose) println("\nReducing:")
                // Reduce step
                val reduceFunction = if (verbose) ::verboseMultiReduce else ::multiReduce
                val reduced = reduceFunction(spimiFiles, dictDirFile, delimiters)

                for (file in spimiFiles.flatten()) file.delete()
                IndexingResult(reduced, documents)
            }

            // Save registry to the disk
            if (saveLocation != null) {
                toJSONFile(documents, "$saveLocation/registry.json", DocumentRegistry.serializer())
                toJSONFile(dict.manifest, "$saveLocation/manifest.json", Manifest.serializer())
            }

            // Print stats
            if (stat) {
                val memoryUsage = (runtime.totalMemory() - runtime.freeMemory()).megabytes
                val unique = dict.count
                val count = files.count()
                val mbs = size.megabytes
                println(
                    "Indexed $count files ($mbs MB total). " +
                            "Unique words: $unique. " +
                            "Memory usage: $memoryUsage MB."
                )
            }

            // REPL
            if (interactive) {
                val uniteFunc = RankedDocument.Companion::unite
                val context = EvalContext(
                    fromID = dict::find,
                    unite = uniteFactory(uniteFunc, uniteFunc),
                    cross = uniteFactory(uniteFunc, uniteFunc),
                    negate = ::negate
                )
                startRankedReplSession(context, documents)
            }

            dict.close()
            // If no save location is specified dict is deleted from the disk
            if (from == null && saveLocation == null) dict.delete()

        }
        Mode.LEGACY -> {
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
                    val id = dict.documents.register(file)
                    if (verbose) println("${id.id} -> $file")
                    val br = BufferedReader(FileReader(file.file))
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
                    unite = defaultUnite(),
                    cross = defaultCross(),
                    negate = ::negate
                )
                startReplSession(eval, dict.documents)
            }
        }
    }
}
