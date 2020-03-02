package dict.spimi

import dict.DocumentID
import kotlinx.serialization.toUtf8Bytes
import parser.mapArray
import util.WriteBuffer
import util.unboxed.UIntArrayList
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

@ExperimentalUnsignedTypes
class SPIMIReducer(private val files: Array<SPIMIFile>) {

    val flags = SPIMIFlags()

    var externalStrings: String? = null
        set(value) {
            flags.es = value == null
            field = value
        }

    var externalDocuments: File? = null
        set(value) {
            flags.ed = value == null
            field = value
        }

    init {
        flags.db = true
        if (files.any { !it.flags.db }) throw UnsupportedOperationException("Cannot reduce entries on unsorted files")

        flags.slc = files.all { it.flags.slc }
        flags.sluc = files.all { it.flags.sluc }

        flags.dic = files.all { it.flags.dic }
        flags.diuc = files.all { it.flags.diuc }
    }

    private var maxDocumentsSize = 0

    fun reduce(to: String) = reduce(File(to))
    fun reduce(to: File) {
        val out = RandomAccessFile(to, "rw")
        val writeBuffer = WriteBuffer(size = 65536, onWrite = out::write)

        writeBuffer.skip(12)

        val stringsWriteBuffer = if (externalStrings != null) {
            val stringsOut = FileOutputStream(externalStrings!!)
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

        flags.spc = writeBuffer.bytesWritten.toULong() < UShort.MAX_VALUE
        flags.spuc = writeBuffer.bytesWritten.toULong() < UByte.MAX_VALUE

        val unifiedEntries = sequence<SPIMIEntry> {
            val iterators = files.mapArray { it.iterator() }
            val entries = iterators.mapArray { if (it.hasNext()) it.next() else null }
            while (iterators.any { it.hasNext() }) {
                var minEntry: SPIMIEntry? = null
                var minIdx = -1
                for ((idx, entry) in entries.withIndex()) {
                    if (minEntry == null) minEntry = entry
                    else if (entry != null && entry < minEntry) {
                        minIdx = idx
                        minEntry = entry
                    }
                }
                yield(minEntry!!)
                val minIterator = iterators[minIdx]
                entries[minIdx] = if (minIterator.hasNext()) minIterator.next() else null
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
                        yield(SPIMIMultiEntry(word, documents.toTypedArray()))
                        documents.clear()
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

        val documents = HashMap<DocumentID, UInt>()
        if (externalDocuments != null) {
            // In this single-pass mode sizes of docID collections cannot be pre-evaluated,
            // so sizes are set to take maximum size.
            flags.dpc = false
            flags.dpuc = false
            flags.dsc = false
            flags.dsuc = false

            val documentsOut = FileOutputStream(externalDocuments!!)
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

            flags.dpc = writeBuffer.bytesWritten.toULong() < UShort.MAX_VALUE
            flags.dpuc = writeBuffer.bytesWritten.toULong() < UByte.MAX_VALUE

            for ((strPtr, docPtr) in documentPositions.zip(stringPositions)) {
                writeEntry(strPtr.toUInt(), docPtr.toUInt(), to = writeBuffer)
            }
        }

        stringsWriteBuffer.flush()
        writeBuffer.flush()
        out.close()
    }
}

