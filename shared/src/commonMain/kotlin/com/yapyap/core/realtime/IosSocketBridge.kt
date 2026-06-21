package com.yapyap.core.realtime

/**
 * Callback bridge between Kotlin [SocketChatRealtimeClient] (iOS) and the Swift
 * Socket.IO native layer in the iosApp target.
 */
object IosSocketBridge {
    private var eventListener: ((String, String?) -> Unit)? = null
    private var ackListener: ((Int, String?) -> Unit)? = null

    private var connectHandler: ((String, String) -> Unit)? = null
    private var disconnectHandler: (() -> Unit)? = null
    private var emitHandler: ((String, String, Int) -> Unit)? = null

    fun setEventListener(listener: (String, String?) -> Unit) {
        eventListener = listener
    }

    fun setAckListener(listener: (Int, String?) -> Unit) {
        ackListener = listener
    }

    fun registerConnectHandler(handler: (String, String) -> Unit) {
        connectHandler = handler
    }

    fun registerDisconnectHandler(handler: () -> Unit) {
        disconnectHandler = handler
    }

    fun registerEmitHandler(handler: (String, String, Int) -> Unit) {
        emitHandler = handler
    }

    fun deliverNativeEvent(event: String, payload: String?) {
        eventListener?.invoke(event, payload)
    }

    fun deliverAck(ackId: Int, payload: String?) {
        ackListener?.invoke(ackId, payload)
    }

    internal fun connect(baseUrl: String, token: String) {
        connectHandler?.invoke(baseUrl, token)
    }

    internal fun disconnect() {
        disconnectHandler?.invoke()
    }

    internal fun emit(event: String, payloadJson: String, ackId: Int) {
        emitHandler?.invoke(event, payloadJson, ackId)
    }
}
