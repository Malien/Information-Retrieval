package util

interface Console {
    var statusLine: String
    fun println(msg: String)
}