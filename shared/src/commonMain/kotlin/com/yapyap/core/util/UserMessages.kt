package com.yapyap.core.util

object UserMessages {
    const val NETWORK_UNAVAILABLE = "No internet connection. Check your network and try again."
    const val AUTH_INVALID = "Invalid email or password."
    const val SESSION_EXPIRED = "Your session expired. Please sign in again."
    const val NOT_FOUND = "We couldn't find what you were looking for."
    const val CONFLICT = "That action couldn't be completed because it conflicts with existing data."
    const val VALIDATION = "Please check your input and try again."
    const val LOAD_CONVERSATIONS_FAILED = "Couldn't load conversations. Please try again."
    const val START_CHAT_FAILED = "Couldn't start a chat. Please try again."
    const val UPDATE_PROFILE_FAILED = "Couldn't update your profile. Please try again."
    const val SEND_MESSAGE_FAILED = "Couldn't send your message. Please try again."
    const val UNKNOWN = "Something went wrong. Please try again."

    fun newMessageNotification(conversationId: Int, preview: String): String =
        "Chat #$conversationId: $preview"
}
