package com.yapyap.feature.call.data.remote

import com.yapyap.core.network.ApiException
import com.yapyap.core.network.ApiResponse
import com.yapyap.core.network.ApiResult
import com.yapyap.core.network.safeApiCall
import com.yapyap.feature.call.data.dto.CallHistoryEntryDto
import com.yapyap.feature.call.data.dto.CallHistoryEntryResponseDto
import com.yapyap.feature.call.data.dto.CallHistoryPageDto
import com.yapyap.feature.call.data.dto.CallSessionResponseDto
import com.yapyap.feature.call.data.dto.CreateCallRequestDto
import com.yapyap.feature.call.data.dto.RedialCallRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess

class CallApi(
    private val httpClient: HttpClient,
) {
    suspend fun createCall(request: CreateCallRequestDto): ApiResult<CallSessionResponseDto> {
        return postSessionResponse("/api/calls") {
            setBody(request)
        }
    }

    suspend fun reconcileCall(callSessionId: String): ApiResult<CallSessionResponseDto> {
        return getSessionResponse("/api/calls/$callSessionId")
    }

    suspend fun fetchActiveCall(): ApiResult<CallSessionResponseDto?> {
        return safeApiCall {
            val response = httpClient.get("/api/calls/active")
            val apiResponse = response.body<ApiResponse<CallSessionResponseDto?>>()
            if (!response.status.isSuccess()) {
                error(apiResponse.message ?: "Failed to fetch active call")
            }
            apiResponse.data
        }
    }

    suspend fun refreshCallToken(callSessionId: String): ApiResult<CallSessionResponseDto> {
        return postSessionResponse("/api/calls/$callSessionId/token")
    }

    suspend fun getCallHistory(
        limit: Int = DEFAULT_HISTORY_LIMIT,
        cursor: String? = null,
        type: String? = null,
        direction: String? = null,
        missed: Boolean? = null,
    ): ApiResult<CallHistoryPageDto> {
        return safeApiCall {
            val response = httpClient.get("/api/calls/history") {
                parameter("limit", limit)
                cursor?.let { parameter("cursor", it) }
                type?.let { parameter("type", it) }
                direction?.let { parameter("direction", it) }
                missed?.let { parameter("missed", it) }
            }
            val apiResponse = response.body<ApiResponse<CallHistoryPageDto>>()
            if (!response.status.isSuccess()) {
                error(apiResponse.message ?: "Failed to fetch call history")
            }
            apiResponse.data ?: CallHistoryPageDto()
        }
    }

    suspend fun getCallHistoryEntry(historyId: String): ApiResult<CallHistoryEntryDto> {
        return safeApiCall {
            val response = httpClient.get("/api/calls/history/$historyId")
            val apiResponse = response.body<ApiResponse<CallHistoryEntryResponseDto>>()
            if (!response.status.isSuccess()) {
                error(apiResponse.message ?: "Failed to fetch call history entry")
            }
            apiResponse.data?.entry ?: error("Missing call history entry")
        }
    }

    suspend fun redialCall(callHistoryId: Int): ApiResult<CallSessionResponseDto> {
        return postSessionResponse("/api/calls/redial") {
            setBody(RedialCallRequestDto(call_history_id = callHistoryId))
        }
    }

    suspend fun answerCall(callSessionId: String): ApiResult<CallSessionResponseDto> {
        return postSessionResponse("/api/calls/$callSessionId/answer")
    }

    suspend fun rejectCall(callSessionId: String): ApiResult<Unit> {
        return postCallAction(callSessionId, "reject")
    }

    suspend fun endCall(callSessionId: String): ApiResult<Unit> {
        return postCallAction(callSessionId, "end")
    }

    private suspend fun getSessionResponse(path: String): ApiResult<CallSessionResponseDto> {
        return safeApiCall {
            val response = httpClient.get(path)
            parseSessionResponse(response.status.value, response.body())
        }
    }

    private suspend fun postSessionResponse(
        path: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<CallSessionResponseDto> {
        return safeApiCall {
            val response = httpClient.post(path, block)
            parseSessionResponse(response.status.value, response.body())
        }
    }

    private fun parseSessionResponse(
        statusCode: Int,
        apiResponse: ApiResponse<CallSessionResponseDto>,
    ): CallSessionResponseDto {
        if (statusCode !in 200..299) {
            val activeSessionId = apiResponse.details?.callSessionId
            if (apiResponse.errorCode == ERROR_CALL_ALREADY_ACTIVE && !activeSessionId.isNullOrBlank()) {
                throw ApiException.CallAlreadyActive(
                    message = apiResponse.message
                        ?: "An active call already exists between these participants",
                    callSessionId = activeSessionId,
                    httpStatus = statusCode,
                )
            }
            throw ApiException.from(
                httpStatus = statusCode,
                errorCode = apiResponse.errorCode,
                message = apiResponse.message ?: "Call request failed",
                details = apiResponse.details,
            )
        }
        return apiResponse.data ?: error("Missing call session payload")
    }

    private suspend fun postCallAction(callSessionId: String, action: String): ApiResult<Unit> {
        return safeApiCall {
            val response = httpClient.post("/api/calls/$callSessionId/$action")
            val apiResponse = response.body<ApiResponse<Unit?>>()
            if (!response.status.isSuccess()) {
                error(apiResponse.message ?: "Call action failed")
            }
        }
    }

    private companion object {
        const val DEFAULT_HISTORY_LIMIT = 20
        const val ERROR_CALL_ALREADY_ACTIVE = "CALL_ALREADY_ACTIVE"
    }
}
