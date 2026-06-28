package com.yapyap.core.push

/**
 * When true, realtime socket messages suppress local tray while backgrounded (FCM owns tray).
 * Android physical devices must show local tray when the socket delivers before FCM.
 */
internal expect fun suppressLocalTrayForRealtimeBackground(): Boolean
