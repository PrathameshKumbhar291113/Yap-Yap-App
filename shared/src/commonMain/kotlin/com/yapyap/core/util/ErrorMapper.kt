package com.yapyap.core.util

import com.yapyap.core.network.ApiException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.io.IOException

object ErrorMapper {
    fun toUserMessage(throwable: Throwable): String {
        when (throwable) {
            is ApiException.AuthInvalid -> return UserMessages.AUTH_INVALID
            is ApiException.TokenInvalid -> return UserMessages.SESSION_EXPIRED
            is ApiException.NotFound -> return UserMessages.NOT_FOUND
            is ApiException.Conflict -> return UserMessages.CONFLICT
            is ApiException.ValidationError -> return throwable.message.takeIf { it.isNotBlank() }
                ?: UserMessages.VALIDATION
            is ApiException.Unknown -> {
                if (isNetworkUnavailable(throwable)) return UserMessages.NETWORK_UNAVAILABLE
                return throwable.message.takeIf { it.isNotBlank() } ?: UserMessages.UNKNOWN
            }
        }

        if (isNetworkUnavailable(throwable)) return UserMessages.NETWORK_UNAVAILABLE
        return throwable.message?.takeIf { it.isNotBlank() } ?: UserMessages.UNKNOWN
    }

    fun toUserMessage(throwable: Throwable, fallback: String): String {
        val mapped = toUserMessage(throwable)
        return mapped.takeUnless { it == UserMessages.UNKNOWN } ?: fallback
    }

    private fun isNetworkUnavailable(throwable: Throwable): Boolean {
        return generateSequence(throwable) { it.cause }.any { cause ->
            cause is SocketTimeoutException ||
                cause is ConnectTimeoutException ||
                (cause is IOException && cause.isNetworkRelated())
        }
    }

    private fun IOException.isNetworkRelated(): Boolean {
        val message = message?.lowercase().orEmpty()
        return message.contains("network") ||
            message.contains("connection") ||
            message.contains("offline") ||
            message.contains("unable to resolve") ||
            message.contains("host") ||
            message.contains("timed out")
    }
}

fun Throwable.toUserMessage(fallback: String = UserMessages.UNKNOWN): String =
    ErrorMapper.toUserMessage(this, fallback)
