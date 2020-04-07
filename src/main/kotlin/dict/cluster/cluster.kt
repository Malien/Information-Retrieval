package dict.cluster

import dict.Document
import dict.DocumentID
import dict.DocumentRegistry
import dict.spimi.tokenSequence
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.random.Random

fun map(file: File): DocumentVec {
    val br = BufferedReader(FileReader(file))
    val vec = DocumentVec()
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
): Pair<List<Pair<DocumentID, DocumentVec>>, InvertedDocumentFrequency> {
    val mapped = files.map {
        registry.register(it) to map(it.file)
    }
    val idf = InvertedDocumentFrequency.fromDocuments(mapped.asSequence().map { it.second }.iterator())
    for ((_, vec) in mapped) {
        vec.relateTo(idf)
    }
    return mapped to idf
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
