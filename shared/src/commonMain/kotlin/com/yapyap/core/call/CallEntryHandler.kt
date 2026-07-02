package com.yapyap.core.call

import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.call.domain.entity.CallDirection
import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.entity.remoteParticipant
import com.yapyap.feature.call.domain.repository.CallRepository

class CallEntryHandler(
    private val callSessionStore: CallSessionStore,
    private val callRepository: CallRepository,
    private val inviteDeduplicator: CallInviteDeduplicator,
    private val stateMachine: CallStateMachine,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
) {
    fun localParticipantOrNull(): CallParticipant? {
        val user = (observeAuthStateUseCase().value as? AuthState.Authenticated)?.user ?: return null
        return CallParticipant(
            userId = user.id,
            displayName = user.name.orEmpty(),
        )
    }

    suspend fun onIncomingPush(
        callSessionId: String,
        callerId: Int?,
        callerName: String?,
        callType: CallType,
        conversationId: Int?,
    ) {
        CallDiagnostics.stage(
            stage = "CALL_INCOMING_PUSH",
            callSessionId = callSessionId,
            extra = mapOf(
                "callerId" to callerId?.toString().orEmpty(),
                "callType" to callType.name,
            ),
        )
        if (isSelfCaller(callerId, callSessionId, source = "push")) return
        if (!inviteDeduplicator.tryClaim(callSessionId)) {
            CallDiagnostics.stage(
                stage = "CALL_INVITE_DEDUPED",
                callSessionId = callSessionId,
                extra = mapOf("source" to "push"),
            )
            val resolvedType = resolveAuthoritativeCallType(callSessionId, callType)
            stateMachine.updateSessionCallType(callSessionId, resolvedType)
            return
        }
        applyIncomingInvite(
            callSessionId = callSessionId,
            conversationId = conversationId,
            callerId = callerId ?: -1,
            callerName = callerName,
            callType = callType,
        )
    }

    suspend fun onIncomingFromNavigation(callSessionId: String) {
        CallDiagnostics.stage(
            stage = "CALL_INCOMING_NAVIGATION",
            callSessionId = callSessionId,
        )
        if (callSessionStore.currentSessionOrNull()?.id == callSessionId) {
            return
        }
        callRepository.fetchSession(callSessionId).onSuccess { session ->
            val localUserId = localParticipantOrNull()?.userId
            if (localUserId != null && session.caller.userId == localUserId) {
                CallDiagnostics.stage(
                    stage = "CALL_INVITE_IGNORED",
                    callSessionId = callSessionId,
                    extra = mapOf("reason" to "self_caller_navigation"),
                )
                return@onSuccess
            }
            if (!inviteDeduplicator.tryClaim(callSessionId)) {
                stateMachine.updateSessionCallType(callSessionId, session.callType)
                return@onSuccess
            }
            val remoteUser = localUserId?.let { session.remoteParticipant(it) } ?: session.caller
            when (stateMachine.callDirectionOrDefault(session, localUserId)) {
                CallDirection.Outgoing -> stateMachine.setOutgoing(session = session, remoteUser = remoteUser)
                CallDirection.Incoming -> stateMachine.setIncoming(session = session, remoteUser = remoteUser)
            }
        }.onFailure {
            onIncomingPush(
                callSessionId = callSessionId,
                callerId = null,
                callerName = null,
                callType = CallType.Voice,
                conversationId = null,
            )
        }
    }

    suspend fun applyIncomingInvite(
        callSessionId: String,
        conversationId: Int?,
        callerId: Int,
        callerName: String?,
        callType: CallType,
    ) {
        CallDiagnostics.stage(
            stage = "CALL_ENTRY_APPLY_INCOMING",
            callSessionId = callSessionId,
            extra = mapOf(
                "callerId" to callerId.toString(),
                "callType" to callType.name,
            ),
        )
        if (isSelfCaller(callerId, callSessionId, source = "invite")) return

        val currentState = stateMachine.currentState()
        val currentSessionId = stateMachine.currentSessionOrNull()?.id
        if (currentSessionId == callSessionId && currentState is CallState.Outgoing) {
            CallDiagnostics.stage(
                stage = "CALL_INVITE_IGNORED",
                callSessionId = callSessionId,
                extra = mapOf("reason" to "already_outgoing"),
            )
            return
        }

        val resolvedType = resolveAuthoritativeCallType(callSessionId, callType)
        if (stateMachine.shouldIgnoreInviteBecauseBusy(callSessionId)) {
            CallDiagnostics.stage(
                stage = "CALL_INVITE_IGNORED",
                callSessionId = callSessionId,
                extra = mapOf("reason" to "busy"),
            )
            return
        }
        if (stateMachine.isDuplicateIncomingInvite(callSessionId)) {
            stateMachine.updateSessionCallType(callSessionId, resolvedType)
            return
        }
        val localUser = localParticipantOrNull() ?: CallParticipant(userId = -1, displayName = "")
        val remoteUser = CallParticipant(
            userId = callerId,
            displayName = callerName.orEmpty(),
        )
        val session = CallSession(
            id = callSessionId,
            conversationId = conversationId,
            callType = resolvedType,
            caller = remoteUser,
            callee = localUser,
        )
        stateMachine.setIncoming(
            session = session,
            remoteUser = remoteUser,
        )
        CallDiagnostics.stage(
            stage = "CALL_INCOMING_ACTIVE",
            callSessionId = callSessionId,
            extra = mapOf("callType" to resolvedType.name),
        )
    }

    private fun isSelfCaller(callerId: Int?, callSessionId: String, source: String): Boolean {
        val localUserId = localParticipantOrNull()?.userId ?: return false
        if (callerId != null && callerId == localUserId) {
            CallDiagnostics.stage(
                stage = "CALL_INVITE_IGNORED",
                callSessionId = callSessionId,
                extra = mapOf("reason" to "self_caller", "source" to source),
            )
            return true
        }
        return false
    }

    private suspend fun resolveAuthoritativeCallType(
        callSessionId: String,
        hintedType: CallType,
    ): CallType {
        val fetched = callRepository.fetchSession(callSessionId).getOrNull()?.callType
        val resolved = fetched ?: hintedType
        if (fetched != null && fetched != hintedType) {
            CallDiagnostics.stage(
                stage = "CALL_TYPE_RECONCILED",
                callSessionId = callSessionId,
                extra = mapOf(
                    "hinted" to hintedType.name,
                    "authoritative" to fetched.name,
                ),
            )
        }
        return resolved
    }
}
