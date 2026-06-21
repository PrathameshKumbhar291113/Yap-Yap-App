package com.yapyap.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.push.PushNavigationRouter
import com.yapyap.core.push.model.PushNavigationTarget
import com.yapyap.core.realtime.IncomingMessageNotifier
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.util.UserMessages
import com.yapyap.core.session.SessionManager
import com.yapyap.feature.auth.presentation.LoginScreen
import com.yapyap.feature.auth.presentation.RegisterScreen
import com.yapyap.feature.chat.presentation.ChatScreen
import com.yapyap.feature.conversations.presentation.ConversationsListScreen
import com.yapyap.feature.profile.presentation.ProfileScreen
import com.yapyap.feature.splash.presentation.SplashScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.session_expired

@Composable
fun YapYapNavHost(
    snackbarHostState: SnackbarHostState,
    sessionManager: SessionManager = koinInject(),
    incomingMessageNotifier: IncomingMessageNotifier = koinInject(),
    pushNavigationRouter: PushNavigationRouter = koinInject(),
    chatRealtimeClient: ChatRealtimeClient = koinInject(),
    tokenStorage: TokenStorage = koinInject(),
) {
    val navController = rememberNavController()
    val bootstrapState by sessionManager.bootstrapState.collectAsState()
    val sessionExpiredMessage = stringResource(Res.string.session_expired)

    LaunchedEffect(sessionManager, sessionExpiredMessage) {
        sessionManager.sessionExpired.collect {
            snackbarHostState.showSnackbar(sessionExpiredMessage)
            navController.navigate(Route.AUTH) {
                popUpTo(Route.SPLASH) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(incomingMessageNotifier) {
        incomingMessageNotifier.events.collect { notification ->
            val preview = notification.preview.take(MAX_NOTIFICATION_PREVIEW_LENGTH)
            snackbarHostState.showSnackbar(
                UserMessages.newMessageNotification(
                    conversationId = notification.conversationId,
                    preview = preview,
                ),
            )
        }
    }

    LaunchedEffect(pushNavigationRouter, chatRealtimeClient) {
        pushNavigationRouter.targets.collect { target ->
            when (target) {
                is PushNavigationTarget.Chat -> {
                    navController.navigate(Route.MAIN) {
                        popUpTo(Route.SPLASH) { inclusive = false }
                        launchSingleTop = true
                    }
                    navController.navigate(YapYapRoute.Chat(target.conversationId)) {
                        launchSingleTop = true
                    }
                    tokenStorage.getAccessToken()?.let { token ->
                        chatRealtimeClient.connect(token)
                    }
                }
                is PushNavigationTarget.IncomingCall -> {
                    snackbarHostState.showSnackbar("Incoming call from notification")
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.SPLASH,
    ) {
        composable(Route.SPLASH) {
            SplashScreen(
                bootstrapState = bootstrapState,
                onNavigateToAuth = {
                    navController.navigate(Route.AUTH) {
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(Route.MAIN) {
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        navigation(
            route = Route.AUTH,
            startDestination = Route.LOGIN,
        ) {
            composable(Route.LOGIN) {
                LoginScreen(
                    onNavigateToRegister = { navController.navigate(Route.REGISTER) },
                    onAuthSuccess = {
                        navController.navigate(Route.MAIN) {
                            popUpTo(Route.AUTH) { inclusive = true }
                        }
                    },
                    snackbarHostState = snackbarHostState,
                )
            }

            composable(Route.REGISTER) {
                RegisterScreen(
                    onNavigateToLogin = { navController.popBackStack() },
                    onAuthSuccess = {
                        navController.navigate(Route.MAIN) {
                            popUpTo(Route.AUTH) { inclusive = true }
                        }
                    },
                )
            }
        }

        navigation(
            route = Route.MAIN,
            startDestination = Route.CONVERSATIONS,
        ) {
            composable(Route.CONVERSATIONS) {
                MainScaffold(navController = navController) { contentPadding ->
                    Box(modifier = contentPadding.fillMaxSize()) {
                        ConversationsListScreen(
                            onConversationClick = { conversationId ->
                                navController.navigate(YapYapRoute.Chat(conversationId))
                            },
                        )
                    }
                }
            }

            composable(Route.PROFILE) {
                MainScaffold(navController = navController) { contentPadding ->
                    Box(modifier = contentPadding.fillMaxSize()) {
                        ProfileScreen(
                            onLogout = {
                                navController.navigate(Route.AUTH) {
                                    popUpTo(Route.MAIN) { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }

            composable<YapYapRoute.Chat> { backStackEntry ->
                val conversationId = backStackEntry.readConversationId()

                ChatScreen(
                    conversationId = conversationId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private const val MAX_NOTIFICATION_PREVIEW_LENGTH = 80
