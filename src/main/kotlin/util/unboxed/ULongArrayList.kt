package util.unboxed

@ExperimentalUnsignedTypes
class ULongArrayList(initialSize: Int = 10, private val growthFactor: Float = 1.5f) : Iterable<ULong>, RandomAccess {
    var arr = ULongArray(initialSize)
        private set
    var size = 0
        private set

    fun add(value: ULong) {
        if (arr.size <= size) {
            val newSize = (size * growthFactor).toInt()
            val newArr = ULongArray(newSize)
            arr.copyInto(newArr)
            arr = newArr
        }
        arr[size++] = value
    }

    fun addAll(array: ULongArray, from: Int = 0, to: Int = array.size) {
        val transferSize = to - from
        if (size + transferSize > arr.size) {
            // TODO: reserve headroom as if it were naturally grown (dunno if needed)
            val newSize = size + transferSize
            val newArr = ULongArray(newSize)
            arr.copyInto(newArr)
            arr = newArr
        }
        array.copyInto(arr, destinationOffset = size, startIndex = from, endIndex = to)
        size += transferSize
    }

    fun addAll(arrayList: ULongArrayList, from: Int = 0, to: Int = arrayList.size) =
        addAll(arrayList.arr, from, to)

    operator fun get(idx: Int) =
        if (idx in 0 until size) arr[idx] else throw IndexOutOfBoundsException("Index: $idx, Size: $size")

    operator fun set(idx: Int, value: ULong) =
        if (idx in 0 until size) arr[idx] = value else throw  IndexOutOfBoundsException("Index: $idx, Size: $size")

    override fun iterator() = iterator {
        for (i in 0 until size) yield(arr[i])
    }

    fun toArray() = arr.copyOfRange(fromIndex = 0, toIndex = size)

    fun clear() {
        size = 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ULongArrayList

        if (size != other.size) return false
        if (!arr.contentEquals(other.arr)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = arr.contentHashCode()
        result = 31 * result + size
        return result
    }

    override fun toString() =
        iterator().asSequence().joinToString(separator = ", ", prefix = "ULongArrayList([", postfix = "])")

}

@ExperimentalUnsignedTypes
fun ulongArrayListOf(vararg values: ULong) = ULongArrayList(values.size).apply { addAll(values) }

@ExperimentalUnsignedTypes
fun Sequence<ULong>.toULongList(): ULongArrayList {
    val list = ULongArrayList()
    for (item in this) list.add(item)
    return list
}