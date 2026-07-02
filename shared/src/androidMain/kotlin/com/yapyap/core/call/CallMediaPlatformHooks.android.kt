package com.yapyap.core.call

import android.content.Context
import org.koin.mp.KoinPlatform.getKoin

actual object CallMediaPlatformHooks {
    private val appContext: Context
        get() = getKoin().get<Context>().applicationContext

    actual fun onActiveCallStarted(callSessionId: String, remoteDisplayName: String, isVideoCall: Boolean) {
        CallForegroundService.start(
            context = appContext,
            title = remoteDisplayName.ifBlank { "Call in progress" },
            isVideoCall = isVideoCall,
        )
    }

    actual fun onActiveCallStopped(callSessionId: String?) {
        CallForegroundService.stop(appContext)
        CallDiagnostics.stage(stage = "CALL_FOREGROUND_STOPPED", callSessionId = callSessionId)
        CallDiagnostics.stage(stage = "CALL_FOREGROUND_SERVICE_STOPPED", callSessionId = callSessionId)
    }
}
