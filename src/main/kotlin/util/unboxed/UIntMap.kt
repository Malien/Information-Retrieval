package util.unboxed

import util.IntPair
import kotlin.math.ceil

/**
 * Return the least power of two greater than or equal to the specified value.
 * Note that this function will return 1 when the argument is 0.
 * @param value a long integer smaller than or equal to 2<sup>62</sup>.
 * @return the least power of two greater than or equal to the specified value.
 */
fun nextPowerOfTwo(value: Long): Long {
    var x = value
    if (x == 0L) return 1
    x--
    x = x or (x shr 1)
    x = x or (x shr 2)
    x = x or (x shr 4)
    x = x or (x shr 8)
    x = x or (x shr 16)
    return (x or (x shr 32)) + 1
}

/**
 * Returns the least power of two smaller than or equal to 2<sup>30</sup> and larger than or equal to `Math.ceil( expected / f )`.
 * @param expected the expected number of elements in a hash table.
 * @param f the load factor.
 * @return the minimum possible size for a backing array.
 * @throws IllegalArgumentException if the necessary size is larger than 2<sup>30</sup>.
 */
fun arraySize(expected: Int, f: Float): Int {
    val s = 2L.coerceAtLeast(nextPowerOfTwo(ceil(expected / f.toDouble()).toLong()))
    require(s <= 1 shl 30) { "Too large ($expected expected elements with load factor $f)" }
    return s.toInt()
}

//taken from FastUtil
private const val INT_PHI = -0x61c88647

fun phiMix(x: Int): Int {
    val h = x * INT_PHI
    return h xor (h shr 16)
}

@ExperimentalUnsignedTypes
class UIntMap(initialSize: Int = 20, private val loadFactor: Float = 0.6f): Iterable<IntPair> {
    /** Keys and values */
    private var array: ULongArray

    /** Do we have 'free' key in the map? */
    private var hasFreeKey: Boolean = false

    /** Value of 'free' key */
    private var freeValue: UInt = 0u

    /** We will resize a map once it reaches this size */
    private var threshold: Int

    /** Mask to calculate the original position */
    private var mask: Int

    var size: Int = 0
        private set

    init {
        require(loadFactor > 0 && loadFactor < 1) { "FillFactor must be in (0, 1)" }
        require(initialSize > 0) { "Size must be positive!" }
        val capacity = arraySize(initialSize, loadFactor)
        mask = capacity - 1

        array = ULongArray(capacity)
        threshold = (capacity * loadFactor).toInt()
    }

    operator fun get(key: UInt): UInt {
        if (key == FREE_KEY) return if (hasFreeKey) freeValue else NO_VALUE

        var idx = getStartIndex(key)
        var cell = array[idx]
        if (cell == FREE_CELL) return NO_VALUE
        if ((cell and KEY_MASK).toUInt() == key) return (cell shr 32).toUInt()
        while (true) {
            idx = getNextIndex(idx)
            cell = array[idx]
            if (cell == FREE_CELL) return NO_VALUE
            if ((cell and KEY_MASK).toUInt() == key) return (cell shr 32).toUInt()
        }
    }

    operator fun set(key: UInt, value: UInt): UInt {
        if (key == FREE_KEY) {
            val ret = freeValue
            if (!hasFreeKey) ++size
            hasFreeKey = true
            freeValue = value
            return ret
        } else {
            var idx = getStartIndex(key)
            var cell = array[idx]
            if (cell == FREE_CELL) {
                array[idx] = (key.toULong() and KEY_MASK) or (value.toULong() shl 32)
                if (size >= threshold) rehash(array.size * 2)
                else ++size
                return NO_VALUE
            } else if ((cell and KEY_MASK).toUInt() == key) {
                array[idx] = (key.toULong() and KEY_MASK) or (value.toULong() shl 32)
                return (cell shr 32).toUInt()
            }

            while (true) {
                idx = getNextIndex(idx)
                cell = array[idx]
                if (cell == FREE_CELL) {
                    array[idx] = (key.toULong() and KEY_MASK) or (value.toULong() shl 32)
                    if (size >= threshold) rehash(array.size * 2)
                    else ++size
                    return NO_VALUE
                } else if ((cell and KEY_MASK).toUInt() == key) {
                    array[idx] = (key.toULong() and KEY_MASK) or (value.toULong() shl 32)
                    return (cell shr 32).toUInt()
                }
            }
        }
    }

    operator fun contains(key: UInt): Boolean {
        if (key == FREE_KEY) return hasFreeKey

        var idx = getStartIndex(key)
        var cell = array[idx]
        if (cell == FREE_CELL) return false
        if ((cell and KEY_MASK).toUInt() == key) return true
        while (true) {
            idx = getNextIndex(idx)
            cell = array[idx]
            if (cell == FREE_CELL) return false
            if ((cell and KEY_MASK).toUInt() == key) return true
        }
    }

    fun keys() = iterator {
        if (hasFreeKey) yield(FREE_KEY)
        for (entry in array) {
            if (entry != FREE_CELL) yield(entry.toUInt())
        }
    }

    override fun iterator() = iterator {
        if (hasFreeKey) yield(IntPair(FREE_KEY, freeValue))
        for (entry in array) {
            if (entry != FREE_CELL) yield(IntPair(entry))
        }
    }

    private fun rehash(newCapacity: Int) {
        threshold = (newCapacity * loadFactor).toInt()
        mask = newCapacity - 1

        val oldCapacity = array.size
        val oldData = array

        array = ULongArray(newCapacity)
        size = if (hasFreeKey) 1 else 0

        for (i in (oldCapacity-1) downTo 0) {
            val oldKey = (oldData[i] and KEY_MASK).toUInt()
            if (oldKey != FREE_KEY) set(oldKey, (oldData[i] shr 32).toUInt())
        }
    }

    private fun getStartIndex(key: UInt) = phiMix(key.toInt()) and mask

    private fun getNextIndex(currentIndex: Int) = (currentIndex + 1) and mask

    companion object {
        private const val FREE_KEY = 0u
        private const val FREE_CELL = 0uL
        private val KEY_MASK = 0xFFFFFFFFuL
        const val NO_VALUE = 0u
    }

}
