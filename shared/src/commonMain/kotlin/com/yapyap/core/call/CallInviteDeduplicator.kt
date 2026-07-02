package com.yapyap.core.call

import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CallInviteDeduplicator(
    private val maxEntries: Int = 100,
    private val ttlMillis: Long = 2 * 60 * 1000L,
) {
    private val mutex = Mutex()
    private val seenCallSessionIds = LinkedHashMap<String, Long>()

    /**
     * Atomically claims [callSessionId] for processing.
     * Returns true only the first time within the TTL window; false for duplicates or blank ids.
     */
    suspend fun tryClaim(callSessionId: String): Boolean {
        if (callSessionId.isBlank()) return false
        return mutex.withLock {
            pruneExpired()
            if (seenCallSessionIds.containsKey(callSessionId)) {
                return@withLock false
            }
            seenCallSessionIds[callSessionId] = nowMillis()
            trimIfNeeded()
            true
        }
    }

    private fun pruneExpired() {
        val cutoff = nowMillis() - ttlMillis
        seenCallSessionIds.entries.removeAll { it.value < cutoff }
    }

    private fun trimIfNeeded() {
        while (seenCallSessionIds.size > maxEntries) {
            val oldest = seenCallSessionIds.entries.firstOrNull()?.key ?: break
            seenCallSessionIds.remove(oldest)
        }
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
