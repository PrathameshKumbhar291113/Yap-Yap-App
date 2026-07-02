package com.yapyap.core.call

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate

class IosCallRingtoneController : CallRingtoneController {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var incomingJob: Job? = null
    private var outgoingJob: Job? = null

    override fun startIncomingRingtone() {
        stopAll()
        incomingJob = scope.launch {
            while (isActive) {
                AudioServicesPlaySystemSound(1005u)
                AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
                delay(2_000)
            }
        }
    }

    override fun startOutgoingRingback() {
        stopAll()
        outgoingJob = scope.launch {
            while (isActive) {
                AudioServicesPlaySystemSound(1003u)
                delay(2_500)
            }
        }
    }

    override fun stopAll() {
        incomingJob?.cancel()
        incomingJob = null
        outgoingJob?.cancel()
        outgoingJob = null
        CallDiagnostics.stage(stage = "CALL_RINGTONE_STOPPED", extra = mapOf("platform" to "ios"))
    }
}
