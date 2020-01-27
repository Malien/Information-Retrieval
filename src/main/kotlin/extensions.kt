import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
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

fun <T:Comparable<T>> ArrayList<T>.insertSorted(value: T) =
    insert(value, findSorted(value))

fun Double.round(digits: Int = 0) = (10.0.pow(digits) * this).roundToInt() / 10.0.pow(digits)

suspend fun AsynchronousFileChannel.read(buf: ByteBuffer): Int =
    suspendCoroutine { cont ->
        read(buf, 0L, Unit, object: CompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit) {
                cont.resume(result)
            }

            override fun failed(exc: Throwable, attachment: Unit) {
                cont.resumeWithException(exc)
            }
        })
    }

val is64bit: Boolean get() =
    if (System.getProperty("os.name").contains("Windows")) {
        System.getenv("ProgramFiles(x86)") != null
    } else {
        System.getProperty("os.arch").indexOf("64") != -1
    }

val Long.megabytes get() = (this / 1024 / 1024.0).round(2)