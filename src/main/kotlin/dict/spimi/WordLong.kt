package dict.spimi

import dict.BookZone
import dict.DocumentID
import util.combine
import util.firstUInt
import util.secondUInt

@ExperimentalUnsignedTypes
inline class WordLong(val value: ULong) : Comparable<WordLong> {
    val wordID get() = value.firstUInt
    val docID get() = value.secondUInt shr flagBitsCount.toInt()
    val docIDAndFlag get() = DocumentWithFlags(value.secondUInt)
    val flags get() = BookZone((value.secondUInt and flagMask).toUByte())

    constructor(wordID: UInt, docID: UInt, flags: BookZone) : this(
        combine(
            wordID,
            DocumentWithFlags(docID, flags).value
        )
    )

    constructor(wordID: UInt, docID: UInt) : this(combine(wordID, docID shl flagBitsCount.toInt()))
    constructor(wordID: UInt, documentID: DocumentID) : this(wordID, documentID.id.toUInt())

    operator fun component1() = wordID
    operator fun component2() = docID
    operator fun component3() = flags

    override fun toString() = "WordLong(wordID=$wordID, docID=$docID, flags=$flags)"

    override fun compareTo(other: WordLong) = value.compareTo(other.value)

    companion object {
        fun withCombinedFlags(wordID: UInt, docAndFlag: UInt) = WordLong(combine(wordID, docAndFlag))
    }
}