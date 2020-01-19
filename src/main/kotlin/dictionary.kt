import java.io.BufferedReader
import java.io.FileReader

inline class DocumentID(val id: Int): Comparable<DocumentID> {
    override fun compareTo(other: DocumentID) =
        id.compareTo(other.id)
}

data class WordCount(val document: DocumentID, var count: Int = 1): Comparable<WordCount> {
    override fun compareTo(other: WordCount) =
        document.compareTo(other.document)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WordCount
        if (document != other.document) return false
        return true
    }

    override fun hashCode() = document.id

}

data class DictionaryEntry(val key: String, val counts: ArrayList<WordCount>): Comparable<DictionaryEntry> {
    constructor(key: String, from: DocumentID) : this(key, arrayListOf(WordCount(from)))

    override fun compareTo(other: DictionaryEntry) =
        key.compareTo(other.key)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DictionaryEntry
        if (key != other.key) return false
        return true
    }

    override fun hashCode() = key.hashCode()

}

class Dictionary: Iterable<DictionaryEntry>{
    val entries = ArrayList<DictionaryEntry>()

    fun add(word: String, from: DocumentID) {
        val insertionPoint = entries.binarySearch { word.compareTo(it.key) }
        if (insertionPoint >= 0) {
            val counts = entries[insertionPoint].counts
            val count = counts.find { it.document == from }
            if (count == null) counts.add(WordCount(from))
            else count.count++
        } else entries.insert(DictionaryEntry(word, from), -insertionPoint-1)
    }

    override fun toString(): String {
        return "Dictionary(entries=$entries)"
    }

    override fun iterator() = entries.iterator()

}

fun main() {
    val br = BufferedReader(FileReader("dict.txt"))
    val dict = Dictionary()
    br.lineSequence()
        .flatMap { it.split(Regex("\\W+")).asSequence() }
        .filter  { it.isNotBlank() }
        .map     { it.toLowerCase() }
        .forEach { dict.add(it, DocumentID(0)) }
    dict.forEach { println(it) }
}
