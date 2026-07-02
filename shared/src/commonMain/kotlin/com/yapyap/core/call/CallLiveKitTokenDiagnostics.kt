package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.CallRoomCredentials
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Decodes LiveKit JWT payload (no signature verification) for debug pipeline diagnosis.
 * Safe to call only for logging — never use for authorization decisions.
 */
@OptIn(ExperimentalEncodingApi::class)
object CallLiveKitTokenDiagnostics {
    private val json = Json { ignoreUnknownKeys = true }

    fun logCredentials(
        callSessionId: String?,
        credentials: CallRoomCredentials,
        source: String,
        canJoinMedia: Boolean = true,
    ) {
        if (CallMediaMode.isStub(credentials)) {
            CallDiagnostics.stage(
                stage = "CALL_TOKEN_STUB",
                callSessionId = callSessionId,
                extra = mapOf(
                    "source" to source,
                    "clientRoomName" to credentials.roomName,
                    "livekitUrl" to credentials.livekitUrl,
                    "tokenPrefix" to credentials.providerToken.take(12),
                    "canJoinMedia" to canJoinMedia.toString(),
                ),
            )
            return
        }

        CallDiagnostics.stage(
            stage = "CALL_MEDIA_LIVE_MODE",
            callSessionId = callSessionId,
            extra = mapOf("source" to source),
        )

        val claims = decodeJwtPayload(credentials.providerToken)
        if (claims == null) {
            CallDiagnostics.stage(
                stage = "CALL_TOKEN_DECODE_FAILED",
                callSessionId = callSessionId,
                extra = mapOf(
                    "source" to source,
                    "clientRoomName" to credentials.roomName,
                    "tokenPrefix" to credentials.providerToken.take(12),
                ),
            )
            return
        }

        val video = claims["video"]?.jsonObject
        val jwtIdentity = claims["sub"]?.jsonPrimitive?.contentOrNull
            ?: claims["name"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val jwtRoom = video?.get("room")?.jsonPrimitive?.contentOrNull
            ?: claims["room"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val roomJoin = video?.get("roomJoin")?.jsonPrimitive?.booleanOrNull?.toString() ?: "unknown"
        val canPublish = video?.get("canPublish")?.jsonPrimitive?.booleanOrNull?.toString() ?: "unknown"
        val canSubscribe = video?.get("canSubscribe")?.jsonPrimitive?.booleanOrNull?.toString() ?: "unknown"
        val canPublishData = video?.get("canPublishData")?.jsonPrimitive?.booleanOrNull?.toString() ?: "unknown"
        val exp = claims["exp"]?.jsonPrimitive?.longOrNull?.toString() ?: "unknown"

        CallDiagnostics.stage(
            stage = "CALL_TOKEN_DECODED",
            callSessionId = callSessionId,
            extra = mapOf(
                "source" to source,
                "identity" to jwtIdentity,
                "jwtRoom" to jwtRoom,
                "clientRoomName" to credentials.roomName,
                "roomJoin" to roomJoin,
                "canPublish" to canPublish,
                "canSubscribe" to canSubscribe,
                "canPublishData" to canPublishData,
                "exp" to exp,
                "livekitUrl" to credentials.livekitUrl,
            ),
        )

        if (jwtRoom.isNotBlank() && credentials.roomName.isNotBlank() && jwtRoom != credentials.roomName) {
            CallDiagnostics.stage(
                stage = "CALL_TOKEN_ROOM_MISMATCH",
                callSessionId = callSessionId,
                extra = mapOf(
                    "jwtRoom" to jwtRoom,
                    "clientRoomName" to credentials.roomName,
                    "source" to source,
                ),
            )
        }
    }

    internal fun decodeJwtPayload(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val normalized = parts[1].replace('-', '+').replace('_', '/')
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            val decoded = Base64.decode(padded)
            val payload = decoded.decodeToString()
            json.parseToJsonElement(payload).jsonObject
        }.getOrNull()
    }
}
