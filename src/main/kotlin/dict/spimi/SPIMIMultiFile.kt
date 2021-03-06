package dict.spimi

@ExperimentalUnsignedTypes
class SPIMIMultiFile(private val files: List<SPIMIFile>, private val delimiters: Array<String>) : SPIMIDict {

    override val count: UInt = files.fold(0u) { acc, file -> acc + file.count }

    fun enclosingFile(word: String): SPIMIFile {
        for ((idx, delimiter) in delimiters.withIndex()) {
            if (word < delimiter) return files[idx]
        }
        return files.last()
    }

    override fun find(word: String): RankedDocuments = enclosingFile(word).find(word)

    override fun delete() {
        for (file in files) file.delete()
    }

    override fun close() {
        for (file in files) file.close()
    }

    override val manifest by lazy {
        Manifest(ranges = files.mapIndexed { idx, file ->
            Range(
                dictionary = file.filepath,
                lowerLimit = if (idx < delimiters.size) delimiters[idx] else null
            )
        })
    }
}