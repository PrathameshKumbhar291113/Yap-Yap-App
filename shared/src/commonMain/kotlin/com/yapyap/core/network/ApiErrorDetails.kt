package com.yapyap.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorDetails(
    @SerialName("callSessionId") val callSessionId: String? = null,
)
