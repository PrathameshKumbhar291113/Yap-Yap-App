package com.yapyap.core.push

import kotlin.time.Clock

class PushMessageDeduplicator(
    private val maxEntries: Int = 200,
    private val ttlMillis: Long = 5 * 60 * 1000L,
) {
    private val seenMessageIds = LinkedHashMap<String, Long>()

    fun markSeen(messageId: String?) {
        if (messageId.isNullOrBlank()) return
        pruneExpired()
        seenMessageIds[messageId] = nowMillis()
        trimIfNeeded()
    }

    fun hasSeen(messageId: String?): Boolean {
        if (messageId.isNullOrBlank()) return false
        pruneExpired()
        return seenMessageIds.containsKey(messageId)
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
