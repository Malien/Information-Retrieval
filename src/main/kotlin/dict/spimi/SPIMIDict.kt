package dict.spimi

import dict.Documents
import java.io.Closeable

interface SPIMIDict: Closeable {
    @ExperimentalUnsignedTypes
    val entries: UInt

    fun find(word: String): Documents
    fun delete()

    val manifest: Manifest
}