package util

import java.math.BigInteger

@ExperimentalUnsignedTypes
fun ByteArray.decodeLong(at: Int) =
    (decodeInt(at).toLong() shl 32) + decodeInt(at + 4)

@ExperimentalUnsignedTypes
fun ByteArray.decodeUInt(at: Int) =
    (this[at].toUInt() shl 24) + (this[at + 1].toUInt() shl 16) + (this[at + 2].toUInt() shl 8) + this[at + 3].toUByte()

@ExperimentalUnsignedTypes
fun ByteArray.decodeUShort(at: Int) =
    (this[at].toUInt() shl 8) + this[at + 1].toUByte()

@ExperimentalUnsignedTypes
fun ByteArray.decodeShort(at: Int) = decodeUShort(at).toShort()

@ExperimentalUnsignedTypes
fun ByteArray.decodeInt(at: Int) = decodeUInt(at).toInt()

@ExperimentalUnsignedTypes
fun ByteArray.decodeULong(at: Int) = decodeLong(at).toULong()

fun ByteArray.encodeLong(at: Int, value: Long) {
    encodeInt(at, (value shr 32).toInt())
    encodeInt(at + 4, value.toInt())
}

fun ByteArray.encodeInt(at: Int, value: Int) {
    this[at] = (value shr 24).toByte()
    this[at + 1] = (value shr 16).toByte()
    this[at + 2] = (value shr 8).toByte()
    this[at + 3] = value.toByte()
}

fun ByteArray.encodeShort(at: Int, value: Short) {
    // Can somebody explain, why there is no shr/shl available for Short?
    this[at] = (value.toInt() shr 8).toByte()
    this[at + 1] = value.toByte()
}

@ExperimentalUnsignedTypes
fun ByteArray.decodeVariableByteEncodedInt(at: Int): IntPair {
    var res: UInt = 0u
    var current = at
    var currentByte = this[current++].toUByte()
    var shift = 0
    while (currentByte < 128u) {
        res = res or (currentByte.toUInt() shl shift)
        shift += 7
        currentByte = this[current++].toUByte()
    }
    return IntPair(res or ((currentByte.toUInt() and 0b1111111u) shl shift), (current - at).toUInt())
}

@ExperimentalUnsignedTypes
fun ByteArray.decodeVariableByteEncodedLong(at: Int): Pair<Long, Int> {
    var res: Long = 0
    var current = at
    var currentByte = this[current++].toUByte()
    var shift = 0
    while (currentByte < 128u) {
        res = res or (currentByte.toLong() shl shift)
        shift += 7
        currentByte = this[current++].toUByte()
    }
    return res to (current - at)
}

@ExperimentalUnsignedTypes
fun ByteArray.decodeVariableByteEncodedBigInteger(at: Int): Pair<BigInteger, Int> {
    var res = BigInteger.ZERO
    var current = at
    var currentByte = this[current++].toUByte()
    var shift = 0
    while (currentByte < 128u) {
        res = res.or(BigInteger.valueOf(currentByte.toLong()).shiftLeft(shift))
        shift += 7
        currentByte = this[current++].toUByte()
    }
    return res to (current - at)
}
