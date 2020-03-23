package dict.spimi

import kotlinx.serialization.Serializable

@Serializable
data class Manifest(val rootDictionary: String? = null, val ranges: List<Range>? = null) {
    init {
        if (rootDictionary != null && ranges != null)
            throw IllegalArgumentException("Either rootDictionary or ranges should be specified. Not both")
        if (rootDictionary == null && ranges == null)
            throw IllegalArgumentException("Either rootDictionary or ranges should be specified")
    }

    val ranged get() = ranges != null

    @ExperimentalUnsignedTypes
    fun openDict(): SPIMIDict =
        if (rootDictionary != null) SPIMIFile(rootDictionary)
        else if (ranges != null) {
            val dicts = ranges.map { SPIMIFile(it.dictionary) }
            val delimiters = ranges.asSequence()
                .map { it.lowerLimit }
                .filterNotNull()
                .sortedBy { it }
                .toMutableList()
                .toTypedArray()
            SPIMIMultiFile(dicts, delimiters)
        } else throw RuntimeException("How did you managed to initialize Manifest without either rootDictionary or ranges?")
}
