package dict.spimi

import dict.DocumentID
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

@ExperimentalUnsignedTypes
class SPIMIFile(file: File) : Closeable, Iterable<WordPair>, RandomAccess {
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
    val headerSize get() = HEADER_SIZE + stringsBlockSize + documentsBlockSize

    init {
        val flagsVal = fileStream.readInt().toUInt()
        stringsBlockSize = fileStream.readInt().toUInt()
        documentsBlockSize = fileStream.readInt().toUInt()
        this.flags = SPIMIFlags(flagsVal)
        entries =
            ((fileStream.length().toULong() - headerSize) / flags.entrySize).toUInt()
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
                flags.slcAction({ stream.readInt() }, { stream.readShort().toInt() }, { stream.readByte().toInt() })
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
                    { stream.readInt().toUInt() },
                    { stream.readShort().toUInt() },
                    { stream.readByte().toUInt() })
            val bytedocs = ByteArray((length * flags.documentIDSize).toInt())
            fileStream.read(bytedocs)
            val docIdSize = flags.documentIDSize.toInt()
            Array(length.toInt()) {
                val idx = it * docIdSize
                DocumentID(
                    flags.dicAction(
                        { bytedocs.decodeInt(idx) },
                        { bytedocs.decodeShort(idx) },
                        { bytedocs[idx].toInt() })
                )
            }
        }

    operator fun get(idx: UInt): WordPair {
        fileStream.seek((headerSize + idx * 0u /* TODO */).toLong())
        val (strPtr, doc) = split(fileStream.readLong().toULong())
        return WordPair(getString(strPtr), DocumentID(doc.toInt()))
    }

    operator fun get(idx: Int) = get(idx.toUInt())

    override fun close() {
        fileStream.close()
    }

    override fun iterator(): Iterator<WordPair> = iterator {
        for (i in 0u until entries) {
            yield(get(i))
        }
    }

}