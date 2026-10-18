package com.findmissingpep.app.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.findmissingpep.app.domain.repository.NotificationRepository
import com.findmissingpep.app.presentation.navigation.AppBottomNavBar
import com.findmissingpep.app.presentation.navigation.AppNavGraph
import com.findmissingpep.app.presentation.navigation.Screen
import com.findmissingpep.app.presentation.theme.*
import com.findmissingpep.app.service.LiveAlertManager
import com.findmissingpep.app.service.LiveSightingAlert
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var liveAlertManager: LiveAlertManager

    private val activeIntentState = mutableStateOf<Intent?>(null)

    // Permission launcher for Android 13+ runtime POST_NOTIFICATIONS
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("MainActivity", "POST_NOTIFICATIONS permission granted by user.")
        } else {
            Log.w("MainActivity", "POST_NOTIFICATIONS permission denied by user.")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeIntentState.value = intent
    }

    @androidx.compose.material3.ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Start the unified real-time alert daemon (SSE stream + FCM token sync + active polling)
        liveAlertManager.start()

        // Initial launch intent
        activeIntentState.value = intent

        // After login / authenticated state, start directly at Dashboard
        val startDest = if (firebaseAuth.currentUser != null) {
            Screen.Dashboard.route
        } else {
            Screen.Login.route
        }

        setContent {
            FindMissingPepTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // In-App Real-Time Alert Banner State
                var activeBannerAlert by remember { mutableStateOf<LiveSightingAlert?>(null) }

                // Observe real-time match alerts emitted by LiveAlertManager
                LaunchedEffect(Unit) {
                    liveAlertManager.liveAlerts.collect { alert ->
                        activeBannerAlert = alert
                    }
                }

                // Auto-dismiss the in-app alert banner after 12 seconds
                LaunchedEffect(activeBannerAlert) {
                    if (activeBannerAlert != null) {
                        delay(12_000)
                        activeBannerAlert = null
                    }
                }

                // Collect real-time unread alert count for bottom navigation badge
                val unreadAlertsCount by notificationRepository.unreadCount.collectAsState(initial = 0)
                LaunchedEffect(Unit) {
                    notificationRepository.fetchUnreadCount()
                }

                // Show bottom bar on primary operational destinations matching the website
                val bottomNavRoutes = remember {
                    setOf(
                        Screen.Dashboard.route,
                        Screen.MyCases.route,
                        Screen.ReportWizard.route,
                        Screen.Notifications.route,
                        Screen.Profile.route,
                        Screen.Home.route
                    )
                }
                val showBottomBar = currentRoute in bottomNavRoutes

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = CreamBackground,
                    bottomBar = {
                        AnimatedVisibility(
                            visible = showBottomBar,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            AppBottomNavBar(
                                currentRoute = currentRoute,
                                unreadAlertsCount = unreadAlertsCount,
                                onTabSelected = { targetRoute ->
                                    if (currentRoute != targetRoute) {
                                        navController.navigate(targetRoute) {
                                            popUpTo(Screen.Dashboard.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        AppNavGraph(
                            navController = navController,
                            startDestination = startDest
                        )

                        // Top Heads-Up In-App Tactical Alert Banner
                        AnimatedVisibility(
                            visible = activeBannerAlert != null,
                            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 40.dp)
                        ) {
                            activeBannerAlert?.let { alert ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            activeBannerAlert = null
                                            navController.navigate(
                                                Screen.ForensicInspector.createRoute(alert.sightingId)
                                            )
                                        },
                                    color = RedDanger,
                                    shadowElevation = 8.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(14.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Alert",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "🚨 LIVE CCTV MATCH",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 11.sp,
                                                    letterSpacing = 1.sp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color.White.copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = alert.similarityText,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${alert.personName} spotted at ${alert.cameraLocation}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Tap to review forensic evidence →",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 11.sp
                                            )
                                        }

                                        IconButton(
                                            onClick = { activeBannerAlert = null },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Handle deep links from notifications & intents
                    val currentIntent = activeIntentState.value
                    LaunchedEffect(currentIntent) {
                        val navigateTo = currentIntent?.getStringExtra("navigate_to")
                        val deepLinkSightingId = currentIntent?.getStringExtra("sighting_id")
                        val deepLinkCaseId = currentIntent?.getStringExtra("case_id")

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
