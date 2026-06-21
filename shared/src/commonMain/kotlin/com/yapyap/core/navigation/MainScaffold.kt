package com.yapyap.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.yapyap.core.ui.YapDrawable
import com.yapyap.core.ui.YapIcon
import com.yapyap.core.ui.YapInsets
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.tab_chats
import yapyap.shared.generated.resources.tab_profile

@Composable
fun MainScaffold(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Route.CONVERSATIONS || currentRoute == Route.PROFILE

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = YapInsets.content,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Route.CONVERSATIONS,
                        onClick = {
                            navController.navigate(Route.CONVERSATIONS) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            YapIcon(
                                resource = YapDrawable.Chat,
                                contentDescription = stringResource(Res.string.tab_chats),
                            )
                        },
                        label = { Text(stringResource(Res.string.tab_chats)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Route.PROFILE,
                        onClick = {
                            navController.navigate(Route.PROFILE) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            YapIcon(
                                resource = YapDrawable.Person,
                                contentDescription = stringResource(Res.string.tab_profile),
                            )
                        },
                        label = { Text(stringResource(Res.string.tab_profile)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}
