package dict.spimi

import dict.DocumentID
import dict.Documents
import dict.emptyDocuments
import util.ReadBuffer
import util.decodeInt
import util.decodeUShort
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

@ExperimentalUnsignedTypes
class SPIMIFile(private val file: File) : Closeable, Iterable<SPIMIEntry>, RandomAccess {
    constructor(path: String): this(File(path))

    private val fileStream = RandomAccessFile(file, "r")
    private val stringsCache by lazy {
        HashMap<UInt, String>()
    }
    private val documentsCache by lazy {
        HashMap<UInt, Array<DocumentID>>()
    }

    val filename get() = file.name

    private var stringsStream: RandomAccessFile? = null
    private var documentsStream: RandomAccessFile? = null
    val flags: SPIMIFlags
    val stringsBlockSize: UInt
    val documentsBlockSize: UInt
    val entries: UInt
    val preambleSize get() = HEADER_SIZE + stringsBlockSize + documentsBlockSize

    init {
        val flagsVal = fileStream.readInt().toUInt()
        stringsBlockSize = fileStream.readInt().toUInt()
        documentsBlockSize = fileStream.readInt().toUInt()
        flags = SPIMIFlags(flagsVal)
        entries =
            ((fileStream.length().toULong() - preambleSize) / flags.entrySize).toUInt()
        if (flags.es) {
            val buffer = ByteArray(stringsBlockSize.toInt())
            fileStream.seek(HEADER_SIZE.toLong())
            fileStream.read(buffer)
            val path = String(buffer)
            stringsStream = RandomAccessFile(path, "r")
        }
        if (flags.ed && flags.db) {
            val buffer = ByteArray(documentsBlockSize.toInt())
            fileStream.seek((HEADER_SIZE + stringsBlockSize).toLong())
            fileStream.read(buffer)
            val path = String(buffer)
            documentsStream = RandomAccessFile(path, "r")
        }
    }

    fun dereferenceString(pointer: UInt) =
        stringsCache.getOrPut(pointer) {
            val stream = stringsStream ?: fileStream
            stream.seek(pointer.toLong())
            val length =
                flags.slcAction(
                       big = { stream.readInt() },
                    medium = { stream.readUnsignedShort() },
                     small = { stream.readUnsignedByte() })
            val bytestring = ByteArray(length)
            stream.read(bytestring)
            String(bytestring)
        }

    fun dereferenceDocuments(pointer: UInt) =
        documentsCache.getOrPut(pointer) {
            val stream = documentsStream ?: fileStream
            stream.seek(pointer.toLong())
            val length =
                flags.dscAction(
                       big = { stream.readInt() },
                    medium = { stream.readUnsignedShort() },
                     small = { stream.readUnsignedByte() }).toUInt()
            val bytedocs = ByteArray((length * flags.documentIDSize).toInt())
            stream.read(bytedocs)
            val docIdSize = flags.documentIDSize.toInt()
            Array(length.toInt()) {
                val idx = it * docIdSize
                DocumentID(
                    flags.dicAction(
                           big = { bytedocs.decodeInt(idx) },
                        medium = { bytedocs.decodeUShort(idx).toInt() },
                         small = { bytedocs[idx].toUByte().toInt() })
                )
            }
        }

    /**
     * Get string, document id pair at index
     * @param idx: index at which entry should be located
     * @throws UnsupportedOperationException: if is called with db flag set on.
     *                                        In such getDocuments should be called instead
     */
    operator fun get(idx: UInt): SPIMIEntry {
        if (flags.db) throw UnsupportedOperationException(
            "Cannot retrieve entry from file with DB flag set on. Use getMulti(idx: UInt) instead"
        )
        fileStream.seek((preambleSize + idx * flags.entrySize).toLong())
        val strPtr = flags.spcAction(
               big = { fileStream.readInt() },
            medium = { fileStream.readUnsignedShort() },
             small = { fileStream.readUnsignedByte() }).toUInt()
        val doc = flags.dicAction(
               big = { fileStream.readInt() },
            medium = { fileStream.readUnsignedShort() },
             small = { fileStream.readUnsignedByte() }).toUInt()
        return SPIMIEntry(dereferenceString(strPtr), DocumentID(doc.toInt()))
    }

