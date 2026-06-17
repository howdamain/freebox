package com.freebox.app

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freebox.app.data.AlertsRepository
import com.freebox.app.data.FiltersStore
import com.freebox.app.data.ListingsRepository
import com.freebox.app.data.LocationRepository
import com.freebox.app.data.LootItem
import com.freebox.app.data.ProfitAlert
import com.freebox.app.data.UserPreferences
import com.freebox.app.data.supabase
import com.freebox.app.ui.alerts.AlertSetScreen
import com.freebox.app.ui.alerts.NewProfitAlertScreen
import com.freebox.app.ui.auth.AuthScreen
import com.freebox.app.ui.auth.AuthViewModel
import com.freebox.app.ui.details.TreasureDetailsScreen
import com.freebox.app.ui.filters.SearchFiltersScreen
import com.freebox.app.ui.main.MainScreen
import com.freebox.app.ui.onboarding.InterestsScreen
import com.freebox.app.ui.onboarding.PermissionScreen
import com.freebox.app.ui.onboarding.WelcomeScreen
import com.freebox.app.ui.onboarding.ZipEntryScreen
import com.freebox.app.ui.pro.EntitlementState
import com.freebox.app.ui.pro.EntitlementViewModel
import com.freebox.app.ui.pro.ProScreen
import com.freebox.app.ui.profile.ProfileScreen
import com.freebox.app.ui.scanner.ScannerScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Top-level gate: the Supabase session decides which app tree renders.
// Unauthenticated -> welcome/auth; Authenticated -> onboarding/main.
// (Phase 4 will insert the paywall/entitlement gate inside the authed tree.)
@Composable
fun FreeboxApp() {
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()
    when (sessionStatus) {
        is SessionStatus.Authenticated -> AuthedApp()
        is SessionStatus.Initializing -> SplashScreen()
        else -> UnauthedApp() // NotAuthenticated, RefreshFailure
    }
}

@Composable
private fun SplashScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
}

@Composable
private fun UnauthedApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "welcome") {
        composable("welcome") {
            WelcomeScreen(onStartClick = { navController.navigateSafe("auth") })
        }
        composable("auth") {
            // On success the session flips to Authenticated and FreeboxApp swaps
            // to AuthedApp, tearing down this graph — no manual navigation needed.
            AuthScreen(onBack = { navController.popBackStackSafe() })
        }
    }
}

// Authenticated but gated by the hard paywall: no entitlement -> the paywall is
// the only reachable screen. Listings are also RLS-gated server-side, so this
// mirrors the data layer rather than just hiding UI.
@Composable
private fun AuthedApp() {
    val authViewModel: AuthViewModel = viewModel()
    val entitlementViewModel: EntitlementViewModel = viewModel()
    val entState by entitlementViewModel.state.collectAsState()
    val working by entitlementViewModel.working.collectAsState()

    when (entState) {
        EntitlementState.UNKNOWN -> SplashScreen()
        EntitlementState.NOT_ENTITLED -> ProScreen(
            onClose = { authViewModel.signOut() }, // only escape from the paywall is to leave
            onStartTrial = { entitlementViewModel.startTrial() },
            working = working
        )
        EntitlementState.ENTITLED -> EntitledApp(onLogOut = { authViewModel.signOut() })
    }
}

@Composable
private fun EntitledApp(onLogOut: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()

    // Freeze the start destination once the persisted onboarding flag loads.
    var startDestination by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (startDestination.isEmpty()) {
            startDestination = if (prefs.onboardingComplete.first()) "main" else "interests"
        }
    }

    if (startDestination.isEmpty()) {
        SplashScreen()
    } else {
        FreeboxNavHost(
            startDestination = startDestination,
            onOnboardingComplete = { scope.launch { prefs.setOnboardingComplete(true) } },
            onInterestsSelected = { scope.launch { prefs.setInterests(it) } },
            onLogOut = onLogOut
        )
    }
}

private sealed interface DetailLoad {
    data object Loading : DetailLoad
    data object Missing : DetailLoad
    data class Loaded(val item: LootItem) : DetailLoad
}

private sealed interface AlertLoad {
    data object Loading : AlertLoad
    data object Missing : AlertLoad
    data class Loaded(val item: ProfitAlert) : AlertLoad
}

