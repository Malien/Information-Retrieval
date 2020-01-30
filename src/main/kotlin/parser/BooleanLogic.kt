package parser

import java.util.*

val id = Terminal("id")
val and = Terminal("&")
val or = Terminal("|")
val not = Terminal("!")
val obracket = Terminal("(")
val cbracket = Terminal(")")

val V = NonTerminal("V")
val O = NonTerminal("O")

inline class BooleanLogicGrammar(val grammar: Grammar)
inline class BooleanLogicParseTree(val tree: ParseTree)

val grammar: BooleanLogicGrammar by lazy {
    BooleanLogicGrammar(
        Grammar(V).add(
            V, arrayListOf(
                Replacement(obracket, V, cbracket),
                Replacement(id, O),
                Replacement(not, V)
            )
        ).add(
            O, arrayListOf(
                Replacement(and, V),
                Replacement(or, V),
                Replacement(EPS)
            )
        )
    )
}

fun parse(token: Queue<Terminal>): BooleanLogicParseTree =
    BooleanLogicParseTree(parse(token, grammar.grammar.table, grammar.grammar.start))

@Suppress("UNCHECKED_CAST")
fun <T> eval(
    tree: BooleanLogicParseTree,
    fromID: (String) -> T,
    cross: (T, T) -> T,
    unite: (T, T) -> T,
    negate: (T) -> T
): T {
    data class Operation(val value: ParseTreeNode<NonTerminal>, val operation: (T, T) -> T) {
        operator fun invoke(a: T, b: T): T = operation(a, b)
    }

    fun parseOperation(node: ParseTreeNode<NonTerminal>): Operation? {
        assert(node.token == O)
        val connections = node.connections ?: throw InterpretationError("Operation node has no connections")
        if (connections.isEmpty()) throw InterpretationError("Invalid Operation node connection size")
        return when (connections.first().token) {
            and -> Operation(connections[1] as ParseTreeNode<NonTerminal>, cross)
            or -> Operation(connections[1] as ParseTreeNode<NonTerminal>, unite)
            EPS -> null
            else -> throw InterpretationError("Unknown Operation node replacement")
        }
    }

    fun evalValue(node: ParseTreeNode<NonTerminal>): T {
        assert(node.token == V)
        val connections = node.connections ?: throw InterpretationError("Value node has no connections")
        if (connections.size < 2) throw InterpretationError("Invalid Value node connections size")
        val first = connections.first().token
        if (first !is Terminal) throw InterpretationError("Unexpected non-terminal")
        return when (first) {
            id -> {
                val lhs = first.repr?.let { fromID(it) }
                    ?: throw InterpretationError("Encountered id without repr field")
                val operation = parseOperation(connections[1] as ParseTreeNode<NonTerminal>)
                return if (operation == null) lhs
                else operation(lhs, evalValue(operation.value))
            }
            obracket -> {
                evalValue(connections[1] as ParseTreeNode<NonTerminal>)
            }
            not -> {
                negate(evalValue(connections[1] as ParseTreeNode<NonTerminal>))
            }
            else -> throw InterpretationError("Unknown Value node replacement")
        }
    }

    return evalValue(tree.tree.root)
}

val tokensRegex = Regex("\\w+|[&|!()]")
fun tokenize(str: String): Queue<Terminal> {
    val queue = ArrayDeque<Terminal>()
    tokensRegex.findAll(str)
        .map { it.groups[0]?.value }
        .filterNotNull()
        .map {
            when (it) {
                "&" -> and
                "|" -> or
                "!" -> not
                "(" -> obracket
                ")" -> cbracket
                else -> Terminal("id", it)
            }
        }
        .toCollection(queue)
    return queue
}

fun main() {
    val tokens = ArrayDeque<Terminal>()
    tokens.add(Terminal("id", "A"))
    tokens.add(and)
    tokens.add(not)
    tokens.add(obracket)
    tokens.add(Terminal("id", "B"))
    tokens.add(or)
    tokens.add(Terminal("id", "C"))
    tokens.add(cbracket)

    val t = tokenize("A & !(B | C)")

    val tree = parse(t)
    val evaluated = eval(tree, { it }, { a, b -> "($a & $b)" }, { a, b -> "($a | $b)" }, { "(! $it)" })
    println(evaluated)
}
