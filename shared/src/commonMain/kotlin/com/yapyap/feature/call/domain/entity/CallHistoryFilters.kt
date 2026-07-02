package com.yapyap.feature.call.domain.entity

data class CallHistoryFilters(
    val type: CallType? = null,
    val direction: CallDirection? = null,
    val missedOnly: Boolean = false,
)

data class CallLogPage(
    val entries: List<CallLogEntry>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)
