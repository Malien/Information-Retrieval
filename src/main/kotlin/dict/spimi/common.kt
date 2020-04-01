package dict.spimi

import dict.BookZone
import dict.DocumentID
import util.KeySet

data class SPIMIEntry(val word: String, val document: DocumentID): Comparable<SPIMIEntry> {
    override fun compareTo(other: SPIMIEntry): Int {
        var cmp = word.compareTo(other.word)
        if (cmp == 0) cmp = document.compareTo(other.document)
        return cmp
    }
}

@ExperimentalUnsignedTypes
data class SPIMIMultiEntry(val word: String, val documents: UIntArray) {
    operator fun get(idx: Int) = DocumentWithFlags(documents[idx])
}

@ExperimentalUnsignedTypes
typealias RankedDocuments = KeySet<DocumentWithFlags>

@ExperimentalUnsignedTypes
fun emptyRankedDocuments(): RankedDocuments = KeySet(iterator {})

@ExperimentalUnsignedTypes
var flagBitsCount = 4u
    set(value) {
        require(value < 8u)
        field = value
        flagMask = 0xFFu shr (8u - value).toInt()
    }

@ExperimentalUnsignedTypes
var flagMask = 0xFu
    private set

@ExperimentalUnsignedTypes
inline class DocumentWithFlags(val value: UInt): Comparable<DocumentWithFlags> {

    constructor(docID: UInt, flags: BookZone) : this(
        docID shl flagBitsCount.toInt() or (flags.flags.toUInt() and flagMask.toUInt())
    )

    val documentID: DocumentID get() = DocumentID(docID.toInt())
    val docID get() = value shr flagBitsCount.toInt()
    val flags get() = BookZone((value and flagMask).toUByte())

    override fun compareTo(other: DocumentWithFlags) = docID.compareTo(other.docID)

    override fun toString() = "DocumentWithFlags(docID=$docID, flags=$flags)"
}

const val ENTRIES_COUNT = 10_000_000

@ExperimentalUnsignedTypes
const val HEADER_FLAG_SIZE = 4u
@ExperimentalUnsignedTypes
const val HEADER_STRING_LENGTH_SIZE = 4u
@ExperimentalUnsignedTypes
const val HEADER_DOCUMENTS_LENGTH_SIZE = 4u

@ExperimentalUnsignedTypes
val HEADER_SIZE = HEADER_FLAG_SIZE + HEADER_STRING_LENGTH_SIZE + HEADER_DOCUMENTS_LENGTH_SIZE

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

const val BAR_LENGTH = 20
const val GLOBAL_BAR_LENGTH = 40

/**
 * Measures time that block execution took, and
 * returns a pair of return value of the block and time it took in milliseconds
 * @param block block of code to be measured
 * @return pair of return value and execution time in milliseconds
 */
inline fun <R> measureReturnTimeMillis(block: () -> R): Pair<R, Long> {
    val start = System.currentTimeMillis()
    val value = block()
    return value to System.currentTimeMillis() - start
}