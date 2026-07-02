package com.yapyap.core.call

expect object CallMediaPlatformHooks {
    fun onActiveCallStarted(callSessionId: String, remoteDisplayName: String, isVideoCall: Boolean)
    fun onActiveCallStopped(callSessionId: String?)
}
