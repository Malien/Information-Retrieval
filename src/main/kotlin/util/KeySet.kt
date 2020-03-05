/**
 * TODO: Write tests
 */
package util

/**
 * Set of SORTED keys from SORTED iterator
 */
data class KeySet<T : Comparable<T>> (val iterator: Iterator<T>, val negated: Boolean = false): Iterable<T> {
    constructor(array: Array<T>): this(array.iterator())
    override fun iterator() = iterator
}

val <T:Comparable<T>> Iterator<T>.keySet get() = KeySet(this)
val <T:Comparable<T>> Iterable<T>.keySet get() = KeySet(this.iterator())

private fun <T : Comparable<T>> uniteSame(lhs: Iterator<T>, rhs: Iterator<T>) : Iterator<T> {
    if (!lhs.hasNext()) return rhs
    if (!rhs.hasNext()) return lhs
    var leftValue = lhs.next()
    var rightValue = rhs.next()
    return iterator {
        while (lhs.hasNext() && rhs.hasNext()) {
            val cmp = leftValue.compareTo(rightValue)
            when {
                cmp > 0 -> {
                    yield(rightValue)
                    rightValue = rhs.next()
                }
                cmp < 0 -> {
                    yield(leftValue)
                    leftValue = lhs.next()
                }
                else -> {
                    yield(leftValue)
                    leftValue = lhs.next()
                    rightValue = rhs.next()
                }
            }
        }
        if (leftValue < rightValue) {
            yield(leftValue)
            yield(rightValue)
        } else {
            yield(rightValue)
            yield(leftValue)
        }
        yieldAll(lhs)
        yieldAll(rhs)
    }
}

fun <T : Comparable<T>> unite(lhs: KeySet<T>, rhs: KeySet<T>) = when {
     lhs.negated &&  rhs.negated -> KeySet(crossSame(lhs.iterator, rhs.iterator), true)
    !lhs.negated &&  rhs.negated -> KeySet(crossDifferent(rhs.iterator, lhs.iterator), true)
     lhs.negated && !rhs.negated -> KeySet(crossDifferent(lhs.iterator, rhs.iterator), true)
    !lhs.negated && !rhs.negated -> KeySet(uniteSame(lhs.iterator, rhs.iterator))
    else -> throw RuntimeException("This error shouldn't be possible")
}

private fun <T : Comparable<T>> crossSame(lhs: Iterator<T>, rhs: Iterator<T>): Iterator<T> {
    if (!lhs.hasNext()) return lhs
    if (!rhs.hasNext()) return rhs
    var leftValue = lhs.next()
    var rightValue = rhs.next()
    return iterator {
        while (lhs.hasNext() && rhs.hasNext()) {
            val cmp = leftValue.compareTo(rightValue)
            when {
                cmp > 0 -> { rightValue = rhs.next() }
                cmp < 0 -> { leftValue = lhs.next() }
                else -> {
                    yield(leftValue)
                    leftValue = lhs.next()
                    rightValue = rhs.next()
                }
            }
        }
        while (lhs.hasNext()) {
            if (lhs.next() == rightValue) yield(rightValue)
        }
        while (rhs.hasNext()) {
            if (rhs.next() == leftValue) yield(leftValue)
        }
        if (leftValue == rightValue) yield(leftValue)
    }
}

private fun <T : Comparable<T>> crossDifferent(lhs: Iterator<T>, rhs: Iterator<T>): Iterator<T> {
    if (!lhs.hasNext() || !rhs.hasNext()) return lhs
    var leftValue = lhs.next()
    var rightValue = rhs.next()
    return iterator {
        while (lhs.hasNext() && rhs.hasNext()) {
            val cmp = leftValue.compareTo(rightValue)
            when {
                cmp > 0 -> { rightValue = rhs.next() }
                cmp < 0 -> {
                    yield(leftValue)
                    leftValue = lhs.next()
                }
                else -> {
                    leftValue = lhs.next()
                    rightValue = rhs.next()
                }
            }
        }
        if (leftValue != rightValue) yield(leftValue)
    }
}

fun <T : Comparable<T>> cross(lhs: KeySet<T>, rhs: KeySet<T>) = when {
     lhs.negated &&  rhs.negated -> KeySet(uniteSame(lhs.iterator, rhs.iterator), true)
    !lhs.negated &&  rhs.negated -> KeySet(crossDifferent(lhs.iterator, rhs.iterator))
     lhs.negated && !rhs.negated -> KeySet(crossDifferent(rhs.iterator, lhs.iterator))
    !lhs.negated && !rhs.negated -> KeySet(crossSame(lhs.iterator, rhs.iterator))
    else -> throw RuntimeException("This error shouldn't be possible")
}

fun <T : Comparable<T>> negate(set: KeySet<T>) = KeySet(set.iterator, !set.negated)

// TODO: define same operations for pure iterators
operator fun <T:Comparable<T>> KeySet<T>.not() = negate(this)
infix fun <T:Comparable<T>> KeySet<T>.and(other: KeySet<T>) = cross(this, other)
infix fun <T:Comparable<T>> KeySet<T>.or (other: KeySet<T>) = unite(this, other)

private fun <T : Comparable<T>> test(set: KeySet<T>) {
    println(if (set.negated) "negated" else "straight")
    set.forEach { println(it) }
}

fun main() {
    val a = arrayOf(1,2)
    val b = arrayOf(1,3)
    val all = arrayOf(1,2,3,4)
    val none = emptyArray<Int>()

    println("--- A or B ---")
    test(KeySet(a) or KeySet(b))

    println("--- All or None ---")
    test(KeySet(all) or KeySet(none))

    println("--- A and B ---")
    test(KeySet(a) and KeySet(b))

    println("--- All and None ---")
    test(KeySet(all) and KeySet(none))

    println("--- not(not A and not B) ---")
    test(!KeySet(a) and !KeySet(b))

    println("--- not(not A or not B) ---")
    test(!KeySet(a) or !KeySet(b))

    println("--- A and not B ---")
    test(KeySet(a) and !KeySet(b))

    println("--- not A or B ---")
    test( !KeySet(a) or KeySet(b))
}
