package util

import kotlinx.serialization.toUtf8Bytes
import java.io.OutputStream

class FancyConsole(val outputStream: OutputStream) : Console {
    private var statusBytes: ByteArray = ByteArray(0)
    override var statusLine: String
        get() = String(statusBytes)
        set(value) {
            val newStatusBytes = value.toUtf8Bytes()
            val redrawBytes = if (newStatusBytes.size > statusBytes.size) {
                ByteArray(newStatusBytes.size + 1).also {
                    it[0] = 13
                    newStatusBytes.copyInto(it, destinationOffset = 1)
                }
            } else {
                ByteArray(statusBytes.size + 1).also {
                    it[0] = 13
                    newStatusBytes.copyInto(it, destinationOffset = 1)
                    it.fill(32, fromIndex = newStatusBytes.size + 1)
                }
            }
            statusBytes = newStatusBytes
            outputStream.write(redrawBytes)
        }

    override fun println(msg: String) {
        val msgBytes = msg.toUtf8Bytes()
        val redrawBytes = if (msgBytes.size > statusBytes.size)
            ByteArray(statusBytes.size + msgBytes.size + 2).also {
                it[0] = 13
                msgBytes.copyInto(it, destinationOffset = 1)
                it[msgBytes.size + 1] = 10
                statusBytes.copyInto(it, destinationOffset = msgBytes.size + 2)
            }
        else
            ByteArray(statusBytes.size * 3 + 1).also {
                it[0] = 13
                msgBytes.copyInto(it, destinationOffset = 1)
                it.fill(32, fromIndex = msgBytes.size + 1, toIndex = statusBytes.size + 1)
                it[statusBytes.size + 1] = 10
                statusBytes.copyInto(it, destinationOffset = statusBytes.size + 2)
            }
        outputStream.write(redrawBytes)
    }

}