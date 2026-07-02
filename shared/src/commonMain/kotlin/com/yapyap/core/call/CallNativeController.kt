package com.yapyap.core.call

/**
 * Platform-native call UI (Android Telecom / iOS CallKit).
 * All user actions are routed back through [CallNativeBridge] into [CallCoordinator].
 */
interface CallNativeController {
    fun registerPhoneAccount()
    fun showIncomingCall(request: CallNativeIncomingRequest)
    fun showOutgoingCall(request: CallNativeIncomingRequest)
    fun setCallActive(callSessionId: String, isVideoCall: Boolean)
    fun dismissCall(callSessionId: String, reason: CallNativeDismissReason = CallNativeDismissReason.Ended)
    fun cancelIncomingNotification(callSessionId: String)
    fun showMissedCallNotification(callSessionId: String, callerDisplayName: String)
}

enum class CallNativeDismissReason {
    Answered,
    Rejected,
    Ended,
    RemoteEnded,
    Missed,
    Failed,
}
