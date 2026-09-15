package com.saa.dreamcue.companion.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import com.saa.dreamcue.companion.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AudioFadeController(private val context: Context) {

    companion object {
        private const val TAG = "AudioFadeController"
        private const val VOLUME_STEP_INTERVAL_MS = 50L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPlaying = false

    suspend fun playWithFade(
        audioUriStr: String,
        totalSeconds: Int,
        maxVolumePercent: Int,
        fadeInSeconds: Int,
        fadeOutSeconds: Int
    ) = withContext(Dispatchers.Default) {
        stop() // Ensure previous instance is stopped

        val targetVolume = (maxVolumePercent.toFloat() / 100f).coerceIn(0.01f, 1.0f)
        var clampedFadeIn = fadeInSeconds.coerceAtLeast(0)
        var clampedFadeOut = fadeOutSeconds.coerceAtLeast(0)

        // If fade in + fade out exceeds total duration, proportionally scale them down
        if (clampedFadeIn + clampedFadeOut >= totalSeconds) {
            val ratio = (totalSeconds.toFloat() - 0.5f).coerceAtLeast(0.5f) / (clampedFadeIn + clampedFadeOut)
            clampedFadeIn = (clampedFadeIn * ratio).toInt()
            clampedFadeOut = (clampedFadeOut * ratio).toInt()
        }

        val totalMs = totalSeconds * 1000L
        val fadeInMs = clampedFadeIn * 1000L
        val fadeOutMs = clampedFadeOut * 1000L

        // Request Audio Focus
        requestAudioFocus()

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                setVolume(0f, 0f)

                if (audioUriStr.isNotBlank()) {
                    setDataSource(context, Uri.parse(audioUriStr))
                } else {
                    val afd = context.resources.openRawResourceFd(R.raw.gentle_chime)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }

                prepare()
                start()
            }
            mediaPlayer = player
            isPlaying = true
            Log.d(TAG, "Audio started. total=${totalSeconds}s, maxVol=$targetVolume, fadeIn=${clampedFadeIn}s, fadeOut=${clampedFadeOut}s")

            val startTime = System.currentTimeMillis()
            while (isActive && isPlaying) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= totalMs) {
                    break
                }

                val currentVol: Float = when {
                    fadeInMs > 0 && elapsed < fadeInMs -> {
                        targetVolume * (elapsed.toFloat() / fadeInMs)
                    }
                    fadeOutMs > 0 && elapsed > (totalMs - fadeOutMs) -> {
                        val remaining = totalMs - elapsed
                        targetVolume * (remaining.toFloat() / fadeOutMs)
                    }
                    else -> targetVolume
                }.coerceIn(0f, targetVolume)

                try {
                    player.setVolume(currentVol, currentVol)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed setting volume", e)
                    break
                }

                delay(VOLUME_STEP_INTERVAL_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio playback", e)
        } finally {
            stop()
        }
    }

    @Synchronized
    fun stop() {
        isPlaying = false
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaPlayer", e)
            }
        }
        mediaPlayer = null
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .build()

            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
