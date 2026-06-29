package com.yapyap.feature.chat.data.remote

import com.yapyap.core.network.ApiException
import com.yapyap.core.network.ApiResponse
import com.yapyap.core.network.ApiResult
import com.yapyap.core.network.requireData
import com.yapyap.core.network.safeApiCall
import com.yapyap.feature.chat.data.dto.MarkReadRequestDto
import com.yapyap.feature.chat.data.dto.MessageDto
import com.yapyap.feature.chat.data.dto.MessagesPageDto
import com.yapyap.feature.chat.data.dto.SendMessageDataDto
import com.yapyap.feature.chat.data.dto.SendMessageRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess

class ChatApi(
    private val httpClient: HttpClient,
) {
    suspend fun getMessages(
        conversationId: Int,
        cursor: Int? = null,
        after: Int? = null,
        limit: Int = DEFAULT_LIMIT,
    ): ApiResult<MessagesPageDto> {
        return safeApiCall {
            val response = httpClient.get("/api/conversations/$conversationId/messages") {
                parameter("limit", limit)
                cursor?.let { parameter("cursor", it) }
                after?.let { parameter("after", it) }
            }
            val apiResponse = response.body<ApiResponse<MessagesPageDto>>()
            if (!response.status.isSuccess()) {
                throw apiError(response.status.value, apiResponse)
            }
            apiResponse.requireData(response.status.value)
        }
    }

    suspend fun sendMessage(
        conversationId: Int,
        request: SendMessageRequestDto,
    ): ApiResult<MessageDto> {
        return safeApiCall {
            val response = httpClient.post("/api/conversations/$conversationId/messages") {
                setBody(request)
            }
            val apiResponse = response.body<ApiResponse<SendMessageDataDto>>()
            if (!response.status.isSuccess()) {
                throw apiError(response.status.value, apiResponse)
            }
            apiResponse.requireData(response.status.value).message
        }
    }

    suspend fun markRead(
        conversationId: Int,
        messageId: Int? = null,
    ): ApiResult<Unit> {
        return safeApiCall {
            val response = httpClient.post("/api/conversations/$conversationId/read") {
                setBody(MarkReadRequestDto(messageId = messageId))
            }
            val apiResponse = response.body<ApiResponse<Unit?>>()
            if (!response.status.isSuccess()) {
                throw apiError(response.status.value, apiResponse)
            }
        }
    }

    private fun apiError(httpStatus: Int, apiResponse: ApiResponse<*>): ApiException {
        return ApiException.from(
            httpStatus = httpStatus,
            errorCode = apiResponse.errorCode,
            message = apiResponse.message ?: "Request failed",
        )
    }

    companion object {
        const val DEFAULT_LIMIT = 30
    }
}
