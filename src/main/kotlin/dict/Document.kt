package dict

import kotlinx.serialization.Serializable
import util.serialization.FileSerializer
import java.io.File

@ExperimentalUnsignedTypes
inline class BookZone(val flags: UByte) {
    operator fun get(idx: Int) =
        (flags.toUInt() shr idx) and 1u == 1u

    val author
        get() = get(0)

    val title
        get() = get(1)

    val annotation
        get() = get(2)

    val body
        get() = get(3)

    override fun toString() = buildString {
        append("BookZone(")
        append(sequence {
            if (author) yield("author")
            if (title) yield("title")
            if (annotation) yield("annotation")
            if (body) yield("body")
        }.joinToString(separator = ", "))
        append(')')
    }

    companion object {
        val ofAuthor = BookZone(1u)
        val ofTitle = BookZone(2u)
        val ofAnnotation = BookZone(4u)
        val ofBody = BookZone(8u)
    }
}

@ExperimentalUnsignedTypes
@Serializable
class Document(
    @Serializable(with = FileSerializer::class) val file: File,
    var authorName: String? = null,
    var authorLastName: String? = null,
    var title: String? = null,
    var genre: String? = null
) {
    override fun toString() = "Book($file, authorName=$authorName, authorLastName=$authorLastName, " +
            "title=$title, genre=$genre)"
}
