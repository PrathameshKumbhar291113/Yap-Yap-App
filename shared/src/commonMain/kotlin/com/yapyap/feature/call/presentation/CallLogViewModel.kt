package com.yapyap.feature.call.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yapyap.feature.call.domain.entity.CallLogEntry
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.usecase.ObserveCallLogUseCase
import com.yapyap.feature.call.domain.usecase.RedialCallUseCase
import com.yapyap.feature.call.domain.usecase.StartOutgoingCallUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CallLogViewModel(
    private val observeCallLogUseCase: ObserveCallLogUseCase,
    private val redialCallUseCase: RedialCallUseCase,
    private val startOutgoingCallUseCase: StartOutgoingCallUseCase,
    private val permissionPromptController: CallPermissionPromptController,
) : ViewModel() {
    val entries: StateFlow<List<CallLogEntry>> = observeCallLogUseCase()
    val hasMore: StateFlow<Boolean> = observeCallLogUseCase.hasMore

    fun refresh() {
        viewModelScope.launch { observeCallLogUseCase.refresh() }
    }

    fun loadMore() {
        viewModelScope.launch { observeCallLogUseCase.loadMore() }
    }

    fun redial(entry: CallLogEntry) {
        viewModelScope.launch {
            val callType = entry.callType
            if (!permissionPromptController.ensurePermissions(callType)) return@launch
            val historyId = entry.id.toIntOrNull() ?: return@launch
            redialCallUseCase(
                callHistoryId = historyId,
                remoteUser = entry.remoteUser,
            )
        }
    }

    fun startCall(entry: CallLogEntry, callType: CallType) {
        viewModelScope.launch {
            if (!permissionPromptController.ensurePermissions(callType)) return@launch
            val conversationId = entry.conversationId ?: return@launch
            if (callType == entry.callType) {
                val historyId = entry.id.toIntOrNull() ?: return@launch
                redialCallUseCase(
                    callHistoryId = historyId,
                    remoteUser = entry.remoteUser,
                )
            } else {
                startOutgoingCallUseCase(
                    conversationId = conversationId,
                    callType = callType,
                    remoteUser = entry.remoteUser,
                )
            }
        }
    }
}
