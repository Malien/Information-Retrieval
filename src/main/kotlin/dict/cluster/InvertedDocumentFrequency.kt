package dict.cluster

import kotlin.math.ln

inline class InvertedDocumentFrequency(val index: HashMap<String, Double> = HashMap()) {

    operator fun get(str: String) = index[str]

    companion object {
        fun fromDocuments(documents: Iterator<DocumentVec>): InvertedDocumentFrequency {
            val idf = InvertedDocumentFrequency()
            var size = 0.0
            for (document in documents) {
                size++
                for (term in document.vector.keys) {
                    val count = idf.index[term] ?: 0.0
                    idf.index[term] = count + 1
                }
            }

            for (entry in idf.index) {
                entry.setValue(ln(size / entry.value))
            }
            return idf
        }
    }
}
