package parser.fb2

import dict.BookZone
import dict.Document
import java.io.BufferedReader
import java.util.*


fun BufferedReader.readUntil(delimiter: Char) = buildString {
    while (true) {
        val valueRead = read()
        if (valueRead == -1) return@buildString
        val ch = valueRead.toChar()
        if (ch == delimiter) return@buildString
        append(ch)
    }
}

fun BufferedReader.seek(delimiter: Char) {
    while (true) {
        val valueRead = read()
        if (valueRead == -1) return
        if (valueRead.toChar() == delimiter) return
    }
}

val String.isSelfClosing: Boolean
    get() {
        var found = false
        for (ch in this) {
            when (ch) {
                '/' -> found = true
                '>' -> return found
                ' ', '\t', '\n', '\r' -> Unit
                else -> found = false
            }
        }
        return found
    }

fun String.isFirstEncounter(char: Char): Boolean {
    for (ch in this) {
        when (ch) {
            ' ', '\t', '\n', '\r' -> Unit
            char -> return true
            else -> return false
        }
    }
    return false
}

val String.isClosing: Boolean get() = isFirstEncounter('/')

val String.isQuestioned: Boolean get() = isFirstEncounter('?')

fun String.Companion.repeat(char: Char, size: Int) = String(CharArray(size) { char })

@ExperimentalUnsignedTypes
data class BookItem(val contents: String, val flags: BookZone)


@ExperimentalUnsignedTypes
fun parseBook(reader: BufferedReader, book: Document? = null) = sequence {
    val stack = Stack<String>()

    var fb = false
    var description = false
    var titleInfo = false
    var author = false
    var annotation = false
    var body = false

    var startedFB = false

    reader.seek('<')
    while (stack.isNotEmpty() || !startedFB) {
        val tag = reader.readUntil('>')
        if (tag.isClosing) {
            val tagName = stack.pop()
//            println("${String.repeat('\t', stack.size)}</$tagName>")
            reader.seek('<')
            when {
                fb && tagName == "FictionBook" -> fb = false
                description && tagName == "description" -> description = false
                titleInfo && tagName == "titleInfo" -> titleInfo = false
                annotation && tagName == "annotation" -> annotation = false
                author && tagName == "author" -> author = false
                body && !description && tagName == "body" -> body = false
            }
        } else {
            val tagName = tag.substringBefore(' ')
//            println("${String.repeat('\t', stack.size)}<$tagName>")
            if (!tag.isSelfClosing && !tag.isQuestioned) {
                stack.push(tagName)
                when {
                    !fb && tagName == "FictionBook" -> {
                        fb = true
                        startedFB = true
                    }
                    !description && tagName == "description" -> description = true
                    !titleInfo && tagName == "title-info" -> titleInfo = true
                    !annotation && tagName == "annotation" -> annotation = true
                    !author && tagName == "author" -> author = true
                    !body && !description && tagName == "body" -> body = true
                }

                val contents = reader.readUntil('<')
                when {
                    tagName == "binary" -> { }
                    titleInfo && tagName == "genre" -> book?.genre = contents
                    author && tagName == "first-name" -> {
                        book?.authorName = contents
                        yield(BookItem(contents, BookZone.ofAuthor))
                    }
                    author && tagName == "last-name" -> {
                        book?.authorLastName = contents
                        yield(BookItem(contents, BookZone.ofAuthor))
                    }
                    titleInfo && tagName == "book-title" -> {
                        book?.title = contents
                        yield(BookItem(contents, BookZone.ofTitle))
                    }
                    annotation -> yield(BookItem(contents, BookZone.ofAnnotation))
                    body -> yield(BookItem(contents, BookZone.ofBody))
                }
            } else {
                reader.seek('<')
//                println("${String.repeat('\t', stack.size)}</$tagName>")
            }
        }
    }
}
