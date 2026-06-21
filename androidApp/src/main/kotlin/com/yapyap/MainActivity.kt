package com.yapyap

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.yapyap.core.push.PushIntentParser
import com.yapyap.core.push.PushNotificationCoordinator
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val pushNotificationCoordinator: PushNotificationCoordinator by inject()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pushNotificationCoordinator.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleNotificationIntent(intent)

        setContent {
            YapYapApp()
        }

        // Request after compose tree is up so PushNotificationCoordinator.start() has run.
        lifecycleScope.launch {
            requestNotificationPermissionIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        pushNotificationCoordinator.onAppForeground()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pushNotificationCoordinator.onPermissionResult(granted = true)
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val data = PushIntentParser.parse(intent) ?: return
        pushNotificationCoordinator.handleNotificationOpened(data)
    }
}