// Ignore taps that land while a navigation is already in flight —
// prevents double-push and double-pop from fast repeated taps.
private fun NavHostController.isCurrentResumed(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

private fun NavHostController.navigateSafe(
    route: String,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    if (isCurrentResumed()) navigate(route, builder)
}

private fun NavHostController.popBackStackSafe() {
    if (isCurrentResumed()) popBackStack()
}

@Composable
private fun FreeboxNavHost(
    startDestination: String,
    onOnboardingComplete: () -> Unit,
    onInterestsSelected: (Set<String>) -> Unit,
    onLogOut: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("interests") {
            InterestsScreen(
                onContinueClick = { selected ->
                    onInterestsSelected(selected)
                    navController.navigateSafe("zip_entry")
                },
                onSkipClick = { navController.navigateSafe("zip_entry") }
            )
        }

        composable("zip_entry") {
            val scope = rememberCoroutineScope()
            var working by remember { mutableStateOf(false) }
            ZipEntryScreen(
                working = working,
                onSubmit = { zip ->
                    working = true
                    scope.launch {
                        // Best-effort: schedule scraping for this ZIP, then continue
                        // regardless so a network hiccup can't trap onboarding.
                        runCatching { LocationRepository.activateZip(zip) }
                        working = false
                        if (navController.currentDestination?.route == "zip_entry") {
                            navController.navigate("location_permission") { launchSingleTop = true }
                        }
                    }
                }
            )
        }

        composable("location_permission") {
            PermissionScreen(
                title = stringResource(R.string.location_title),
                subtitle = stringResource(R.string.location_subtitle),
                icon = Icons.Default.LocationOn,
                primaryActionText = stringResource(R.string.action_enable_radar),
                secondaryActionText = stringResource(R.string.action_maybe_later),
                permissionsToRequest = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                // Fires from the permission-result callback, which Android delivers
                // before RESUMED — so gate on "still on this screen" rather than
                // navigateSafe's RESUMED check, which would swallow the navigation.
                onPrimaryClick = {
                    if (navController.currentDestination?.route == "location_permission") {
                        navController.navigate("notification_permission") { launchSingleTop = true }
                    }
                },
                onSecondaryClick = { navController.navigateSafe("notification_permission") }
            )
        }

        composable("notification_permission") {
            val finishOnboarding = {
                if (navController.currentDestination?.route == "notification_permission") {
                    onOnboardingComplete()
                    navController.navigate("main") {
                        popUpTo("interests") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            PermissionScreen(
                title = stringResource(R.string.notification_title),
                subtitle = stringResource(R.string.notification_subtitle),
                icon = Icons.Default.Notifications,
                isNotification = true,
                primaryActionText = stringResource(R.string.action_enable_alerts),
                secondaryActionText = stringResource(R.string.action_skip),
                permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyArray()
                },
                onPrimaryClick = { finishOnboarding() },
                onSecondaryClick = { finishOnboarding() }
            )
        }

        composable("main") {
            MainScreen(
                onItemClick = { itemId -> navController.navigateSafe("details/$itemId") },
                onOpenFilters = { navController.navigateSafe("filters") },
                onCreateAlert = { navController.navigateSafe("new_alert") },
                onOpenScanner = { navController.navigateSafe("scanner") },
                onOpenProfile = { navController.navigateSafe("profile") },
                onLogOut = onLogOut // signs out; session change swaps the whole tree
            )
        }

        composable("details/{itemId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("itemId")
            val scope = rememberCoroutineScope()
            // Load the live listing from Supabase by id (RLS-gated by entitlement).
            val load = produceState<DetailLoad>(DetailLoad.Loading, id) {
                value = id?.let { runCatching { ListingsRepository.fetchById(it) }.getOrNull() }
                    ?.let { DetailLoad.Loaded(it) } ?: DetailLoad.Missing
            }
            when (val s = load.value) {
                DetailLoad.Loading -> SplashScreen()
                DetailLoad.Missing -> LaunchedEffect(Unit) { navController.popBackStack() }
                is DetailLoad.Loaded -> TreasureDetailsScreen(
                    item = s.item,
                    onBack = { navController.popBackStackSafe() },
                    onClaim = { scope.launch { runCatching { ListingsRepository.claim(s.item.id) } } }
                )
            }
        }

        composable("filters") {
            SearchFiltersScreen(
                initialFilters = FiltersStore.filters,
                onApply = { applied ->
                    if (navController.isCurrentResumed()) {
                        FiltersStore.filters = applied
                        navController.popBackStack()
                    }
                },
                onClose = { navController.popBackStackSafe() }
            )
        }

        composable("new_alert") {
            val scope = rememberCoroutineScope()
            NewProfitAlertScreen(
                onClose = { navController.popBackStackSafe() },
                onCreateAlert = { draft ->
                    scope.launch {
                        val created = runCatching {
                            AlertsRepository.create(draft.keyword, draft.category, draft.minProfit, draft.frequency)
                        }.getOrNull()
                        if (created != null && navController.isCurrentResumed()) {
                            navController.navigate("alert_set/${created.id}") {
                                popUpTo("new_alert") { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        composable("profile") {
            ProfileScreen(
                onBack = { navController.popBackStackSafe() },
                onUpgradeToPro = { navController.navigateSafe("pro") }
            )
        }

        composable("pro") {
            ProScreen(
                onClose = { navController.popBackStackSafe() }
            )
        }

        composable("scanner") {
            ScannerScreen(
                onClose = { navController.popBackStackSafe() }
            )
        }

        composable("alert_set/{alertId}") { backStackEntry ->
            val alertId = backStackEntry.arguments?.getString("alertId")
            val load = produceState<AlertLoad>(AlertLoad.Loading, alertId) {
                value = alertId?.let { runCatching { AlertsRepository.byId(it) }.getOrNull() }
                    ?.let { AlertLoad.Loaded(it) } ?: AlertLoad.Missing
            }
            when (val s = load.value) {
                AlertLoad.Loading -> SplashScreen()
                AlertLoad.Missing -> LaunchedEffect(Unit) { navController.popBackStack() }
                is AlertLoad.Loaded -> AlertSetScreen(
                    alert = s.item,
                    onBackToFeed = { navController.popBackStackSafe() }
                )
            }
        }
    }
}
