import kotlinx.serialization.Serializable

//TODO: Write custom serializer
@Serializable
class ArrayMap<K : Comparable<K>, V>(private val initialCapacity: Int = 10, private val multiplier: Float = 2f): Iterable<ArrayMap.Container<K,V>> {
    val size: Int get() = _size
    val capacity: Int get() = arr.size

    private var arr: Array<Container<K, V>?>
    private var _size = 0

    init {
        arr = Array(initialCapacity) { null }
    }

    fun put(key: K, value: V) {
        val idx = insertionIndex(key)
        if (idx >= 0) arr[idx]!!.value = value
        else add(Container(key, value), -idx-1)
    }

    fun exists(key: K) = insertionIndex(key) >= 0

    fun ifPresent(key: K, callback: (V) -> Unit, `else`: (() -> Unit)? = null) {
        val idx = insertionIndex(key)
        if (idx >= 0) callback(arr[idx]!!.value)
        else if(`else` != null) `else`()
    }

    fun getOrSet(key: K, factory: () -> V): V {
        val idx = insertionIndex(key)
        return if (idx >= 0) arr[idx]!!.value
        else {
            val value = factory()
            add(Container(key, value), -idx-1)
            value
        }
    }

    fun mutateOrSet(key: K, transform: (V) -> V, factory: () -> V): V {
        val idx = insertionIndex(key)
        return if (idx >= 0) {
            val value = transform(arr[idx]!!.value)
            arr[idx]!!.value = value
            value
        } else {
            val value = transform(factory())
            add(Container(key, value), -idx-1)
            value
        }
    }

    fun get(key: K): V? {
        val idx = insertionIndex(key)
        return if (idx >= 0) arr[idx]!!.value else null
    }

    override fun iterator() = object: Iterator<Container<K,V>> {
        var idx = 0
        override fun hasNext() = idx < size
        override fun next() = arr[idx++]!!
    }

    fun keys() = Iterable { object: Iterator<K> {
        var idx = 0
        override fun hasNext() = idx < size
        override fun next() = arr[idx++]!!.key
    }}

    fun values() = Iterable { object: Iterator<V> {
        var idx = 0
        override fun hasNext() = idx < size
        override fun next() = arr[idx++]!!.value
    }}

    private fun add(container: Container<K, V>, idx: Int = size) {
        if (size >= arr.size) {
            val newCap = arr.size * multiplier
            val newArr = Array<Container<K,V>?>(newCap.toInt()) {null}
            arr.copyInto(newArr)
            arr = newArr
        }
        var current = container
        for (i in idx until size) {
            val tmp = arr[i]!!
            arr[i] = current
            current = tmp
        }
        arr[_size++] = current
    }

    private fun insertionIndex(key: K) =
        arr.binarySearch(0, size-1) { key.compareTo(it!!.key) }

    @Serializable
    data class Container<K : Comparable<K>, V>(val key: K, var value: V) : Comparable<K> {
        override fun compareTo(other: K) = key.compareTo(other)
    }
}