package dict.cluster

import dict.Document
import dict.DocumentID
import dict.DocumentRegistry
import dict.spimi.tokenSequence
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.random.Random

fun map(file: File, id: DocumentID): DocumentVec {
    val br = BufferedReader(FileReader(file))
    val vec = DocumentVec(id)
    for (token in br.tokenSequence) {
        vec.add(token)
    }
//    vec.normalize()
    return vec
}

@ExperimentalUnsignedTypes
fun map(
    files: List<Document>,
    registry: DocumentRegistry
): DocumentCollection {
    val mapped = files.map {
        val id = registry.register(it)
        map(it.file, id)
    }
    val idf = InvertedDocumentFrequency.fromDocuments(mapped.iterator())
//    for ((_, vec) in mapped) {
//        vec.relateTo(idf)
//    }
    return DocumentCollection(mapped.sortedBy { it.id }, idf)
}

fun <T> select(list: List<T>, length: Int): List<T> {
    val indices = IntArray(list.size) { it }
    for (i in list.indices) {
        val mv = Random.nextInt(until = list.size)
        val tmp = indices[i]
        indices[i] = indices[mv]
        indices[mv] = tmp
    }
    return List(length) { list[indices[it]] }
}

@ExperimentalUnsignedTypes
fun closest(leaders: List<DocumentVec>, vec: DocumentVec) = leaders.maxBy { it.cos(vec) }

@ExperimentalUnsignedTypes
fun closestIndex(leaders: List<DocumentVec>, vec: DocumentVec) =
    leaders.withIndex().maxBy { (_, leader) -> leader.cos(vec) }?.index ?: -1

fun bm25score(
    query: Sequence<String>,
    document: DocumentVec,
    idf: InvertedDocumentFrequency,
    averageDocumentLength: Double,
    k: Double = 1.5,
    b: Double = 0.75
) = query.fold(0.0) { acc, term ->
    val frequency = document.vector[term] ?: return acc
    val invertedFrequency = idf[term] ?: return acc

    val top = invertedFrequency * frequency * (k + 1)
    val bottom = frequency + k * (1 - b + b * document.vector.size / averageDocumentLength)

    acc + top / bottom
}
