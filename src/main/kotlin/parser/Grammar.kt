package parser

inline class Replacement(val values: Array<Token> = emptyArray()) {
    constructor(token: Token, vararg tokens: Token) : this(arrayOf(token, *tokens))
}

inline class ParsingTable(val table: HashMap<NonTerminal, HashMap<Terminal, Replacement>> = hashMapOf())

class Grammar(val start: NonTerminal) {
    val rules = HashMap<NonTerminal, ArrayList<Replacement>>()

    fun add(nonTerminal: NonTerminal, replacement: Replacement): Grammar {
        rules.getOrPut(nonTerminal, { ArrayList() }).add(replacement)
        return this
    }

    fun add(nonTerminal: NonTerminal, replacements: ArrayList<Replacement>): Grammar {
        rules[nonTerminal] = replacements
        return this
    }

    val table
        get() : ParsingTable {
            fun findTerminal(replacement: Replacement): List<Terminal> {
                if (replacement.values.isEmpty()) throw SyntaxError("Token has no replacements")
                val token = replacement.values[0] // It's LL(1) parser after all
                return when (token) {
                    is Terminal -> listOf(token)
                    is NonTerminal -> {
                        val replacements = rules[token] ?: throw SyntaxError("Token $token has no replacement rules")
                        replacements.flatMap { findTerminal(it) }
                    }
                }
            }

            val _table = ParsingTable()
            for ((nonTerminal, replacements) in rules) {
                _table.table[nonTerminal] = hashMapOf()
                for (replacement in replacements) {
                    val terminals = findTerminal(replacement).toTypedArray()
                    for (terminal in terminals) {
                        _table.table[nonTerminal]!![terminal] = replacement
                    }
                }
            }
            return _table
        }

}

val BooleanLogic = {
    val id = Terminal("id")
    val and = Terminal("and")
    val or = Terminal("or")
    val not = Terminal("not")
    val obracket = Terminal("(")
    val cbracket = Terminal(")")

    val V = NonTerminal("V")
    val O = NonTerminal("O")

    Grammar(V).add(
        V, arrayListOf(
            Replacement(obracket, id, cbracket),
            Replacement(id, O),
            Replacement(not, id, O)
        )
    ).add(
        O, arrayListOf(
            Replacement(and, V),
            Replacement(or, V),
            Replacement(EPS)
        )
    )
}
