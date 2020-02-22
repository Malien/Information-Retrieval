package dict.joker

import kotlinx.serialization.Serializable

@Serializable
class PrefixTree {

    @Serializable
    private class Node(val symbol: Char, var terminal: Boolean = false) {
        val conn = HashMap<Char, Node>(26)
    }

    private val root = Node((0).toChar())
    private var _size = 0
    val size get() = _size

    fun add(word: String) {
        var cur = root
        for ((i, ch) in word.withIndex()) {
            cur = cur.conn.getOrPut(ch) { Node(ch) }
            if (i == word.length - 1 && !cur.terminal) _size++
        }
        cur.terminal = cur !== root
    }

    operator fun contains(word: String): Boolean {
        fun traverse(query: String): Node? {
            var cur = root
            for (ch in query) {
                cur = cur.conn[ch] ?: return null
            }
            return cur
        }

        val node = traverse(word)
        return node != null && node.terminal
    }

    fun remove(word: String) {
        fun rec(cur: Node, word: String, index: Int) {
            if (index == word.length) {
                if (cur.terminal) {
                    cur.terminal = false
                    _size--
                }
            } else {
                val ch = word[index]
                val next = cur.conn[ch]
                if (next != null) {
                    rec(next, word, index + 1)
                    if (next.conn.size == 0 && !next.terminal) cur.conn.remove(ch)
                }
            }
        }
        rec(root, word, 0)
    }

    fun query(query: String): Iterator<String> {
        fun populateFrom(cur: Node, list: MutableList<String>, prefix: String) {
            for (node in cur.conn.values) {
                if (node.terminal) list.add(prefix + node.symbol)
                populateFrom(node, list, prefix + node.symbol)
            }
        }

        var cur = root
        val prefix = StringBuilder()
        for (ch in query) {
            when (val next = cur.conn[ch]) {
                null -> return iterator {}
                else -> {
                    cur = next
                    prefix.append(cur.symbol)
                }
            }
        }
        val list = mutableListOf<String>()
        if (cur.terminal) list.add(prefix.toString())
        populateFrom(cur, list, prefix.toString())
        return list.iterator()
    }

}
