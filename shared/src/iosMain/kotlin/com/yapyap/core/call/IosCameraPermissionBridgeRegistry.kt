package com.yapyap.core.call

enum class IosCameraPermissionStatus {
    NotDetermined,
    Authorized,
    Denied,
    Restricted,
}

interface IosCameraPermissionSwiftHandler {
    fun currentAuthorizationStatusRaw(): String
    fun requestAccess()
}

object IosCameraPermissionBridgeRegistry {
    private var handler: IosCameraPermissionSwiftHandler? = null
    private var pendingCallback: ((Boolean) -> Unit)? = null

    fun register(handler: IosCameraPermissionSwiftHandler) {
        this.handler = handler
    }

    fun currentAuthorizationStatus(): IosCameraPermissionStatus {
        return when (handler?.currentAuthorizationStatusRaw()?.lowercase()) {
            "authorized" -> IosCameraPermissionStatus.Authorized
            "denied" -> IosCameraPermissionStatus.Denied
            "restricted" -> IosCameraPermissionStatus.Restricted
            else -> IosCameraPermissionStatus.NotDetermined
        }
    }

    fun requestAccess(callback: (Boolean) -> Unit) {
        val swiftHandler = handler
        if (swiftHandler == null) {
            callback(false)
            return
        }
        pendingCallback = callback
        swiftHandler.requestAccess()
    }

    fun completeAccess(granted: Boolean) {
        pendingCallback?.invoke(granted)
        pendingCallback = null
    }
}

fun registerIosCameraPermissionHandler(handler: IosCameraPermissionSwiftHandler) {
    IosCameraPermissionBridgeRegistry.register(handler)
}

fun iosCameraPermissionAccessCompleted(granted: Boolean) {
    IosCameraPermissionBridgeRegistry.completeAccess(granted)
}
