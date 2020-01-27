import kotlin.math.pow
import kotlin.math.roundToInt

fun <T> Array<T>.binarySearch(fromIndex: Int = 0, toIndex: Int = this.size, comparison: (T) -> Int): Int {
    var lo = fromIndex
    var hi = toIndex
    while (lo <= hi) {
        val mid = (lo + hi)/2
        val cmp = comparison(this[mid])
        when {
            cmp > 0 -> { hi = mid - 1 }
            cmp < 0 -> { lo = mid + 1}
            else -> return mid
        }
    }
    return -lo-1
}

fun Double.round(digits: Int = 0) = (10.0.pow(digits) * this).roundToInt() / 10.0.pow(digits)

val Long.megabytes get() = (this / 1024 / 1024.0).round(2)