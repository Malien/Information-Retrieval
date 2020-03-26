package util.unboxed

class LongArrayList(initialSize: Int = 10, private val growthFactor: Float = 1.5f) : Iterable<Long>, RandomAccess {
    var arr = LongArray(initialSize)
    var size = 0
        private set

    fun add(value: Long) {
        if (arr.size <= size) {
            val newSize = (size * growthFactor).toInt()
            val newArr = LongArray(newSize)
            arr.copyInto(newArr)
            arr = newArr
        }
        arr[size++] = value
    }

    fun addAll(array: LongArray, from: Int = 0, to: Int = array.size) {
        val transferSize = to - from
        if (size + transferSize > arr.size) {
            // TODO: reserve headroom as if it were naturally grown (dunno if needed)
            val newSize = size + transferSize
            val newArr = LongArray(newSize)
            arr.copyInto(newArr)
            arr = newArr
        }
        array.copyInto(arr, destinationOffset = size, startIndex = from, endIndex = to)
        size += transferSize
    }

    fun addAll(arrayList: LongArrayList, from: Int = 0, to: Int = arrayList.size) =
        addAll(arrayList.arr, from, to)

    operator fun get(idx: Int) =
        if (idx in 0 until size) arr[idx] else throw IndexOutOfBoundsException("Index: $idx, Size: $size")

    operator fun set(idx: Int, value: Long) =
        if (idx in 0 until size) arr[idx] = value else throw  IndexOutOfBoundsException("Index: $idx, Size: $size")

    override fun iterator() = iterator {
        for (i in 0 until size) yield(arr[i])
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LongArrayList

        if (size != other.size) return false
        if (!arr.contentEquals(other.arr)) return false

        return true
    }

    fun toArray() = arr.copyOfRange(fromIndex = 0, toIndex = size)

    fun clear() {
        size = 0
    }

    override fun hashCode(): Int {
        var result = arr.contentHashCode()
        result = 31 * result + size
        return result
    }

    override fun toString() =
        iterator().asSequence().joinToString(separator = ", ", prefix = "LongArrayList([", postfix = "])")

}

fun longArrayListOf(vararg values: Long) = LongArrayList(values.size).also { it.addAll(values) }
