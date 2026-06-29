package com.yapyap.feature.chat.data.outbox

import com.yapyap.core.network.ApiException
import com.yapyap.core.realtime.RealtimeConnectionState
import com.yapyap.core.util.ErrorMapper
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.io.IOException

object OutgoingSendFailureClassifier {
    private val retryableHttpStatuses = setOf(500, 502, 503, 504)

    fun isRetryable(
        throwable: Throwable,
        socketReady: Boolean,
        networkOnline: Boolean,
    ): Boolean {
        if (!networkOnline) return true
        if (!socketReady && isSocketRelated(throwable)) return true
        return generateSequence(throwable) { it.cause }.any { cause ->
            when (cause) {
                is SocketTimeoutException,
                is ConnectTimeoutException,
                -> true
                is IOException -> cause.isNetworkRelated()
                is ApiException.AuthInvalid,
                is ApiException.TokenInvalid,
                is ApiException.NotFound,
                is ApiException.ValidationError,
                -> false
                is ApiException.Conflict -> false
                is ApiException.Unknown -> {
                    val status = cause.httpStatus
                    status == null || status in retryableHttpStatuses || isNetworkException(cause)
                }
                else -> ErrorMapper.toUserMessage(cause) == com.yapyap.core.util.UserMessages.NETWORK_UNAVAILABLE
            }
        }
    }

    fun isPermanent(throwable: Throwable): Boolean = !isRetryable(
        throwable = throwable,
        socketReady = true,
        networkOnline = true,
    )

    private fun isSocketRelated(throwable: Throwable): Boolean {
        val message = throwable.message?.lowercase().orEmpty()
        return message.contains("socket") ||
            message.contains("not connected") ||
            message.contains("disconnected")
    }

    private fun isNetworkException(cause: ApiException.Unknown): Boolean {
        val message = cause.message.lowercase()
        return message.contains("network") ||
            message.contains("connection") ||
            message.contains("timeout") ||
            message.contains("failed to connect")
    }

    private fun IOException.isNetworkRelated(): Boolean {
        val message = message?.lowercase().orEmpty()
        return message.contains("network") ||
            message.contains("connection") ||
            message.contains("offline") ||
            message.contains("unable to resolve") ||
            message.contains("host") ||
            message.contains("timed out") ||
            message.contains("failed to connect")
    }
}

fun RealtimeConnectionState.isReadyForSend(): Boolean = this is RealtimeConnectionState.Ready
