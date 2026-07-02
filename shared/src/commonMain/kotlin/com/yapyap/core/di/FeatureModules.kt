package com.yapyap.core.di

import com.yapyap.core.call.di.callModule
import com.yapyap.core.push.di.pushModule
import com.yapyap.core.realtime.di.realtimeModule
import com.yapyap.core.session.di.sessionModule
import com.yapyap.feature.auth.di.authModule
import com.yapyap.feature.call.di.callFeatureModule
import com.yapyap.feature.chat.di.chatModule
import com.yapyap.feature.conversations.di.conversationsModule
import com.yapyap.feature.profile.di.profileModule

val featureModules = listOf(
    authModule,
    conversationsModule,
    chatModule,
    profileModule,
    callFeatureModule,
)

val appModules = listOf(coreModule, networkModule, sessionModule) +
    featureModules +
    listOf(realtimeModule, callModule, pushModule)
