/**
 * TODO: Write tests
 */
package util

/**
 * Set of SORTED keys from SORTED iterator
 */
data class KeySet<T : Comparable<T>> (val iterator: Iterator<T>, val negated: Boolean = false): Iterable<T> {
    override fun iterator() = iterator
}

val <T:Comparable<T>> Iterator<T>.keySet get() = KeySet(this)

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
    test(unite(
        KeySet(a.iterator()),
        KeySet(b.iterator())
    ))

    println("--- All or None ---")
    test(unite(
        KeySet(all.iterator()),
        KeySet(none.iterator())
    ))

    println("--- A and B ---")
    test(cross(
        KeySet(a.iterator()),
        KeySet(b.iterator())
    ))

    println("--- All and None ---")
    test(cross(
        KeySet(all.iterator()),
        KeySet(none.iterator())
    ))

    println("--- not(not A and not B) ---")
    test(cross(
        KeySet(a.iterator(), true),
        KeySet(b.iterator(), true)
    ))

    println("--- not(not A or not B) ---")
    test(unite(
        KeySet(a.iterator(), true),
        KeySet(b.iterator(), true)
    ))

    println("--- A and not B ---")
    test(cross(
        KeySet(a.iterator()),
        KeySet(b.iterator(), true)
    ))

    println("--- not A or B ---")
    test(unite(
        KeySet(a.iterator(), true),
        KeySet(b.iterator())
    ))
}
