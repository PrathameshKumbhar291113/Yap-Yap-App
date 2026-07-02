package com.yapyap.core.call

interface CallSignalingClient {
    suspend fun emitAnswer(callSessionId: String): Result<Unit>

    suspend fun emitReject(callSessionId: String): Result<Unit>

    suspend fun emitEnd(callSessionId: String): Result<Unit>

    suspend fun emitIceCandidate(callSessionId: String, candidate: String): Result<Unit>
}
