package util.kotlinx

import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Rounds number to the specified decimal place after dot
 * @param digits digits to be left after dot
 * @return rounded number
 */
fun Double.round(digits: Int = 0) = (10.0.pow(digits) * this).roundToInt() / 10.0.pow(digits)

/**
 * Converts to a printable-ish representation of value in megabytes (if value specifies size in bytes)
 */
val Long.megabytes get() = (this / 1024 / 1024.0).round(2)

/**
 * Retrieves the sequence of files in the directory with optionally provided extension name
 * @param path path to a directory
 * @param extension optional. Extension of files to be included in the sequence
 * @return sequence of files
 */
fun getFiles(path: String, extension: String? = null): Sequence<File> {
    val directory = File(path)
    if (!directory.exists() && !directory.isDirectory) return emptySequence()
    val files = directory.list { dir, name ->
        val file = File(dir, name)
        file.exists() && file.isFile && (extension == null || file.extension == extension)
    } ?: emptyArray()
    return files.asSequence().map { File(directory, it) }
}
