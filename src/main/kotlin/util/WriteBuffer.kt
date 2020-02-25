package util

import dict.spimi.encodeInt
import dict.spimi.encodeLong
import dict.spimi.encodeShort

const val WRITE_BLOCK_SIZE = 4049

class WriteBuffer(val size: Int = WRITE_BLOCK_SIZE, val onWrite: (ByteArray, Int, Int) -> Unit) {
    val buffer = ByteArray(size)
    var ready = 0

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

    fun skip(length: Int) = dump(length) { }

    fun flush() {
        onWrite(buffer, 0, ready)
        ready = 0
    }

    inline fun dump(size: Int, setFunc: () -> Unit) {
        if (ready + size > buffer.size) {
            onWrite(buffer, 0, ready)
            ready = 0
        }
        setFunc()
        ready += size
    }
}