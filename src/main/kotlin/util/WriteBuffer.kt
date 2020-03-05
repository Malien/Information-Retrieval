package util

import java.io.Closeable

const val WRITE_BLOCK_SIZE = 4096

// Might consider doing what java does and just wrap OutputStream instead of using callbacks
// as it doesn't operate on anything else than streams of files
class WriteBuffer(
    size: Int = WRITE_BLOCK_SIZE,
    val onWrite: (buffer: ByteArray, offset: Int, length: Int) -> Unit,
    val onClose: () -> Unit = {}
) : Closeable {
    val buffer = ByteArray(size)
    var ready = 0
        private set
    var bytesWritten = 0L
        private set

    fun add(arr: ByteArray, offset: Int = 0, length: Int = arr.size) = dump(length) {
        System.arraycopy(arr, offset, buffer, ready, length)
    }

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