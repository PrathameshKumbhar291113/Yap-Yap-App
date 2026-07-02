package com.yapyap.feature.call.domain.repository

import com.yapyap.feature.call.domain.entity.CallHistoryFilters
import com.yapyap.feature.call.domain.entity.CallLogEntry
import com.yapyap.feature.call.domain.entity.CallLogPage
import kotlinx.coroutines.flow.StateFlow

interface CallLogRepository {
    val entries: StateFlow<List<CallLogEntry>>
    val nextCursor: StateFlow<String?>
    val hasMore: StateFlow<Boolean>

    suspend fun refresh(filters: CallHistoryFilters = CallHistoryFilters()): Result<Unit>

    suspend fun loadMore(filters: CallHistoryFilters = CallHistoryFilters()): Result<Unit>

    suspend fun fetchEntry(historyId: String): Result<CallLogEntry>

    suspend fun recordLocalEntry(entry: CallLogEntry): Result<Unit>
}
