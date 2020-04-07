package dict.cluster

import java.util.*
import kotlin.math.sqrt

data class DocumentVec(val vector: TreeMap<String, Double> = TreeMap()) {
    var words = 0
        private set

    fun norm() = sqrt(vector.values.fold(0.0, { acc, v -> acc + v * v }))

    fun cos(other: DocumentVec) =
        (this dot other) / norm() / other.norm()

    infix fun dot(rhs: DocumentVec) =
        this.zipSame(rhs).asSequence().fold(0.0, { acc, (l, v) -> acc + l * v })

    infix fun zip(rhs: DocumentVec) = iterator {
        val leftIter = vector.iterator()
        val rightIter = vector.iterator()
        if (!leftIter.hasNext()) {
            for ((_, k) in leftIter) yield(0.0 to k)
        }
        if (!rightIter.hasNext()) {
            for ((_, k) in rightIter) yield(k to 0.0)
        }
        var left = leftIter.next()
        var right = rightIter.next()

        while (leftIter.hasNext() && rightIter.hasNext()) {
            val cmp = left.key.compareTo(right.key)
            when {
                cmp > 0 -> {
                    yield(0.0 to right.value)
                    right = rightIter.next()
                }
                cmp < 0 -> {
                    yield(left.value to 0.0)
                    left = leftIter.next()
                }
                else -> {
                    yield(left.value to right.value)
                    left = leftIter.next()
                    right = rightIter.next()
                }
            }
        }
    }

    infix fun zipSame(rhs: DocumentVec) = iterator {
        val leftIter = vector.iterator()
        val rightIter = rhs.vector.iterator()
        if (!leftIter.hasNext()) return@iterator
        if (!rightIter.hasNext()) return@iterator
        var left = leftIter.next()
        var right = rightIter.next()

        while (leftIter.hasNext() && rightIter.hasNext()) {
            val cmp = left.key.compareTo(right.key)
            when {
                cmp > 0 -> right = rightIter.next()
                cmp < 0 -> left = leftIter.next()
                else -> {
                    yield(left.value to right.value)
                    left = leftIter.next()
                    right = rightIter.next()
                }
            }
        }
    }

    fun add(token: String) {
        words++
        val value = vector.getOrPut(token) { 0.0 }
        vector[token] = value + 1
    }

    fun normalize() {
        for (entry in vector) {
            entry.setValue(entry.value / words)
        }
    }

    fun relateTo(idf: InvertedDocumentFrequency) {
        for (entry in vector) {
            entry.setValue(idf[entry.key] * entry.value)
        }
    }

}
