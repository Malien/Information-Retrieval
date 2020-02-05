package dict

import kotlinx.serialization.Serializable
import util.TreeMapArraySerializer
import util.cross
import util.keySet
import java.util.*
import kotlin.collections.ArrayList

@Serializable
class PositionedDict {
    @Serializable(with = TreeMapArraySerializer::class)
    private val entries =
        TreeMap<String, @Serializable(with = TreeMapArraySerializer::class) TreeMap<DocumentID, ArrayList<Int>>>()

    val uniqueWords get() = entries.size

    // Please keep indexes sorted util I write serializer for PriorityQueue, and don't insert negative indexes
    fun add(word: String, position: Int, from: DocumentID) {
        val documents = entries.getOrPut(word) { TreeMap() }
        val entry = documents.getOrPut(from) { ArrayList() }
        entry.add(position)
    }

    fun get(word: String): Documents =
        entries[word]?.keys?.iterator()?.keySet ?: emptyDocuments()

    private fun <T> containsWithin(left: Iterable<Int>, right: T, offset: Int = 1)
            where T : List<Int>,
                  T : RandomAccess = containsWithin(left.iterator(), right, offset)


    private fun <T> containsWithin(left: Iterator<Int>, right: T, offset: Int = 1): Boolean
            where T : List<Int>,
                  T : RandomAccess {
        if (right.isEmpty()) return false
        var idx = 0
        leftLoop@ for (leftElement in left) {
            while (idx < right.size) {
                val rightElement = right[idx] - offset
                when {
                    leftElement == rightElement -> return true
                    leftElement > rightElement -> idx++
                    leftElement < rightElement -> continue@leftLoop
                }
            }
        }
        return false
    }

    fun get(vararg words: String): Documents {
        val wordEntries = words.map {
            entries[it] ?: return@get emptyDocuments()
        }
        val crossed = wordEntries.asSequence().map { it.keys.iterator().keySet }.reduce(::cross)
        return Documents(iterator {
            documentLoop@for (document in crossed) {
                for ((prevWordDocs, nextWordDocs) in wordEntries.zipWithNext()) {
                    val prevDoc = prevWordDocs[document]!!
                    val nextDoc = nextWordDocs[document]!!
                    if (!containsWithin(prevDoc, nextDoc)) continue@documentLoop
                }
                yield(document)
            }
        })
    }

    fun get(vararg spacedWords: SpacedWord): Documents {
        val wordEntries = spacedWords.map { (word, space) ->
            (entries[word] ?: return@get emptyDocuments()) to space
        }
        val crossed = wordEntries.asSequence()
            .map { it.first }
            .map { it.keys.iterator().keySet }
            .reduce(::cross)
        return Documents(iterator {
            documentLoop@for(document in crossed) {
                    for ((prev, next) in wordEntries.zipWithNext()) {
                        val (prevWordDocs) = prev
                        val (nextWordDocs, spaced) = next
                        val prevDoc = prevWordDocs[document]!!
                        val nextDoc = nextWordDocs[document]!!
                        if (!containsWithin(prevDoc, nextDoc, spaced)) continue@documentLoop
                    }
                yield(document)
            }
        })
    }

}