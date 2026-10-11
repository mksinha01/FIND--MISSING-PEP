package com.findmissingpep.app.presentation.navigation

/** All screen routes in the app */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object ReportWizard : Screen("report_wizard")
    data object MyCases : Screen("my_cases")
    data object CaseDetail : Screen("case_detail/{caseId}") {
        fun createRoute(caseId: String) = "case_detail/$caseId"
    }
    data object SightingsFeed : Screen("sightings_feed")
    data object ForensicInspector : Screen("forensic_inspector/{sightingId}") {
        fun createRoute(sightingId: String) = "forensic_inspector/$sightingId"
    }
    data object BreadcrumbMap : Screen("breadcrumb_map/{caseId}") {
        fun createRoute(caseId: String) = "breadcrumb_map/$caseId"
    }
    data object Notifications : Screen("notifications")
    data object Profile : Screen("profile")
    data object FirQueue : Screen("fir_queue")
}
