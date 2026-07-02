package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.CallRoomCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CallMediaCredentialsStore {
    private val mutex = Mutex()
    private var sessionId: String? = null
    private var credentials: CallRoomCredentials? = null
    private var canJoinMedia: Boolean = true
    private val _revision = MutableStateFlow(0)

    /** Bumps whenever credentials or join eligibility change. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    suspend fun store(
        sessionId: String,
        value: CallRoomCredentials,
        canJoinMedia: Boolean = true,
    ) {
        if (!value.isValid()) return
        mutex.withLock {
            this.sessionId = sessionId
            credentials = value
            this.canJoinMedia = canJoinMedia
        }
        _revision.value = _revision.value + 1
        CallLiveKitTokenDiagnostics.logCredentials(
            callSessionId = sessionId,
            credentials = value,
            source = "credentials_store",
            canJoinMedia = canJoinMedia,
        )
        CallDiagnostics.stage(
            stage = "CALL_TOKEN_CREATED",
            callSessionId = sessionId,
            extra = mapOf(
                "roomName" to value.roomName,
                "livekitUrl" to value.livekitUrl,
                "tokenPrefix" to value.providerToken.take(12),
                "canJoinMedia" to canJoinMedia.toString(),
            ),
        )
    }

    suspend fun get(sessionId: String): CallRoomCredentials? {
        return mutex.withLock {
            if (sessionId != this.sessionId) return null
            credentials
        }
    }

    suspend fun canJoinMedia(sessionId: String): Boolean {
        return mutex.withLock {
            sessionId == this.sessionId && canJoinMedia
        }
    }

    suspend fun setCanJoinMedia(sessionId: String, allowed: Boolean) {
        mutex.withLock {
            if (sessionId == this.sessionId) {
                canJoinMedia = allowed
            }
        }
        _revision.value = _revision.value + 1
    }

    suspend fun clear(sessionId: String? = null) {
        mutex.withLock {
            if (sessionId == null || sessionId == this.sessionId) {
                this.sessionId = null
                credentials = null
                canJoinMedia = true
            }
        }
        _revision.value = _revision.value + 1
    }
}
