package com.yapyap.core.push

import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PushMessageDeduplicator(
    private val maxEntries: Int = 200,
    private val ttlMillis: Long = 5 * 60 * 1000L,
) {
    private val mutex = Mutex()
    private val seenMessageIds = LinkedHashMap<String, Long>()

    suspend fun markSeen(messageId: String?) {
        if (messageId.isNullOrBlank()) return
        mutex.withLock {
            pruneExpired()
            seenMessageIds[messageId] = nowMillis()
            trimIfNeeded()
        }
    }

    suspend fun hasSeen(messageId: String?): Boolean {
        if (messageId.isNullOrBlank()) return false
        return mutex.withLock {
            pruneExpired()
            seenMessageIds.containsKey(messageId)
        }
    }

    private fun pruneExpired() {
        val cutoff = nowMillis() - ttlMillis
        seenMessageIds.entries.removeAll { it.value < cutoff }
    }

    private fun trimIfNeeded() {
        while (seenMessageIds.size > maxEntries) {
            val oldest = seenMessageIds.entries.firstOrNull()?.key ?: break
            seenMessageIds.remove(oldest)
        }
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
