import kotlin.math.pow
import kotlin.math.roundToInt

val <T:Comparable<T>> ArrayList<T>.isSorted: Boolean
    get() {
        var prev: T? = null
        for (next in this) {
            if (prev == null) prev = next
            else if (prev < next) return false
        }
        return true
    }

fun <T:Comparable<T>> ArrayList<T>.findSorted(value: T): Int {
    assert(isSorted)
    var lo = 0
    var hi = size
    while (lo != hi) {
        val mid = (lo + hi) / 2
        when {
            this[mid] > value -> { hi = mid }
            this[mid] < value -> { lo = mid }
            else -> return mid
        }
    }
    return lo
}

fun <T> ArrayList<T>.insert(value: T, idx: Int) {
    var current = value
    for (i in idx until size) {
        val tmp = this[i]
        this[i] = current
        current = tmp
    }
    add(current)
}

fun <T:Comparable<T>> ArrayList<T>.insertSorted(value: T) =
    insert(value, findSorted(value))

fun Double.round(digits: Int = 0) = (10.0.pow(digits) * this).roundToInt() / 10.0.pow(digits)
