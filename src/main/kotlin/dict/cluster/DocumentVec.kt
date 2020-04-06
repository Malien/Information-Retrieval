package dict.cluster

import util.IntPair
import java.util.*
import kotlin.math.sqrt

@ExperimentalUnsignedTypes
inline class DocumentVec(private val vector: TreeMap<String, UInt>) {
    fun norm() = sqrt(vector.values.fold(0u, { acc, v -> acc + v * v }).toDouble())

    fun cos(other: DocumentVec) =
        (this dot other).toDouble() / norm() / other.norm()

    infix fun dot(rhs: DocumentVec) =
        this.zip(rhs).asSequence().fold(0, { acc, (l, v) -> acc + (l * v).toInt() })

    infix fun zip(rhs: DocumentVec): Iterator<IntPair> = iterator {
        val leftIter = vector.iterator()
        val rightIter = vector.iterator()
        if (!leftIter.hasNext()) {
            for ((_, k) in leftIter) yield(IntPair(0u, k))
        }
        if (!rightIter.hasNext()) {
            for ((_, k) in rightIter) yield(IntPair(k, 0u))
        }
        var left = leftIter.next()
        var right = rightIter.next()

        while (leftIter.hasNext() && rightIter.hasNext()) {
            val cmp = left.key.compareTo(right.key)
            when {
                cmp > 0 -> {
                    yield(IntPair(0u, right.value))
                    right = rightIter.next()
                }
                cmp < 0 -> {
                    yield(IntPair(left.value, 0u))
                    left = leftIter.next()
                }
                else -> {
                    yield(IntPair(left.value, right.value))
                    left = leftIter.next()
                    right = rightIter.next()
                }
            }
        }
    }
}
