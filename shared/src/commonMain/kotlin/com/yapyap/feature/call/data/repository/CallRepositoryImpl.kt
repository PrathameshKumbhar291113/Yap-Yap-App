package com.yapyap.feature.call.data.repository

import com.yapyap.core.call.CallDiagnostics
import com.yapyap.core.call.CallSignalingClient
import com.yapyap.core.call.CallMediaCredentialsStore
import com.yapyap.core.network.ApiException
import com.yapyap.core.network.ApiResult
import com.yapyap.core.network.getOrThrow
import com.yapyap.feature.call.data.dto.CreateCallRequestDto
import com.yapyap.feature.call.data.mapper.toDomain
import com.yapyap.feature.call.data.mapper.toReconcileSnapshot
import com.yapyap.feature.call.data.mapper.toRoomCredentials
import com.yapyap.feature.call.data.remote.CallApi
import com.yapyap.feature.call.domain.entity.ActiveCallSnapshot
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.repository.CallRepository

class CallRepositoryImpl(
    private val callApi: CallApi,
    private val callSignalingClient: CallSignalingClient,
    private val credentialsStore: CallMediaCredentialsStore,
) : CallRepository {

    override suspend fun createOutgoingSession(
        conversationId: Int,
        callType: CallType,
        calleeUserId: Int,
    ): Result<CallSession> {
        return runCatching {
            val payload = callApi.createCall(
                CreateCallRequestDto(
                    conversationId = conversationId,
                    callType = callType.name.lowercase(),
                    calleeUserId = calleeUserId,
                ),
            ).getOrThrow()
            storeCredentials(payload.session.id, payload)
            payload.session.toDomain()
        }
    }

    override suspend fun fetchSession(callSessionId: String): Result<CallSession> {
        return runCatching {
            val payload = callApi.reconcileCall(callSessionId).getOrThrow()
            storeCredentials(callSessionId, payload)
            payload.session.toDomain()
        }
    }

    override suspend fun reconcileSession(callSessionId: String): Result<ActiveCallSnapshot?> {
        return when (val result = callApi.reconcileCall(callSessionId)) {
            is ApiResult.Success -> {
                val snapshot = result.data.toReconcileSnapshot()
                storeCredentials(callSessionId, result.data)
                Result.success(snapshot)
            }
            is ApiResult.Error -> {
                if (result.exception is ApiException.NotFound) {
                    Result.success(null)
                } else {
                    Result.failure(result.exception)
                }
            }
        }
    }

    override suspend fun fetchActiveCall(): Result<ActiveCallSnapshot?> {
        CallDiagnostics.stage(stage = "CALL_INCOMING_RECONCILE_REQUEST")
        return when (val result = callApi.fetchActiveCall()) {
            is ApiResult.Success -> {
                val payload = result.data
                if (payload == null) {
                    CallDiagnostics.stage(stage = "CALL_INCOMING_RECONCILE_NONE")
                    Result.success(null)
                } else {
                    val snapshot = payload.toReconcileSnapshot()
                    storeCredentials(snapshot.session.id, payload)
                    CallDiagnostics.stage(
                        stage = "CALL_INCOMING_RECONCILE_FOUND",
                        callSessionId = snapshot.session.id,
                        extra = mapOf(
                            "status" to snapshot.status.name,
                            "callType" to snapshot.session.callType.name,
                        ),
                    )
                    Result.success(snapshot)
                }
            }
            is ApiResult.Error -> {
                CallDiagnostics.stage(
                    stage = "CALL_INCOMING_RECONCILE_FAILED",
                    extra = mapOf("error" to (result.exception.message ?: "unknown")),
                )
                Result.failure(result.exception)
            }
        }
    }

    override suspend fun refreshCallToken(callSessionId: String): Result<ActiveCallSnapshot> {
        return runCatching {
            val payload = callApi.refreshCallToken(callSessionId).getOrThrow()
            storeCredentials(callSessionId, payload)
            payload.toReconcileSnapshot()
        }
    }

    override suspend fun redialFromHistory(callHistoryId: Int): Result<CallSession> {
        return runCatching {
            val payload = callApi.redialCall(callHistoryId).getOrThrow()
            storeCredentials(payload.session.id, payload)
            payload.session.toDomain()
        }
    }

    override suspend fun answerSession(callSessionId: String): Result<Unit> {
        CallDiagnostics.stage(stage = "CALL_ACCEPT_REST", callSessionId = callSessionId)
        val restResult = runCatching {
            val payload = callApi.answerCall(callSessionId).getOrThrow()
            storeCredentials(callSessionId, payload)
        }
        if (restResult.isFailure) {
            val error = restResult.exceptionOrNull()
            if (error.isAlreadyAcceptedOnServer()) {
                CallDiagnostics.stage(
                    stage = "CALL_ACCEPT_ALREADY_ACCEPTED",
                    callSessionId = callSessionId,
                    extra = mapOf("via" to "rest"),
                )
                return reconcileAfterAlreadyAccepted(callSessionId)
            }
            CallDiagnostics.stage(
                stage = "CALL_ACCEPT_FAILED",
                callSessionId = callSessionId,
                extra = mapOf("via" to "rest", "error" to (error?.message ?: "unknown")),
            )
            return restResult.map { }
        }
        CallDiagnostics.stage(stage = "CALL_ACCEPT_SUCCESS", callSessionId = callSessionId, extra = mapOf("via" to "rest"))
        // REST /answer is authoritative; server emits call:accepted to both parties.
        return Result.success(Unit)
    }

    override suspend fun rejectSession(callSessionId: String): Result<Unit> {
        CallDiagnostics.stage(stage = "CALL_REJECT_REST", callSessionId = callSessionId)
        return runCatching {
            callApi.rejectCall(callSessionId).getOrThrow()
            CallDiagnostics.stage(stage = "CALL_REJECT_SUCCESS", callSessionId = callSessionId, extra = mapOf("via" to "rest"))
        }.onFailure { error ->
            CallDiagnostics.stage(
                stage = "CALL_REJECT_FAILED",
                callSessionId = callSessionId,
                extra = mapOf("error" to (error.message ?: "unknown")),
            )
        }
    }

    override suspend fun endSession(callSessionId: String): Result<Unit> {
        CallDiagnostics.stage(stage = "CALL_END_REST", callSessionId = callSessionId)
        val apiResult = runCatching {
            callApi.endCall(callSessionId).getOrThrow()
        }
        if (apiResult.isSuccess) {
            CallDiagnostics.stage(stage = "CALL_END_SUCCESS", callSessionId = callSessionId, extra = mapOf("via" to "rest"))
            callSignalingClient.emitEnd(callSessionId)
            return apiResult.map { }
        }
        val error = apiResult.exceptionOrNull()
        if (error.isTerminalTransitionOnServer()) {
            CallDiagnostics.stage(
                stage = "CALL_END_ALREADY_TERMINAL",
                callSessionId = callSessionId,
                extra = mapOf("error" to (error?.message ?: "unknown")),
            )
            return Result.success(Unit)
        }
        CallDiagnostics.stage(
            stage = "CALL_END_FAILED",
            callSessionId = callSessionId,
            extra = mapOf("via" to "rest", "error" to (error?.message ?: "unknown")),
        )
        return apiResult.map { }
    }

    private suspend fun reconcileAfterAlreadyAccepted(callSessionId: String): Result<Unit> {
        return reconcileSession(callSessionId).mapCatching { snapshot ->
            check(snapshot != null && !snapshot.status.isTerminal) {
                "Call session is no longer active"
            }
        }
    }

    private fun Throwable?.isAlreadyAcceptedOnServer(): Boolean {
        if (this !is ApiException) return false
        return message?.contains("status is 'accepted'", ignoreCase = true) == true ||
            message?.contains("status is \"accepted\"", ignoreCase = true) == true
    }

    private fun Throwable?.isTerminalTransitionOnServer(): Boolean {
        if (this !is ApiException) return false
        val message = message.orEmpty()
        return message.contains("status is 'ended'", ignoreCase = true) ||
            message.contains("status is 'rejected'", ignoreCase = true) ||
            message.contains("status is 'missed'", ignoreCase = true) ||
            message.contains("status is 'failed'", ignoreCase = true) ||
            message.contains("status is 'cancelled'", ignoreCase = true)
    }

    private suspend fun storeCredentials(
        callSessionId: String,
        payload: com.yapyap.feature.call.data.dto.CallSessionResponseDto,
    ) {
        val canJoin = payload.reconciliation?.canJoinMedia ?: true
        payload.toRoomCredentials()?.let { credentials ->
            credentialsStore.store(
                sessionId = callSessionId,
                value = credentials,
                canJoinMedia = canJoin,
            )
        }
    }
}
