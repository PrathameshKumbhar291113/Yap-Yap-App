package com.yapyap.core.realtime

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class IncomingMessageNotification(
    val conversationId: Int,
    val preview: String,
)

class IncomingMessageNotifier {
    private val _events = MutableSharedFlow<IncomingMessageNotification>(extraBufferCapacity = 8)
    val events: SharedFlow<IncomingMessageNotification> = _events.asSharedFlow()

    fun notify(notification: IncomingMessageNotification) {
        _events.tryEmit(notification)
    }
}
