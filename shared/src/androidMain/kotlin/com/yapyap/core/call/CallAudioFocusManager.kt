package com.yapyap.core.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

internal class CallAudioFocusManager(
    private val appContext: Context,
) {
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    fun requestFocus(): Boolean {
        abandonFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { /* LiveKit manages ducking */ }
                .build()
            focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            CallDiagnostics.stage(
                stage = "CALL_AUDIOFOCUS_REQUESTED",
                extra = mapOf("granted" to (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED).toString()),
            )
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        @Suppress("DEPRECATION")
        val result = audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN,
        )
        CallDiagnostics.stage(
            stage = "CALL_AUDIOFOCUS_REQUESTED",
            extra = mapOf("granted" to (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED).toString()),
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        CallDiagnostics.stage(stage = "CALL_AUDIOFOCUS_RELEASED")
    }
}
