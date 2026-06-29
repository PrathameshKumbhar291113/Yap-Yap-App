package com.yapyap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import com.yapyap.core.navigation.YapYapNavHost
import com.yapyap.core.push.PushNotificationCoordinator
import com.yapyap.core.session.SessionManager
import com.yapyap.core.ui.YapZeroWindowInsets
import com.yapyap.core.theme.YapYapTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.compose.koinInject

@Composable
fun YapYapApp() {
    val snackbarHostState = remember { SnackbarHostState() }
    val sessionManager: SessionManager = koinInject()
    val pushNotificationCoordinator: PushNotificationCoordinator = koinInject()

    LaunchedEffect(sessionManager, pushNotificationCoordinator) {
        val appScope = CoroutineScope(coroutineContext + SupervisorJob())
        sessionManager.start(appScope)
        pushNotificationCoordinator.start(appScope)
    }

    YapYapTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = YapZeroWindowInsets,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                YapYapNavHost(snackbarHostState = snackbarHostState)
            }
        }
    }
}

@Preview
@Composable
fun YapYapAppPreview() {
    YapYapApp()
}
