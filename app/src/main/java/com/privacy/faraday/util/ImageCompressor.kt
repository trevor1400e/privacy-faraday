package com.privacy.faraday.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

object ImageCompressor {

    private const val MAX_DIMENSION = 1024
    private const val MAX_BYTES = 340_000
    private val QUALITY_STEPS = intArrayOf(85, 75, 65, 55, 45)

    fun compressImage(context: Context, uri: Uri): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI: $uri")

        // Read EXIF rotation before decoding
        val rotation = try {
            val exifStream = context.contentResolver.openInputStream(uri)
            val exif = exifStream?.let { ExifInterface(it) }
            exifStream?.close()
            when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) { 0f }

        // Decode with sample size for memory efficiency
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val sizeStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(sizeStream, null, options)
        sizeStream?.close()

        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, MAX_DIMENSION)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }

        val rawBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        inputStream.close()

        if (rawBitmap == null) throw IllegalArgumentException("Failed to decode image")

        // Resize to max dimension
        val scaled = scaleBitmap(rawBitmap, MAX_DIMENSION)
        val finalBitmap = if (rotation != 0f) rotateBitmap(scaled, rotation) else scaled

        // Compress with stepping quality
        for (quality in QUALITY_STEPS) {
            val out = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= MAX_BYTES) {
                if (finalBitmap !== rawBitmap) finalBitmap.recycle()
                if (scaled !== rawBitmap && scaled !== finalBitmap) scaled.recycle()
                rawBitmap.recycle()
                return bytes
            }
        }

        // Last resort: compress at lowest quality
        val out = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 30, out)
        if (finalBitmap !== rawBitmap) finalBitmap.recycle()
        if (scaled !== rawBitmap && scaled !== finalBitmap) scaled.recycle()
        rawBitmap.recycle()
        return out.toByteArray()
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        val larger = maxOf(width, height)
        while (larger / sample > maxDim * 2) {
            sample *= 2
        }
        return sample
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
