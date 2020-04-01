package dict.spimi

import dict.BookZone
import kotlinx.serialization.toUtf8Bytes
import util.WriteBuffer
import util.kotlinx.mapArray
import util.unboxed.UIntArrayList
import util.unboxed.UIntMap
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.*
import kotlin.collections.HashMap

@ExperimentalUnsignedTypes
fun WriteBuffer.dumpString(string: String, flags: SPIMIFlags) {
    flags.slcAction(
           big = { add(string.length) },
        medium = { add(string.length.toShort()) },
         small = { add(string.length.toByte()) }
    )
    add(string.toUtf8Bytes())
}

@ExperimentalUnsignedTypes
fun reduceFlags(flags: Sequence<SPIMIFlags>, externalStrings: String?, externalDocuments: String?) = SPIMIFlags().apply {
    db = true
    se = true
    ud = true
    es = externalStrings != null
    ed = externalDocuments != null
    if (flags.any { !it.se }) throw UnsupportedOperationException("Cannot reduce entries on unsorted files")

    slc = flags.all { it.slc }
    sluc = flags.all { it.sluc }

    dic = flags.all { it.dic }
    diuc = flags.all { it.diuc }

    ss = flags.all { it.ss }
}

@ExperimentalUnsignedTypes
fun useStringsWriteBuffer(path: String, writeBuffer: WriteBuffer): Pair<WriteBuffer, UInt> {
    val stringsFile = File(path)
    if (!stringsFile.exists()) {
        stringsFile.parentFile.mkdirs()
        stringsFile.createNewFile()
    }
    val bytestring = path.toUtf8Bytes()
    val stringsBlockSize = bytestring.size.toUInt()
    writeBuffer.add(bytestring)
    val stringsOut = FileOutputStream(stringsFile)
    return WriteBuffer(size = 65536, onWrite = stringsOut::write, onClose = stringsOut::close) to stringsBlockSize
}

@ExperimentalUnsignedTypes
typealias Mappings = Array<UIntMap>

@ExperimentalUnsignedTypes
fun reduceSortedStrings(files: Array<SPIMIFile>, writeBuffer: WriteBuffer, flags: SPIMIFlags): Mappings {
    val mappings = Array(files.size) { UIntMap(files.size * 1000) }
    val iterators = files.mapArray { it.stringsWithAddress }
    val queue = PriorityQueue(
        Comparator.comparing<Pair<Pair<String, UInt>, Int>, String> { it.first.first }
    )
    for ((idx, iterator) in iterators.withIndex()) {
        if (iterator.hasNext()) queue.add(iterator.next() to idx)
    }
    var prevString: String? = null
    var prevAddress: UInt = 0u
    while (queue.isNotEmpty()) {
        val (addressedString, idx) = queue.poll()
        val (string, address) = addressedString
        if (string == prevString) {
            mappings[idx][address] = prevAddress
            prevString = string
        } else {
            prevAddress = writeBuffer.bytesWritten.toUInt()
            prevString = string
            writeBuffer.dumpString(string, flags)
            mappings[idx][address] = prevAddress
        }
        val iterator = iterators[idx]
        if (iterator.hasNext()) queue.add(iterator.next() to idx)
    }
    return mappings
}

@ExperimentalUnsignedTypes
fun reduceStrings(files: Array<SPIMIFile>, writeBuffer: WriteBuffer, flags: SPIMIFlags): Mappings {
    val mappings = Array(files.size) { UIntMap(files.size * 1000) }
    val strings = HashMap<String, UInt>()
    for ((idx, file) in files.withIndex()) {
        for ((string, address) in file.stringsWithAddress) {
            val alreadyWrittenAddress = strings[string]
            if (alreadyWrittenAddress != null) {
                mappings[idx][address] = alreadyWrittenAddress
            } else {
                val writtenAddress = writeBuffer.bytesWritten.toUInt()
                mappings[idx][address] = writtenAddress
                strings[string] = writtenAddress
                writeBuffer.dumpString(string, flags)
            }
        }
    }
    return mappings
}

@ExperimentalUnsignedTypes
data class MappedEntry(
    val word: String,
    val idx: Int,
    val docID: UInt,
    val wordID: UInt,
    val flags: BookZone
): Comparable<MappedEntry> {
    override fun compareTo(other: MappedEntry): Int {
        var cmp = word.compareTo(other.word)
        if (cmp == 0) cmp = docID.compareTo(other.docID)
        return cmp
    }
}

