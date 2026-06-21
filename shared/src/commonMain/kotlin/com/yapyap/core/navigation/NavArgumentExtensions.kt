package com.yapyap.core.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute

/**
 * Reads the chat conversation id from a [NavBackStackEntry].
 *
 * Prefer type-safe [toRoute]; fall back to regex when the resolved route string is
 * available but typed decoding fails (legacy deep links).
 */
internal fun NavBackStackEntry.readConversationId(): String? {
    runCatching {
        return toRoute<YapYapRoute.Chat>().conversationId
            .takeIf { it.isNotBlank() && it != "{conversationId}" }
    }

    return destination.route
        ?.let { route -> CONVERSATION_ID_FROM_ROUTE.find(route)?.groupValues?.get(1) }
        ?.takeIf { it.isNotBlank() }
}

private val CONVERSATION_ID_FROM_ROUTE = Regex("""chat/(\d+)$""")
