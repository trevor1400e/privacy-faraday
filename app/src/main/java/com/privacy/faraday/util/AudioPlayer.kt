package com.privacy.faraday.util

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AudioPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var currentPath: String? = null

    fun play(
        filePath: String,
        scope: CoroutineScope,
        onProgress: (Float) -> Unit,
        onComplete: () -> Unit
    ) {
        stop()
        currentPath = filePath

        val mp = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            start()
        }
        mediaPlayer = mp

        mp.setOnCompletionListener {
            onProgress(1f)
            onComplete()
            currentPath = null
        }

        progressJob = scope.launch(Dispatchers.Main) {
            while (mp.isPlaying) {
                val progress = mp.currentPosition.toFloat() / mp.duration.coerceAtLeast(1)
                onProgress(progress)
                delay(100)
            }
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        progressJob?.cancel()
    }

    fun resume() {
        mediaPlayer?.start()
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) { }
        mediaPlayer?.release()
        mediaPlayer = null
        currentPath = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun isPlayingPath(path: String): Boolean = currentPath == path && isPlaying()

    fun getDuration(filePath: String): Int {
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(filePath)
            mp.prepare()
            mp.duration
        } catch (_: Exception) {
            0
        } finally {
            mp.release()
        }
    }
}
