package com.yapyap.core.call

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AndroidCallRingtoneController(
    private val appContext: Context,
) : CallRingtoneController {
    private var ringtonePlayer: MediaPlayer? = null
    private var ringbackTone: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    override fun startIncomingRingtone() {
        stopAll()
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        runCatching {
            ringtonePlayer = MediaPlayer().apply {
                setDataSource(appContext, uri)
                isLooping = true
                setAudioStreamType(AudioManager.STREAM_RING)
                prepare()
                start()
            }
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = appContext.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            val pattern = longArrayOf(0, 800, 400, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    override fun startOutgoingRingback() {
        stopAll()
        runCatching {
            ringbackTone = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80).apply {
                startTone(ToneGenerator.TONE_SUP_RINGTONE)
            }
        }
    }

    override fun stopAll() {
        runCatching { ringtonePlayer?.stop() }
        runCatching { ringtonePlayer?.release() }
        ringtonePlayer = null
        runCatching { ringbackTone?.stopTone() }
        runCatching { ringbackTone?.release() }
        ringbackTone = null
        vibrator?.cancel()
        vibrator = null
        CallDiagnostics.stage(stage = "CALL_RINGTONE_STOPPED")
    }
}
