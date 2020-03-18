package dict.spimi

import dict.DocumentID
import kotlinx.serialization.toUtf8Bytes
import parser.mapArray
import util.WriteBuffer
import util.unboxed.UIntArrayList
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@ExperimentalUnsignedTypes
fun reduce(files: Array<SPIMIFile>, to: String, externalStrings: String? = null, externalDocuments: String? = null) =
    reduce(files, File(to), externalStrings, externalDocuments)

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
        val stringsOut = FileOutputStream(stringsFile)
        WriteBuffer(size = 65536, onWrite = stringsOut::write, onClose = stringsOut::close)
    } else writeBuffer

    val strings = HashMap<String, UInt>()
    for (file in files) {
        for (string in file.strings) {
            strings.putIfAbsent(string, strings.size.toUInt())
        }
    }

    for (entry in strings) {
        val (string) = entry
        entry.setValue(stringsWriteBuffer.bytesWritten.toUInt())
        flags.slcAction(
            big = { writeBuffer.add(string.length) },
            medium = { writeBuffer.add(string.length.toShort()) },
            small = { writeBuffer.add(string.length.toByte()) }
        )
        writeBuffer.add(string.toUtf8Bytes())
    }

    if (stringsWriteBuffer === writeBuffer) {
        stringsBlockSize = writeBuffer.bytesWritten.toUInt() - HEADER_SIZE
    }

    flags.spc = writeBuffer.bytesWritten.toULong() < UShort.MAX_VALUE
    flags.spuc = writeBuffer.bytesWritten.toULong() < UByte.MAX_VALUE

    val unifiedEntries = sequence<SPIMIEntry> {
        val iterators = files.mapArray { it.iterator() }
        val entries = PriorityQueue(
            Comparator.comparing<Pair<SPIMIEntry, Int>, SPIMIEntry> { it.first }
        )
        for ((idx, iterator) in iterators.withIndex()) {
            if (iterator.hasNext()) entries.add(iterator.next() to idx)
        }
        while (entries.isNotEmpty()) {
            val (entry, idx) = entries.poll()
            yield(entry)
            val iterator = iterators[idx]
            if (iterator.hasNext()) entries.add(iterator.next() to idx)
        }
    }.constrainOnce()

    val compressedEntries = sequence<SPIMIMultiEntry> {
        val documents = ArrayList<DocumentID>()
        var term: String? = null

        for ((word, doc) in unifiedEntries) {
            if (term == null) {
                term = word
                documents.add(doc)
            } else {
                if (term != word) {
                    yield(SPIMIMultiEntry(term, documents.toTypedArray()))
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

    fun Array<DocumentID>.writeDocumentIDs(to: WriteBuffer) {
        flags.dscAction(
               big = { to.add(size) },
            medium = { to.add(size.toShort()) },
             small = { to.add(size.toByte()) }
        )
        for ((id) in this) {
            flags.dicAction(
                   big = { to.add(id) },
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
        for ((word, documentIDs) in compressedEntries) {
            val strPtr = strings[word]!!
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
        for ((word, documentIDs) in compressedEntries) {
            stringPositions.add(strings[word]!!)
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
