package dict.spimi

import dict.DocumentID
import dict.DocumentRegistry
import util.async
import util.mapArray
import util.megabytes
import util.round
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.ArrayBlockingQueue

/**
 * Retrieves and parses tokens from the file. And constructs sequence of tokens
 * @return Sequence of lexical tokens
 */
val BufferedReader.tokenSequence
    get() = this.lineSequence()
        .flatMap { it.split(Regex("\\W+")).asSequence() }
        .filter { it.isNotBlank() }
        .map { it.toLowerCase() }

/**
 * Measures time that block execution took, and
 * returns a pair of return value of the block and time it took in milliseconds
 * @param block block of code to be measured
 * @return pair of return value and execution time in milliseconds
 */
inline fun <R> measureReturnTimeMillis(block: () -> R): Pair<R, Long> {
    val start = System.currentTimeMillis()
    val value = block()
    return value to System.currentTimeMillis() - start
}

data class ProcessingStatus(
    val filesAssigned: Int,
    var filesMapped: Int = 0,
    var bytesMapped: Long = 0,
    var done: Boolean = false,
    var dotsCount: Int = 0,
    var lineLength: Int = 0
)

const val BAR_LENGTH = 20
const val GLOBAL_BAR_LENGTH = 40

sealed class Report(val idx: Int)
class MappedFile(idx: Int, val bytes: Long) : Report(idx)
class MappingStageDone(idx: Int, val stage: Int) : Report(idx)
class DoneMapping(idx: Int) : Report(idx)

fun genSplits(files: List<File>, processingThreads: Int) = buildList<List<File>> {
    val splitSize = files.size.toDouble() / processingThreads
    for (splitno in 0 until processingThreads) {
        val start = (splitno * splitSize).toInt()
        val end = ((splitno + 1) * splitSize).toInt()
        add(files.slice(start until end))
    }
}

fun mapperStatusLine(stat: ProcessingStatus, idx: Int): String {
    val percentDone = stat.filesMapped.toDouble() / stat.filesAssigned
    val barsFilled = (percentDone * BAR_LENGTH).toInt()
    return buildString {
        append("Mapper ")
        append(idx + 1)
        append(": [")
        for (i in 0 until barsFilled)
            append('#')
        for (i in barsFilled until BAR_LENGTH)
            append(' ')
        append(']')
        append(' ')
        append((percentDone * 100).round(digits = 2))
        append("% (")
        append(stat.bytesMapped.megabytes)
        append(" Mb) ")
        if (stat.done) append("done")
        for (i in 0 until stat.dotsCount)
            append('.')
        for (i in length until stat.lineLength)
            append(' ')
    }
}

fun globalStatusLine(stat: ProcessingStatus): String {
    val percentDone = stat.filesMapped.toDouble() / stat.filesAssigned
    val barsFilled = (percentDone * GLOBAL_BAR_LENGTH).toInt()
    return buildString {
        append(stat.filesMapped)
        append('/')
        append(stat.filesAssigned)
        append(' ')
        append('[')
        for (i in 0 until barsFilled)
            append('#')
        for (i in barsFilled until GLOBAL_BAR_LENGTH)
            append(' ')
        append(']')
        append(' ')
        append((percentDone * 100).round(digits = 2))
        append("% (")
        append(stat.bytesMapped.megabytes)
        append(" Mb)")
        for (i in length until stat.lineLength)
            append(' ')
    }
}

