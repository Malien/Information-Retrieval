package parser.cfg

sealed class Token(val value: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Token

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    fun component1() = value
    override fun toString() = value

}

class Terminal(value: String, val repr: String? = null): Token(value) {
    override fun toString() = when (repr) {
        null -> super.toString()
        else -> "$value($repr)"
    }
}

class NonTerminal(value: String): Token(value)

val EPS = Terminal("")
