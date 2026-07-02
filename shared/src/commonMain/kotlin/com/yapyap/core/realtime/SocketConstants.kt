package com.yapyap.core.realtime

object SocketConstants {
    const val SOCKET_READY = "socket:ready"
    const val MESSAGE_SEND = "message:send"
    const val MESSAGE_NEW = "message:new"
    const val MESSAGE_STATUS = "message:status"
    const val MESSAGE_READ = "message:read"
    const val MESSAGE_DELIVERED = "message:delivered"
    const val TYPING_START = "typing:start"
    const val TYPING_STOP = "typing:stop"
    const val TYPING = "typing"
    const val PRESENCE_UPDATE = "presence:update"

    const val CALL_ACCEPT = "call:accept"
    const val CALL_REJECT = "call:reject"
    const val CALL_END = "call:end"
    const val CALL_INCOMING = "call:incoming"
    const val CALL_ACCEPTED = "call:accepted"
    const val CALL_REJECTED = "call:rejected"
    const val CALL_ENDED = "call:ended"
    const val CALL_SYNC = "call:sync"

    const val NATIVE_CONNECT = "__native_connect__"
    const val NATIVE_DISCONNECT = "__native_disconnect__"
    const val NATIVE_CONNECT_ERROR = "__native_connect_error__"
}
