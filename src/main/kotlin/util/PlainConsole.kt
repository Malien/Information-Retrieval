package util

import kotlinx.serialization.toUtf8Bytes
import java.io.OutputStream

class PlainConsole(private val outputStream: OutputStream): Console {
    override var statusLine = ""
        set(value) {
            println(value)
            field = value
        }

    override fun println(msg: String) {
        val msgBytes = msg.toUtf8Bytes()
        val writeBytes = ByteArray(msgBytes.size + 1)
        msgBytes.copyInto(writeBytes)
        writeBytes[writeBytes.size - 1] = 10
        outputStream.write(writeBytes)
    }
}