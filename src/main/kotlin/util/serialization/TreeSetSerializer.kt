package util.serialization

import kotlinx.serialization.*
import kotlinx.serialization.internal.ArrayClassDesc
import java.util.*

@Serializer(forClass = TreeSet::class)
class TreeSetSerializer<T>(private val elementSerializer: KSerializer<T>): KSerializer<TreeSet<T>> {
    override val descriptor: SerialDescriptor = ArrayClassDesc(elementSerializer.descriptor)

    override fun deserialize(decoder: Decoder): TreeSet<T> {
        val struct = decoder.beginStructure(descriptor)
        val set = TreeSet<T>()
        loop@while (true) {
            val i = struct.decodeElementIndex(descriptor)
            if (i == CompositeDecoder.READ_DONE) break@loop
            set.add(struct.decodeSerializableElement(descriptor, i, elementSerializer))
        }
        struct.endStructure(descriptor)
        return set
    }

    override fun serialize(encoder: Encoder, obj: TreeSet<T>) {
        val struct = encoder.beginStructure(descriptor)
        for ((idx, value) in obj.withIndex()) {
            struct.encodeSerializableElement(descriptor, idx, elementSerializer, value)
        }
        struct.endStructure(descriptor)
    }
}