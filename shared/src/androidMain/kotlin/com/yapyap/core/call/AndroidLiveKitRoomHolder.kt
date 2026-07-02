package com.yapyap.core.call

import io.livekit.android.room.Room

/**
 * Holds the active LiveKit [Room] for video renderer initialization.
 * Updated by [AndroidCallMediaController] for the duration of a call.
 */
object AndroidLiveKitRoomHolder {
    @Volatile
    var room: Room? = null
}
