package com.yapyap.core.network

import kotlinx.serialization.json.Json

val appJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}
