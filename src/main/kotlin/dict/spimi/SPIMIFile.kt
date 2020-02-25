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
    val flags: UInt
    val headerSize: UInt
    val entries: UInt

    init {
        val options = fileStream.readLong().toULong()
        val (flags, headerSize) = split(options)
        this.flags = flags
        this.headerSize = headerSize
        entries = 0u // TODO
//            ((fileStream.length().toULong() - HEADER_FLAG_SIZE - HEADER_LENGTH_SIZE - headerSize) / ENTRY_SIZE).toUInt()
    }

    fun getString(pos: UInt) =
        stringsCache.getOrPut(pos) {
            fileStream.seek(pos.toLong())
            val length = fileStream.readInt()
            val bytestring = ByteArray(length)
            fileStream.read(bytestring)
            String(bytestring)
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