package com.yapyap.feature.chat.domain.sync

import com.yapyap.feature.chat.domain.entity.ChatSyncSnapshot

/**
 * Detects interior holes in locally cached server message IDs.
 *
 * Tail metadata (lastMessageId, updatedAt, unreadCount) cannot detect gaps such as
 * local {100, 102} when the server has {100, 101, 102}.
 */
object MessageSequenceGapDetector {

    data class GapRange(
        val afterId: Int,
        val beforeId: Int,
    )

    fun sortedDistinctServerIds(localServerIds: Collection<Int>): List<Int> =
        localServerIds.sorted().distinct()

    fun hasInteriorGap(localServerIds: Collection<Int>): Boolean {
        val sorted = sortedDistinctServerIds(localServerIds)
        if (sorted.size < 2) return false
        return sorted.zipWithNext().any { (left, right) -> right - left > 1 }
    }

    fun interiorGaps(localServerIds: Collection<Int>): List<GapRange> {
        val sorted = sortedDistinctServerIds(localServerIds)
        return sorted.zipWithNext()
            .filter { (left, right) -> right - left > 1 }
            .map { (left, right) -> GapRange(afterId = left, beforeId = right) }
    }

    fun isContiguousRange(localServerIds: Collection<Int>): Boolean {
        val sorted = sortedDistinctServerIds(localServerIds)
        if (sorted.isEmpty()) return true
        return sorted.size == sorted.last() - sorted.first() + 1
    }

    /**
     * Returns true when [localLatestMessageId] has caught up to the server tail but the cached
     * history may still be incomplete (interior gap, socket-only cache, or missing prefix).
     */
    fun needsGapRepair(
        snapshot: ChatSyncSnapshot,
        serverLatestMessageId: Int?,
        localServerIds: Collection<Int>,
        hasMoreOlderMessages: Boolean,
    ): Boolean {
        val localLatest = snapshot.localLatestMessageId ?: return false
        val serverLatest = serverLatestMessageId ?: return false
        if (localLatest < serverLatest) return false
        if (snapshot.cachedMessageCount == 0) return false
        if (hasInteriorGap(localServerIds)) return true
        if (!snapshot.initialLoaded || hasMoreOlderMessages) return true
        if (!isContiguousRange(localServerIds)) return true
        return false
    }
}
