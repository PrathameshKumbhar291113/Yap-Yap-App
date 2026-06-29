package com.yapyap.core.realtime.reconcile

import com.yapyap.core.diagnostics.ReceiverRepairTrace
import com.yapyap.core.network.getOrThrow
import com.yapyap.core.network.NetworkConnectivityMonitor
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.RealtimeConnectionState
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.chat.domain.entity.ReconcileResult
import com.yapyap.feature.chat.domain.entity.ReconcileStrategy
import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.conversations.domain.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ReceiverRepairService(
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val chatRealtimeClient: ChatRealtimeClient,
    private val dispatcherProvider: DispatcherProvider,
    private val periodicIntervalMs: Long = PERIODIC_REPAIR_INTERVAL_MS,
) : ReceiverRepairGateway {
    private val repairMutex = Mutex()

    override fun start(parentScope: CoroutineScope) {
        parentScope.launch(dispatcherProvider.default) {
            while (isActive) {
                delay(periodicIntervalMs)
                val authenticated = observeAuthStateUseCase().value is AuthState.Authenticated
                val online = networkMonitor.isOnline.value
                val socketReady = chatRealtimeClient.connectionState.value is RealtimeConnectionState.Ready
                if (authenticated && online && !socketReady) {
                    repairAll(trigger = TRIGGER_PERIODIC)
                }
            }
        }
    }

    override suspend fun repairAll(trigger: String): Result<RepairSummary> {
        return repairMutex.withLock {
            withContext(dispatcherProvider.io) {
                runRepairAll(trigger)
            }
        }
    }

    override suspend fun repairConversation(
        conversationId: Int,
        trigger: String,
        hintServerMessageId: Int?,
    ): Result<ReconcileResult> {
        return repairMutex.withLock {
            withContext(dispatcherProvider.io) {
                runRepairConversation(conversationId, trigger, hintServerMessageId)
            }
        }
    }

    private suspend fun runRepairAll(trigger: String): Result<RepairSummary> {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        ReceiverRepairTrace.log(stage = "REPAIR_TRIGGERED", trigger = trigger)

        return runCatching {
            val localBefore = conversationRepository.snapshotConversations()
            conversationRepository.refreshConversations().getOrThrow()
            val serverAfter = conversationRepository.snapshotConversations()

            val metadataTargets = ConversationChangeDetector.detectChangedConversations(
                local = localBefore,
                server = serverAfter,
            )
            val gapCheckTargets = ConversationChangeDetector.detectCacheGapCheckTargets(
                server = serverAfter,
                excludedConversationIds = metadataTargets.map { it.conversationId }.toSet(),
                needsGapRepair = { conversationId, serverLatestMessageId ->
                    chatRepository.needsInteriorGapRepair(conversationId, serverLatestMessageId)
                },
            )
            val targets = metadataTargets + gapCheckTargets

            ReceiverRepairTrace.log(
                stage = "REPAIR_BEGIN",
                trigger = trigger,
                extra = mapOf(
                    "scope" to "all",
                    "changedCount" to metadataTargets.size.toString(),
                    "gapCheckCount" to gapCheckTargets.size.toString(),
                    "targetCount" to targets.size.toString(),
                ),
            )

            val results = targets.map { target ->
                executeConversationRepair(
                    conversationId = target.conversationId,
                    serverLatestMessageId = target.serverLatestMessageId,
                    trigger = trigger,
                )
            }

            val durationMs = Clock.System.now().toEpochMilliseconds() - startedAt
            ReceiverRepairTrace.log(
                stage = "REPAIR_COMPLETE",
                trigger = trigger,
                extra = mapOf(
                    "scope" to "all",
                    "conversationsRepaired" to results.size.toString(),
                    "totalMerged" to results.sumOf { it.messagesMerged }.toString(),
                    "durationMs" to durationMs.toString(),
                ),
            )

            RepairSummary(
                trigger = trigger,
                changedConversationCount = targets.size,
                repairedConversationCount = results.count { it.strategy.isActiveRepair() },
                totalMessagesMerged = results.sumOf { it.messagesMerged },
                totalAcksSent = results.sumOf { it.acksSent },
                durationMs = durationMs,
                results = results,
            )
        }
    }

    private suspend fun runRepairConversation(
        conversationId: Int,
        trigger: String,
        hintServerMessageId: Int?,
    ): Result<ReconcileResult> {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        ReceiverRepairTrace.log(
            stage = "REPAIR_TRIGGERED",
            trigger = trigger,
            conversationId = conversationId,
        )

        return runCatching {
            val localSnapshot = chatRepository.getLocalSyncSnapshot(conversationId)
            conversationRepository.refreshConversations().getOrThrow()
            val serverConversation = conversationRepository.snapshotConversations()
                .firstOrNull { it.id == conversationId }
            val serverLatestMessageId = serverConversation?.lastMessage?.id ?: hintServerMessageId

            ReceiverRepairTrace.log(
                stage = "REPAIR_BEGIN",
                trigger = trigger,
                conversationId = conversationId,
                lastKnownId = localSnapshot.localLatestMessageId,
                extra = mapOf(
                    "serverLatestId" to serverLatestMessageId?.toString(),
                    "strategy" to strategyLabel(localSnapshot),
                ),
            )

            val result = executeConversationRepair(
                conversationId = conversationId,
                serverLatestMessageId = serverLatestMessageId,
                trigger = trigger,
            )

            val durationMs = Clock.System.now().toEpochMilliseconds() - startedAt
            ReceiverRepairTrace.log(
                stage = "REPAIR_COMPLETE",
                trigger = trigger,
                conversationId = conversationId,
                extra = mapOf(
                    "durationMs" to durationMs.toString(),
                    "merged" to result.messagesMerged.toString(),
                ),
            )

            result
        }
    }

    private suspend fun executeConversationRepair(
        conversationId: Int,
        serverLatestMessageId: Int?,
        trigger: String,
    ): ReconcileResult {
        val localSnapshot = chatRepository.getLocalSyncSnapshot(conversationId)
        val result = chatRepository.reconcileConversation(
            conversationId = conversationId,
            serverLatestMessageId = serverLatestMessageId,
            trigger = trigger,
        ).getOrThrow()

        ReceiverRepairTrace.log(
            stage = "REPAIR_DOWNLOAD",
            trigger = trigger,
            conversationId = conversationId,
            lastKnownId = localSnapshot.localLatestMessageId,
            extra = mapOf(
                "downloaded" to result.messagesDownloaded.toString(),
                "duplicates" to result.duplicatesIgnored.toString(),
                "strategy" to result.strategy.name,
            ),
        )

        ReceiverRepairTrace.log(
            stage = "REPAIR_MERGE",
            trigger = trigger,
            conversationId = conversationId,
            extra = mapOf(
                "merged" to result.messagesMerged.toString(),
                "pendingPreserved" to result.pendingMessagesPreserved.toString(),
                "acksSent" to result.acksSent.toString(),
                "conversationUpdated" to "true",
                "chatUpdated" to (result.messagesMerged > 0 || result.strategy.isActiveRepair()).toString(),
            ),
        )

        return result
    }

    private fun strategyLabel(snapshot: com.yapyap.feature.chat.domain.entity.ChatSyncSnapshot): String {
        return if (snapshot.localLatestMessageId != null && snapshot.cachedMessageCount > 0) {
            "incremental_after"
        } else {
            "full_page"
        }
    }

    private fun ReconcileStrategy.isActiveRepair(): Boolean {
        return this == ReconcileStrategy.INCREMENTAL_AFTER ||
            this == ReconcileStrategy.FULL_PAGE ||
            this == ReconcileStrategy.GAP_REPAIR
    }

    companion object {
        const val TRIGGER_SOCKET_READY = "socket_ready"
        const val TRIGGER_FOREGROUND = "foreground"
        const val TRIGGER_FCM = "fcm"
        const val TRIGGER_NOTIFICATION_TAP = "notification_tap"
        const val TRIGGER_CHAT_OPEN = "chat_open"
        const val TRIGGER_PERIODIC = "periodic"

        private const val PERIODIC_REPAIR_INTERVAL_MS = 45_000L
    }
}
