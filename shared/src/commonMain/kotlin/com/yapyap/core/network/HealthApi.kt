package com.yapyap.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class HealthApi(
    private val httpClient: HttpClient,
) {
    suspend fun checkRoot(): Result<String> = safeApiCallResult {
        val response = httpClient.get("/")
        val apiResponse = response.body<ApiResponse<RootHealthDto>>()
        if (!response.status.isSuccess()) {
            throw ApiException.from(
                httpStatus = response.status.value,
                errorCode = apiResponse.errorCode,
                message = apiResponse.message ?: "Backend unreachable",
            )
        }
        val data = apiResponse.requireData(response.status.value)
        buildString {
            append(apiResponse.message ?: "Backend OK")
            append(" (")
            append(data.service)
            data.docs?.let { docs ->
                append(", docs: ")
                append(docs)
            }
            append(')')
        }
    }
}

@Serializable
internal data class RootHealthDto(
    val service: String,
    val docs: String? = null,
)
