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
    data object Profile : YapYapRoute()

    @Serializable
    data class Chat(val conversationId: String) : YapYapRoute()
}

/** String routes for Navigation Compose (type-safe [YapYapRoute] maps here). */
object Route {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"
    const val MAIN = "main"
    const val CONVERSATIONS = "main/conversations"
    const val PROFILE = "main/profile"
    const val CHAT_PATTERN = "main/chat/{conversationId}"
    const val CHAT_ARG = "conversationId"

    fun chat(conversationId: String): String = "main/chat/$conversationId"
}

fun YapYapRoute.toNavRoute(): String = when (this) {
    YapYapRoute.Splash -> Route.SPLASH
    YapYapRoute.AuthGraph -> Route.AUTH
    YapYapRoute.Login -> Route.LOGIN
    YapYapRoute.Register -> Route.REGISTER
    YapYapRoute.MainGraph -> Route.MAIN
    YapYapRoute.ConversationsList -> Route.CONVERSATIONS
    YapYapRoute.Profile -> Route.PROFILE
    is YapYapRoute.Chat -> Route.chat(conversationId)
}
