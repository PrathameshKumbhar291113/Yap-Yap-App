package com.yapyap.feature.call.domain.repository

import com.yapyap.feature.call.domain.entity.ActiveCallSnapshot
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.CallType

interface CallRepository {
    suspend fun createOutgoingSession(
        conversationId: Int,
        callType: CallType,
        calleeUserId: Int,
    ): Result<CallSession>

    suspend fun fetchSession(callSessionId: String): Result<CallSession>

    suspend fun reconcileSession(callSessionId: String): Result<ActiveCallSnapshot?>

    suspend fun fetchActiveCall(): Result<ActiveCallSnapshot?>

    suspend fun refreshCallToken(callSessionId: String): Result<ActiveCallSnapshot>

    suspend fun redialFromHistory(callHistoryId: Int): Result<CallSession>

    suspend fun answerSession(callSessionId: String): Result<Unit>

    suspend fun rejectSession(callSessionId: String): Result<Unit>

    suspend fun endSession(callSessionId: String): Result<Unit>
}
