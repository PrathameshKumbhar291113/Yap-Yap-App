package com.yapyap.core.call

actual object CallMediaPlatformHooks {
    actual fun onActiveCallStarted(callSessionId: String, remoteDisplayName: String, isVideoCall: Boolean) {
        CallDiagnostics.stage(
            stage = "CALL_FOREGROUND_SERVICE_STARTED",
            callSessionId = callSessionId,
            extra = mapOf(
                "platform" to "ios",
                "isVideo" to isVideoCall.toString(),
            ),
        )
    }

    actual fun onActiveCallStopped(callSessionId: String?) {
        CallDiagnostics.stage(
            stage = "CALL_FOREGROUND_SERVICE_STOPPED",
            callSessionId = callSessionId,
            extra = mapOf("platform" to "ios"),
        )
    }
}
