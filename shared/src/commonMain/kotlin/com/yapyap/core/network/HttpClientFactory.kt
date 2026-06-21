package com.yapyap.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import com.yapyap.core.util.YapLogger

expect fun createHttpClientEngine(): HttpClientEngine

fun createRefreshHttpClient(): HttpClient {
    return HttpClient(createHttpClientEngine()) {
        applyCommonConfig(enableLogging = Environment.isDebug)
    }
}

fun createHttpClient(authTokenProvider: AuthTokenProvider): HttpClient {
    return HttpClient(createHttpClientEngine()) {
        applyCommonConfig(enableLogging = Environment.isDebug)
        install(Auth) {
            bearer {
                loadTokens {
                    authTokenProvider.getAccessToken()?.let { access ->
                        BearerTokens(
                            accessToken = access,
                            refreshToken = authTokenProvider.getRefreshToken().orEmpty(),
                        )
                    }
                }
                refreshTokens {
                    val refreshed = authTokenProvider.refreshAccessToken()
                    if (!refreshed) {
                        return@refreshTokens null
                    }
                    authTokenProvider.getAccessToken()?.let { access ->
                        BearerTokens(
                            accessToken = access,
                            refreshToken = authTokenProvider.getRefreshToken().orEmpty(),
                        )
                    }
                }
            }
        }
    }
}

private fun HttpClientConfig<*>.applyCommonConfig(enableLogging: Boolean) {
    install(ContentNegotiation) {
        json(appJson)
    }
    if (enableLogging) {
        install(Logging) {
            level = LogLevel.INFO
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    YapLogger.i(message, tag = "Ktor")
                }
            }
        }
    }
    defaultRequest {
        url(Environment.BASE_URL)
        contentType(ContentType.Application.Json)
    }
}
