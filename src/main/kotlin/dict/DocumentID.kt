package dict

import kotlinx.serialization.*
import kotlinx.serialization.internal.IntDescriptor

//TODO: Return inline classes and make them serializable
@Serializable
data class DocumentID internal constructor(val id: Int) : Comparable<DocumentID> {
    override fun compareTo(other: DocumentID) =
        id.compareTo(other.id)

    @Serializer(forClass = DocumentID::class)
    companion object : KSerializer<DocumentID> {
        override val descriptor: SerialDescriptor =
            IntDescriptor.withName("id")

        override fun deserialize(decoder: Decoder) =
            DocumentID(decoder.decodeInt())

        override fun serialize(encoder: Encoder, obj: DocumentID) {
            encoder.encodeInt(obj.id)
        }
    }
}
