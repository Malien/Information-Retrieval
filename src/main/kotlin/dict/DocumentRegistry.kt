package dict

import kotlinx.serialization.Serializable
import util.serialization.TreeMapArraySerializer
import java.util.*

@Serializable
@ExperimentalUnsignedTypes
class DocumentRegistry: Iterable<DocumentID> {

    var documentCount = 0
        private set

    @Serializable(with = TreeMapArraySerializer::class)
    private val documents: TreeMap<DocumentID, Document> = TreeMap()

    fun register(document: Document): DocumentID =
        DocumentID(documentCount++).also { documents[it] = document }

    fun deregister(id: DocumentID) =
        if (id in documents) {
            documents.remove(id)
            true
        } else false

    fun path(id: DocumentID) = documents[id]?.file?.path

    fun document(at: DocumentID) = documents[at]

    override fun iterator() = documents.keys.iterator()

}