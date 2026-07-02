package com.yapyap.core.call

interface IosCallNativeSwiftHandler {
    fun showIncoming(callSessionId: String, callerDisplayName: String, isVideoCall: Boolean)
    fun setActive(callSessionId: String, isVideoCall: Boolean)
    fun dismiss(callSessionId: String)
    fun showMissed(callSessionId: String, callerDisplayName: String)
}

object IosCallNativeSwiftBridgeRegistry {
    private var handler: IosCallNativeSwiftHandler? = null

    fun register(handler: IosCallNativeSwiftHandler) {
        this.handler = handler
    }

    fun showIncoming(callSessionId: String, callerDisplayName: String, isVideoCall: Boolean) {
        handler?.showIncoming(callSessionId, callerDisplayName, isVideoCall)
    }

    fun setActive(callSessionId: String, isVideoCall: Boolean) {
        handler?.setActive(callSessionId, isVideoCall)
    }

    fun dismiss(callSessionId: String) {
        handler?.dismiss(callSessionId)
    }

    fun showMissed(callSessionId: String, callerDisplayName: String) {
        handler?.showMissed(callSessionId, callerDisplayName)
    }
}
