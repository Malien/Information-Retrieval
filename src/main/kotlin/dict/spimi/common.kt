package dict.spimi

import dict.DocumentID

typealias WordPair = Pair<String, DocumentID>

const val ENTRIES_COUNT = 10_000_000

@ExperimentalUnsignedTypes
const val HEADER_FLAG_SIZE = 4u
@ExperimentalUnsignedTypes
const val HEADER_STRING_LENGTH_SIZE = 4u
@ExperimentalUnsignedTypes
const val HEADER_DOCUMENTS_LENGTH_SIZE = 4u

@ExperimentalUnsignedTypes
val HEADER_SIZE = HEADER_FLAG_SIZE + HEADER_STRING_LENGTH_SIZE + HEADER_DOCUMENTS_LENGTH_SIZE

/*
SPIMI file structure:
     _________________________________________________________________________________________________________
    | 4 bytes |         4 bytes        |          4 bytes         |    n bytes    |     m bytes     | ...rest | EOF
    |---------|------------------------|--------------------------|---------------|-----------------|---------|
    |  flags  | strings block size (n) | documents block size (m) | strings block | documents block | entries |
    |_________|________________________|__________________________|_______________|_________________|_________|
    |                                                header                                         |   body  |
    |_______________________________________________________________________________________________|_________|

ABSTRACT:
    FILE POINTER:
        A byte sequence that represents position of data within file counting from the file's beginning. (in other terms - offset)
        If pointer is said to be pointing to the structure, it it pointing to the first byte of said structure.

HEADER:
    Contains 4 bytes worth of binary flags.
    Followed by 4 bytes that signify the length of strings block.
    Followed by 4 bytes that signify the length of documents block.

    FLAGS:
        ----- String length compression ----
        0  - SLC  - String length compression
        1  - SLUC - String length ultra compression
        ----- String pointer compression ----
        2  - SPC  - String pointer compression
        3  - SPUC - String pointer ultra compression
        ----- Document block size compression -----
        4  - DSC  - Document block size compression
        5  - DSUC - Document block size ultra compression
        ----- Document id compression -----
        6  - DIC  - Document id compression
        7  - DIUC - Document id ultra compression
        ----- Document pointer compression -----
        8  - DPC  - Document pointer compression
        9  - DPUC - Document pointer ultra compression
        ----- Reduction traits -----
        10 - SS   - Whether entries are sorted by the strings in lexical order
        11 - UD   - Whether entries contain unique pairs of string - document id
        ----- Block presence -----
        12 - DB   - Whether contains document block
        ----- External references -----
        13 - ES   - Whether strings block is within another file
        14 - ED   - Whether document block is within another file
        ----- Reserved -----
        bits from 15 to 31 are reserved for future use

        TODO: Implement DEFLATE string compression
        TODO: DocumentID -> DocumentInfo lookup table

    COMPRESSION FLAGS:
        Flags from 0 to 9 are called compression flags. They determine size of corresponding elements.
        There is two flags: compression (C) and ultra compression (UC).
        Size of element is determined by the next criteria:
        if compression flag (C) is set to 0, element size is 4 bytes
        otherwise elements are either 2 or 1 bytes in size;
        if compression flag (C) is 0 then ultra compression (UC) flag is ignored.
        otherwise if ultra compression flag is 0, element size is 2 bytes, if is 1 - 1 byte.
        This can be illustrated clearly by the next table where C values are in columns, and UC - in rows:
        ___|____0____|____1____| C
        _0_|_4_bytes_|_2_bytes_|
        _1_|_4_bytes_|_1_bytes_|
        UC

        or by following pseudo code:
        if C then {
            if UC then size = 1
            else size = 2
        } else size = 4

    STRINGS:
        Length of strings block is written in the 4 bytes offset by the 4 bytes from the beginning of the file.
        Those bytes encode length as unsigned int32 value.
        String entry itself is comprised from two blocks:
        | x bytes | y bytes |
        | length  |  string |
        Where size of x is determined by the header flags SLC and SLUC (see HEADER:COMPRESSION FLAGS)
        First block contains information about length of the next one (y) encoded as either uint8, uint16 or uint32
        Second block is UTF-8 encoded string. NOTE: It is not null terminated, but the size of it is encoded as y

        If ES flag is set, instead of inline strings withing strings block, they are located in another file.
        With ES flag strings block contain only a single UTF-8 encoded string that represents a path to a file.
        Length of said string is determined by the size of strings block.
        Strings block file itself is structured in the same manner as if it were to be inlined inside SPIMI file.
        It does not contain other blocks, such as body, document or flag blocks, besides strings block.

    DOCUMENTS:
        Length of documents block is written in the 4 bytes offset by the 8 bytes from the beginning of the file.
        Those bytes encode length as unsigned int32 value.
        Whether documents lookup should occur is determined by the DB flag.
        It is not recommended to set block size to anything other than 0 is DB is set to off as whole block will just be ignored.
        Document entry consists of two blocks:
        | x bytes |  y bytes  |
        |  size   | documents |
        Where size of x is determined by the header flags DSC and DSUC (see HEADER:COMPRESSION FLAGS)
        First block contains information about amount of document ids in the next block encoded as either uint8, uint16 or uint32
        Second block contains a sequence of document ids. The length of the sequence is set in the first block.
        Size of a single sequence entry (document id) is determined by flags DIC and DIUC
        If `s` is a size of sequence, and `b` is a size of a document id entry, then size of the second block b = s * b

        If DS flag is set, instead of inline document sequences withing documents block, they are located in another file.
        With DS flag documents block contain only a single UTF-8 encoded string that represents a path to a file.
        Length of said string is determined by the size of strings block.
        Documents block file itself is structured in the same manner as if it were to be inlined inside SPIMI file.
        It does not contain other blocks, such as body, strings or flag blocks, besides documents block.

BODY:
    Consists entirely of document - string pairs,
    where document is either document id or file pointer to the sequence of document ids in document block,
    and string is a file pointer to the string entry in the strings block.
    Whether document is id or pointer is determined by the DB flag (whether document block is present).
    If DB is 0, document is id, else document is a file pointer.

    Size of pairs are consistent and determined by the size of document id or document pointer
    and by the size of string pointer, which are all the same withing a single file.
    This allows random access to a pair by it's id.

    As noted, there is two cases: with document block present and with it absent:

    WITHOUT DOCUMENT BLOCK:
        The entry has next structure:
        |   x bytes   |    y bytes     |
        | document id | string pointer |
        Where first block size is determined by the SPC and SPUC flags.
        First block contains document id. And it's size is determined by the DIC and DIUC flags.
        Second block contains file pointer to the string entry (see HEADER:STRINGS) in the strings block.

    WITH DOCUMENT BLOCK:
        The entry has next structure:
        |          x bytes          |    y bytes     |
        | document sequence pointer | string pointer |
        Where first block size is determined by the SPC and SPUC flags.
        First block contains file pointer to the document sequence (see HEADER:DOCUMENTS) in the documents block.
        Second block contains file pointer to the string entry (see HEADER:STRINGS) in the strings block.
 */

@ExperimentalUnsignedTypes
fun split(long: ULong): Pair<UInt, UInt> =
    Pair((long shr 32).toUInt(), long.toUInt())

@ExperimentalUnsignedTypes
fun combine(left: UInt, right: UInt): ULong =
    (left.toULong() shl 32) or right.toULong()

@ExperimentalUnsignedTypes
inline class WordLong(val value: ULong) {
    val wordID get() = value.shr(32).toUInt()
    val docID get() = value.toUInt()
    val pair get() = split(value)

    constructor(wordID: UInt, docID: UInt): this(combine(wordID, docID))
    constructor(wordID: UInt, documentID: DocumentID): this(wordID, documentID.id.toUInt())

    override fun toString() = "WordLong(wordID=$wordID, docID=$docID)"
}
