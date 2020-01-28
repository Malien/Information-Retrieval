package util

import binarySearch
import kotlinx.serialization.*
import kotlinx.serialization.internal.ArrayClassDesc

//TODO: Implement fully SortedMap interface just for LOLz
@Serializable(with = ArrayMap.ArraySerializer::class)
class ArrayMap<K : Comparable<K>, V>(
    initialCapacity: Int = 10,
    private val multiplier: Float = 2f
) : Iterable<ArrayMap.Container<K, V>>/*, SortedMap<K, V> */ {
    val size: Int get() = _size

    private constructor(arr: Array<Container<K, V>?>, size: Int = arr.size) : this(0, 2f) {
        _size = size
        this.arr = arr
    }

    private var arr: Array<Container<K, V>?> = arrayOfNulls(initialCapacity)
    private var _size = 0

    fun put(key: K, value: V): V? {
        val idx = insertionIndex(key)
        return if (idx >= 0) {
            val prev = arr[idx]
            arr[idx]!!.value = value
            prev?.value
        } else {
            add(Container(key, value), -idx - 1)
            null
        }
    }

    fun exists(key: K) = insertionIndex(key) >= 0

    fun getOrSet(key: K, factory: () -> V): V {
        val idx = insertionIndex(key)
        return if (idx >= 0) arr[idx]!!.value
        else {
            val value = factory()
            add(Container(key, value), -idx - 1)
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
            add(Container(key, value), -idx - 1)
            value
        }
    }

    fun <T> intersect(other: ArrayMap<K, T>): ArrayList<K> {
        val res = ArrayList<K>()
        var i = 0
        var j = 0
        while (i != size && j != other.size) {
            val k1 = arr[i]!!.key
            val k2 = arr[j]!!.key
            when {
                k1 > k2 -> { j++ }
                k1 < k2 -> { i++ }
                else -> {
                    res.add(k1)
                    i++
                    j++
                }
            }
        }
        return res
    }

    fun get(key: K): V? {
        val idx = insertionIndex(key)
        return if (idx >= 0) arr[idx]!!.value else null
    }

    override fun iterator() = object : Iterator<Container<K, V>> {
        var idx = 0
        override fun hasNext() = idx < size
        override fun next() = arr[idx++]!!
    }

    fun keys() = Iterable {
        object : Iterator<K> {
            var idx = 0
            override fun hasNext() = idx < size
            override fun next() = arr[idx++]!!.key
        }
    }

    fun values() = Iterable {
        object : Iterator<V> {
            var idx = 0
            override fun hasNext() = idx < size
            override fun next() = arr[idx++]!!.value
        }
    }

    private fun add(container: Container<K, V>, idx: Int = size) {
        if (size >= arr.size) {
            val newCap = arr.size * multiplier
            val newArr = Array<Container<K, V>?>(newCap.toInt()) { null }
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
        arr.binarySearch(0, size - 1) { key.compareTo(it!!.key) }

    @Serializable
    data class Container<K : Comparable<K>, V>(val key: K, var value: V) : Comparable<K> {
        override fun compareTo(other: K) = key.compareTo(other)
    }

    @Serializer(forClass = ArrayMap::class)
    class ArraySerializer<K : Comparable<K>, V>(
        keySerializer: KSerializer<K>,
        valueSerializer: KSerializer<V>
    ) : KSerializer<ArrayMap<K, V>> {
        private val containerSerializer = Container.serializer(keySerializer, valueSerializer)

        override val descriptor: SerialDescriptor = ArrayClassDesc(containerSerializer.descriptor)

        override fun deserialize(decoder: Decoder): ArrayMap<K, V> {
            val collection = decoder.beginStructure(descriptor)
            val list = ArrayList<Container<K, V>?>()
            while (true) {
                val i = collection.decodeElementIndex(descriptor)
                if (i == CompositeDecoder.READ_DONE) break
                list.add(collection.decodeSerializableElement(descriptor, i, containerSerializer))
            }
            collection.endStructure(descriptor)
            return ArrayMap(list.toTypedArray())
        }

        override fun serialize(encoder: Encoder, obj: ArrayMap<K, V>) {
            val collection = encoder.beginCollection(descriptor, obj.size)
            for ((idx, container) in obj.withIndex()) {
                collection.encodeSerializableElement(descriptor, idx, containerSerializer, container)
            }
            collection.endStructure(descriptor)
        }
    }
}