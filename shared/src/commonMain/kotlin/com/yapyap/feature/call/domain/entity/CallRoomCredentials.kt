package com.yapyap.feature.call.domain.entity

data class CallRoomCredentials(
    val livekitUrl: String,
    val providerToken: String,
    val roomName: String,
) {
    fun isValid(): Boolean =
        livekitUrl.isNotBlank() && providerToken.isNotBlank() && roomName.isNotBlank()
}
