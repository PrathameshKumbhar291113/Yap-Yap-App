package com.yapyap.core.diagnostics

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Correlates outbox transport decisions with socket/network readiness timing.
 * Filter: `adb logcat -s YapRuntimeDiag:I | grep OutboxTrace`
 */
@OptIn(ExperimentalTime::class)
object RealtimeTransportClock {
    @Volatile
    private var lastSocketReadyAtMs: Long? = null

    @Volatile
    private var lastNetworkOnlineAtMs: Long? = null

    @Volatile
    private var socketReadyGeneration: Long = 0

    @Volatile
    private var lastSocketReadyUserId: Int? = null

    @Volatile
    private var lastSocketId: String? = null

    fun recordSocketReady(userId: Int, socketId: String?, timestampMs: Long = nowMs()) {
        val previousReadyAtMs = lastSocketReadyAtMs
        lastSocketReadyAtMs = timestampMs
        lastNetworkOnlineAtMs = lastNetworkOnlineAtMs ?: timestampMs
        socketReadyGeneration += 1
        lastSocketReadyUserId = userId
        lastSocketId = socketId
        OutboxPipelineTrace.stage(
            stage = "SOCKET_READY",
            extra = mapOf(
                "timestampMs" to timestampMs.toString(),
                "userId" to userId.toString(),
                "socketId" to (socketId ?: "null"),
                "socketReadyGeneration" to socketReadyGeneration.toString(),
                "timeSincePreviousReadyMs" to previousReadyAtMs?.let { (timestampMs - it).toString() },
            ),
        )
    }

    fun recordNetworkOnline(timestampMs: Long = nowMs()) {
        val wasOffline = lastNetworkOnlineAtMs == null
        lastNetworkOnlineAtMs = timestampMs
        if (wasOffline) {
            OutboxPipelineTrace.stage(
                stage = "NETWORK_ONLINE",
                extra = mapOf("timestampMs" to timestampMs.toString()),
            )
        }
    }

    fun recordSocketDisconnected(timestampMs: Long = nowMs()) {
        OutboxPipelineTrace.stage(
            stage = "SOCKET_DISCONNECTED",
            extra = mapOf(
                "timestampMs" to timestampMs.toString(),
                "lastReadyAtMs" to lastSocketReadyAtMs?.toString(),
            ),
        )
    }

    fun timeSinceSocketReadyMs(atMs: Long = nowMs()): Long? {
        val readyAt = lastSocketReadyAtMs ?: return null
        return (atMs - readyAt).coerceAtLeast(0)
    }

    fun timeSinceNetworkOnlineMs(atMs: Long = nowMs()): Long? {
        val onlineAt = lastNetworkOnlineAtMs ?: return null
        return (atMs - onlineAt).coerceAtLeast(0)
    }

    fun socketReadyGeneration(): Long = socketReadyGeneration

    fun lastSocketReadyTimestampMs(): Long? = lastSocketReadyAtMs

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}
