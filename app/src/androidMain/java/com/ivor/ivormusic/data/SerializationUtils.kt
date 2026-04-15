package com.ivor.ivormusic.data

import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for nullable Uri? fields.
 * Handles null values gracefully to prevent crashes during serialization/deserialization.
 */
object UriAsStringSerializer : KSerializer<Uri?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri?) {
        encoder.encodeString(value?.toString() ?: "")
    }

    override fun deserialize(decoder: Decoder): Uri? {
        val string = decoder.decodeString()
        return if (string.isBlank()) null else Uri.parse(string)
    }
}

