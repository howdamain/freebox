package com.freebox.app.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freebox.app.R
import com.freebox.app.ui.discover.DiscoverScreen
import com.freebox.app.ui.map.MapScreen
import com.freebox.app.ui.settings.SettingsScreen
import com.freebox.app.ui.trends.TrendsScreen
import com.freebox.app.ui.vault.VaultScreen

sealed class Screen(val route: String, val labelId: Int, val icon: ImageVector) {
    object Discover : Screen("discover", R.string.nav_discover, Icons.Default.Explore)
    object Map : Screen("map", R.string.nav_map, Icons.Default.Map)
    object Vault : Screen("vault", R.string.nav_vault, Icons.Default.AccountBalanceWallet)
    object Trends : Screen("trends", R.string.nav_trends, Icons.AutoMirrored.Filled.TrendingUp)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun MainScreen(
    onItemClick: (String) -> Unit = {},
    onOpenFilters: () -> Unit = {},
    onCreateAlert: () -> Unit = {},
    onOpenScanner: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onLogOut: () -> Unit = {}
) {
    val screens = listOf(
        Screen.Discover,
        Screen.Map,
        Screen.Vault,
        Screen.Trends,
        Screen.Settings
    )
    // Saved as a route string so the selection survives detours to
    // detail/filter/alert routes and configuration changes.
    var selectedRoute by rememberSaveable { mutableStateOf(Screen.Discover.route) }
    val selectedScreen = screens.find { it.route == selectedRoute } ?: Screen.Discover
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                screens.forEach { screen ->
                    val isSelected = selectedScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedRoute = screen.route },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                // Label text below carries the name; avoid TalkBack
                                // announcing each destination twice.
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(screen.labelId),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.secondary,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Crossfade(
                targetState = selectedScreen,
                animationSpec = tween(200),
                label = "tab_crossfade"
            ) { screen ->
                saveableStateHolder.SaveableStateProvider(screen.route) {
                    when (screen) {
                        Screen.Discover -> DiscoverScreen(
                            onItemClick = onItemClick,
                            onOpenFilters = onOpenFilters,
                            onOpenScanner = onOpenScanner,
                            onOpenProfile = onOpenProfile
                        )
                        Screen.Map -> MapScreen(onItemClick = onItemClick)
                        Screen.Vault -> VaultScreen(onCreateAlert = onCreateAlert)
                        Screen.Trends -> TrendsScreen()
                        Screen.Settings -> SettingsScreen(onLogOut = onLogOut)
                    }
                }
            }
        }
    }
}
