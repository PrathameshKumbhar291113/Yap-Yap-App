package com.yapyap.core.realtime

import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.util.DispatcherProvider

expect fun createChatRealtimeClient(
    socketEventParser: SocketEventParser,
    tokenStorage: TokenStorage,
    dispatcherProvider: DispatcherProvider,
): ChatRealtimeClient
