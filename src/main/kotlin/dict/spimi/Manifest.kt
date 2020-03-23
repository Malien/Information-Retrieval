package dict.spimi

import kotlinx.serialization.Serializable

@Serializable
data class Manifest(val rootDictionary: String? = null, val ranges: ArrayList<Range>? = null) {
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
        else SPIMIMultiFile(ranges!!.map { SPIMIFile(it.dictionary) })
}
