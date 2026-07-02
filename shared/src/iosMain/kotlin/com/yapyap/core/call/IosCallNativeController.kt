package com.yapyap.core.call

class IosCallNativeController : CallNativeController {
    override fun registerPhoneAccount() {
        // Swift YapCallKitManager.install() registers the handler at launch.
    }

    override fun showIncomingCall(request: CallNativeIncomingRequest) {
        CallDiagnostics.stage(
            stage = "CALL_CALLKIT_INCOMING",
            callSessionId = request.callSessionId,
            extra = mapOf(
                "caller" to request.callerDisplayName,
                "video" to request.isVideoCall.toString(),
            ),
        )
        IosCallNativeSwiftBridgeRegistry.showIncoming(
            request.callSessionId,
            request.callerDisplayName,
            request.isVideoCall,
        )
    }

    override fun showOutgoingCall(request: CallNativeIncomingRequest) = Unit

    override fun setCallActive(callSessionId: String, isVideoCall: Boolean) {
        IosCallNativeSwiftBridgeRegistry.setActive(callSessionId, isVideoCall)
    }

    override fun dismissCall(callSessionId: String, reason: CallNativeDismissReason) {
        IosCallNativeSwiftBridgeRegistry.dismiss(callSessionId)
    }

    override fun cancelIncomingNotification(callSessionId: String) {
        IosCallNativeSwiftBridgeRegistry.dismiss(callSessionId)
    }

    override fun showMissedCallNotification(callSessionId: String, callerDisplayName: String) {
        IosCallNativeSwiftBridgeRegistry.showMissed(callSessionId, callerDisplayName)
    }
}
