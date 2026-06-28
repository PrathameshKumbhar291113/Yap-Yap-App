package com.yapyap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.push.PushIntentParser
import com.yapyap.core.push.PushNotificationCoordinator
import com.yapyap.core.session.SessionManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val pushNotificationCoordinator: PushNotificationCoordinator by inject()
    private val sessionManager: SessionManager by inject()

    private var notificationPermissionRequested = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pushNotificationCoordinator.onPermissionResult(granted = true)
        } else {
            pushNotificationCoordinator.onPermissionResult(granted = false)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                openNotificationSettings()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleNotificationIntent(intent)

        setContent {
            YapYapApp()
        }

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
        RuntimeDiagnostics.event("Lifecycle", "MainActivity.onResume")
        sessionManager.onAppForeground()
        pushNotificationCoordinator.onAppForeground()
    }

    override fun onPause() {
        RuntimeDiagnostics.event("Lifecycle", "MainActivity.onPause")
        super.onPause()
    }

    override fun onStop() {
        RuntimeDiagnostics.event("Lifecycle", "MainActivity.onStop")
        super.onStop()
    }

    override fun onDestroy() {
        RuntimeDiagnostics.event("Lifecycle", "MainActivity.onDestroy")
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            pushNotificationCoordinator.onPermissionResult(granted = true)
            return
        }

        when (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
            PackageManager.PERMISSION_GRANTED -> {
                pushNotificationCoordinator.onPermissionResult(granted = true)
            }
            else -> {
                if (!notificationPermissionRequested) {
                    notificationPermissionRequested = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    pushNotificationCoordinator.onPermissionResult(granted = false)
                    openNotificationSettings()
                }
            }
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val data = PushIntentParser.parse(intent) ?: return
        pushNotificationCoordinator.handleNotificationOpened(data)
    }
}
