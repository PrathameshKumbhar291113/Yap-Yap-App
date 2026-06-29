package com.yapyap.feature.chat.domain.entity

/**
 * Result of reconciling a single conversation's chat cache against server metadata.
 */
data class ReconcileResult(
    val conversationId: Int,
    val strategy: ReconcileStrategy,
    val localLatestMessageId: Int?,
    val serverLatestMessageId: Int?,
    val messagesDownloaded: Int,
    val messagesMerged: Int,
    val duplicatesIgnored: Int,
    val pendingMessagesPreserved: Int,
    val acksSent: Int = 0,
)

enum class ReconcileStrategy {
    /** Server has no last message — nothing to fetch. */
    SKIP_NO_SERVER_MESSAGES,
    /** Local cache already contains server latest. */
    SKIP_ALREADY_CURRENT,
    /** Fetched messages with id > lastKnownMessageId. */
    INCREMENTAL_AFTER,
    /** Full latest page fetch (cache cold or never loaded). */
    FULL_PAGE,
    /** Filled interior or prefix gaps while local tail matched server tail. */
    GAP_REPAIR,
}

/**
 * Local chat cache state used to choose a reconciliation strategy.
 */
data class ChatSyncSnapshot(
    val conversationId: Int,
    val localLatestMessageId: Int?,
    val cachedMessageCount: Int,
    val initialLoaded: Boolean,
    val pendingMessageCount: Int,
)
