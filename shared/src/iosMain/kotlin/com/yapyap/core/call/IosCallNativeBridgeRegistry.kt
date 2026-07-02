package com.yapyap.core.call

object IosCallNativeBridgeRegistry {
    fun onAnswer(callSessionId: String) {
        CallDiagnostics.stage(stage = "CALL_CALLKIT_ANSWER", callSessionId = callSessionId)
        CallNativeBridge.onAnswerFromNative(callSessionId)
    }

    fun onReject(callSessionId: String) {
        CallDiagnostics.stage(stage = "CALL_CALLKIT_REJECT", callSessionId = callSessionId)
        CallNativeBridge.onRejectFromNative(callSessionId)
    }

    fun onEnd(callSessionId: String) {
        CallDiagnostics.stage(stage = "CALL_CALLKIT_END", callSessionId = callSessionId)
        CallNativeBridge.onEndFromNative(callSessionId)
    }
}

fun iosCallNativeOnAnswer(callSessionId: String) = IosCallNativeBridgeRegistry.onAnswer(callSessionId)
fun iosCallNativeOnReject(callSessionId: String) = IosCallNativeBridgeRegistry.onReject(callSessionId)
fun iosCallNativeOnEnd(callSessionId: String) = IosCallNativeBridgeRegistry.onEnd(callSessionId)

fun registerIosCallNativeHandler(handler: IosCallNativeSwiftHandler) {
    IosCallNativeSwiftBridgeRegistry.register(handler)
}