@ExperimentalUnsignedTypes
fun PriorityQueue<MappedEntry>.pushEntry(from: Iterator<WordLong>,
                                         mappings: Mappings,
                                         files: Array<SPIMIFile>,
                                         idx: Int
) {
    if (from.hasNext()) {
        val (wordID, docID, flags) = from.next()
        val word = files[idx].dereferenceStringUncached(wordID)
        val mappedWordID = mappings[idx][wordID]
        add(MappedEntry(word, idx, docID, mappedWordID, flags))
    }
}

@ExperimentalUnsignedTypes
fun unifyEntries(files: Array<SPIMIFile>, mappings: Mappings) = iterator {
    val iterators = files.mapArray { it.entries }
    val queue = PriorityQueue<MappedEntry>()
    for ((idx, iterator) in iterators.withIndex()) {
        queue.pushEntry(iterator, mappings, files, idx)
    }
    while (queue.isNotEmpty()) {
        val entry = queue.poll()
        val idx = entry.idx
        yield(WordLong(wordID = entry.wordID, docID = entry.docID, flags = entry.flags))
        val iterator = iterators[idx]
        queue.pushEntry(iterator, mappings, files, idx)
    }
}

@ExperimentalUnsignedTypes
data class MappedMultiEntry(val wordID: UInt, val documents: UIntArray)

@ExperimentalUnsignedTypes
fun compressEntries(unifiedEntries: Iterator<WordLong>) = iterator {
    if (unifiedEntries.hasNext()) {
        var (prevWordID, firstDocID, firstFlags) = unifiedEntries.next()
        val documents = UIntArrayList()
        documents.add(DocumentWithFlags(firstDocID, firstFlags).value)

        for ((wordID, docID, flags) in unifiedEntries) {
            if (wordID != prevWordID) {
                yield(MappedMultiEntry(prevWordID, documents.toArray()))
                documents.clear()
                documents.add(DocumentWithFlags(docID, flags).value)
                prevWordID = wordID
            } else if (docID != documents.last()) documents.add(DocumentWithFlags(docID, flags).value)
        }
        yield(MappedMultiEntry(prevWordID, documents.toArray()))
    }
}


@ExperimentalUnsignedTypes
fun writeEntry(strPtr: UInt, docPtr: UInt, to: WriteBuffer, flags: SPIMIFlags) {
    flags.spcAction(
           big = { to.add(strPtr.toInt()) },
        medium = { to.add(strPtr.toShort()) },
         small = { to.add(strPtr.toByte()) }
    )
    flags.dpcAction(
           big = { to.add(docPtr.toInt()) },
        medium = { to.add(docPtr.toShort()) },
         small = { to.add(docPtr.toByte()) }
    )
}

@ExperimentalUnsignedTypes
fun UIntArray.writeDocumentIDs(to: WriteBuffer, flags: SPIMIFlags) {
    if (flags.dbi) {
        if (flags.dvbe) {
            var prev = 0u
            for (id in this) {
                val interval = (id - prev).toInt()
                to.encodeVariable(interval)
                prev = id
            }
        } else {
            var prev = 0u
            for (id in this) {
                val interval = id - prev
                flags.dicAction(
                       big = { to.add(interval.toInt()) },
                    medium = { to.add(interval.toShort()) },
                     small = { to.add(interval.toByte()) }
                )
                prev = id
            }
        }
    } else {
        if (flags.dvbe) {
            for (id in this) {
                to.encodeVariable(id.toInt())
            }
        } else {
            for (id in this) {
                flags.dicAction(
                       big = { to.add(id.toInt()) },
                    medium = { to.add(id.toShort()) },
                     small = { to.add(id.toByte()) }
                )
            }
        }
    }
}

