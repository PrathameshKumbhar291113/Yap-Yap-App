package com.yapyap.feature.call.domain.usecase

import com.yapyap.feature.call.domain.entity.CallHistoryFilters
import com.yapyap.feature.call.domain.entity.CallLogEntry
import com.yapyap.feature.call.domain.repository.CallLogRepository
import kotlinx.coroutines.flow.StateFlow

class ObserveCallLogUseCase(
    private val callLogRepository: CallLogRepository,
) {
    operator fun invoke(): StateFlow<List<CallLogEntry>> = callLogRepository.entries

    val nextCursor: StateFlow<String?> get() = callLogRepository.nextCursor

    val hasMore: StateFlow<Boolean> get() = callLogRepository.hasMore

    suspend fun refresh(filters: CallHistoryFilters = CallHistoryFilters()): Result<Unit> =
        callLogRepository.refresh(filters)

    suspend fun loadMore(filters: CallHistoryFilters = CallHistoryFilters()): Result<Unit> =
        callLogRepository.loadMore(filters)
}
