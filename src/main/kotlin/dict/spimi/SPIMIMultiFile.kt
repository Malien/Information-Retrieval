package dict.spimi

import dict.Documents

@ExperimentalUnsignedTypes
class SPIMIMultiFile(files: List<SPIMIFile>): SPIMIDict {
    @ExperimentalUnsignedTypes
    override val entries: UInt
        get() = TODO("Not yet implemented")

    override fun find(word: String): Documents {
        TODO("Not yet implemented")
    }

    override fun delete() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}