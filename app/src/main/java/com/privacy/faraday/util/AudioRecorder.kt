package com.privacy.faraday.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var _isRecording = false

    fun start(context: Context): File {
        val dir = File(context.cacheDir, "voice_recording")
        dir.mkdirs()
        val file = File(dir, "recording_${System.currentTimeMillis()}.ogg")
        outputFile = file

        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        mr.setAudioSource(MediaRecorder.AudioSource.MIC)

        if (Build.VERSION.SDK_INT >= 29) {
            mr.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
        } else {
            mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }

        mr.setAudioEncodingBitRate(16000)
        mr.setAudioSamplingRate(16000)
        mr.setMaxDuration(120_000)
        mr.setOutputFile(file.absolutePath)

        mr.prepare()
        mr.start()
        recorder = mr
        _isRecording = true

        return file
    }

    fun stop(): ByteArray {
        _isRecording = false
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        recorder?.release()
        recorder = null

        val file = outputFile ?: return ByteArray(0)
        val bytes = file.readBytes()
        file.delete()
        outputFile = null
        return bytes
    }

    fun cancel() {
        _isRecording = false
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    fun isRecording(): Boolean = _isRecording
}
