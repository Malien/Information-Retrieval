package dict.joker

import kotlinx.serialization.Serializable

@Serializable
class RelocationDict : JokerDict {
    private val prefixTree = PrefixTree()

    override fun add(word: String) {
        for (relocation in rotations(word)) {
            prefixTree.add(relocation)
        }
    }

    override fun getRough(query: String): Iterator<String>? {
        val split = query.split('*')
        val prefixed = prefixTree.query("${split.last()}$${split.first()}")
        return prefixed.asSequence().map { restore(it) }.iterator()
    }

    companion object {
        fun restore(word: String): String =
            if ('$' in word)
                word.split('$').let { it.last() + it.first() }
            else word

        fun rotate(arr: CharArray) {
            val first = arr.first()
            for (i in 1 until arr.size) {
                arr[i - 1] = arr[i]
            }
            arr[arr.size - 1] = first
        }

        fun rotations(word: String) = iterator {
            val arr = CharArray(word.length + 1) { if (it != word.length) word[it] else '$' }
            yield(String(arr))
            for (i in 1 until word.length) {
                rotate(arr)
                yield(String(arr))
            }
        }

    }
}