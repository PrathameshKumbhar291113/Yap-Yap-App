package com.yapyap.core.network

import com.yapyap.feature.conversations.data.dto.ConversationListItemDto
import com.yapyap.feature.conversations.data.dto.ConversationsListDataDto
import com.yapyap.feature.conversations.data.remote.ConversationApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BearerAuthRefreshInterceptorTest {

    @Test
    fun refreshesTokenAndRetriesOnceAfter401() = runTest {
        var accessToken: String? = "stale-token"
        var refreshInvocations = 0
        var conversationRequests = 0

        val tokenProvider = object : AuthTokenProvider {
            override fun getAccessToken(): String? = accessToken
            override fun getRefreshToken(): String? = "refresh-token"
            override fun saveTokens(newAccessToken: String, refreshToken: String) {
                accessToken = newAccessToken
            }
            override fun clearTokens() {
                accessToken = null
            }
            override suspend fun refreshAccessToken(): Boolean {
                refreshInvocations++
                accessToken = "fresh-token"
                return true
            }
        }

        val successBody = appJson.encodeToString(
            ApiResponse.serializer(ConversationsListDataDto.serializer()),
            ApiResponse(
                success = true,
                data = ConversationsListDataDto(
                    conversations = listOf(
                        ConversationListItemDto(
                            id = 1,
                            type = "direct",
                            createdAt = "2026-01-01T00:00:00Z",
                            updatedAt = "2026-01-01T00:00:00Z",
                            unreadCount = 0,
                        ),
                    ),
                ),
            ),
        )

        val authorizationHeaders = mutableListOf<String?>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/conversations" -> {
                    conversationRequests++
                    authorizationHeaders += request.headers[HttpHeaders.Authorization]
                    if (conversationRequests == 1) {
                        respond(
                            content = """{"success":false,"message":"Unauthorized"}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond(
                            content = successBody,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(appJson)
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        tokenProvider.getAccessToken()?.let { access ->
                            BearerTokens(
                                accessToken = access,
                                refreshToken = tokenProvider.getRefreshToken().orEmpty(),
                            )
                        }
                    }
                    refreshTokens {
                        if (!tokenProvider.refreshAccessToken()) {
                            return@refreshTokens null
                        }
                        tokenProvider.getAccessToken()?.let { access ->
                            BearerTokens(
                                accessToken = access,
                                refreshToken = tokenProvider.getRefreshToken().orEmpty(),
                            )
                        }
                    }
                }
            }
        }

        val api = ConversationApi(httpClient)
        val result = api.getConversations()

        assertTrue(result is ApiResult.Success)
        assertEquals(1, refreshInvocations)
        assertEquals(2, conversationRequests)
        assertEquals(
            listOf<String?>("Bearer stale-token", "Bearer fresh-token"),
            authorizationHeaders,
        )
        assertEquals(1, (result as ApiResult.Success).data.size)
    }
}