@ExperimentalUnsignedTypes
fun reduce(
    files: Array<SPIMIFile>,
    to: File,
    externalStrings: String? = null,
    externalDocuments: String? = null,
    reporter: ReducerReporter? = null,
    reportRate: Long = 1000,
    intervalEncoding: Boolean = true,
    variableByteEncoding: Boolean = true
): SPIMIFile {
    val flags = reduceFlags(files.asSequence().map { it.flags }, externalStrings, externalDocuments)
    flags.dbi = intervalEncoding
    flags.dvbe = variableByteEncoding

    if (!to.exists()) {
        to.parentFile.mkdirs()
        to.createNewFile()
    }
    val out = RandomAccessFile(to, "rw")
    val writeBuffer = WriteBuffer(size = 65536, onWrite = out::write, onClose = out::close)

    var stringsBlockSize = 0u
    var documentsBlockSize = 0u

    writeBuffer.skip(12)

    val stringsWriteBuffer = if (externalStrings != null) {
        val (buffer, blockSize) = useStringsWriteBuffer(externalStrings, writeBuffer)
        stringsBlockSize = blockSize
        buffer
    } else writeBuffer

    val mappings =
        if (flags.ss) reduceSortedStrings(files, stringsWriteBuffer, flags)
        else reduceStrings(files, stringsWriteBuffer, flags)

    if (stringsWriteBuffer === writeBuffer) {
        stringsBlockSize = writeBuffer.bytesWritten.toUInt() - HEADER_SIZE
    }

    flags.spc = writeBuffer.bytesWritten.toULong() < UShort.MAX_VALUE
    flags.spuc = writeBuffer.bytesWritten.toULong() < UByte.MAX_VALUE

    reporter?.reportHeaderReduction()

    val unifiedEntries = unifyEntries(files, mappings)

    val compressedEntries = compressEntries(unifiedEntries)

    if (externalDocuments != null) {
        // In this single-pass mode sizes of docID collections cannot be pre-evaluated,
        // so sizes are set to take maximum size.
        flags.dpc = false
        flags.dpuc = false
        flags.dsc = false
        flags.dsuc = false

        val documentsFile = File(externalDocuments)
        if (!documentsFile.exists()) {
            documentsFile.parentFile.mkdirs()
            documentsFile.createNewFile()
        }
        val bytestring = externalDocuments.toUtf8Bytes()
        writeBuffer.add(bytestring)
        documentsBlockSize = bytestring.size.toUInt()
        val documentsOut = FileOutputStream(documentsFile)
        val documentsWriteBuffer =
            WriteBuffer(size = 65536, onWrite = documentsOut::write, onClose = documentsOut::close)
        var entriesReduced = 0L
        for ((strPtr, documentIDs) in compressedEntries) {
            val docPtr = documentsWriteBuffer.bytesWritten.toUInt()
            writeEntry(strPtr, docPtr, to = writeBuffer, flags = flags)
            documentIDs.writeDocumentIDs(to = documentsWriteBuffer, flags = flags)
            entriesReduced += documentIDs.size
            if (entriesReduced >= reportRate) {
                reporter?.reportEntriesReduction(entriesReduced)
                entriesReduced = 0
            }
        }
        reporter?.reportEntriesReduction(entriesReduced)
        documentsWriteBuffer.close()
    } else {
        // Still can't evaluate sizes of docID collections, but while we are noting positions,
        // we still can determine pointer compressions
        flags.dsc = false
        flags.dsuc = false

        val documentPositions = UIntArrayList()
        val stringPositions = UIntArrayList()
        for ((strPtr, documentIDs) in compressedEntries) {
            stringPositions.add(strPtr)
            documentPositions.add(writeBuffer.bytesWritten.toUInt())
            documentIDs.writeDocumentIDs(to = writeBuffer, flags = flags)
        }

        documentsBlockSize = writeBuffer.bytesWritten.toUInt() - HEADER_SIZE - stringsBlockSize

        flags.dpc = writeBuffer.bytesWritten.toULong() < UShort.MAX_VALUE
        flags.dpuc = writeBuffer.bytesWritten.toULong() < UByte.MAX_VALUE

        for ((strPtr, docPtr) in documentPositions.zip(stringPositions)) {
            writeEntry(strPtr.toUInt(), docPtr.toUInt(), to = writeBuffer, flags = flags)
        }
    }

    stringsWriteBuffer.flush()
    writeBuffer.flush()
    out.seek(0)
    writeBuffer.add(flags.flags.toInt())
    writeBuffer.add(stringsBlockSize.toInt())
    writeBuffer.add(documentsBlockSize.toInt())
    writeBuffer.close()

    reporter?.reportDone()

    return SPIMIFile(to)
}
