package com.yapyap.core.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class PresenceStore {
    private val onlineUsers = MutableStateFlow<Map<Int, Boolean>>(emptyMap())

    fun observeUserOnline(userId: Int): Flow<Boolean> {
        return onlineUsers.map { it[userId] == true }
    }

    fun observeAll(): Flow<Map<Int, Boolean>> = onlineUsers

    fun isUserOnline(userId: Int): Boolean = onlineUsers.value[userId] == true

    fun applyPresenceUpdate(userId: Int, online: Boolean) {
        onlineUsers.update { current ->
            if (online) {
                current + (userId to true)
            } else {
                current - userId
            }
        }
    }
}
