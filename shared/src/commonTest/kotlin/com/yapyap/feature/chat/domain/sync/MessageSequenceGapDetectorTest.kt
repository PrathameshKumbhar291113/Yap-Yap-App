package com.yapyap.feature.chat.domain.sync

import com.yapyap.feature.chat.domain.entity.ChatSyncSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageSequenceGapDetectorTest {

    @Test
    fun hasInteriorGap_detectsSingleMissingMessage() {
        assertTrue(MessageSequenceGapDetector.hasInteriorGap(listOf(100, 102)))
    }

    @Test
    fun hasInteriorGap_detectsMultipleMissingMessages() {
        assertTrue(MessageSequenceGapDetector.hasInteriorGap(listOf(100, 102, 104)))
    }

    @Test
    fun hasInteriorGap_falseForContiguousRange() {
        assertFalse(MessageSequenceGapDetector.hasInteriorGap(listOf(100, 101, 102)))
    }

    @Test
    fun interiorGaps_returnsBoundedRanges() {
        assertEquals(
            listOf(
                MessageSequenceGapDetector.GapRange(afterId = 100, beforeId = 102),
                MessageSequenceGapDetector.GapRange(afterId = 102, beforeId = 104),
            ),
            MessageSequenceGapDetector.interiorGaps(listOf(100, 102, 104)),
        )
    }

    @Test
    fun needsGapRepair_trueForInteriorGapEvenWhenTailMatchesServer() {
        val snapshot = ChatSyncSnapshot(
            conversationId = 1,
            localLatestMessageId = 102,
            cachedMessageCount = 2,
            initialLoaded = false,
            pendingMessageCount = 0,
        )
        assertTrue(
            MessageSequenceGapDetector.needsGapRepair(
                snapshot = snapshot,
                serverLatestMessageId = 102,
                localServerIds = listOf(100, 102),
                hasMoreOlderMessages = false,
            ),
        )
    }

    @Test
    fun needsGapRepair_falseForFullyLoadedContiguousCache() {
        val snapshot = ChatSyncSnapshot(
            conversationId = 1,
            localLatestMessageId = 102,
            cachedMessageCount = 3,
            initialLoaded = true,
            pendingMessageCount = 0,
        )
        assertFalse(
            MessageSequenceGapDetector.needsGapRepair(
                snapshot = snapshot,
                serverLatestMessageId = 102,
                localServerIds = listOf(100, 101, 102),
                hasMoreOlderMessages = false,
            ),
        )
    }

    @Test
    fun needsGapRepair_trueForSocketOnlyPrefixMissing() {
        val snapshot = ChatSyncSnapshot(
            conversationId = 1,
            localLatestMessageId = 102,
            cachedMessageCount = 1,
            initialLoaded = false,
            pendingMessageCount = 0,
        )
        assertTrue(
            MessageSequenceGapDetector.needsGapRepair(
                snapshot = snapshot,
                serverLatestMessageId = 102,
                localServerIds = listOf(102),
                hasMoreOlderMessages = false,
            ),
        )
    }
}
