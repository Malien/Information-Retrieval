package dict.spimi

import dict.DocumentID
import kotlinx.serialization.toUtf8Bytes
import util.WriteBuffer
import util.unboxed.toULongList
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@ExperimentalUnsignedTypes
class SPIMIMapper {

    private val stringMap = HashMap<String, UInt>()
    private val strings = ArrayList<String>()
    private val entries = ULongArray(ENTRIES_COUNT)
    var size = 0
        private set

    private var sorted = false
    private var sortedStrings = false
    private var unified = false
    private var maxWordLength = 0u
    private var maxDocID = 0u

    fun add(word: String, document: DocumentID): Boolean {
        if (size == ENTRIES_COUNT) return false
        if (document.id.toUInt() > maxDocID) maxDocID = document.id.toUInt()
        if (word.length.toUInt() > maxWordLength) maxWordLength = word.length.toUInt()
        sorted = false
        unified = false
        val wordID = stringMap.getOrPut(word) {
            strings.add(word)
            (strings.size - 1).toUInt()
        }
        entries[size++] = WordLong(wordID, document).value
        return size != ENTRIES_COUNT
    }

    fun sortStrings() {
        strings.sort()
        val pointerMappings = UIntArray(strings.size)
        for ((newAddr, string) in strings.withIndex()) {
            val oldAddr = stringMap[string]!!
            pointerMappings[oldAddr.toInt()] = newAddr.toUInt()
            stringMap[string] = newAddr.toUInt()
        }
        entries.inplaceMap(0 until size) {
            val (wordID, docID) = WordLong(it)
            combine(pointerMappings[wordID.toInt()].toUInt(), docID)
        }
        sortedStrings = true
    }

    fun sort() {
        if (!sortedStrings) sortStrings()
        entries.asLongArray().sort(fromIndex = 0, toIndex = size)
        sorted = true
    }

    fun unify() {
        if (!sorted) sort()
        val seq = entries.asSequence().take(size)
        val res = sequence {
            var prev: ULong? = null
            for (sorted in seq) {
                if (prev == null) prev = sorted
                else if (prev != sorted) {
                    prev = sorted
                    yield(sorted)
                }
            }
        }.toULongList()
        sorted = true
        unified = true
        size = res.size
        res.arr.copyInto(entries)
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
        flags.se = sorted
        flags.ss = sortedStrings
        flags.ud = unified

        writeBuffer.skip(12)
        val mapping = HashMap<UInt, UInt>()
        for ((string, wordID) in stringMap) {
            val bytes = string.toUtf8Bytes()
            mapping[wordID] = writeBuffer.bytesWritten.toUInt()
            flags.slcAction(
                   big = { writeBuffer.add(bytes.size) },
                medium = { writeBuffer.add(bytes.size.toShort()) },
                 small = { writeBuffer.add(bytes.size.toByte()) }
            )
            writeBuffer.add(bytes)
        }

        // Rest of the flags
        val stringsSize = writeBuffer.bytesWritten.toULong() - HEADER_SIZE
        flags.spc = stringsSize < UShort.MAX_VALUE
        flags.spuc = stringsSize < UByte.MAX_VALUE

        for (i in 0 until size) {
            val entry = WordLong(entries[i])
            val strPtr = mapping[entry.wordID]!!
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
        out.close()
    }

    fun clear() {
        stringMap.clear()
        strings.clear()
        size = 0

        sorted = false
        unified = false
        maxWordLength = 0u
        maxDocID = 0u
    }

}