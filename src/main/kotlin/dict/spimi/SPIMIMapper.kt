package dict.spimi

import dict.BookZone
import dict.DocumentID
import kotlinx.serialization.toUtf8Bytes
import util.WriteBuffer
import util.firstInt
import util.secondUInt
import util.unboxed.toULongList
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@ExperimentalUnsignedTypes
inline fun ULongArray.inPlaceMap(inRange: IntRange, transform: (value: ULong) -> ULong) {
    for (i in inRange) {
        this[i] = transform(this[i])
    }
}

val IntRange.length get() = last - first + 1

@ExperimentalUnsignedTypes
fun ULongArray.sequenceOf(range: IntRange) = range.asSequence().map { this[it] }

fun <C, T> C.sequenceOf(range: IntRange)
    where C : List<T>,
          C : RandomAccess = range.asSequence().map { this[it] }

inline fun IntRange.mapRange(transform: (value: Int) -> Int) =
    IntRange(start = transform(start), endInclusive = transform(endInclusive))

fun <T> buildList(capacity: Int, builder: MutableList<T>.() -> Unit) =
    ArrayList<T>(capacity).apply(builder)

fun <T> buildList(builder: MutableList<T>.() -> Unit) = ArrayList<T>().apply(builder)

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

    fun add(word: String, document: DocumentID, zone: BookZone = BookZone.ofBody): Boolean {
        if (size == ENTRIES_COUNT) return false
        if (document.id.toUInt() > maxDocID) maxDocID = document.id.toUInt()
        if (word.length.toUInt() > maxWordLength) maxWordLength = word.length.toUInt()
        sorted = false
        unified = false
        val wordID = stringMap.getOrPut(word) {
            sortedStrings = false
            strings.add(word)
            (strings.size - 1).toUInt()
        }
        entries[size++] = WordLong(wordID, document.id.toUInt(), zone).value
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
        entries.inPlaceMap(0 until size) {
            val (wordID, docID, flags) = WordLong(it)
            WordLong(pointerMappings[wordID.toInt()].toUInt(), docID, flags).value
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
        val mask = flagMask.toULong().inv()
        val res = sequence {
            var prev: ULong = 0u
            var assigned = false
            for (sorted in seq) {
                if (!assigned) {
                    prev = sorted
                    assigned = true
                } else {
                    val wordAndDoc = sorted and mask
                    if (prev and mask == wordAndDoc) {
                        prev = prev or sorted
                    } else {
                        yield(prev)
                        prev = sorted
                    }
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

    private fun dumpStrings(range: IntRange, writeBuffer: WriteBuffer, flags: SPIMIFlags): UIntArray {
        val mapping = UIntArray(range.length)
        for (idx in range) {
            val string = strings[idx]
            mapping[idx - range.first] = writeBuffer.bytesWritten.toUInt()
            val bytes = string.toUtf8Bytes()
            flags.slcAction(
                   big = { writeBuffer.add(bytes.size) },
                medium = { writeBuffer.add(bytes.size.toShort()) },
                 small = { writeBuffer.add(bytes.size.toByte()) }
            )
            writeBuffer.add(bytes)
        }
        return mapping
    }

    private fun dumpEntries(range: IntRange,
                            writeBuffer: WriteBuffer,
                            flags: SPIMIFlags,
                            mapping: UIntArray,
                            stringOffset: Int = 0
    ) {
        for (i in range) {
            val word = WordLong(entries[i])
            val docAndFlags = word.docIDAndFlag.value
            val strPtr = mapping[word.wordID.toInt() - stringOffset]
            flags.spcAction(
                   big = { writeBuffer.add(strPtr.toInt()) },
                medium = { writeBuffer.add(strPtr.toShort()) },
                 small = { writeBuffer.add(strPtr.toByte()) }
            )
            flags.dicAction(
                   big = { writeBuffer.add(docAndFlags.toInt()) },
                medium = { writeBuffer.add(docAndFlags.toShort()) },
                 small = { writeBuffer.add(docAndFlags.toByte()) }
            )
        }
    }

    private fun dumpHeader(flags: SPIMIFlags,
                           stringsBlockSize: UInt,
                           writeBuffer: WriteBuffer
    ) {
        writeBuffer.add(flags.flags.toInt())
        writeBuffer.add(stringsBlockSize.toInt())
        writeBuffer.add(0)
    }

    fun dump(file: File) {
        // TODO: Add guards to get notified if values overflow UInt32
        val out = RandomAccessFile(file, "rw")
        val writeBuffer = WriteBuffer(size = 65536, onWrite = out::write, onClose = out::close)

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
        val mapping = dumpStrings(strings.indices, writeBuffer, flags)

        // Rest of the flags
        val stringsSize = writeBuffer.bytesWritten.toULong() - HEADER_SIZE
        flags.spc = stringsSize < UShort.MAX_VALUE
        flags.spuc = stringsSize < UByte.MAX_VALUE

        dumpEntries(0 until size, writeBuffer, flags, mapping)
        writeBuffer.flush()

        out.seek(0)
        dumpHeader(flags, stringsBlockSize = stringsSize.toUInt(), writeBuffer = writeBuffer)
        writeBuffer.close()
    }

    fun dumpRange(to: File, range: IntRange) {
        assert(sorted && sortedStrings)
        val out = RandomAccessFile(to, "rw")
        val writeBuffer = WriteBuffer(size = 65536, onWrite = out::write, onClose = out::close)

        val stringRange = range.mapRange { (if (it < size) entries[it] else entries[size - 1]).firstInt }

        // Setting up flags
        val flags = SPIMIFlags()
        if (maxWordLength < UByte.MAX_VALUE) {
            flags.slc = true
            flags.sluc = true
        } else {
            val maxLength = strings.sequenceOf(stringRange)
                .map { it.length.toUInt() }
                .max()
                ?: 0u
            flags.slc = maxLength < UShort.MAX_VALUE
            flags.sluc = maxLength < UByte.MAX_VALUE
        }
        if (maxDocID < UByte.MAX_VALUE) {
            flags.slc = true
            flags.sluc = true
        } else {
            val maxDocID = entries.sequenceOf(range)
                .map { it.secondUInt }
                .max()
                ?: 0u
            flags.dic = maxDocID < UShort.MAX_VALUE
            flags.diuc = maxDocID < UByte.MAX_VALUE
        }
        flags.se = true
        flags.ss = true
        flags.ud = unified

        writeBuffer.skip(12)
        val mapping = dumpStrings(stringRange, writeBuffer, flags)

        // Rest of the flags
        val stringsSize = writeBuffer.bytesWritten.toULong() - HEADER_SIZE
        flags.spc = stringsSize < UShort.MAX_VALUE
        flags.spuc = stringsSize < UByte.MAX_VALUE

        dumpEntries(range, writeBuffer, flags, mapping, stringOffset = stringRange.first)
        writeBuffer.flush()

        out.seek(0)
        dumpHeader(flags, stringsBlockSize = stringsSize.toUInt(), writeBuffer = writeBuffer)
        writeBuffer.close()
    }

    fun dumpRanges(dir: String, delimiters: Array<String>) = dumpRanges(File(dir), delimiters)

    fun dumpRanges(dir: File, delimiters: Array<String>): Array<SPIMIFile> = buildList<SPIMIFile> {
        fun dumpChunkInRange(rangeName: String, range: IntRange) {
            val uuid = UUID.randomUUID().toString()
            val dumpRange = dir.resolve(rangeName)
            dumpRange.mkdir()
            val dumpfile = dumpRange.resolve(uuid)
            if (!dumpfile.createNewFile()) throw FileAlreadyExistsException(dumpfile)
            dumpRange(dumpfile, range)
            add(SPIMIFile(dumpfile))
        }

        if (!dir.isDirectory) throw FileNotFoundException("$dir is not a directory")
        if (!sorted) sort()
        var start = 0
        for (delimiter in delimiters) {
            if (strings.isEmpty()) {
                dumpChunkInRange(delimiter, start until start)
                continue
            }
            var str = strings.first()
            var idx = start
            while (str < delimiter && idx < this@SPIMIMapper.size) {
                idx++
                val entry = entries[idx]
                str = strings[entry.firstInt]
            }
            dumpChunkInRange(delimiter, start until idx)
            start = idx
        }
        dumpChunkInRange(".final", start until this@SPIMIMapper.size)
    }.toTypedArray()

    fun clear() {
        stringMap.clear()
        strings.clear()
        size = 0

        sortedStrings = false
        sorted = false
        unified = false
        maxWordLength = 0u
        maxDocID = 0u
    }

}