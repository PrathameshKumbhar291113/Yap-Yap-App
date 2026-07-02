package com.yapyap.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import yapyap.shared.generated.resources.tab_calls
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
    val showBottomBar = currentRoute == Route.CONVERSATIONS ||
        currentRoute == Route.CALLS ||
        currentRoute == Route.PROFILE

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = YapInsets.scaffoldContent,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    windowInsets = YapInsets.bottomNavigationBar,
                ) {
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
                                tint = if (currentRoute == Route.CONVERSATIONS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        label = { Text(stringResource(Res.string.tab_chats)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    NavigationBarItem(
                        selected = currentRoute == Route.CALLS,
                        onClick = {
                            navController.navigate(Route.CALLS) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            YapIcon(
                                resource = YapDrawable.Call,
                                contentDescription = stringResource(Res.string.tab_calls),
                                tint = if (currentRoute == Route.CALLS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        label = { Text(stringResource(Res.string.tab_calls)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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
                                tint = if (currentRoute == Route.PROFILE) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        label = { Text(stringResource(Res.string.tab_profile)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}
