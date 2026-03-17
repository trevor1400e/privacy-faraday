package com.privacy.faraday.util

import android.content.Context
import java.io.File
import java.util.UUID

object MediaStorage {

    fun saveMedia(context: Context, subDir: String, extension: String, data: ByteArray): String {
        val dir = File(context.filesDir, "media/$subDir")
        dir.mkdirs()
        val file = File(dir, "${UUID.randomUUID()}.$extension")
        file.writeBytes(data)
        return file.absolutePath
    }

    fun deleteMedia(path: String) {
        try {
            File(path).delete()
        } catch (_: Exception) { }
    }

    fun getMediaFile(path: String): File = File(path)
}
