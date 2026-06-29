package com.yapyap.core.realtime.reconcile

import com.yapyap.feature.chat.domain.entity.ReconcileResult
import kotlinx.coroutines.CoroutineScope

/**
 * Authoritative receiver synchronization. Realtime [message:new] is best-effort only.
 */
interface ReceiverRepairGateway {
    fun start(parentScope: CoroutineScope)

    /** Refresh conversation metadata and repair every changed conversation. */
    suspend fun repairAll(trigger: String): Result<RepairSummary>

    /** Repair a single conversation (incremental when possible). */
    suspend fun repairConversation(
        conversationId: Int,
        trigger: String,
        hintServerMessageId: Int? = null,
    ): Result<ReconcileResult>
}
