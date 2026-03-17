package com.privacy.faraday.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed class ContentPayload {
    data class Text(val text: String) : ContentPayload()
    data class Image(val mimeType: String, val caption: String, val imageBytes: ByteArray) : ContentPayload()
    data class File(val fileName: String, val mimeType: String, val fileBytes: ByteArray) : ContentPayload()
    data class Voice(val durationMs: Int, val audioBytes: ByteArray) : ContentPayload()
    data class Location(val latitude: Double, val longitude: Double, val accuracy: Float) : ContentPayload()

    companion object {
        private const val TYPE_TEXT: Byte = 0x00
        private const val TYPE_IMAGE: Byte = 0x01
        private const val TYPE_FILE: Byte = 0x02
        private const val TYPE_VOICE: Byte = 0x03
        private const val TYPE_LOCATION: Byte = 0x04

        fun serialize(payload: ContentPayload): ByteArray {
            return when (payload) {
                is Text -> {
                    val textBytes = payload.text.toByteArray(Charsets.UTF_8)
                    ByteBuffer.allocate(1 + textBytes.size).order(ByteOrder.BIG_ENDIAN)
                        .put(TYPE_TEXT)
                        .put(textBytes)
                        .array()
                }
                is Image -> {
                    val mimeBytes = payload.mimeType.toByteArray(Charsets.UTF_8)
                    val captionBytes = payload.caption.toByteArray(Charsets.UTF_8)
                    ByteBuffer.allocate(1 + 2 + mimeBytes.size + 2 + captionBytes.size + payload.imageBytes.size)
                        .order(ByteOrder.BIG_ENDIAN)
                        .put(TYPE_IMAGE)
                        .putShort(mimeBytes.size.toShort())
                        .put(mimeBytes)
                        .putShort(captionBytes.size.toShort())
                        .put(captionBytes)
                        .put(payload.imageBytes)
                        .array()
                }
                is File -> {
                    val nameBytes = payload.fileName.toByteArray(Charsets.UTF_8)
                    val mimeBytes = payload.mimeType.toByteArray(Charsets.UTF_8)
                    ByteBuffer.allocate(1 + 2 + nameBytes.size + 2 + mimeBytes.size + payload.fileBytes.size)
                        .order(ByteOrder.BIG_ENDIAN)
                        .put(TYPE_FILE)
                        .putShort(nameBytes.size.toShort())
                        .put(nameBytes)
                        .putShort(mimeBytes.size.toShort())
                        .put(mimeBytes)
                        .put(payload.fileBytes)
                        .array()
                }
                is Voice -> {
                    ByteBuffer.allocate(1 + 4 + payload.audioBytes.size)
                        .order(ByteOrder.BIG_ENDIAN)
                        .put(TYPE_VOICE)
                        .putInt(payload.durationMs)
                        .put(payload.audioBytes)
                        .array()
                }
                is Location -> {
                    ByteBuffer.allocate(1 + 8 + 8 + 4)
                        .order(ByteOrder.BIG_ENDIAN)
                        .put(TYPE_LOCATION)
                        .putDouble(payload.latitude)
                        .putDouble(payload.longitude)
                        .putFloat(payload.accuracy)
                        .array()
                }
            }
        }

        fun deserialize(data: ByteArray): ContentPayload {
            if (data.isEmpty()) return Text("")

            val type = data[0]
            // Backward compat: if first byte isn't a known type, treat as legacy text
            if (type !in TYPE_TEXT..TYPE_LOCATION) {
                return Text(String(data, Charsets.UTF_8))
            }

            val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            buf.get() // skip type byte

            return when (type) {
                TYPE_TEXT -> {
                    val textBytes = ByteArray(buf.remaining())
                    buf.get(textBytes)
                    Text(String(textBytes, Charsets.UTF_8))
                }
                TYPE_IMAGE -> {
                    val mimeLen = buf.getShort().toInt() and 0xFFFF
                    val mimeBytes = ByteArray(mimeLen).also { buf.get(it) }
                    val captionLen = buf.getShort().toInt() and 0xFFFF
                    val captionBytes = ByteArray(captionLen).also { buf.get(it) }
                    val imageBytes = ByteArray(buf.remaining()).also { buf.get(it) }
                    Image(
                        String(mimeBytes, Charsets.UTF_8),
                        String(captionBytes, Charsets.UTF_8),
                        imageBytes
                    )
                }
                TYPE_FILE -> {
                    val nameLen = buf.getShort().toInt() and 0xFFFF
                    val nameBytes = ByteArray(nameLen).also { buf.get(it) }
                    val mimeLen = buf.getShort().toInt() and 0xFFFF
                    val mimeBytes = ByteArray(mimeLen).also { buf.get(it) }
                    val fileBytes = ByteArray(buf.remaining()).also { buf.get(it) }
                    File(
                        String(nameBytes, Charsets.UTF_8),
                        String(mimeBytes, Charsets.UTF_8),
                        fileBytes
                    )
                }
                TYPE_VOICE -> {
                    val durationMs = buf.getInt()
                    val audioBytes = ByteArray(buf.remaining()).also { buf.get(it) }
                    Voice(durationMs, audioBytes)
                }
                TYPE_LOCATION -> {
                    val lat = buf.getDouble()
                    val lng = buf.getDouble()
                    val accuracy = buf.getFloat()
                    Location(lat, lng, accuracy)
                }
                else -> Text(String(data, Charsets.UTF_8))
            }
        }
    }
}
