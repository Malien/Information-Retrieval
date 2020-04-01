package dict.spimi

import java.io.Closeable

@ExperimentalUnsignedTypes
interface SPIMIDict: Closeable {
    val count: UInt

    fun find(word: String): RankedDocuments
    fun delete()

    val manifest: Manifest
}