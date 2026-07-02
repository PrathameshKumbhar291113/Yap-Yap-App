package com.yapyap.feature.call.data.repository

import com.russhwolf.settings.Settings
import com.yapyap.core.network.getOrThrow
import com.yapyap.feature.call.data.mapper.toDomain
import com.yapyap.feature.call.data.remote.CallApi
import com.yapyap.feature.call.domain.entity.CallDirection
import com.yapyap.feature.call.domain.entity.CallHistoryFilters
import com.yapyap.feature.call.domain.entity.CallLogEntry
import com.yapyap.feature.call.domain.entity.CallLogStatus
import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.repository.CallLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CallLogRepositoryImpl(
    private val callApi: CallApi,
    private val settings: Settings,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CallLogRepository {
    private val _entries = MutableStateFlow(loadCached())
    private val _nextCursor = MutableStateFlow<String?>(null)
    private val _hasMore = MutableStateFlow(false)

    override val entries: StateFlow<List<CallLogEntry>> = _entries.asStateFlow()
    override val nextCursor: StateFlow<String?> = _nextCursor.asStateFlow()
    override val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    override suspend fun refresh(filters: CallHistoryFilters): Result<Unit> {
        return runCatching {
            val page = callApi.getCallHistory(
                cursor = null,
                type = filters.type?.toHistoryFilter(),
                direction = filters.direction?.toHistoryFilter(),
                missed = filters.missedOnly.takeIf { it },
            ).getOrThrow()
            val remote = page.items.map { it.toDomain() }
            persist(remote)
            _entries.value = remote
            _nextCursor.value = page.nextCursor
            _hasMore.value = page.hasMore
        }
    }

    override suspend fun loadMore(filters: CallHistoryFilters): Result<Unit> {
        val cursor = _nextCursor.value ?: return Result.success(Unit)
        if (!_hasMore.value) return Result.success(Unit)
        return runCatching {
            val page = callApi.getCallHistory(
                cursor = cursor,
                type = filters.type?.toHistoryFilter(),
                direction = filters.direction?.toHistoryFilter(),
                missed = filters.missedOnly.takeIf { it },
            ).getOrThrow()
            val merged = _entries.value + page.items.map { it.toDomain() }
            persist(merged.take(MAX_ENTRIES))
            _entries.value = merged.take(MAX_ENTRIES)
            _nextCursor.value = page.nextCursor
            _hasMore.value = page.hasMore
        }
    }

    override suspend fun fetchEntry(historyId: String): Result<CallLogEntry> {
        return runCatching {
            callApi.getCallHistoryEntry(historyId).getOrThrow().toDomain()
        }
    }

    override suspend fun recordLocalEntry(entry: CallLogEntry): Result<Unit> {
        return runCatching {
            val updated = listOf(entry) + _entries.value.filterNot { it.id == entry.id }
            persist(updated.take(MAX_ENTRIES))
            _entries.value = updated.take(MAX_ENTRIES)
        }
    }

    private fun loadCached(): List<CallLogEntry> {
        val raw = settings.getStringOrNull(CACHE_KEY) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<CallLogEntryDto>>(raw).map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    private fun persist(entries: List<CallLogEntry>) {
        val payload = entries.map { CallLogEntryDto.from(it) }
        settings.putString(CACHE_KEY, json.encodeToString(payload))
    }

    private fun CallType.toHistoryFilter(): String = when (this) {
        CallType.Video -> "video"
        CallType.Voice -> "voice"
    }

    private fun CallDirection.toHistoryFilter(): String = name.lowercase()

    @Serializable
    private data class CallLogEntryDto(
        val id: String,
        val conversationId: Int? = null,
        val latestMessageId: String? = null,
        val callType: String,
        val direction: String,
        val status: String,
        val answered: Boolean = false,
        val remoteUserId: Int,
        val remoteUserName: String,
        val startedAtIso: String? = null,
        val durationSec: Int? = null,
        val endReason: String? = null,
        val roomName: String? = null,
    ) {
        fun toDomain(): CallLogEntry = CallLogEntry(
            id = id,
            conversationId = conversationId,
            latestMessageId = latestMessageId,
            callType = CallType.fromRaw(callType),
            direction = if (direction == "incoming") CallDirection.Incoming else CallDirection.Outgoing,
            status = when (status) {
                "Missed" -> CallLogStatus.Missed
                "Rejected" -> CallLogStatus.Rejected
                "Failed" -> CallLogStatus.Failed
                "Cancelled" -> CallLogStatus.Cancelled
                else -> CallLogStatus.Completed
            },
            answered = answered,
            remoteUser = CallParticipant(
                userId = remoteUserId,
                displayName = remoteUserName,
            ),
            startedAtIso = startedAtIso,
            durationSec = durationSec,
            endReason = endReason,
            roomName = roomName,
        )

        companion object {
            fun from(entry: CallLogEntry): CallLogEntryDto = CallLogEntryDto(
                id = entry.id,
                conversationId = entry.conversationId,
                latestMessageId = entry.latestMessageId,
                callType = entry.callType.name.lowercase(),
                direction = entry.direction.name.lowercase(),
                status = entry.status.name,
                answered = entry.answered,
                remoteUserId = entry.remoteUser.userId,
                remoteUserName = entry.remoteUser.displayName,
                startedAtIso = entry.startedAtIso,
                durationSec = entry.durationSec,
                endReason = entry.endReason,
                roomName = entry.roomName,
            )
        }
    }

    private companion object {
        const val CACHE_KEY = "call_log_cache_v2"
        const val MAX_ENTRIES = 100
    }
}
