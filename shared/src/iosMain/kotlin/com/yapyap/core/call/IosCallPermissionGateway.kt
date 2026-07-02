package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.CallType
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IosCallPermissionGateway : CallPermissionGateway {
    override suspend fun ensurePermissions(callType: CallType): CallPermissionStatus {
        val mic = ensureMicrophonePermission()
        if (mic != CallPermissionStatus.Granted) return mic
        if (callType == CallType.Video) {
            return ensureCameraPermission()
        }
        return CallPermissionStatus.Granted
    }

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(
            url = url,
            options = emptyMap<Any?, Any?>(),
            completionHandler = null,
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun ensureMicrophonePermission(): CallPermissionStatus {
        return when (AVAudioSession.sharedInstance().recordPermission()) {
            AVAudioSessionRecordPermissionGranted -> CallPermissionStatus.Granted
            AVAudioSessionRecordPermissionDenied -> CallPermissionStatus.PermanentlyDenied
            AVAudioSessionRecordPermissionUndetermined -> requestMicrophoneAccess()
            else -> CallPermissionStatus.Denied
        }
    }

    private suspend fun ensureCameraPermission(): CallPermissionStatus {
        return when (IosCameraPermissionBridgeRegistry.currentAuthorizationStatus()) {
            IosCameraPermissionStatus.Authorized -> {
                CallDiagnostics.stage(stage = "CALL_CAMERA_PERMISSION_GRANTED")
                CallPermissionStatus.Granted
            }
            IosCameraPermissionStatus.Denied,
            IosCameraPermissionStatus.Restricted,
            -> CallPermissionStatus.PermanentlyDenied
            IosCameraPermissionStatus.NotDetermined -> requestCameraAccess()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun requestMicrophoneAccess(): CallPermissionStatus = suspendCoroutine { continuation ->
        dispatch_async(dispatch_get_main_queue()) {
            AVAudioSession.sharedInstance().requestRecordPermission { granted: Boolean ->
                continuation.resume(
                    if (granted) CallPermissionStatus.Granted else CallPermissionStatus.Denied,
                )
            }
        }
    }

    private suspend fun requestCameraAccess(): CallPermissionStatus = suspendCoroutine { continuation ->
        dispatch_async(dispatch_get_main_queue()) {
            IosCameraPermissionBridgeRegistry.requestAccess { granted ->
                if (granted) {
                    CallDiagnostics.stage(stage = "CALL_CAMERA_PERMISSION_GRANTED")
                }
                continuation.resume(
                    if (granted) CallPermissionStatus.Granted else CallPermissionStatus.Denied,
                )
            }
        }
    }
}
