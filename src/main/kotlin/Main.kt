import dict.Dictionary
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
    "disable-single-word",
    "disable-double-word",
    "disable-position"
)
val stringArguments = hashMapOf<String, String?>("execute" to null, "find" to null, "o" to null, "from" to null)

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
    val singleWord = "disable-single-word" !in parsed.booleans
    val doubleWord = "disable-double-word" !in parsed.booleans
    val positioned = "disable-position" !in parsed.booleans

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
        val dict = json.parse(Dictionary.serializer(), string)
        if (verbose) {
            if (singleWord != dict.singleWord) {
                println("Mismatch in read dict and arguments: " +
                        "disable-single-word is set to ${!singleWord} in args " +
                        "and to ${!dict.singleWord} in read dictionary. " +
                        "Falling back to ${!dict.singleWord}, and ignoring commandline argument")
            }
            if (doubleWord != dict.doubleWord) {
                println("Mismatch in read dict and arguments: " +
                        "disable-double-word is set to ${!doubleWord} in args " +
                        "and to ${!dict.doubleWord} in read dictionary. " +
                        "Falling back to ${!dict.doubleWord}, and ignoring commandline argument")
            }
            if (positioned != dict.position) {
                println("Mismatch in read dict and arguments: " +
                        "disable-position is set to ${!positioned} in args " +
                        "and to ${!dict.position} in read dictionary. " +
                        "Falling back to ${!dict.position}, and ignoring commandline argument")
            }
        }
        dict
    } else Dictionary(
        singleWord = singleWord,
        doubleWord = doubleWord,
        position = positioned
    )

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
            var prev: String? = null
            br.lineSequence()
                .flatMap { it.split(Regex("\\W+")).asSequence() }
                .filter  { it.isNotBlank() }
                .map     { it.toLowerCase() }
                .forEachIndexed { idx, word ->
                    dict.add(word, idx, id)
                    if (prev != null) {
                        dict.add(prev!!, word, id)
                    }
                    prev = word
                }
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
        val eval = EvalContext (
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
                        cross(
                            KeySet(dict.documents),
                            res
                        ).forEach { println(dict.documentPath(it)) }
                    } else {
                        println(
                            "Warning. Got negated result. Evaluation of such can take a lot of resources." +
                                    "If you want to enable negation evaluation launch program with '-n' argument"
                        )
                    }
                } else {
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
