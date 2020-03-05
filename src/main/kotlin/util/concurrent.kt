package util

import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class Future<T : Any>(private val getter: () -> T) {
    var value: T? = null
        private set
    var done = false
        private set

    fun get(): T = if (done) value!! else {
        value = getter()
        done = true
        value!!
    }
}

val taskCount = AtomicInteger()
fun <R : Any> async(block: () -> R): Future<R> {
    var returnValue: R? = null
    val executor = thread(name = "Async-${taskCount.getAndIncrement()}") {
        returnValue = block()
    }
    return Future {
        executor.join()
        returnValue!!
    }
}
