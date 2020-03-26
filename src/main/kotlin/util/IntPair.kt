package util

@ExperimentalUnsignedTypes
fun split(long: ULong): Pair<UInt, UInt> =
    long.firstUInt to long.secondUInt

@ExperimentalUnsignedTypes
fun combine(left: UInt, right: UInt): ULong =
    (left.toULong() shl 32) or right.toULong()

@ExperimentalUnsignedTypes
val ULong.firstUInt get() = (this shr 32).toUInt()

@ExperimentalUnsignedTypes
val ULong.firstInt get() = (this shr 32).toInt()

@ExperimentalUnsignedTypes
val ULong.secondUInt get() = this.toUInt()

@ExperimentalUnsignedTypes
val ULong.secondInt get() = this.toInt()

@ExperimentalUnsignedTypes
inline class IntPair(val value: ULong) {
    val first get() = value.firstUInt
    val second get() = value.secondUInt
    val pair get() = split(value)

    fun component1() = first
    fun component2() = second

    constructor(first: UInt, second: UInt): this(combine(first, second))

    override fun toString() = "IntPair($first, $second)"
}
