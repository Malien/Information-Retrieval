package parser

import java.util.*

inline class ParseTree(val root: ParseTreeNode<NonTerminal>)

class ParseTreeNode<T : Token>(internal var _token: T, var connections: Array<ParseTreeNode<Token>>? = null) {
    val token get() = _token
}

inline fun <T, reified U> Array<T>.mapArray(transform: (T) -> U): Array<U> =
    Array<U>(this.size) { transform(this[it]) }

/**
 * top-down parsing
 */
fun parse(tokens: Queue<Terminal>, parsingTable: ParsingTable, start: NonTerminal): ParseTree {
    val root = ParseTreeNode(start)

    @Suppress("UNCHECKED_CAST")
    fun rec(node: ParseTreeNode<NonTerminal>) {
        val next = tokens.peek()
        val forNonTerminal = parsingTable.table[node.token]
            ?: throw SyntaxError("Fount unexpected non-terminal ${node.token}")
        val replacement = forNonTerminal[next]
            ?: forNonTerminal[EPS]
            ?: throw SyntaxError("Found unexpected token $next")
        node.connections = replacement.values.mapArray { ParseTreeNode(it) }
        for (leaf in node.connections!!) {
            when (leaf.token) {
                is Terminal -> {
                    if (leaf.token != EPS) {
                        val token = tokens.poll()
                        if (token.value != leaf.token.value)
                            throw SyntaxError("Found unexpected '$token', (expected '${leaf.token}')")
                        leaf._token = token
                    }
                }
                is NonTerminal -> {
                    rec(leaf as ParseTreeNode<NonTerminal>)
                }
            }
        }
    }

    rec(root)

    return ParseTree(root)
}
