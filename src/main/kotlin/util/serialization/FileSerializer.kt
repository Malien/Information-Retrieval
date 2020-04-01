package util.serialization

import kotlinx.serialization.*
import kotlinx.serialization.internal.StringDescriptor
import java.io.File

@Serializer(forClass = File::class)
object FileSerializer: KSerializer<File> {
    override val descriptor: SerialDescriptor = StringDescriptor.withName("File")

    override fun deserialize(decoder: Decoder) = File(decoder.decodeString())

    override fun serialize(encoder: Encoder, obj: File) {
        encoder.encodeString(obj.path)
    }
}