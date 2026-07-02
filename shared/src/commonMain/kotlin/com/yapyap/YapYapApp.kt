package com.yapyap

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yapyap.core.call.CallCoordinator
import com.yapyap.core.navigation.YapYapNavHost
import com.yapyap.core.push.PushNotificationCoordinator
import com.yapyap.core.session.SessionManager
import com.yapyap.core.ui.YapZeroWindowInsets
import com.yapyap.core.theme.YapYapTheme
import com.yapyap.feature.call.presentation.CallExperienceHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun YapYapApp() {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        val appScope = CoroutineScope(coroutineContext + SupervisorJob())
        val koin = getKoin()
        // Call signaling must register before chat socket collector forwards events.
        koin.get<CallCoordinator>().start(appScope)
        koin.get<SessionManager>().start(appScope)
        koin.get<PushNotificationCoordinator>().start(appScope)
    }

    YapYapTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = YapZeroWindowInsets,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) {
            CallExperienceHost(modifier = Modifier.fillMaxSize()) {
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
