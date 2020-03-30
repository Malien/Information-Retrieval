package dict.spimi

import dict.DocumentID
import util.combine
import util.firstUInt
import util.secondUInt
import util.split

data class SPIMIEntry(val word: String, val document: DocumentID): Comparable<SPIMIEntry> {
    override fun compareTo(other: SPIMIEntry): Int {
        var cmp = word.compareTo(other.word)
        if (cmp == 0) cmp = document.compareTo(other.document)
        return cmp
    }
}
typealias SPIMIMultiEntry = Pair<String, Array<DocumentID>>

const val ENTRIES_COUNT = 10_000_000

@ExperimentalUnsignedTypes
const val HEADER_FLAG_SIZE = 4u
@ExperimentalUnsignedTypes
const val HEADER_STRING_LENGTH_SIZE = 4u
@ExperimentalUnsignedTypes
const val HEADER_DOCUMENTS_LENGTH_SIZE = 4u

@ExperimentalUnsignedTypes
val HEADER_SIZE = HEADER_FLAG_SIZE + HEADER_STRING_LENGTH_SIZE + HEADER_DOCUMENTS_LENGTH_SIZE

@ExperimentalUnsignedTypes
inline class WordLong(val value: ULong): Comparable<WordLong> {
    val wordID get() = value.firstUInt
    val docID get() = value.secondUInt
    val pair get() = split(value)

    constructor(wordID: UInt, docID: UInt) : this(combine(wordID, docID))
    constructor(wordID: UInt, documentID: DocumentID) : this(wordID, documentID.id.toUInt())

    operator fun component1() = wordID
    operator fun component2() = docID

    override fun toString() = "WordLong(wordID=$wordID, docID=$docID)"

    override fun compareTo(other: WordLong) = value.compareTo(other.value)
}

fun genDelimiters(processingThreads: Int): Array<String> {
    val splitPoints = "0abcdefghijklmnopqrstuvwxyz"
    val delimiterLength = 4
    val fraction = 1.0 / processingThreads
    val splitFraction = 1.0 / splitPoints.length

    return Array(processingThreads - 1) {
        buildString {
            var position = (it + 1) * fraction
            repeat(times = delimiterLength) {
                val stop = position / splitFraction
                val idx = stop.toInt()
                if (position < splitFraction) return@repeat
                append(splitPoints[idx])
                position = stop - idx
            }
        }
    }
}
