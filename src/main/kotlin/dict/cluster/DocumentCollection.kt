package dict.cluster

import dict.spimi.RankedDocument
import dict.spimi.RankedDocuments
import dict.spimi.tokenSequence
import util.keySet

// DOCS HAVE TO BE SORTED
data class DocumentCollection(val documents: List<DocumentVec>, val idf: InvertedDocumentFrequency) {
    private val averageDocumentLength = documents.sumBy { it.vector.size } / documents.size

    fun find(query: String): RankedDocuments {
        val querySequence = query.tokenSequence
        return documents.asSequence()
            .map {
                val score = bm25score(querySequence, it, idf, averageDocumentLength.toDouble())
                RankedDocument(it.id, score)
            }.filter { it.rating > 0 }
            .iterator().keySet
    }
}