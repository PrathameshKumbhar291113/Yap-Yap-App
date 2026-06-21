package com.yapyap.feature.conversations.data.remote

import com.yapyap.core.network.ApiException
import com.yapyap.core.network.ApiResponse
import com.yapyap.core.network.ApiResult
import com.yapyap.core.network.requireData
import com.yapyap.core.network.safeApiCall
import com.yapyap.feature.conversations.data.dto.ConversationListItemDto
import com.yapyap.feature.conversations.data.dto.ConversationSummaryDto
import com.yapyap.feature.conversations.data.dto.ConversationsListDataDto
import com.yapyap.feature.conversations.data.dto.DirectConversationDataDto
import com.yapyap.feature.conversations.data.dto.DirectConversationRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess

class ConversationApi(
    private val httpClient: HttpClient,
) {
    suspend fun getConversations(): ApiResult<List<ConversationListItemDto>> {
        return safeApiCall {
            val response = httpClient.get("/api/conversations")
            val apiResponse = response.body<ApiResponse<ConversationsListDataDto>>()
            if (!response.status.isSuccess()) {
                throw apiError(response.status.value, apiResponse)
            }
            apiResponse.requireData(response.status.value).conversations
        }
    }

    suspend fun createDirectConversation(contact: String): ApiResult<ConversationSummaryDto> {
        return safeApiCall {
            val trimmed = contact.trim()
            val body = if ('@' in trimmed) {
                DirectConversationRequestDto(email = trimmed.lowercase())
            } else {
                DirectConversationRequestDto(phone = trimmed)
            }
            val response = httpClient.post("/api/conversations/direct") {
                setBody(body)
            }
            val apiResponse = response.body<ApiResponse<DirectConversationDataDto>>()
            if (!response.status.isSuccess()) {
                throw apiError(response.status.value, apiResponse)
            }
            apiResponse.requireData(response.status.value).conversation
        }
    }

    private fun apiError(httpStatus: Int, apiResponse: ApiResponse<*>): ApiException {
        return ApiException.from(
            httpStatus = httpStatus,
            errorCode = apiResponse.errorCode,
            message = apiResponse.message ?: "Request failed",
        )
    }
}
