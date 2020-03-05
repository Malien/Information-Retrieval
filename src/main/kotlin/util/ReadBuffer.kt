package util

import java.io.Closeable
import java.io.EOFException

const val READ_BLOCK_SIZE = 4096

class BufferOverflowException(message: String) : Exception(message)

class ReadBuffer(
    size: Int = READ_BLOCK_SIZE,
    val onRead: ReadBuffer.(array: ByteArray, offset: Int, length: Int) -> Int,
    val onClose: () -> Unit = {}
) : Closeable {
    val buffer = ByteArray(size)
    var used = 0
        private set
    var available = 0
        private set
    var bytesRead = 0L
        private set

    val freeSpace get() = buffer.size - used
    val availableToUse get() = available - used

    fun read(arr: ByteArray, offset: Int = 0, length: Int = arr.size) = request<Unit>(length,
        getFunc = {
            buffer.copyInto(arr, destinationOffset = offset, startIndex = used, endIndex = used + length)
            System.arraycopy(buffer, used, arr, offset, length)
        }, overflow = {
            System.arraycopy(buffer, used, arr, offset, availableToUse)
            onRead(arr, offset + availableToUse, length - availableToUse)
            used = buffer.size
        })

    @ExperimentalUnsignedTypes
    fun readLong() = request(8) { buffer.decodeLong(used) }

    @ExperimentalUnsignedTypes
    fun readInt() = request(4) { buffer.decodeInt(used) }

    @ExperimentalUnsignedTypes
    fun readShort() = request(2) { buffer.decodeShort(used) }

    @ExperimentalUnsignedTypes
    fun readByte() = request(1) { buffer[used] }

    fun skip(length: Int) = request(length) {}

    private inline fun <reified T> request(size: Int, getFunc: () -> T) =
        request(size, getFunc) { throw BufferOverflowException("Buffer size is too small to read primitive value") }

    private inline fun <reified T> request(size: Int, getFunc: () -> T, overflow: () -> T): T {
        if (used + size > availableToUse) {
            if (freeSpace < size) {
                val res = overflow()
                bytesRead += size
                return res
            } else {
                buffer.copyInto(buffer, destinationOffset = 0, startIndex = used, endIndex = availableToUse)
                available = onRead(buffer, availableToUse, buffer.size - availableToUse)
                if (available == -1) throw EOFException()
                used = 0
            }
        }
        val res = getFunc()
        used += size
        bytesRead += size
        return res
    }

    override fun close() = onClose()
}