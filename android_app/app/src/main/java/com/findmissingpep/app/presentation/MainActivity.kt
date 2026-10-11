package com.findmissingpep.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.findmissingpep.app.presentation.navigation.AppNavGraph
import com.findmissingpep.app.presentation.navigation.Screen
import com.findmissingpep.app.presentation.theme.FindMissingPepTheme
import com.findmissingpep.app.presentation.theme.Slate950
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @androidx.compose.material3.ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Determine start destination based on auth state
        val startDest = if (firebaseAuth.currentUser != null) {
            Screen.Home.route
        } else {
            Screen.Login.route
        }

        // Handle deep link from FCM notification
        val navigateTo = intent?.getStringExtra("navigate_to")
        val deepLinkSightingId = intent?.getStringExtra("sighting_id")
        val deepLinkCaseId = intent?.getStringExtra("case_id")

        setContent {
            FindMissingPepTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Slate950
                ) {
                    val navController = rememberNavController()

                    AppNavGraph(
                        navController = navController,
                        startDestination = startDest
                    )

                    // Handle deep links after composition
                    LaunchedEffect(navigateTo) {
                        when (navigateTo) {
                            "forensic_inspector" -> {
                                deepLinkSightingId?.let { id ->
                                    navController.navigate(
                                        Screen.ForensicInspector.createRoute(id)
                                    )
                                }
                            }
                            "case_detail" -> {
                                deepLinkCaseId?.let { id ->
                                    navController.navigate(
                                        Screen.CaseDetail.createRoute(id)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
