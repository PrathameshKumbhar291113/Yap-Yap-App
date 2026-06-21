package com.yapyap.core.realtime

data class TypingState(
    val typingUserIds: Set<Int> = emptySet(),
) {
    val isAnyoneTyping: Boolean get() = typingUserIds.isNotEmpty()
}
