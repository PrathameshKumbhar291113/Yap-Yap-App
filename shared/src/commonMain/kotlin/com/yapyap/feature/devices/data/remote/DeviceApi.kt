package com.yapyap.feature.devices.data.remote

import com.yapyap.core.network.ApiException
import com.yapyap.core.network.ApiResponse
import com.yapyap.core.network.ApiResult
import com.yapyap.core.network.requireData
import com.yapyap.core.network.safeApiCall
import com.yapyap.feature.devices.data.dto.RegisterDeviceDataDto
import com.yapyap.feature.devices.data.dto.RegisterDeviceRequestDto
import com.yapyap.feature.devices.data.dto.UnregisterDeviceRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess

class DeviceApi(
    private val httpClient: HttpClient,
) {
    suspend fun registerDevice(request: RegisterDeviceRequestDto): ApiResult<Int> {
        return safeApiCall {
            val response = httpClient.post("/api/devices/register") {
                setBody(request)
            }
            val apiResponse = response.body<ApiResponse<RegisterDeviceDataDto>>()
            if (!response.status.isSuccess()) {
                throw apiError(response.status.value, apiResponse)
            }
            apiResponse.requireData(response.status.value).device.id
        }
    }

    suspend fun unregisterDevice(request: UnregisterDeviceRequestDto): ApiResult<Unit> {
        return safeApiCall {
            val response = httpClient.delete("/api/devices/unregister") {
                setBody(request)
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
}
