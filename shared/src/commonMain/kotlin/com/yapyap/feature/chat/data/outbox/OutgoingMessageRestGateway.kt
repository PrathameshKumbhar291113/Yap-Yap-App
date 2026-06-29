package com.yapyap.feature.chat.data.outbox

import com.yapyap.core.network.ApiResult
import com.yapyap.feature.chat.data.dto.MessageDto
import com.yapyap.feature.chat.data.dto.SendMessageRequestDto

fun interface OutgoingMessageRestGateway {
    suspend fun sendMessage(
        conversationId: Int,
        request: SendMessageRequestDto,
    ): ApiResult<MessageDto>
}
