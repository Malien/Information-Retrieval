import kotlinx.serialization.*
import kotlinx.serialization.internal.ArrayClassDesc
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

inline fun <T> Array<T>.binarySearch(fromIndex: Int = 0, toIndex: Int = this.size, comparison: (T) -> Int): Int {
    var lo = fromIndex
    var hi = toIndex
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val cmp = comparison(this[mid])
        when {
            cmp > 0 -> { hi = mid - 1 }
            cmp < 0 -> { lo = mid + 1 }
            else -> return mid
        }
    }
    return -lo - 1
}

fun Double.round(digits: Int = 0) = (10.0.pow(digits) * this).roundToInt() / 10.0.pow(digits)

val Long.megabytes get() = (this / 1024 / 1024.0).round(2)

inline fun <reified T, reified U> Array<T>.mapArray(transform: (T) -> U): Array<U> =
    Array<U>(this.size) { transform(this[it]) }

//TODO: To avoid re-allocations write serializer for Map.Entry

@Serializer(forClass = TreeMap::class)
class TreeMapArraySerializer<K:Comparable<K>, V>(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>
) : KSerializer<TreeMap<K, V>> {
    private val containerSerializer = Container.serializer(keySerializer, valueSerializer)

    override val descriptor: SerialDescriptor = ArrayClassDesc(containerSerializer.descriptor)

    override fun deserialize(decoder: Decoder): TreeMap<K, V> {
        val collection = decoder.beginStructure(descriptor)
        val list = ArrayList<Container<K, V>>()
        while (true) {
            val i = collection.decodeElementIndex(descriptor)
            if (i == CompositeDecoder.READ_DONE) break
            list.add(collection.decodeSerializableElement(descriptor, i, containerSerializer))
        }
        collection.endStructure(descriptor)
        val pairs = list.map { it.key to it.value }.toTypedArray()
        return sortedMapOf(*pairs) as TreeMap<K,V>
    }

    override fun serialize(encoder: Encoder, obj: TreeMap<K, V>) {
        val collection = encoder.beginCollection(descriptor, obj.size)
        for ((idx, entry) in obj.iterator().withIndex()) {
            collection.encodeSerializableElement(descriptor, idx, containerSerializer, Container(entry.key, entry.value))
        }
        collection.endStructure(descriptor)
    }

    companion object {
        @Serializable
        data class Container<K, V>(val key: K, val value: V)
    }
}
