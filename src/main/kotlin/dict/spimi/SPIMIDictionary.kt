package dict.spimi

import dict.DocumentID
import kotlinx.serialization.toUtf8Bytes
import util.WriteBuffer
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@ExperimentalUnsignedTypes
class SPIMIDictionary {

    private val stringMap = HashMap<String, UInt>()
    private val strings = ArrayList<String>()
    private val entries = ULongArray(ENTRIES_COUNT)
    var size = 0
        private set

    private var sorted = false
    private var maxWordLength = 0u
    private var maxDocID = 0u

    fun add(word: String, document: DocumentID): Boolean {
        if (size == ENTRIES_COUNT) return false
        if (document.id.toUInt() > maxDocID) maxDocID = document.id.toUInt()
        if (word.length.toUInt() > maxWordLength) maxWordLength = word.length.toUInt()
        val wordID = stringMap.getOrPut(word) {
            strings.add(word)
            (strings.size - 1).toUInt()
        }
        entries[size++] = WordLong(wordID, document).value
        return size != ENTRIES_COUNT
    }

    fun dumpToDir(path: String) = dumpToDir(File(path))

    fun dumpToDir(file: File): SPIMIFile {
        if (!file.isDirectory) throw FileNotFoundException("$file is not a directory")
        val uuid = UUID.randomUUID().toString()
        val dumpfile = file.resolve(uuid)
        if (!dumpfile.createNewFile()) throw FileAlreadyExistsException(dumpfile)
        dump(dumpfile)
        return SPIMIFile(dumpfile)
    }

    fun dump(file: File) {
        // TODO: Add guards to get notified if values overflow UInt32
        val out = RandomAccessFile(file, "rw")
        val writeBuffer = WriteBuffer(size = 65536, onWrite = out::write)

        // Setting up flags
        val flags = SPIMIFlags()
        flags.slc = maxWordLength < UShort.MAX_VALUE
        flags.sluc = maxWordLength < UByte.MAX_VALUE
        flags.dic = maxDocID < UShort.MAX_VALUE
        flags.diuc = maxDocID < UByte.MAX_VALUE
        flags.ss = sorted

        writeBuffer.skip(12)
        val mapping = HashMap<String, UInt>()
        for (string in strings) {
            val bytes = string.toUtf8Bytes()
            mapping[string] = out.filePointer.toUInt()
            flags.slcAction(
                   big = { writeBuffer.add(bytes.size) },
                medium = { writeBuffer.add(bytes.size.toShort()) },
                 small = { writeBuffer.add(bytes.size.toByte()) }
            )
            writeBuffer.add(bytes)
        }
        writeBuffer.flush()

        // Rest of the flags
        val stringsSize = out.filePointer.toULong() - HEADER_SIZE
        flags.spc = stringsSize < UShort.MAX_VALUE
        flags.spuc = stringsSize < UByte.MAX_VALUE

        for (i in 0 until size) {
            val entry = WordLong(entries[i])
            val word = strings[entry.wordID.toInt()]
            val strPtr = mapping[word]!!
            flags.dicAction(
                   big = { writeBuffer.add(entry.docID.toInt()) },
                medium = { writeBuffer.add(entry.docID.toShort()) },
                 small = { writeBuffer.add(entry.docID.toByte()) }
            )
            flags.spcAction(
                   big = { writeBuffer.add(strPtr.toInt()) },
                medium = { writeBuffer.add(strPtr.toShort()) },
                 small = { writeBuffer.add(strPtr.toByte()) }
            )
        }
        writeBuffer.flush()

        out.seek(0)
        out.writeInt(flags.flags.toInt())
        out.writeInt(stringsSize.toInt())
    }

}