package com.yapyap.core.realtime

import com.yapyap.core.network.appJson
import com.yapyap.feature.chat.data.dto.MessageDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SocketEventParser(
    private val json: Json = appJson,
) {
    fun parseReady(rawJson: String): SocketEvent.Ready {
        val payload = json.decodeFromString<SocketReadyPayload>(rawJson)
        return SocketEvent.Ready(userId = payload.userId)
    }

    fun parseMessageNew(rawJson: String): SocketEvent.MessageNew {
        val payload = json.decodeFromString<MessageNewPayload>(rawJson)
        return SocketEvent.MessageNew(
            conversationId = payload.conversationId,
            message = payload.message,
        )
    }

    fun parseMessageStatus(rawJson: String): SocketEvent.MessageStatus {
        val payload = json.decodeFromString<MessageStatusPayload>(rawJson)
        return SocketEvent.MessageStatus(
            conversationId = payload.conversationId,
            messageId = payload.messageId,
            status = payload.status,
            userId = payload.userId,
        )
    }

    fun parseTyping(rawJson: String): SocketEvent.Typing {
        val payload = json.decodeFromString<TypingPayload>(rawJson)
        return SocketEvent.Typing(
            conversationId = payload.conversationId,
            userId = payload.userId,
            isTyping = payload.isTyping,
        )
    }

    fun parsePresenceUpdate(rawJson: String): SocketEvent.PresenceUpdate {
        val payload = json.decodeFromString<PresenceUpdatePayload>(rawJson)
        return SocketEvent.PresenceUpdate(
            userId = payload.userId,
            online = payload.online,
        )
    }

    fun parseCallIncoming(rawJson: String): SocketEvent.CallIncoming {
        val payload = json.decodeFromString<CallIncomingPayload>(rawJson)
        return SocketEvent.CallIncoming(
            callSessionId = payload.callSessionId,
            callerId = payload.callerId,
            callerName = payload.callerName,
            callType = payload.resolvedCallTypeRaw(),
            conversationId = payload.conversationId,
            roomName = payload.roomName,
        )
    }

    fun parseCallAccepted(rawJson: String): SocketEvent.CallAccepted {
        val payload = json.decodeFromString<CallAcceptedPayload>(rawJson)
        return SocketEvent.CallAccepted(
            callSessionId = payload.callSessionId,
            providerToken = payload.providerToken,
            livekitUrl = payload.livekitUrl,
            roomName = payload.roomName,
        )
    }

    fun parseCallRejected(rawJson: String): SocketEvent.CallRejected {
        val payload = json.decodeFromString<CallRejectedPayload>(rawJson)
        return SocketEvent.CallRejected(
            callSessionId = payload.callSessionId,
            reason = payload.reason,
        )
    }

    fun parseCallEnded(rawJson: String): SocketEvent.CallEnded {
        val payload = json.decodeFromString<CallEndedPayload>(rawJson)
        return SocketEvent.CallEnded(
            callSessionId = payload.callSessionId,
            durationSec = payload.durationSec,
        )
    }

    fun parseCallSync(rawJson: String): SocketEvent.CallSync {
        val payload = json.decodeFromString<CallSyncPayload>(rawJson)
        return SocketEvent.CallSync(
            callSessionId = payload.callSessionId,
            status = payload.status,
            endReason = payload.endReason ?: payload.reason,
            durationSec = payload.durationSec,
            event = payload.event,
            conversationId = payload.conversationId,
        )
    }

    fun parseSendMessageAck(rawJson: String): Result<MessageDto> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val success = root["success"]?.jsonPrimitive?.boolean ?: false
        if (!success) {
            val errorMessage = root["message"]?.jsonPrimitive?.content ?: "Failed to send message"
            return Result.failure(IllegalStateException(errorMessage))
        }
        val messageElement = root["message"] ?: return Result.failure(IllegalStateException("Missing message"))
        if (messageElement !is JsonObject) {
            return Result.failure(IllegalStateException("Invalid message payload"))
        }
        return runCatching { json.decodeFromJsonElement(MessageDto.serializer(), messageElement) }
    }

    fun parseAckSuccess(rawJson: String): Result<Unit> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val success = root["success"]?.jsonPrimitive?.boolean ?: false
        return if (success) {
            Result.success(Unit)
        } else {
            val errorMessage = root["message"]?.jsonPrimitive?.content ?: "Request failed"
            Result.failure(IllegalStateException(errorMessage))
        }
    }

    fun encodePayload(value: Any): String = when (value) {
        is SocketSendMessagePayload -> json.encodeToString(value)
        is SocketConversationPayload -> json.encodeToString(value)
        is SocketMessageReadPayload -> json.encodeToString(value)
        is SocketMessageDeliveredPayload -> json.encodeToString(value)
        is CallSessionIdPayload -> json.encodeToString(value)
        else -> error("Unsupported socket payload type: ${value::class.simpleName}")
    }
}
