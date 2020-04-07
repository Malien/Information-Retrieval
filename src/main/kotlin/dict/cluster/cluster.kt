package dict.cluster

import dict.spimi.tokenSequence
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.random.Random

@ExperimentalUnsignedTypes
fun map(file: File): DocumentVec {
    val br = BufferedReader(FileReader(file))
    val vec = DocumentVec()
    for (token in br.tokenSequence) {
        vec.add(token)
    }
    return vec
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
