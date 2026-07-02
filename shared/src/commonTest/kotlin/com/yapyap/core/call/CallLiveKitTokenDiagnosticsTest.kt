package com.yapyap.core.call

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CallLiveKitTokenDiagnosticsTest {

    @Test
    fun decodeJwtPayload_extractsLiveKitClaims() {
        // {"sub":"alice","video":{"room":"room_52","roomJoin":true,"canPublish":true,"canSubscribe":true,"canPublishData":true},"exp":9999999999}
        val token = "eyJhbGciOiJub25lIn0." +
            "eyJzdWIiOiJhbGljZSIsInZpZGVvIjp7InJvb20iOiJyb29tXzUyIiwicm9vbUpvaW4iOnRydWUsImNhblB1Ymxpc2giOnRydWUsImNhblN1YnNjcmliZSI6dHJ1ZSwiY2FuUHVibGlzaERhdGEiOnRydWV9LCJleHAiOjk5OTk5OTk5OTl9." +
            "sig"

        val claims = CallLiveKitTokenDiagnostics.decodeJwtPayload(token)
        assertNotNull(claims)
        assertEquals("alice", claims["sub"]?.toString()?.trim('"'))
        val video = claims["video"]?.toString().orEmpty()
        assertTrue(video.contains("room_52"))
        assertTrue(video.contains("roomJoin"))
        assertTrue(video.contains("canPublish"))
    }
}
