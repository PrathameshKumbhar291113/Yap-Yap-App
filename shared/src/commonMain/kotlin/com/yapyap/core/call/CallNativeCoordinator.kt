package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.call.domain.entity.CallEndReason
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.entity.remoteDisplayName
import com.yapyap.feature.call.domain.entity.remoteParticipant
import com.yapyap.feature.call.domain.reader.CallSessionReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallNativeCoordinator(
    private val callCoordinator: Lazy<CallCoordinator>,
    private val callNativeController: CallNativeController,
    private val callSessionReader: CallSessionReader,
    private val entryHandler: CallEntryHandler,
    private val dispatcherProvider: DispatcherProvider,
) {
    private var scope: CoroutineScope? = null
    private var lastPresentedSessionId: String? = null

    fun start(parentScope: CoroutineScope) {
        if (scope != null) return
        scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
        CallNativeBridge.bind(this)
        callNativeController.registerPhoneAccount()
        val coordinatorScope = scope ?: return
        coordinatorScope.launch(dispatcherProvider.default) {
            callSessionReader.state.collect { state -> syncNativeUi(state) }
        }
    }

    internal fun onAnswerFromNative(callSessionId: String) {
        CallDiagnostics.stage(stage = "CALL_NATIVE_ANSWER", callSessionId = callSessionId)
        scope?.launch(dispatcherProvider.default) {
            val current = callSessionReader.state.value
            if (current is CallState.Incoming && current.session.id == callSessionId) {
                callCoordinator.value.answerIncoming()
            } else {
                CallDiagnostics.stage(
                    stage = "CALL_NATIVE_SYNC",
                    callSessionId = callSessionId,
                    extra = mapOf("action" to "answer_restore"),
                )
                callCoordinator.value.onIncomingFromNavigation(callSessionId)
                callCoordinator.value.answerIncoming()
            }
        }
    }

    internal fun onRejectFromNative(callSessionId: String) {
        CallDiagnostics.stage(stage = "CALL_NATIVE_REJECT", callSessionId = callSessionId)
        scope?.launch(dispatcherProvider.default) {
            val current = callSessionReader.state.value
            if (current is CallState.Incoming && current.session.id == callSessionId) {
                callCoordinator.value.rejectIncoming()
            } else {
                callCoordinator.value.onIncomingFromNavigation(callSessionId)
                callCoordinator.value.rejectIncoming()
            }
        }
    }

    internal fun onEndFromNative(callSessionId: String) {
        CallDiagnostics.stage(stage = "CALL_NATIVE_CALL_ENDED", callSessionId = callSessionId)
        CallDiagnostics.stage(stage = "CALL_NATIVE_END", callSessionId = callSessionId)
        scope?.launch(dispatcherProvider.default) {
            callCoordinator.value.endActiveCall()
        }
    }

    internal fun onRestoreFromNative(callSessionId: String) {
        CallDiagnostics.stage(stage = "CALL_NATIVE_RESTORE", callSessionId = callSessionId)
        scope?.launch(dispatcherProvider.default) {
            callCoordinator.value.onIncomingFromNavigation(callSessionId)
        }
    }

    fun presentIncomingFromPush(request: CallNativeIncomingRequest) {
        CallDiagnostics.stage(
            stage = "CALL_PUSH_RECEIVED",
            callSessionId = request.callSessionId,
            extra = mapOf(
                "caller" to request.callerDisplayName,
                "video" to request.isVideoCall.toString(),
            ),
        )
        callNativeController.showIncomingCall(request)
    }

    fun presentIncomingFromPushOpened(request: CallNativeIncomingRequest) {
        CallDiagnostics.stage(
            stage = "CALL_PUSH_OPENED",
            callSessionId = request.callSessionId,
        )
        callNativeController.showIncomingCall(request)
    }

    private fun syncNativeUi(state: CallState) {
        when (state) {
            is CallState.Incoming -> {
                if (lastPresentedSessionId == state.session.id) return
                lastPresentedSessionId = state.session.id
                callNativeController.showIncomingCall(toRequest(state))
            }
            is CallState.Outgoing -> {
                lastPresentedSessionId = state.session.id
                callNativeController.showOutgoingCall(toRequest(state))
            }
            is CallState.Connected -> {
                lastPresentedSessionId = state.session.id
                callNativeController.setCallActive(
                    callSessionId = state.session.id,
                    isVideoCall = state.session.callType == CallType.Video,
                )
            }
            is CallState.Ended -> {
                val sessionId = state.session.id
                lastPresentedSessionId = null
                val reason = when (state.reason) {
                    CallEndReason.Missed -> CallNativeDismissReason.Missed
                    CallEndReason.Rejected -> CallNativeDismissReason.Rejected
                    CallEndReason.RemoteHangup -> CallNativeDismissReason.RemoteEnded
                    else -> CallNativeDismissReason.Ended
                }
                callNativeController.dismissCall(sessionId, reason)
                if (state.reason == CallEndReason.Missed) {
                    callNativeController.showMissedCallNotification(
                        sessionId,
                        remoteNameForEnded(state),
                    )
                }
            }
            is CallState.Failed -> {
                state.session?.id?.let { id ->
                    lastPresentedSessionId = null
                    callNativeController.dismissCall(id, CallNativeDismissReason.Failed)
                }
            }
            CallState.Idle -> {
                lastPresentedSessionId?.let { id ->
                    callNativeController.dismissCall(id, CallNativeDismissReason.Ended)
                }
                lastPresentedSessionId = null
            }
            else -> Unit
        }
    }

    private fun toRequest(state: CallState.Incoming): CallNativeIncomingRequest {
        val localUserId = entryHandler.localParticipantOrNull()?.userId ?: return fallbackRequest(state.session)
        val remote = state.session.remoteParticipant(localUserId)
        return CallNativeIncomingRequest(
            callSessionId = state.session.id,
            callerDisplayName = remote.displayName.ifBlank { "Unknown" },
            callerId = remote.userId,
            isVideoCall = state.session.callType == CallType.Video,
            conversationId = state.session.conversationId,
        )
    }

    private fun toRequest(state: CallState.Outgoing): CallNativeIncomingRequest {
        val localUserId = entryHandler.localParticipantOrNull()?.userId ?: return fallbackRequest(state.session)
        val remote = state.session.remoteParticipant(localUserId)
        return CallNativeIncomingRequest(
            callSessionId = state.session.id,
            callerDisplayName = remote.displayName.ifBlank { "Unknown" },
            callerId = remote.userId,
            isVideoCall = state.session.callType == CallType.Video,
            conversationId = state.session.conversationId,
        )
    }

    private fun fallbackRequest(session: com.yapyap.feature.call.domain.entity.CallSession): CallNativeIncomingRequest =
        CallNativeIncomingRequest(
            callSessionId = session.id,
            callerDisplayName = "Unknown",
            callerId = null,
            isVideoCall = session.callType == CallType.Video,
            conversationId = session.conversationId,
        )

    private fun remoteNameForEnded(state: CallState.Ended): String {
        val localUserId = entryHandler.localParticipantOrNull()?.userId
        return state.remoteDisplayName(localUserId).ifBlank { "Unknown" }
    }
}
