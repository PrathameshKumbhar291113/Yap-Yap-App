package com.yapyap.core.di

import com.yapyap.core.realtime.TokenRefreshNotifier
import com.yapyap.core.network.AuthTokenProvider
import com.yapyap.core.network.DefaultAuthTokenProvider
import com.yapyap.core.network.DefaultSessionEventPublisher
import com.yapyap.core.network.HealthApi
import com.yapyap.core.network.SessionEventPublisher
import com.yapyap.core.network.createHttpClient
import com.yapyap.core.network.createRefreshHttpClient
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val REFRESH_CLIENT = "refresh_client"
private const val AUTH_CLIENT = "auth_client"

val networkModule = module {
    single<SessionEventPublisher> { DefaultSessionEventPublisher() }
    single { TokenRefreshNotifier() }

    single(named(REFRESH_CLIENT)) { createRefreshHttpClient() }

    single { HealthApi(get(named(REFRESH_CLIENT))) }

    single<AuthTokenProvider> {
        DefaultAuthTokenProvider(
            tokenStorage = get(),
            refreshHttpClient = get(named(REFRESH_CLIENT)),
            sessionEvents = get(),
            tokenRefreshNotifier = get(),
        )
    }

    single(named(AUTH_CLIENT)) { createHttpClient(get()) }

    single<HttpClient> { get(named(AUTH_CLIENT)) }
}
