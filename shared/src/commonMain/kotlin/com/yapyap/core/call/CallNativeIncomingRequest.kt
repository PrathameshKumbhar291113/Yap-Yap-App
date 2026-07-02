package com.yapyap.core.call

data class CallNativeIncomingRequest(
    val callSessionId: String,
    val callerDisplayName: String,
    val callerId: Int?,
    val isVideoCall: Boolean,
    val conversationId: Int?,
)
