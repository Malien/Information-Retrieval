package util

interface Console {
    /** Line to be shown on the bottom at all times. Setting new one should refresh the line set */
    var statusLine: String
    /** Prints line to the stream */
    fun println(msg: String)
}