package dict.spimi

import dict.BookZone
import dict.Document
import dict.DocumentID
import dict.DocumentRegistry
import parser.fb2.parseBook
import util.async
import util.kotlinx.mapArray
import util.kotlinx.megabytes
import util.kotlinx.round
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.ArrayBlockingQueue

/**
 * Retrieves and parses tokens from the file. And constructs sequence of tokens
 * @return Sequence of lexical tokens
 */
val BufferedReader.tokenSequence
    get() = this.lineSequence().tokenSequence

val Sequence<String>.tokenSequence
    get() = this.flatMap { it.tokenSequence }

val String.tokenSequence
    get() = split(Regex("\\W+")).asSequence()
        .filter { it.isNotBlank() }
        .map    { it.toLowerCase() }

data class MappingStatus(
    val filesAssigned: Int,
    var filesMapped: Int = 0,
    var bytesMapped: Long = 0,
    var done: Boolean = false,
    var dotsCount: Int = 0,
    var lineLength: Int = 0
)

sealed class MappingReport(val idx: Int)
class MappedFile(idx: Int, val bytes: Long) : MappingReport(idx)
class MappingStageDone(idx: Int, val stage: Int) : MappingReport(idx)
class DoneMapping(idx: Int) : MappingReport(idx)

fun <T> genSplits(files: List<T>, processingThreads: Int) = buildList<List<T>> {
    val splitSize = files.size.toDouble() / processingThreads
    for (splitno in 0 until processingThreads) {
        val start = (splitno * splitSize).toInt()
        val end = ((splitno + 1) * splitSize).toInt()
        add(files.slice(start until end))
    }
}

fun mapperStatusLine(stat: MappingStatus, idx: Int) = buildString {
    val percentDone = stat.filesMapped.toDouble() / stat.filesAssigned
    val barsFilled = (percentDone * BAR_LENGTH).toInt()

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

fun globalMapperStatusLine(stat: MappingStatus) = buildString {
    val percentDone = stat.filesMapped.toDouble() / stat.filesAssigned
    val barsFilled = (percentDone * GLOBAL_BAR_LENGTH).toInt()

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

@ExperimentalUnsignedTypes
data class ZonedString(val string: String, val zone: BookZone)

@ExperimentalUnsignedTypes
data class DocumentTokenizerResult(val id: DocumentID, val br: BufferedReader, val seq: Sequence<ZonedString>)

@ExperimentalUnsignedTypes
fun Document.tokenize(documents: DocumentRegistry): DocumentTokenizerResult {
    val id: DocumentID
    // Register document in centralized dictionary
    synchronized(documents) {
        id = documents.register(this)
    }
    return if (this.file.extension != "fb2") {
        val br = BufferedReader(FileReader(file))
        DocumentTokenizerResult(id, br, br.tokenSequence.map { ZonedString(it, BookZone.ofBody) })
    } else {
        val br = FileReader(file).buffered(bufferSize = 65536)
        val seq = parseBook(br, book = this)
            .flatMap { (contents, flags) ->
                contents.tokenSequence.map { ZonedString(it, flags) }
            }
        DocumentTokenizerResult(id, br, seq)
    }
}

@ExperimentalUnsignedTypes
fun verboseMultiMap(
    files: List<Document>,
    to: File,
    documents: DocumentRegistry,
    processingThreads: Int = Runtime.getRuntime().availableProcessors(),
    delimiters: Array<String> = genDelimiters(processingThreads)
): Array<Array<SPIMIFile>> {
    require(to.isDirectory)

    // Splits files between processing threads
    val splits = genSplits(files, processingThreads)

    val queue = ArrayBlockingQueue<MappingReport>(100_000)

    // Mapping
    val spimiFiles = splits.mapIndexed { idx, split ->
        async { // Launches separate thread of execution which returns a value
            val mapper = SPIMIMapper()
            val list = ArrayList<Array<SPIMIFile>>()
            for (file in split) {
                val (id, br, seq) = file.tokenize(documents)
                seq.forEach { (word, flags) ->
                    val hasMoreSpace = mapper.add(word, id, flags)
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
                queue.put(MappedFile(idx, file.file.length()))
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

    val stats = splits.mapArray { MappingStatus(filesAssigned = it.size) }
    for ((idx, stat) in stats.withIndex()) {
        println(mapperStatusLine(stat, idx))
    }
    val globalStat = MappingStatus(filesAssigned = files.size)
    print(globalMapperStatusLine(globalStat))

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

            val globalLine = globalMapperStatusLine(globalStat)
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
fun multiMap(
    files: List<Document>,
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
                val (id, br, seq) = file.tokenize(documents)
                seq.forEach { (word, flags) ->
                    val hasMoreSpace = mapper.add(word, id, flags)
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