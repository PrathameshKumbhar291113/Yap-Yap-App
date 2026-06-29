package com.yapyap.core.realtime.reconcile

import com.yapyap.feature.chat.domain.entity.ReconcileResult

data class RepairSummary(
    val trigger: String,
    val changedConversationCount: Int,
    val repairedConversationCount: Int,
    val totalMessagesMerged: Int,
    val totalAcksSent: Int = 0,
    val durationMs: Long = 0,
    val results: List<ReconcileResult>,
)
