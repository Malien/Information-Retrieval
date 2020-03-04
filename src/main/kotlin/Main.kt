import dict.Dictionary
import dict.DocumentRegistry
import dict.JokerDictType
import dict.spimi.SPIMIFile
import dict.spimi.SPIMIMapper
import dict.spimi.SPIMIReducer
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
val stringArguments =
    hashMapOf<String, String?>("execute" to null, "find" to null, "o" to null, "from" to null, "joker" to null)

val json = Json(JsonConfiguration.Stable)

@ExperimentalUnsignedTypes
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
    val doubleWord = "disable-double-word" !in parsed.booleans
    val positioned = "disable-position" !in parsed.booleans
    val jokerType = parsed.strings["joker"]?.let { JokerDictType.fromArgument(it) }
    val mapReduce = "map-reduce" in parsed.booleans
    val interactive = "i" in parsed.booleans || "interactive" in parsed.booleans
    val stat = "stat" in parsed.booleans
    val from = parsed.strings["from"]
    val saveLocation = parsed.strings["o"]

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
            if (positioned) println("Double-word dict is disabled to support map-reduce indexing") // TODO
            if (jokerType != null) println("Joker is disabled to support map-reduce indexing")
            if (from != null) println("Cannot load dict when map-reduce is enables") // TODO
            if (interactive) println("Interactive sessions are not supported in map-reduce mode") //TODO
        }
        // TODO: stat
        // TODO: save
        // TODO: Thread affinity

        val mappers = Array(4) { SPIMIMapper() }
        val documents = DocumentRegistry()

        val splits = sequence {
            val splitSize = files.size.toDouble() / mappers.size
            for(splitno in mappers.indices) {
                val start = (splitno * splitSize).toInt()
                val end = ((splitno + 1) * splitSize).toInt()
                yield(files.slice(start until end))
            }
        }

        val spimiFiles = splits.zip(mappers.asSequence()).map { (split, mapper) ->
            for (file in split) {
                val id = documents.register(file.path)
                if (verbose) println("${id.id} -> $file")
                val br = BufferedReader(FileReader(file))
                br.lineSequence()
                    .flatMap { it.split(Regex("\\W+")).asSequence() }
                    .filter { it.isNotBlank() }
                    .map { it.toLowerCase() }
                    .forEach { mapper.add(it, id) }
                br.close()
            }
            mapper.unify()
            mapper.dumpToDir("./chunks")
        }.constrainOnce()

        val fileList = spimiFiles.toMutableList().toTypedArray()

        val reducer = SPIMIReducer(fileList)
        reducer.externalDocuments = "./chunks/reduced/documents"
        reducer.reduce("./chunks/reduced/dictionary")
        val registry = json.stringify(DocumentRegistry.serializer(), documents)
        val out = FileWriter("./chunks/reduced/registry.json")
        out.write(registry)
        out.close()
        for (file in fileList) file.close()

        val reduced = SPIMIFile(File("./chunks/reduced/dictionary"))
        println(reduced.getMulti(0).second.contentToString())
        println(reduced)

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
                      |joker is set to ${jokerType} in args and to ${dict.jokerType} in read dictionary.
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
        if (interactive) {
            val eval = EvalContext(
                fromID = dict::eval,
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
                            cross(dict.documents.keySet, res)
                                .forEach { println(dict.documents.path(it)) }
                        } else {
                            println(
                                "Warning. Got negated result. Evaluation of such can take a lot of resources." +
                                        "If you want to enable negation evaluation launch program with '-n' argument"
                            )
                        }
                    } else {
                        res.forEach { println(dict.documents.path(it)) }
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
    }

}
