package util

import kotlinx.serialization.*
import kotlinx.serialization.internal.ArrayClassDesc
import java.util.*

@Serializer(forClass = PriorityQueue::class)
class PriorityQueueSerializer<T : Comparable<T>>(private val valueSerializer: KSerializer<T>) : KSerializer<PriorityQueue<T>> {
    override val descriptor: SerialDescriptor = ArrayClassDesc(valueSerializer.descriptor)

    override fun deserialize(decoder: Decoder): PriorityQueue<T> {
        val struct = decoder.beginStructure(descriptor)
        val queue = PriorityQueue<T>()
        loop@while (true) {
            val i = struct.decodeElementIndex(descriptor)
            if (i == CompositeDecoder.READ_DONE) break@loop
            else queue.add(struct.decodeSerializableElement(descriptor, i, valueSerializer))
        }
        struct.endStructure(descriptor)
        return queue
    }

    override fun serialize(encoder: Encoder, obj: PriorityQueue<T>) {
        val struct = encoder.beginStructure(descriptor)
        for ((idx, value) in obj.withIndex()) {
            struct.encodeSerializableElement(descriptor, idx, valueSerializer, value)
        }
        struct.endStructure(descriptor)
    }
}