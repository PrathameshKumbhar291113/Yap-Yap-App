package com.yapyap.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yapyap.core.call.CallCoordinator
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.push.PushNavigationRouter
import com.yapyap.core.push.model.PushNavigationTarget
import com.yapyap.core.realtime.IncomingMessageNotifier
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.util.UserMessages
import com.yapyap.core.session.BootstrapState
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
    callCoordinator: CallCoordinator = koinInject(),
    chatRealtimeClient: ChatRealtimeClient = koinInject(),
    tokenStorage: TokenStorage = koinInject(),
) {
    val navController = rememberNavController()
    val bootstrapState by sessionManager.bootstrapState.collectAsState()
    val sessionExpiredMessage = stringResource(Res.string.session_expired)

    LaunchedEffect(bootstrapState) {
        RuntimeDiagnostics.event("Navigation", "BootstrapState=$bootstrapState NavHostAttached=true")
    }

    LaunchedEffect(Unit) {
        RuntimeDiagnostics.enter("Navigation", "YapYapNavHost.compose", "NavHostReady=true")
    }

    LaunchedEffect(sessionManager, sessionExpiredMessage) {
        sessionManager.sessionExpired.collect {
            if (sessionManager.bootstrapState.value != BootstrapState.Authenticated) {
                return@collect
            }
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

    LaunchedEffect(pushNavigationRouter, chatRealtimeClient, bootstrapState) {
        if (bootstrapState != BootstrapState.Authenticated) return@LaunchedEffect
        pushNavigationRouter.consumePending()?.let { target ->
            when (target) {
                is PushNavigationTarget.Chat -> executePushNavigation(
                    target = target,
                    navController = navController,
                    chatRealtimeClient = chatRealtimeClient,
                    tokenStorage = tokenStorage,
                )
                is PushNavigationTarget.IncomingCall -> {
                    executeIncomingCallNavigation(
                        target = target,
                        callCoordinator = callCoordinator,
                    )
                }
            }
        }
    }

    LaunchedEffect(pushNavigationRouter, chatRealtimeClient, bootstrapState) {
        pushNavigationRouter.targets.collect { target ->
            if (bootstrapState != BootstrapState.Authenticated) {
                RuntimeDiagnostics.event(
                    "Navigation",
                    "PushNavigation DEFERRED target=$target bootstrapState=$bootstrapState",
                )
                return@collect
            }
            when (target) {
                is PushNavigationTarget.Chat -> executePushNavigation(
                    target = target,
                    navController = navController,
                    chatRealtimeClient = chatRealtimeClient,
                    tokenStorage = tokenStorage,
                )
                is PushNavigationTarget.IncomingCall -> {
                    executeIncomingCallNavigation(
                        target = target,
                        callCoordinator = callCoordinator,
                    )
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

            composable(Route.CALLS) {
                MainScaffold(navController = navController) { contentPadding ->
                    Box(modifier = contentPadding.fillMaxSize()) {
                        com.yapyap.feature.call.presentation.CallLogScreen(
                            onOpenChat = { conversationId ->
                                navController.navigate(YapYapRoute.Chat(conversationId, openAtLatest = true))
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
                val chatRoute = backStackEntry.toRoute<YapYapRoute.Chat>()
                val conversationId = chatRoute.conversationId

                LaunchedEffect(conversationId, chatRoute.openAtLatest) {
                    RuntimeDiagnostics.enter(
                        "Navigation",
                        "ChatScreen.compose",
                        "CHAT_OPENED conversationId=$conversationId openAtLatest=${chatRoute.openAtLatest}",
                    )
                }

                ChatScreen(
                    conversationId = conversationId,
                    openAtLatest = chatRoute.openAtLatest,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<YapYapRoute.Call> { backStackEntry ->
                val callRoute = backStackEntry.toRoute<YapYapRoute.Call>()
                LaunchedEffect(callRoute.callSessionId) {
                    RuntimeDiagnostics.enter(
                        "Navigation",
                        "CallDeepLink.compose",
                        "CALL_OPENED callSessionId=${callRoute.callSessionId}",
                    )
                    callCoordinator.onIncomingFromNavigation(callRoute.callSessionId)
                }
                // TODO(Phase 2): Remove empty Box once deep-link route only triggers overlay without placeholder screen.
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private suspend fun executeIncomingCallNavigation(
    target: PushNavigationTarget.IncomingCall,
    callCoordinator: CallCoordinator,
) {
    RuntimeDiagnostics.enter(
        "Navigation",
        "executeIncomingCallNavigation",
        "callSessionId=${target.callSessionId}",
    )
    callCoordinator.onIncomingFromNavigation(target.callSessionId)
}

private const val MAX_NOTIFICATION_PREVIEW_LENGTH = 80

private fun executePushNavigation(
    target: PushNavigationTarget.Chat,
    navController: androidx.navigation.NavHostController,
    chatRealtimeClient: ChatRealtimeClient,
    tokenStorage: TokenStorage,
) {
    RuntimeDiagnostics.enter(
        "Navigation",
        "executePushNavigation",
        "currentDestination=${navController.currentDestination?.route} targetConversationId=${target.conversationId}",
    )
    RuntimeDiagnostics.event(
        "Navigation",
        "NAVIGATION_EXECUTED conversationId=${target.conversationId}",
    )
    navController.navigate(Route.MAIN) {
        popUpTo(Route.SPLASH) { inclusive = true }
        launchSingleTop = true
    }
    navController.navigate(YapYapRoute.Chat(conversationId = target.conversationId, openAtLatest = true)) {
        launchSingleTop = true
    }
    tokenStorage.getAccessToken()?.let { token ->
        chatRealtimeClient.connect(token)
    }
    RuntimeDiagnostics.event(
        "Navigation",
        "CHAT_OPENED conversationId=${target.conversationId}",
    )
}
