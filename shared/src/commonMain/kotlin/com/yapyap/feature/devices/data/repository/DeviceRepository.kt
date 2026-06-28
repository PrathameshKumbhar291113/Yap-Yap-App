package com.yapyap.feature.devices.data.repository

import com.yapyap.core.network.getOrThrow
import com.yapyap.core.push.model.PushPlatformType
import com.yapyap.feature.devices.data.dto.RegisterDeviceRequestDto
import com.yapyap.feature.devices.data.dto.UnregisterDeviceRequestDto
import com.yapyap.feature.devices.data.remote.DeviceApi

class DeviceRepository(
    private val deviceApi: DeviceApi,
) {
    suspend fun registerDevice(
        deviceToken: String,
        platform: PushPlatformType,
        deviceName: String?,
        appVersion: String?,
    ): Result<Int> {
        return runCatching {
            deviceApi.registerDevice(
                RegisterDeviceRequestDto(
                    deviceToken = deviceToken,
                    platform = platform.apiValue,
                    deviceName = deviceName,
                    appVersion = appVersion,
                ),
            ).getOrThrow()
        }
    }

    suspend fun unregisterDevice(deviceToken: String): Result<Unit> {
        return runCatching {
            deviceApi.unregisterDevice(
                UnregisterDeviceRequestDto(deviceToken = deviceToken),
            ).getOrThrow()
        }
    }
}
