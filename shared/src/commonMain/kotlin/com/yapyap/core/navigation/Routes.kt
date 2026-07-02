package com.yapyap.core.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class YapYapRoute {
    @Serializable
    data object Splash : YapYapRoute()

    @Serializable
    data object AuthGraph : YapYapRoute()

    @Serializable
    data object Login : YapYapRoute()

    @Serializable
    data object Register : YapYapRoute()

    @Serializable
    data object MainGraph : YapYapRoute()

    @Serializable
    data object ConversationsList : YapYapRoute()

    @Serializable
    data object CallLog : YapYapRoute()

    @Serializable
    data object Profile : YapYapRoute()

    @Serializable
    data class Chat(
        val conversationId: String,
        /** When true (notification/deep link), open pinned to the latest message instead of first unread. */
        val openAtLatest: Boolean = false,
    ) : YapYapRoute()

    @Serializable
    data class Call(
        val callSessionId: String,
    ) : YapYapRoute()
}

/** String routes for Navigation Compose (type-safe [YapYapRoute] maps here). */
object Route {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"
    const val MAIN = "main"
    const val CONVERSATIONS = "main/conversations"
    const val CALLS = "main/calls"
    const val PROFILE = "main/profile"
    const val CHAT_PATTERN = "main/chat/{conversationId}"
    const val CHAT_ARG = "conversationId"
    const val CALL_PATTERN = "main/call/{callSessionId}"
    const val CALL_ARG = "callSessionId"

    fun chat(conversationId: String): String = "main/chat/$conversationId"
    fun call(callSessionId: String): String = "main/call/$callSessionId"
}

fun YapYapRoute.toNavRoute(): String = when (this) {
    YapYapRoute.Splash -> Route.SPLASH
    YapYapRoute.AuthGraph -> Route.AUTH
    YapYapRoute.Login -> Route.LOGIN
    YapYapRoute.Register -> Route.REGISTER
    YapYapRoute.MainGraph -> Route.MAIN
    YapYapRoute.ConversationsList -> Route.CONVERSATIONS
    YapYapRoute.CallLog -> Route.CALLS
    YapYapRoute.Profile -> Route.PROFILE
    is YapYapRoute.Chat -> Route.chat(conversationId)
    is YapYapRoute.Call -> Route.call(callSessionId)
}
