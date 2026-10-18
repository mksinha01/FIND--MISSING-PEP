package com.findmissingpep.app.presentation.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.findmissingpep.app.presentation.common.FmpLogo
import com.findmissingpep.app.presentation.theme.*
import kotlinx.coroutines.launch

enum class DashboardViewMode {
    PAGER_TABS,
    THREE_SCREEN_SHOWCASE
}

/**
 * FIND-MISSING-PEP AI Mission Dashboard Screen
 *
 * Implements the complete design brief:
 * 1. Screen 1 (Left): Live Sighting Radar & Active Search Feed
 * 2. Screen 2 (Center): AI Breadcrumb GIS Trajectory & Camera Route Map
 * 3. Screen 3 (Right): Forensic Match Dossier & M-of-N Temporal Verification
 *
 * Backed by real production backend data via DashboardViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToInspector: ((String) -> Unit)? = null,
    onNavigateToMap: ((String) -> Unit)? = null,
    viewModel: DashboardViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    val heroMatch = uiState.heroMatch
    val metrics = uiState.metrics
    val recentSightings = uiState.recentSightings
    val waypoints = uiState.waypoints
    val forensicDossier = uiState.forensicDossier

    var viewMode by remember { mutableStateOf(DashboardViewMode.PAGER_TABS) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    // Three concise, unambiguous mission tabs that fit cleanly without wrapping or overlapping
    val tabs = listOf(
        "Live Radar" to Icons.Default.Radar,
        "Breadcrumbs" to Icons.Default.Timeline,
        "Forensics" to Icons.Default.Fingerprint
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = CreamBackground,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = PaperWhite,
                border = BorderStroke(1.dp, LineBorderSubtle)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header row with official brand emblem, title, and live status pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.then(
                                if (onNavigateBack != null) Modifier.clickable { onNavigateBack() } else Modifier
                            )
                        ) {
                            FmpLogo(size = 38.dp)

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = "AI Mission Dashboard",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BlueDeep,
                                    letterSpacing = (-0.3).sp
                                )
                                Text(
                                    text = "FIND-MISSING-PEP Command",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = InkMuted
                                )
                            }
                        }

                        // Right side: Active Status Pill + Subtle View Mode Switcher
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Operational Status Indicator
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = when (pagerState.currentPage) {
                                    0 -> GreenSuccessBg
                                    1 -> BlueWash
                                    else -> AmberAlertBg
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when (pagerState.currentPage) {
                                        0 -> GreenSuccess.copy(alpha = 0.35f)
                                        1 -> BluePrimary.copy(alpha = 0.35f)
                                        else -> AmberAlert.copy(alpha = 0.35f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (pagerState.currentPage) {
                                                    0 -> GreenSuccess
                                                    1 -> BluePrimary
                                                    else -> AmberAlert
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (pagerState.currentPage) {
                                            0 -> "SSE LIVE"
                                            1 -> "GIS ROUTE"
                                            else -> "ARCFACE"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (pagerState.currentPage) {
                                            0 -> GreenSuccess
                                            1 -> BluePrimary
                                            else -> AmberAlert
                                        },
                                        letterSpacing = 0.4.sp
                                    )
                                }
                            }

                            // Real-time Refresh action button
                            IconButton(
                                onClick = {
                                    viewModel.refreshDashboard()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Synced real-time CCTV mission data")
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(PaperSubtle)
                                    .border(1.dp, LineBorderSubtle, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync Real Data",
                                    tint = BluePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // View mode toggle icon button (Clean and subtle)
                            IconButton(
                                onClick = {
                                    viewMode = if (viewMode == DashboardViewMode.PAGER_TABS) {
                                        DashboardViewMode.THREE_SCREEN_SHOWCASE
                                    } else {
                                        DashboardViewMode.PAGER_TABS
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (viewMode == DashboardViewMode.THREE_SCREEN_SHOWCASE) BlueWash else PaperSubtle)
                                    .border(
                                        1.dp,
                                        if (viewMode == DashboardViewMode.THREE_SCREEN_SHOWCASE) BluePrimary else LineBorderSubtle,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (viewMode == DashboardViewMode.THREE_SCREEN_SHOWCASE) Icons.Default.ViewAgenda else Icons.Default.ViewCarousel,
                                    contentDescription = "Toggle Showcase View",
                                    tint = if (viewMode == DashboardViewMode.THREE_SCREEN_SHOWCASE) BluePrimary else InkMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Modern Segmented Control Tab Switcher (No text wrapping, no overlapping)
                    if (viewMode == DashboardViewMode.PAGER_TABS) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = PaperSubtle,
                            border = BorderStroke(1.dp, LineBorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tabs.forEachIndexed { index, (label, icon) ->
                                    val isSelected = pagerState.currentPage == index
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .clickable {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(index)
                                                }
                                            },
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (isSelected) BluePrimary else Color.Transparent,
                                        shadowElevation = if (isSelected) 2.dp else 0.dp
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else InkMuted,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = label,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else InkText,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
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
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (viewMode) {
                DashboardViewMode.PAGER_TABS -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> SightingRadarTab(
                                heroMatch = heroMatch,
                                metrics = metrics,
                                recentSightings = recentSightings,
                                onDispatchUnit = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Field unit dispatched to ${heroMatch.cameraNode}"
                                        )
                                    }
                                },
                                onViewAllSightings = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                },
                                onSightingClick = { sighting ->
                                    onNavigateToInspector?.invoke(sighting.id)
                                },
                                showDeviceChrome = false
                            )

                            1 -> BreadcrumbRouteTab(
                                waypoints = waypoints,
                                caseId = heroMatch.personId,
                                onNavigateBack = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                },
                                onNavigateToLastSeen = { waypoint ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "GPS route plotted to ${waypoint.name}"
                                        )
                                    }
                                    onNavigateToMap?.invoke(heroMatch.personId)
                                },
                                onAlertBeats = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Surrounding beat officers alerted on channel 4"
                                        )
                                    }
                                },
                                showDeviceChrome = false
                            )

                            2 -> ForensicDossierTab(
                                dossier = forensicDossier,
                                onNavigateBack = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                },
                                onExportPdf = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Forensic Evidence Dossier exported as PDF"
                                        )
                                    }
                                },
                                onBroadcastAlert = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "City-wide missing person broadcast triggered for ${heroMatch.personName}"
                                        )
                                    }
                                },
                                onCallOfficer = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Connecting to IO for Case #${forensicDossier.firNumber}..."
                                        )
                                    }
                                },
                                showDeviceChrome = false
                            )
                        }
                    }
                }

                DashboardViewMode.THREE_SCREEN_SHOWCASE -> {
                    // 3-Screen Side-by-Side Presentation in 3:2 layout as described in brief
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFEFE8E1))
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = PaperWhite,
                            border = BorderStroke(1.dp, LineBorder)
                        ) {
                            Text(
                                text = "3-Screen Showcase Presentation (iOS 390 × 844 px frame)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Horizontal row of 3 mobile device frames
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            // Screen 1: Live Sighting Radar
                            DeviceFrame(title = "Screen 1 • Sightings Radar") {
                                SightingRadarTab(
                                    heroMatch = heroMatch,
                                    metrics = metrics,
                                    recentSightings = recentSightings,
                                    onDispatchUnit = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Field unit dispatched to ${heroMatch.cameraNode}")
                                        }
                                    },
                                    showDeviceChrome = true
                                )
                            }

                            // Screen 2: AI Breadcrumb GIS Trajectory
                            DeviceFrame(title = "Screen 2 • Breadcrumb Route") {
                                BreadcrumbRouteTab(
                                    waypoints = waypoints,
                                    caseId = heroMatch.personId,
                                    onNavigateBack = {},
                                    onNavigateToLastSeen = { waypoint ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Navigating to ${waypoint.name}")
                                        }
                                        onNavigateToMap?.invoke(heroMatch.personId)
                                    },
                                    onAlertBeats = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Surrounding beat patrol units alerted")
                                        }
                                    },
                                    showDeviceChrome = true
                                )
                            }

                            // Screen 3: Forensic Match Dossier
                            DeviceFrame(title = "Screen 3 • Forensic Dossier") {
                                ForensicDossierTab(
                                    dossier = forensicDossier,
                                    onNavigateBack = {},
                                    onExportPdf = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Forensic Dossier PDF exported")
                                        }
                                    },
                                    onBroadcastAlert = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("City-wide alert broadcasted for ${heroMatch.personName}")
                                        }
                                    },
                                    onCallOfficer = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Connecting to IO for Case #${forensicDossier.firNumber}")
                                        }
                                    },
                                    showDeviceChrome = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mobile device mockup frame (390 × 844 px proportional container) with soft claymorphic shadow
 */
@Composable
private fun DeviceFrame(
    title: String,
    content: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = BlueDeep,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Surface(
            modifier = Modifier
                .width(360.dp)
                .height(780.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = InkText.copy(alpha = 0.15f),
                    spotColor = BluePrimary.copy(alpha = 0.10f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = CreamBackground,
            border = BorderStroke(2.dp, LineBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
            ) {
                content()
            }
        }
    }
}
