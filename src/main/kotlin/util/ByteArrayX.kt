package util

fun ByteArray.decodeLong(at: Int) =
    (decodeInt(at).toLong() shl 32) + decodeInt(at+4)

fun ByteArray.decodeInt(at: Int) =
    (this[at].toInt() shl 24) + (this[at + 1].toInt() shl 16) + (this[at + 2].toInt() shl 8) + this[at + 3]

fun ByteArray.decodeShort(at: Int) =
    ((this[at].toInt() shl 8) + this[at + 1]).toShort()

fun ByteArray.encodeLong(at: Int, value: Long) {
    encodeInt(at, (value shr 32).toInt())
    encodeInt(at+4, value.toInt())
}

fun ByteArray.encodeInt(at: Int, value: Int) {
    this[at  ] = (value shr 24).toByte()
    this[at+1] = (value shr 16).toByte()
    this[at+2] = (value shr  8).toByte()
    this[at+3] = value.toByte()
}

fun ByteArray.encodeShort(at: Int, value: Short) {
    // Can somebody explain, why there is no shr/shl available for Short?
    this[at] = (value.toInt() shr 8).toByte()
    this[at + 1] = value.toByte()
}