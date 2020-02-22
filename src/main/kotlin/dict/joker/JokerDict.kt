package dict.joker

interface JokerDict {
    fun add(word: String)
    fun getStar(query: String): Iterator<String>? {
        val regex = Regex(query.replace("*", ".*"))
        return getRough(query)?.asSequence()?.filter { regex.matches(it) }?.iterator()
    }
    fun getRough(query: String): Iterator<String>?
}