package com.yapyap

import android.app.Application
import com.google.firebase.FirebaseApp
import com.yapyap.core.push.PushNotificationChannels
import org.koin.android.ext.koin.androidContext
import com.yapyap.core.di.initKoin

class YapYapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@YapYapApplication)
        }
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        PushNotificationChannels.create(this)
    }
}
