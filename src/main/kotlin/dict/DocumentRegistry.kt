package dict

import kotlinx.serialization.Serializable
import util.TreeMapArraySerializer
import java.util.*

@Serializable
class DocumentRegistry: Iterable<DocumentID> {

    var documentCount = 0
        private set

    @Serializable(with = TreeMapArraySerializer::class)
    private val documents: TreeMap<DocumentID, String> = TreeMap()

    fun register(path: String): DocumentID =
        DocumentID(documentCount++).also { documents[it] = path }

    fun deregister(id: DocumentID) =
        if (id in documents) {
            documents.remove(id)
            true
        } else false

    fun path(id: DocumentID) = documents[id]

    override fun iterator() = documents.keys.iterator()
}