    /**
     * Get string, array of document ids pair at index
     * @param idx: index at which entry should be located
     * @throws UnsupportedOperationException: if is called with db flag set off.
     *                                        In such get should be called instead
     */
    fun getMulti(idx: UInt): SPIMIMultiEntry {
        if (!flags.db) throw UnsupportedOperationException(
            "Cannot retrieve multi-entry from file without DB flag set on. Use get(idx: UInt) instead"
        )
        fileStream.seek((preambleSize + idx * flags.entrySize).toLong())
        val strPtr = flags.spcAction(
               big = { fileStream.readInt() },
            medium = { fileStream.readUnsignedShort() },
             small = { fileStream.readUnsignedByte() }).toUInt()
        val docPtr = flags.dpcAction(
               big = { fileStream.readInt() },
            medium = { fileStream.readUnsignedShort() },
             small = { fileStream.readUnsignedByte() }).toUInt()
        return SPIMIMultiEntry(dereferenceString(strPtr), dereferenceDocuments(docPtr))
    }

    fun getMulti(idx: Int) = getMulti(idx.toUInt())

    fun getString(idx: UInt): String {
        fileStream.seek((preambleSize + idx * flags.entrySize).toLong())
        val stringPointer = flags.spcAction(
               big = { fileStream.readInt() },
            medium = { fileStream.readUnsignedShort() },
             small = { fileStream.readUnsignedByte() }).toUInt()
        return dereferenceString(stringPointer)
    }

    operator fun get(idx: Int) = get(idx.toUInt())

    fun delete() {
        close()
        file.delete()
    }

    fun deleteOnExit() {
        close()
        file.deleteOnExit()
    }

    override fun close() {
        stringsStream?.close()
        documentsStream?.close()
        fileStream.close()
    }

    override fun iterator(): Iterator<SPIMIEntry> = iterator {
        val readBuffer = ReadBuffer(size = 65536, onRead = { array, offset, length ->
            fileStream.seek((preambleSize.toLong() + bytesRead))
            fileStream.read(array, offset, length)
        })
        for (i in 0u until entries) {
            val strPtr = flags.spcAction(
                   big = { readBuffer.readInt().toUInt() },
                medium = { readBuffer.readShort().toUInt() },
                 small = { readBuffer.readByte().toUInt() }
            )
            val doc = flags.dicAction(
                   big = { readBuffer.readInt() },
                medium = { readBuffer.readShort().toInt() },
                 small = { readBuffer.readByte().toInt() }
            )
            yield(SPIMIEntry(dereferenceString(strPtr), DocumentID(doc)))
        }
    }

    val strings
        get() = iterator {
            var pos = if (stringsStream != null) 0u else HEADER_SIZE
            val stream = stringsStream ?: fileStream
            while (pos < HEADER_SIZE + stringsBlockSize) {
                stream.seek(pos.toLong())
                val length = flags.slcAction(
                       big = { stream.readInt() },
                    medium = { stream.readUnsignedShort() },
                     small = { stream.readUnsignedByte() }
                ).toUInt()
                val bytestring = ByteArray(length.toInt())
                stream.read(bytestring)
                pos += flags.stringLengthSize
                pos += length
                yield(String(bytestring))
            }
        }

    override fun toString() = "SPIMIFile(${file.toRelativeString(File("."))})"

    fun binarySearch(word: String, fromIndex: UInt = 0u, toIndex: UInt = entries): Int {
        if (!flags.ss && !flags.db && !flags.ud)
            throw UnsupportedOperationException("Binary search is only possible on sorted unified multi-entry files")
        var lo = fromIndex
        var hi = toIndex
        while (lo <= hi) {
            val mid = (lo + hi) / 2u
            val currentWord = getString(mid)
//            val (currentWord) = getMulti(mid)
            val cmp = currentWord.compareTo(word)
            when {
                cmp > 0 -> { hi = mid - 1u }
                cmp < 0 -> { lo = mid + 1u }
                else -> return mid.toInt()
            }
        }
        return -(lo.toInt()) - 1
    }

    fun find(word: String): Documents = binarySearch(word).let { idx ->
        if (idx < 0) emptyDocuments() else Documents(getMulti(idx).second)
    }

    operator fun get(word: String) = find(word)

}
