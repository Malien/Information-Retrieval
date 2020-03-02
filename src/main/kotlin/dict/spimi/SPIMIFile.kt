package dict.spimi

import dict.DocumentID
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

@ExperimentalUnsignedTypes
class SPIMIFile(private val file: File) : Closeable, Iterable<SPIMIEntry>, RandomAccess {
    private val fileStream = RandomAccessFile(file, "r")
    private val stringsCache by lazy {
        HashMap<UInt, String>()
    }
    private val documentsCache by lazy {
        HashMap<UInt, Array<DocumentID>>()
    }

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

    fun getString(pos: UInt) =
        stringsCache.getOrPut(pos) {
            val stream = stringsStream ?: fileStream
            stream.seek(pos.toLong())
            val length =
                flags.slcAction(
                       big = { stream.readInt() },
                    medium = { stream.readShort().toInt() },
                     small = { stream.readByte().toInt() })
            val bytestring = ByteArray(length)
            fileStream.read(bytestring)
            String(bytestring)
        }

    fun getDocuments(pos: UInt) =
        documentsCache.getOrPut(pos) {
            val stream = documentsStream ?: fileStream
            stream.seek(pos.toLong())
            val length =
                flags.dscAction(
                       big = { stream.readInt().toUInt() },
                    medium = { stream.readShort().toUInt() },
                     small = { stream.readByte().toUInt() })
            val bytedocs = ByteArray((length * flags.documentIDSize).toInt())
            fileStream.read(bytedocs)
            val docIdSize = flags.documentIDSize.toInt()
            Array(length.toInt()) {
                val idx = it * docIdSize
                DocumentID(
                    flags.dicAction(
                           big = { bytedocs.decodeInt(idx) },
                        medium = { bytedocs.decodeShort(idx).toInt() },
                         small = { bytedocs[idx].toInt() })
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
        val doc = flags.dicAction(
            big = { fileStream.readInt().toUInt() },
            medium = { fileStream.readShort().toUInt() },
            small = { fileStream.readByte().toUInt() })
        val strPtr = flags.spcAction(
               big = { fileStream.readInt().toUInt() },
            medium = { fileStream.readShort().toUInt() },
             small = { fileStream.readByte().toUInt() })
        return SPIMIEntry(getString(strPtr), DocumentID(doc.toInt()))
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
        val doc = flags.dpcAction(
            big = { fileStream.readInt().toUInt() },
            medium = { fileStream.readShort().toUInt() },
            small = { fileStream.readByte().toUInt() })
        val strPtr = flags.spcAction(
               big = { fileStream.readInt().toUInt() },
            medium = { fileStream.readShort().toUInt() },
             small = { fileStream.readByte().toUInt() })
        return SPIMIMultiEntry(getString(strPtr), getDocuments(doc))
    }

    fun getMulti(idx: Int) = getMulti(idx.toUInt())

    operator fun get(idx: Int) = get(idx.toUInt())

    override fun close() {
        fileStream.close()
    }

    override fun iterator(): Iterator<SPIMIEntry> = iterator {
        // TODO: Read blocks of entries instead of one at the time
        for (i in 0u until entries) {
            yield(get(i))
        }
    }

    val strings get() = iterator {
        var pos = if (stringsStream != null) 0u else HEADER_SIZE
        val stream = stringsStream ?: fileStream
        while (pos < HEADER_SIZE + stringsBlockSize) {
            stream.seek(pos.toLong())
            val length = flags.slcAction(
                   big = { stream.readInt() },
                medium = { stream.readShort().toInt() },
                 small = { stream.readByte().toInt() }
            )
            val bytestring = ByteArray(length)
            stream.read(bytestring)
            pos += flags.stringLengthSize
            pos += length.toUInt()
            yield(String(bytestring))
        }
    }

    override fun toString() = "SPIMIFile(${file.toRelativeString(File("."))})"

}