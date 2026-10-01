
package com.monicauditya.hf_model_hub_api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A custom serializer implementation for the java.time.LocalDateTime class */
class CustomDateSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    private val utcFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.atOffset(ZoneOffset.UTC).format(utcFormatter))
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        val stringRepr = decoder.decodeString()
        return LocalDateTime.parse(stringRepr, utcFormatter)
    }
}
