package dict.spimi

import util.Future
import util.async
import util.round
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * Transpose matrix(?) of values
 * @param input Array of Arrays of T
 * @return iterator of iterators on values in rotated order
 */
fun <T> rotate(input: Array<Array<T>>) = iterator {
    val segments = input.asSequence().map { it.size }.max() ?: 0
    for (i in 0 until segments) {
        yield(iterator {
            for (arr in input) {
                if (i < arr.size) yield(arr[i])
            }
        })
    }
}

fun pathname(filename: String, delimiters: Array<String>, to: File, idx: Int) =
    if (idx < delimiters.size) "${to.path}/${delimiters[idx]}/$filename"
    else "${to.path}/.final/$filename"

@ExperimentalUnsignedTypes
inline fun reductionBatch(
    files: Array<Array<SPIMIFile>>,
    to: File,
    delimiters: Array<String>,
    crossinline process: (files: Array<SPIMIFile>, to: File, externalDocuments: String, idx: Int) -> SPIMIFile
): List<Future<SPIMIFile>> {
    require(to.isDirectory)
    val reduced = rotate(files).asSequence().mapIndexed { idx, file ->
        async {
            val arr = file.asSequence().toMutableList().toTypedArray()
            process(
                arr,
                File(pathname("dictionary.spimi", delimiters, to, idx)),
                pathname("documents.sdoc", delimiters, to, idx),
                idx
            )
        }
    }.toList()
    return reduced
}

@ExperimentalUnsignedTypes
fun multiReduce(files: Array<Array<SPIMIFile>>, to: File, delimiters: Array<String>) =
    SPIMIMultiFile(reductionBatch(files, to, delimiters) { batch, dictionaryPath, externalDocuments, _ ->
        reduce(batch, to = dictionaryPath, externalDocuments = externalDocuments)
    }.map { it.get() }, delimiters)

sealed class ReducerReport(val idx: Int)
class ReducedHeader(idx: Int) : ReducerReport(idx)
class ReducedEntries(idx: Int, val count: Long) : ReducerReport(idx)
class DoneReducing(idx: Int) : ReducerReport(idx)

class ReducerReporter(private val queue: BlockingQueue<ReducerReport>, private val idx: Int) {
    fun reportHeaderReduction() = queue.put(ReducedHeader(idx))
    fun reportEntriesReduction(count: Long) = queue.put(ReducedEntries(idx, count))
    fun reportDone() = queue.put(DoneReducing(idx))
}

data class ReducerStatus(
    val entriesAssigned: Long,
    var entriesReduced: Long = 0,
    var header: Boolean = false,
    var done: Boolean = false,
    var lineLength: Int = 0
)

fun reducerStatusLine(stat: ReducerStatus, idx: Int) = buildString {
    val percentDone = stat.entriesReduced.toDouble() / stat.entriesAssigned
    val barsFilled = (percentDone * BAR_LENGTH).toInt()

    append("Reducer ")
    append(idx + 1)
    append(": [")
    append(if (stat.header) '#' else ' ')
    append("] [")
    for (i in 0 until barsFilled)
        append('#')
    for (i in barsFilled until BAR_LENGTH)
        append(' ')
    append(']')
    append(' ')
    append((percentDone * 100).round(digits = 2))
    append('%')
    if (stat.done) append(" done")
    for (i in length until stat.lineLength)
        append(' ')
}

fun globalReducerStatusLine(stat: ReducerStatus) = buildString {
    val percentDone = stat.entriesReduced.toDouble() / stat.entriesAssigned
    val barsFilled = (percentDone * GLOBAL_BAR_LENGTH).toInt()

    append('[')
    append(if (stat.header) '#' else ' ')
    append("] [")
    for (i in 0 until barsFilled)
        append('#')
    for (i in barsFilled until GLOBAL_BAR_LENGTH)
        append(' ')
    append(']')
    append(' ')
    append((percentDone * 100).round(digits = 2))
    append('%')
    for (i in length until stat.lineLength)
        append(' ')
}

@ExperimentalUnsignedTypes
fun verboseMultiReduce(
    files: Array<Array<SPIMIFile>>,
    to: File,
    delimiters: Array<String>
): SPIMIMultiFile {
    val ranges = delimiters.size + 1
    val queue = ArrayBlockingQueue<ReducerReport>(100_000)

    val (reduced, reductionTime) = measureReturnTimeMillis {
        val stats = Array(ranges) { idx ->
            val assignedEntries = files.fold(0L) { acc, spimiFiles -> acc + spimiFiles[idx].count.toLong() }
            ReducerStatus(entriesAssigned = assignedEntries)
        }
        val globalStat = ReducerStatus(entriesAssigned = stats.fold(0L) { acc, stat -> acc + stat.entriesAssigned })

        val reducedFutures = reductionBatch(files, to, delimiters) { batch, dictionaryPath, externalDocuments, idx ->
            reduce(
                batch,
                to = dictionaryPath,
                externalDocuments = externalDocuments,
                reporter = ReducerReporter(queue, idx),
                reportRate = (stats[idx].entriesAssigned / 1000).coerceAtLeast(100)
            )
        }

        for ((idx, stat) in stats.withIndex()) {
            println(reducerStatusLine(stat, idx))
        }
        print(globalReducerStatusLine(globalStat))

        var headersDone = 0
        var reducersDone = 0
        while (!globalStat.done) {
            val report = queue.take()
            val stat = stats[report.idx]
            when (report) {
                is ReducedHeader -> {
                    stat.header = true
                    headersDone++
                    globalStat.header = headersDone >= stats.size
                }
                is ReducedEntries -> {
                    stat.entriesReduced += report.count
                    globalStat.entriesReduced += report.count
                }
                is DoneReducing -> {
                    val diff = stat.entriesAssigned - stat.entriesReduced
                    stat.entriesReduced += diff
                    globalStat.entriesReduced += diff

                    stat.done = true
                    reducersDone++
                    globalStat.done = reducersDone >= stats.size
                }
            }

            val reducerLine = reducerStatusLine(stat, report.idx)
            stat.lineLength = reducerLine.length

            val globalLine = globalReducerStatusLine(globalStat)
            globalStat.lineLength = globalStat.lineLength

            val lineDiff = stats.size - report.idx
            val updateString = "\u001b[${lineDiff}A\r$reducerLine\u001b[${lineDiff}B\r$globalLine"
            System.out.write(updateString.toByteArray())
        }

        reducedFutures.map { it.get() }
    }

    println("\nReduction done in $reductionTime ms")
    return SPIMIMultiFile(reduced, delimiters)
}

