package com.yapyap.feature.devices.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequestDto(
    @SerialName("device_token") val deviceToken: String,
    val platform: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class UnregisterDeviceRequestDto(
    @SerialName("device_token") val deviceToken: String,
)

@Serializable
data class DeviceResponseDto(
    val id: Int,
    val platform: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
)

@Serializable
data class RegisterDeviceDataDto(
    val device: DeviceResponseDto,
)
