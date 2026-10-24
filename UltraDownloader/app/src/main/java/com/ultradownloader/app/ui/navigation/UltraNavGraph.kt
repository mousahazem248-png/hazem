package com.ultradownloader.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ultradownloader.app.data.local.AppLanguage
import com.ultradownloader.app.ui.screens.AboutPrivacyScreen
import com.ultradownloader.app.ui.screens.ActiveDownloadsScreen
import com.ultradownloader.app.ui.screens.CompletedDownloadsScreen
import com.ultradownloader.app.ui.screens.ErrorDiagnosticScreen
import com.ultradownloader.app.ui.screens.HomeScreen
import com.ultradownloader.app.ui.screens.LocalVideoPlayerScreen
import com.ultradownloader.app.ui.screens.OnboardingScreen
import com.ultradownloader.app.ui.screens.SettingsScreen
import com.ultradownloader.app.ui.screens.VideoPreviewScreen
import com.ultradownloader.app.ui.viewmodel.UltraMainViewModel

object UltraDestinations {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PREVIEW = "preview"
    const val ACTIVE_DOWNLOADS = "active_downloads"
    const val COMPLETED_DOWNLOADS = "completed_downloads"
    const val PLAYER = "local_player"
    const val SETTINGS = "settings"
    const val ABOUT = "about_privacy"
    const val ERROR = "error_diagnostics"
}

@Composable
fun UltraAppRoot(
    viewModel: UltraMainViewModel
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settingsState.collectAsState()
    val activeTasks by viewModel.activeDownloads.collectAsState()
    val completedTasks by viewModel.completedDownloads.collectAsState()

    val isAr = settings.language == AppLanguage.ARABIC
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val startDestination = if (settings.onboardingCompleted) {
        UltraDestinations.HOME
    } else {
        UltraDestinations.ONBOARDING
    }

    val topLevelItems = listOf(
        Triple(UltraDestinations.HOME, if (isAr) "الرئيسية" else "Home", Icons.Outlined.Home),
        Triple(UltraDestinations.ACTIVE_DOWNLOADS, if (isAr) "الجارية" else "Active", Icons.Outlined.Downloading),
        Triple(UltraDestinations.COMPLETED_DOWNLOADS, if (isAr) "المكتملة" else "Completed", Icons.Outlined.CheckCircle),
        Triple(UltraDestinations.SETTINGS, if (isAr) "الإعدادات" else "Settings", Icons.Outlined.Settings)
    )

    val showNavigationChrome = currentRoute != UltraDestinations.ONBOARDING && currentRoute != UltraDestinations.PLAYER

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showNavigationChrome && !isExpandedScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    topLevelItems.forEach { (route, label, icon) ->
                        val selected = currentRoute == route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (route == UltraDestinations.ACTIVE_DOWNLOADS && activeTasks.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text("${activeTasks.size}") } }) {
                                        Icon(imageVector = icon, contentDescription = label)
                                    }
                                } else {
                                    Icon(imageVector = icon, contentDescription = label)
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showNavigationChrome && isExpandedScreen) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    topLevelItems.forEach { (route, label, icon) ->
                        val selected = currentRoute == route
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(imageVector = icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable(UltraDestinations.ONBOARDING) {
                        OnboardingScreen(
                            language = settings.language,
                            onFinishOnboarding = {
                                viewModel.completeOnboarding()
                                navController.navigate(UltraDestinations.HOME) {
                                    popUpTo(UltraDestinations.ONBOARDING) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(UltraDestinations.HOME) {
                        HomeScreen(
                            uiState = uiState,
                            language = settings.language,
                            onUrlChange = viewModel::onUrlInputChanged,
                            onAnalyzeClick = {
                                viewModel.analyzeCurrentLink(
                                    onNavigateToPreview = { navController.navigate(UltraDestinations.PREVIEW) },
                                    onNavigateToError = { navController.navigate(UltraDestinations.ERROR) }
                                )
                            },
                            onOpenPreviewClick = {
                                navController.navigate(UltraDestinations.PREVIEW)
                            }
                        )
                    }

                    composable(UltraDestinations.PREVIEW) {
                        VideoPreviewScreen(
                            analysis = uiState.latestAnalysis,
                            selectedFormat = uiState.selectedFormat,
                            language = settings.language,
                            onSelectFormat = viewModel::selectFormat,
                            onStartDownload = {
                                viewModel.startDownloadSelectedFormat {
                                    navController.navigate(UltraDestinations.ACTIVE_DOWNLOADS)
                                }
                            },
                            onBackToHome = { navController.popBackStack() }
                        )
                    }

                    composable(UltraDestinations.ACTIVE_DOWNLOADS) {
                        ActiveDownloadsScreen(
                            tasks = activeTasks,
                            language = settings.language,
                            onPause = viewModel::pauseDownload,
                            onResumeOrRetry = viewModel::resumeOrRetryDownload,
                            onCancelDelete = viewModel::deleteDownload
                        )
                    }

                    composable(UltraDestinations.COMPLETED_DOWNLOADS) {
                        CompletedDownloadsScreen(
                            tasks = completedTasks,
                            language = settings.language,
                            onPlayInApp = { task ->
                                viewModel.openInLocalPlayer(task) {
                                    navController.navigate(UltraDestinations.PLAYER)
                                }
                            },
                            onDeleteTask = viewModel::deleteDownload
                        )
                    }

                    composable(UltraDestinations.PLAYER) {
                        LocalVideoPlayerScreen(
                            task = uiState.activePlayerTask,
                            language = settings.language,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(UltraDestinations.SETTINGS) {
                        SettingsScreen(
                            settings = settings,
                            onThemeChange = viewModel::setTheme,
                            onLanguageChange = viewModel::setLanguage,
                            onWifiOnlyChange = viewModel::setWifiOnly,
                            onConcurrentLimitChange = viewModel::setMaxConcurrentDownloads,
                            onNavigateToAbout = { navController.navigate(UltraDestinations.ABOUT) }
                        )
                    }

                    composable(UltraDestinations.ABOUT) {
                        AboutPrivacyScreen(
                            language = settings.language,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(UltraDestinations.ERROR) {
                        ErrorDiagnosticScreen(
                            error = uiState.latestError,
                            language = settings.language,
                            onBackToHome = {
                                navController.popBackStack(UltraDestinations.HOME, inclusive = false)
                            }
                        )
                    }
                }
            }
        }
    }
}
