package com.yapyap.core.call

import com.yapyap.core.call.CallMediaSessionBinder
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.call.domain.entity.CallEndReason
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.repository.CallMediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallMediaLifecycle(
    private val callMediaRepository: CallMediaRepository,
    private val callSessionStore: CallSessionStore,
    private val stateMachine: CallStateMachine,
    private val sessionBinder: CallMediaSessionBinder,
    private val dispatcherProvider: DispatcherProvider,
) {
    private var scope: CoroutineScope? = null

    fun bindScope(parentScope: CoroutineScope) {
        scope = parentScope
        sessionBinder.start(parentScope)
    }

    suspend fun releaseCall(callSessionId: String) {
        callMediaRepository.releaseCall(callSessionId)
    }

    fun scheduleReleaseAndDismiss(session: CallSession) {
        dismissEndedSoon(session.id)
    }

    fun resetToIdle(reason: CallEndReason) {
        val session = callSessionStore.currentSessionOrNull()
        if (session == null) {
            scope?.launch(dispatcherProvider.default) {
                sessionBinder.releaseBoundIfAny()
            }
            stateMachine.setIdle()
            return
        }
        stateMachine.setEnded(session = session, reason = reason)
        scheduleReleaseAndDismiss(session)
    }

    fun dismissEndedSoon(callSessionId: String) {
        scope?.launch(dispatcherProvider.default) {
            delay(END_SCREEN_DISMISS_MS)
            if (callSessionStore.currentSessionOrNull()?.id == callSessionId) {
                stateMachine.setIdle()
            }
        }
    }

    private companion object {
        const val END_SCREEN_DISMISS_MS = 2_000L
    }
}