@ExperimentalUnsignedTypes
fun verboseMultimap(
    files: List<File>,
    to: File,
    documents: DocumentRegistry,
    processingThreads: Int = Runtime.getRuntime().availableProcessors(),
    delimiters: Array<String> = genDelimiters(processingThreads)
): Array<Array<SPIMIFile>> {
    require(to.isDirectory)

    // Splits files between processing threads
    val splits = genSplits(files, processingThreads)

    val queue = ArrayBlockingQueue<Report>(100_000)

    // Mapping
    val spimiFiles = splits.mapIndexed { idx, split ->
        async { // Launches separate thread of execution which returns a value
            val mapper = SPIMIMapper()
            val list = ArrayList<Array<SPIMIFile>>()
            for (file in split) {
                val id: DocumentID
                // Register document in centralized dictionary
                synchronized(documents) {
                    id = documents.register(file.path)
                }
                val br = BufferedReader(FileReader(file))
                br.tokenSequence.forEach {
                    val hasMoreSpace = mapper.add(it, id)
                    if (!hasMoreSpace) {
                        queue.put(MappingStageDone(idx, 1))
                        mapper.sort()
                        queue.put(MappingStageDone(idx, 2))
                        mapper.unify()
                        queue.put(MappingStageDone(idx, 3))
                        val dumpFile = mapper.dumpRanges(to, delimiters)
                        queue.put(MappingStageDone(idx, 0))
                        list.add(dumpFile)
                        mapper.clear()
                    }
                }
                br.close()
                queue.put(MappedFile(idx, file.length()))
            }
            queue.put(MappingStageDone(idx, 1))
            mapper.unify()
            queue.put(MappingStageDone(idx, 2))
            val file = mapper.dumpRanges(to, delimiters)
            queue.put(MappingStageDone(idx, 3))
            queue.put(DoneMapping(idx))
            list.add(file)
            list
        }
    }

    val stats = splits.mapArray { ProcessingStatus(filesAssigned = it.size) }
    for ((idx, stat) in stats.withIndex()) {
        println(mapperStatusLine(stat, idx))
    }
    val globalStat = ProcessingStatus(filesAssigned = files.size)
    print(globalStatusLine(globalStat))

    val (fileList, mapTime) = measureReturnTimeMillis {
        var mappersDone = 0
        while (!globalStat.done) {
            val report = queue.take()
            val stat = stats[report.idx]
            when (report) {
                is MappedFile -> {
                    stat.bytesMapped += report.bytes
                    globalStat.bytesMapped += report.bytes
                    stat.filesMapped++
                    globalStat.filesMapped++
                }
                is MappingStageDone -> {
                    stat.dotsCount = report.stage
                }
                is DoneMapping -> {
                    stat.done = true
                    stat.dotsCount = 0
                    mappersDone++
                    globalStat.done = mappersDone >= stats.size
                }
            }
            val mapperLine = mapperStatusLine(stat, report.idx)
            stat.lineLength = mapperLine.length

            val globalLine = globalStatusLine(globalStat)
            globalStat.lineLength = globalStat.lineLength
            val lineDiff = stats.size - report.idx
            val updateString = "\u001b[${lineDiff}A\r$mapperLine\u001b[${lineDiff}B\r$globalLine"
            System.out.write(updateString.toByteArray())
        }

        // This part actually begins evaluation of mapper sequence
        spimiFiles.toMutableList().flatMap { it.get() }.toTypedArray()
    }

    println("\nMapping done in $mapTime ms")
    return fileList
}

@ExperimentalUnsignedTypes
fun multimap(
    files: List<File>,
    to: File,
    documents: DocumentRegistry,
    processingThreads: Int = Runtime.getRuntime().availableProcessors(),
    delimiters: Array<String> = genDelimiters(processingThreads)
): Array<Array<SPIMIFile>> {
    require(to.isDirectory)

    // Splits files between processing threads
    val splits = genSplits(files, processingThreads)

    // Mapping
    val spimiFiles = splits.map { split ->
        async { // Launches separate thread of execution which returns a value
            val mapper = SPIMIMapper()
            val list = ArrayList<Array<SPIMIFile>>()
            for (file in split) {
                val id: DocumentID
                // Register document in centralized dictionary
                synchronized(documents) {
                    id = documents.register(file.path)
                }
                val br = BufferedReader(FileReader(file))
                br.tokenSequence.forEach {
                    val hasMoreSpace = mapper.add(it, id)
                    if (!hasMoreSpace) {
                        mapper.unify()
                        val dumpFile = mapper.dumpRanges(to, delimiters)
                        list.add(dumpFile)
                        mapper.clear()
                    }
                }
                br.close()
            }
            mapper.unify()
            val file = mapper.dumpRanges(to, delimiters)
            list.add(file)
            list
        }
    }

    // This part actually begins evaluation of mapper sequence
    return spimiFiles.toMutableList().flatMap { it.get() }.toTypedArray()
}