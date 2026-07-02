package com.yapyap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.yapyap.core.call.CallNativeBridge
import com.yapyap.core.call.CallPermissionLauncher
import com.yapyap.core.call.CallPermissionRequestRegistry
import com.yapyap.core.call.YAP_TELECOM_EXTRA_CALL_SESSION_ID
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.feature.call.presentation.ActivityHolder
import com.yapyap.core.push.AndroidPushRuntimeTrace
import com.yapyap.core.push.PushIntentParser
import com.yapyap.core.push.PushNotificationCoordinator
import com.yapyap.core.session.SessionManager
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val pushNotificationCoordinator: PushNotificationCoordinator by inject()
    private val sessionManager: SessionManager by inject()

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grantResults ->
        CallPermissionRequestRegistry.onResult(grantResults)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pushNotificationCoordinator.onPermissionResult(granted = true)
        } else {
            pushNotificationCoordinator.onPermissionResult(granted = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        RuntimeDiagnostics.enter("Lifecycle", "MainActivity.onCreate")
        AndroidPushRuntimeTrace.dumpIntent("Lifecycle", "MainActivity.onCreate.intent", intent)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        CallPermissionRequestRegistry.bindLauncher(
            CallPermissionLauncher { permissions ->
                callPermissionLauncher.launch(permissions)
            },
        )

        handleNotificationIntent(intent, source = "onCreate")
        handleTelecomCallIntent(intent, source = "onCreate")

        setContent {
            YapYapApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        RuntimeDiagnostics.enter("Lifecycle", "MainActivity.onNewIntent")
        AndroidPushRuntimeTrace.dumpIntent("Lifecycle", "MainActivity.onNewIntent.intent", intent)
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent, source = "onNewIntent")
        handleTelecomCallIntent(intent, source = "onNewIntent")
    }

    override fun onResume() {
        super.onResume()
        ActivityHolder.current = this
        RuntimeDiagnostics.event("Lifecycle", "MainActivity.onResume")
        sessionManager.onAppForeground()
        pushNotificationCoordinator.onAppForeground()
        syncNotificationPermissionState()
    }

    override fun onPause() {
        ActivityHolder.current = null
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

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            pushNotificationCoordinator.onPermissionResult(granted = true)
            return
        }
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
            PackageManager.PERMISSION_GRANTED -> {
                pushNotificationCoordinator.onPermissionResult(granted = true)
            }
            else -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun syncNotificationPermissionState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            pushNotificationCoordinator.onPermissionResult(granted = true)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        pushNotificationCoordinator.onPermissionResult(granted = granted)
    }

    private fun handleNotificationIntent(intent: Intent?, source: String) {
        RuntimeDiagnostics.enter("Lifecycle", "MainActivity.handleNotificationIntent", "source=$source")
        val data = PushIntentParser.parse(intent)
        if (data == null) {
            RuntimeDiagnostics.event(
                "Lifecycle",
                "MainActivity.handleNotificationIntent SKIP reason=parse_returned_null source=$source " +
                    "action=${intent?.action} categories=${intent?.categories}",
            )
            if (intent?.action == Intent.ACTION_MAIN) {
                RuntimeDiagnostics.event(
                    "Notification",
                    "SYSTEM_NOTIFICATION_ONLY candidate=launcher_intent_without_push_extras source=$source",
                )
            }
            RuntimeDiagnostics.stack("Lifecycle", "handleNotificationIntent_null")
            return
        }
        RuntimeDiagnostics.event(
            "Lifecycle",
            "MainActivity.handleNotificationIntent PARSED data=$data source=$source",
        )
        pushNotificationCoordinator.handleNotificationOpened(data)
    }

    private fun handleTelecomCallIntent(intent: Intent?, source: String) {
        if (intent?.getBooleanExtra("yapyap_open_call", false) != true) return
        val callSessionId = intent.getStringExtra(YAP_TELECOM_EXTRA_CALL_SESSION_ID) ?: return
        RuntimeDiagnostics.enter(
            "Lifecycle",
            "MainActivity.handleTelecomCallIntent",
            "callSessionId=$callSessionId source=$source",
        )
        lifecycleScope.launch {
            CallNativeBridge.onRestoreFromNative(callSessionId)
        }
    }
}
