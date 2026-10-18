package com.findmissingpep.app.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.findmissingpep.app.presentation.screens.auth.LoginScreen
import com.findmissingpep.app.presentation.screens.cases.CaseDetailScreen
import com.findmissingpep.app.presentation.screens.cases.MyCasesScreen
import com.findmissingpep.app.presentation.screens.home.HomeScreen
import com.findmissingpep.app.presentation.screens.map.BreadcrumbMapScreen
import com.findmissingpep.app.presentation.screens.notifications.NotificationScreen
import com.findmissingpep.app.presentation.screens.profile.ProfileScreen
import com.findmissingpep.app.presentation.screens.report_wizard.ReportWizardScreen
import com.findmissingpep.app.presentation.screens.sightings.ForensicInspectorScreen
import com.findmissingpep.app.presentation.screens.sightings.SightingsFeedScreen
import com.findmissingpep.app.presentation.screens.auth.ForgotPasswordScreen
import com.findmissingpep.app.presentation.screens.fir.FirQueueScreen
import com.findmissingpep.app.presentation.screens.dashboard.DashboardScreen

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) },
        exitTransition = { slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300)) },
        popEnterTransition = { slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300)) },
        popExitTransition = { slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)) }
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                }
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onResetSent = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToReport = { navController.navigate(Screen.ReportWizard.route) },
                onNavigateToSightings = { navController.navigate(Screen.SightingsFeed.route) },
                onNavigateToCases = { navController.navigate(Screen.MyCases.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToFirQueue = { navController.navigate(Screen.FirQueue.route) },
                onNavigateToDashboard = { navController.navigate(Screen.Dashboard.route) },
                onNavigateToCaseDetail = { caseId ->
                    navController.navigate(Screen.CaseDetail.createRoute(caseId))
                }
            )
        }

        composable(Screen.ReportWizard.route) {
            ReportWizardScreen(
                onReportSubmitted = { caseId ->
                    navController.navigate(Screen.MyCases.route) {
                        popUpTo(Screen.Dashboard.route) {
                            saveState = false
                        }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.MyCases.route) {
            MyCasesScreen(
                onNavigateToCaseDetail = { caseId ->
                    navController.navigate(Screen.CaseDetail.createRoute(caseId))
                },
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.CaseDetail.route,
            arguments = listOf(navArgument("caseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val caseId = backStackEntry.arguments?.getString("caseId") ?: return@composable
            CaseDetailScreen(
                caseId = caseId,
                onNavigateToTimeline = {
                    navController.navigate(Screen.BreadcrumbMap.createRoute(caseId))
                },
                onNavigateToSighting = { sightingId ->
                    navController.navigate(Screen.ForensicInspector.createRoute(sightingId))
                },
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.MyCases.route) {
                            popUpTo(Screen.Dashboard.route)
                        }
                    }
                }
            )
        }

        composable(Screen.SightingsFeed.route) {
            SightingsFeedScreen(
                onNavigateToInspector = { sightingId ->
                    navController.navigate(Screen.ForensicInspector.createRoute(sightingId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ForensicInspector.route,
            arguments = listOf(navArgument("sightingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sightingId = backStackEntry.arguments?.getString("sightingId") ?: return@composable
            ForensicInspectorScreen(
                sightingId = sightingId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMap = { caseId ->
                    navController.navigate(Screen.BreadcrumbMap.createRoute(caseId))
                }
            )
        }

        composable(
            route = Screen.BreadcrumbMap.route,
            arguments = listOf(navArgument("caseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val caseId = backStackEntry.arguments?.getString("caseId") ?: return@composable
            BreadcrumbMapScreen(
                caseId = caseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Notifications.route) {
            NotificationScreen(
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    }
                },
                onNavigateToCase = { caseId ->
                    navController.navigate(Screen.CaseDetail.createRoute(caseId))
                },
                onNavigateToSighting = { sightingId ->
                    navController.navigate(Screen.ForensicInspector.createRoute(sightingId))
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    }
                },
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.FirQueue.route) {
            FirQueueScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCaseDetail = { caseId ->
                    navController.navigate(Screen.CaseDetail.createRoute(caseId))
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateBack = null,
                onNavigateToInspector = { sightingId ->
                    navController.navigate(Screen.ForensicInspector.createRoute(sightingId))
                },
                onNavigateToMap = { caseId ->
                    navController.navigate(Screen.BreadcrumbMap.createRoute(caseId))
                }
            )
        }
    }
}
