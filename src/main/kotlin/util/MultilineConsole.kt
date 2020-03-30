package util

import java.io.OutputStream

// Well, this is a class with two methods, one of which is init, which makes this construct just a function.
class MultilineConsole(
    lineCount: Int,
    private val stream: OutputStream = System.out,
    initialLine: (idx: Int) -> String = { "" }
) {
    val lines = Array(lineCount, initialLine)

    operator fun set(idx: Int, line: String) {
        val diff = lines.size - 1 - idx
        val drawInstructions = "\r\\033[${diff}A$line"
        stream.write(drawInstructions.toByteArray())
    }
}