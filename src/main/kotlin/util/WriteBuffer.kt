package util

import util.kotlinx.encodeInt
import util.kotlinx.encodeLong
import util.kotlinx.encodeShort
import java.io.Closeable

const val WRITE_BLOCK_SIZE = 4096
@ExperimentalUnsignedTypes
const val vbMask = 0b1111111

/**
 * Buffer that automatically fetches data from stream and buffers it.
 * @param size size of a buffer
 * @param onWrite callback that is fired when buffer needs more data.
 *                It is made to be compatible in signature with stream::read
 * @param onClose optional. Callback that is fired when buffer is to be closed.
 *                Should be used to close underlying streams.
 * NOTE: Might consider doing what java does and just wrap OutputStream instead of using callbacks
 * as it doesn't operate on anything else than streams of files. It actually makes more sense
 */
class WriteBuffer(
    val size: Int = WRITE_BLOCK_SIZE,
    val onWrite: (arr: ByteArray, offset: Int, length: Int) -> Unit,
    val onClose: () -> Unit = {}
) : Closeable {
    val buffer = ByteArray(size)
    var ready = 0
        private set
    var bytesWritten = 0L
        private set

    fun add(arr: ByteArray, offset: Int, length: Int) = dump(length) {
        System.arraycopy(arr, offset, buffer, ready, length)
    }

    fun add(arr: ByteArray) = add(arr, 0, arr.size)

    fun add(long: Long) = dump(8) {
        buffer.encodeLong(ready, long)
    }

    fun add(int: Int) = dump(4) {
        buffer.encodeInt(ready, int)
    }

    fun add(short: Short) = dump(2) {
        buffer.encodeShort(ready, short)
    }

    fun add(byte: Byte) = dump(1) {
        buffer[ready] = byte
    }

    @ExperimentalUnsignedTypes
    fun encodeVariable(int: Int): Int {
        var res = int
        var iterations = 0
        while (res > 127) {
            dump(1) {
                buffer[ready] = (res and vbMask).toByte()
                res = res shr 7
                iterations++
            }
        }
        dump(1) {
            buffer[ready] = (res + 128).toByte()
        }
        return iterations + 1
    }

    fun skip(length: Int) = dump(length) { }

    fun flush() {
        onWrite(buffer, 0, ready)
        ready = 0
    }

    private inline fun dump(size: Int, setFunc: () -> Unit) {
        if (ready + size > buffer.size) {
            onWrite(buffer, 0, ready)
            ready = 0
        }
        setFunc()
        ready += size
        bytesWritten += size
    }

    override fun close() {
        flush()
        onClose()
    }
}