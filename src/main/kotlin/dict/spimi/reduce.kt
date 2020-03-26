package dict.spimi

import kotlinx.serialization.toUtf8Bytes
import parser.mapArray
import util.WriteBuffer
import util.unboxed.UIntArrayList
import util.unboxed.UIntMap
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.*
import kotlin.collections.HashMap

@ExperimentalUnsignedTypes
fun reduce(files: Array<SPIMIFile>, to: String, externalStrings: String? = null, externalDocuments: String? = null) =
    reduce(files, File(to), externalStrings, externalDocuments)

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
fun reduce(
    files: Array<SPIMIFile>,
    to: File,
    externalStrings: String? = null,
    externalDocuments: String? = null
): SPIMIFile {
    val flags = SPIMIFlags()

    flags.db = true
    flags.se = true
    flags.ud = true
    flags.es = externalStrings != null
    flags.ed = externalDocuments != null
    if (files.any { !it.flags.se }) throw UnsupportedOperationException("Cannot reduce entries on unsorted files")

    flags.slc = files.all { it.flags.slc }
    flags.sluc = files.all { it.flags.sluc }

    flags.dic = files.all { it.flags.dic }
    flags.diuc = files.all { it.flags.diuc }

    flags.ss = files.all { it.flags.ss }

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
        val stringsFile = File(externalStrings)
        if (!stringsFile.exists()) {
            stringsFile.parentFile.mkdirs()
            stringsFile.createNewFile()
        }
        val bytestring = externalStrings.toUtf8Bytes()
        stringsBlockSize = bytestring.size.toUInt()
        writeBuffer.add(bytestring)
        val stringsOut = FileOutputStream(stringsFile)
        WriteBuffer(size = 65536, onWrite = stringsOut::write, onClose = stringsOut::close)
    } else writeBuffer

    val mappings = if (flags.ss) {
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
                prevAddress = stringsWriteBuffer.bytesWritten.toUInt()
                prevString = string
                stringsWriteBuffer.dumpString(string, flags)
                mappings[idx][address] = prevAddress
            }
            val iterator = iterators[idx]
            if (iterator.hasNext()) queue.add(iterator.next() to idx)
        }
        mappings
    } else {
        val mappings = Array(files.size) { UIntMap(files.size * 1000) }
        val strings = HashMap<String, UInt>()
        for ((idx, file) in files.withIndex()) {
            for ((string, address) in file.stringsWithAddress) {
                val alreadyWrittenAddress = strings[string]
                if (alreadyWrittenAddress != null) {
                    mappings[idx][address] = alreadyWrittenAddress
                } else {
                    val writtenAddress = stringsWriteBuffer.bytesWritten.toUInt()
                    mappings[idx][address] = writtenAddress
                    strings[string] = writtenAddress
                    stringsWriteBuffer.dumpString(string, flags)
                }
            }
        }
        mappings
    }

    if (stringsWriteBuffer === writeBuffer) {
        stringsBlockSize = writeBuffer.bytesWritten.toUInt() - HEADER_SIZE
    }

    flags.spc = writeBuffer.bytesWritten.toULong() < UShort.MAX_VALUE
    flags.spuc = writeBuffer.bytesWritten.toULong() < UByte.MAX_VALUE

    data class MappedEntry(val word: String, val idx: Int, val docID: UInt, val wordID: UInt): Comparable<MappedEntry> {
        override fun compareTo(other: MappedEntry): Int {
            var cmp = word.compareTo(other.word)
            if (cmp == 0) cmp = docID.compareTo(other.docID)
            return cmp
        }
    }

    fun PriorityQueue<MappedEntry>.pushEntry(from: Iterator<WordLong>, idx: Int) {
        if (from.hasNext()) {
            val (wordID, docID) = from.next()
            val word = files[idx].dereferenceStringUncached(wordID)
            val mappedWordID = mappings[idx][wordID]
            add(MappedEntry(word, idx, docID, mappedWordID))
        }
    }

    val unifiedEntries = sequence<MappedEntry> {
        val iterators = files.mapArray { it.entries }
        val queue = PriorityQueue<MappedEntry>()
        for ((idx, iterator) in iterators.withIndex()) {
            queue.pushEntry(from = iterator,  idx = idx)
        }
        while (queue.isNotEmpty()) {
            val entry = queue.poll()
            val idx = entry.idx
            yield(entry)
            val iterator = iterators[idx]
            queue.pushEntry(from = iterator, idx = idx)
        }
    }.constrainOnce()

    data class MappedMultiEntry(val wordID: UInt, val documents: UIntArray)

    val compressedEntries = sequence<MappedMultiEntry> {
        val documents = UIntArrayList()
        var term: String? = null

        for ((word, _, doc, wordID) in unifiedEntries) {
            if (term == null) {
                term = word
                documents.add(doc)
            } else {
                if (term != word) {
                    yield(MappedMultiEntry(wordID, documents.toArray()))
                    documents.clear()
                    documents.add(doc)
                    term = word
                } else if (doc != documents.last()) documents.add(doc)
            }
        }
    }.constrainOnce()

    fun writeEntry(strPtr: UInt, docPtr: UInt, to: WriteBuffer) {
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

    fun UIntArray.writeDocumentIDs(to: WriteBuffer) {
        flags.dscAction(
               big = { to.add(size) },
            medium = { to.add(size.toShort()) },
             small = { to.add(size.toByte()) }
        )
        for (id in this) {
            flags.dicAction(
                   big = { to.add(id.toInt()) },
                medium = { to.add(id.toShort()) },
                 small = { to.add(id.toByte()) }
            )
        }
    }

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
        for ((strPtr, documentIDs) in compressedEntries) {
            val docPtr = documentsWriteBuffer.bytesWritten.toUInt()
            writeEntry(strPtr, docPtr, to = writeBuffer)
            documentIDs.writeDocumentIDs(to = documentsWriteBuffer)
        }
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
            documentIDs.writeDocumentIDs(to = writeBuffer)
        }

        documentsBlockSize = writeBuffer.bytesWritten.toUInt() - HEADER_SIZE - stringsBlockSize

        flags.dpc = writeBuffer.bytesWritten.toULong() < UShort.MAX_VALUE
        flags.dpuc = writeBuffer.bytesWritten.toULong() < UByte.MAX_VALUE

        for ((strPtr, docPtr) in documentPositions.zip(stringPositions)) {
            writeEntry(strPtr.toUInt(), docPtr.toUInt(), to = writeBuffer)
        }
    }

    stringsWriteBuffer.flush()
    writeBuffer.flush()
    out.seek(0)
    writeBuffer.add(flags.flags.toInt())
    writeBuffer.add(stringsBlockSize.toInt())
    writeBuffer.add(documentsBlockSize.toInt())
    writeBuffer.close()

    return SPIMIFile(to)
}
