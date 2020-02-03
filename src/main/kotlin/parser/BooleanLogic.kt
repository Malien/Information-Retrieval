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
val Vx = NonTerminal("Vx")
val T = NonTerminal("T")
val Tx = NonTerminal("Tx")
val N = NonTerminal("N")

inline class BooleanLogicGrammar(val grammar: Grammar)
inline class BooleanLogicParseTree(val tree: ParseTree)

val grammar: BooleanLogicGrammar by lazy {
    BooleanLogicGrammar(
        Grammar(V).add(
            V, Replacement(T, Vx)
        ).add(
            Vx, arrayListOf(
                Replacement(EPS),
                Replacement(or, T)
            )
        ).add(
            T, Replacement(O, Tx)
        ).add(
            Tx, arrayListOf(
                Replacement(EPS),
                Replacement(and, O)
            )
        ).add(
            O, arrayListOf(
                Replacement(id),
                Replacement(not, N),
                Replacement(obracket, V, cbracket)
            )
        ).add(
            N, arrayListOf(
                Replacement(id),
                Replacement(obracket, V, cbracket)
            )
        )
    )
}

fun parse(token: Queue<Terminal>): BooleanLogicParseTree =
    BooleanLogicParseTree(parse(token, grammar.grammar.table, grammar.grammar.start))

class EvalContext<T>(
    private val fromID: (String) -> T,
    private val cross: (T, T) -> T,
    private val unite: (T, T) -> T,
    private val negate: (T) -> T
) {
    private fun assertNode(node: ParseTreeNode<out Token>, expected: Token): Array<ParseTreeNode<Token>> {
        if (node.token != expected) throw InterpretationError("Expected $expected node, but got ${node.token}")
        val connections = node.connections ?: throw InterpretationError("$expected node has no connections")
        if (connections.isEmpty()) throw InterpretationError("Invalid $expected node connection size")
        return connections
    }

    private fun evalV(node: ParseTreeNode<out Token>): T {
        val connections = assertNode(node, V)
        val lhs = evalT(connections[0])
        val vx = connections[1]
        val vxConnections = assertNode(vx, Vx)
        return when (vxConnections[0].token) {
            EPS -> lhs
            or -> unite(lhs, evalT(vxConnections[1]))
            else ->
                throw InterpretationError("Invalid node. Expected EPS or and, but got ${vxConnections[0].token}")
        }
    }

    private fun evalT(node: ParseTreeNode<out Token>): T {
        val connections = assertNode(node, T)
        val lhs = evalO(connections[0])
        val tx = connections[1]
        val txConnections = assertNode(tx, Tx)
        return when (txConnections[0].token) {
            EPS -> lhs
            and -> cross(lhs, evalO(txConnections[1]))
            else ->
                throw InterpretationError("Invalid node. Expected EPS or and, but got ${txConnections[0].token}")
        }
    }

    private fun evalO(node: ParseTreeNode<out Token>): T {
        val connections = assertNode(node, O)
        return when (connections.first().token) {
            id -> evalID(connections.first())
            not -> evalN(connections[1])
            obracket -> evalV(connections[1])
            else -> throw InterpretationError("Invalid node")
        }
    }

    private fun evalN(node: ParseTreeNode<out Token>): T {
        val connections = assertNode(node, N)
        return when (connections.first().token) {
            id -> negate(evalID(connections.first()))
            obracket -> negate(evalV(connections[1]))
            else -> throw InterpretationError("Invalid node")
        }
    }

    private fun evalID(node: ParseTreeNode<out Token>): T {
        val idToken = node.token
        return if (idToken is Terminal && idToken.repr != null) fromID(idToken.repr)
        else throw InterpretationError("Invalid node taken as id. Got $idToken, expected $id")
    }

    fun eval(tree: BooleanLogicParseTree): T =
        evalV(tree.tree.root)

    operator fun invoke(tree: BooleanLogicParseTree) = eval(tree)
}

val tokensRegex = Regex("\\w+|[&|!()]")
fun tokenize(str: String): Queue<Terminal> {
    val queue = ArrayDeque<Terminal>()
    tokensRegex.findAll(str)
        .map { it.groups[0]?.value }
        .filterNotNull()
        .map { it.toLowerCase() }
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
    val tokens = tokenize("A & !(B | C)")
    val tree = parse(tokens)
    val context = EvalContext({ it }, { a, b -> "($a & $b)" }, { a, b -> "($a | $b)" }, { "(! $it)" })
    println(context(tree))
}
