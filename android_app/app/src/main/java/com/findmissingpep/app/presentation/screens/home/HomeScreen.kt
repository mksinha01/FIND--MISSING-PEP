package com.findmissingpep.app.presentation.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.findmissingpep.app.domain.model.MissingCase
import com.findmissingpep.app.presentation.common.*
import com.findmissingpep.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToReport: () -> Unit,
    onNavigateToSightings: () -> Unit,
    onNavigateToCases: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToFirQueue: () -> Unit,
    onNavigateToCaseDetail: (String) -> Unit,
    onNavigateToDashboard: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = CreamBackground,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = CreamBackground,
                border = BorderStroke(1.dp, LineBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brand Mark + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigateToCases() }
                    ) {
                        FmpLogo(size = 38.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FIND-MISSING-PEP",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                letterSpacing = 0.4.sp,
                                color = BlueDeep
                            )
                            Text(
                                text = "Search & support",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                color = InkMuted
                            )
                        }
                    }

                    // Header Action Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Notifications
                        IconButton(onClick = onNavigateToNotifications) {
                            BadgedBox(
                                badge = {
                                    if (uiState.unreadNotifications > 0) {
                                        Badge(
                                            containerColor = RedDanger,
                                            contentColor = Color.White
                                        ) {
                                            Text("${uiState.unreadNotifications}", fontSize = 9.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = InkText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Profile Avatar
                        IconButton(onClick = onNavigateToProfile) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(BlueWash),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = BluePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                // ── 1. Greeting Section (Blue & Cream) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your next step,\nmade clear.",
                            fontSize = 22.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Report a missing person or follow up on a case.",
                            fontSize = 12.sp,
                            color = InkMuted
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BlueWash,
                        border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = if (uiState.isOfficer) "Officer view" else "Citizen view",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueDeep,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── AI Mission Radar & Trajectory Dashboard Card ──
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(18.dp),
                            ambientColor = BluePrimary.copy(alpha = 0.15f),
                            spotColor = BluePrimary.copy(alpha = 0.08f)
                        )
                        .clickable { onNavigateToDashboard() },
                    shape = RoundedCornerShape(18.dp),
                    color = PaperWhite,
                    border = BorderStroke(1.5.dp, BluePrimary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = GreenSuccessBg,
                                border = BorderStroke(0.5.dp, GreenSuccess.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PulsingDot(color = GreenSuccess)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "SSE LIVE RADAR",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = GreenSuccess
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = RedDangerBg
                            ) {
                                Text(
                                    text = "Critical Match • 94.8%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RedDanger,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "AI Breadcrumb GIS Radar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BlueDeep
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Real-time CCTV edge tracking, movement trajectory cone & forensic dossier verification.",
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = InkMuted
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "3-Screen Mission Control",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BluePrimary
                            )

                            Button(
                                onClick = onNavigateToDashboard,
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BluePrimary,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Open Dashboard", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── 2. Review Possible Match Card ──
                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToSightings
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Review a possible match",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkText
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AmberAlertBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AmberAlert)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Pending review",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberAlert
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Check a sighting before deciding",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BlueDeep
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Compare the submitted evidence in the sightings feed. Verification lead confirms sighting area.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = InkMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Browse surveillance observations",
                            fontSize = 10.sp,
                            color = InkSubtle
                        )
                        Button(
                            onClick = onNavigateToSightings,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BluePrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Review sightings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── 3. Report a Missing Person Card ──
                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = PaperWhite,
                    onClick = onNavigateToReport
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Report a missing person",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = InkText
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Share key details, an FIR and reference photos.",
                                fontSize = 11.sp,
                                color = InkMuted
                            )
                        }
                        Button(
                            onClick = onNavigateToReport,
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BluePrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Start report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── 4. Your Casework Section ──
                Text(
                    text = "Your casework",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )
                Spacer(modifier = Modifier.height(8.dp))

                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: Reported Cases
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToCases() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(BlueWash),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = BluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reported cases (${uiState.activeCasesCount} active)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = InkText
                            )
                            Text(
                                text = "Open your cases and see their current status.",
                                fontSize = 11.sp,
                                color = InkMuted
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = InkSubtle,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = LineBorderSubtle
                    )

                    // Row 2: Live Sightings
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSightings() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(GreenSuccessBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Live sightings (${uiState.verifiedMatchesToday} verified)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = InkText
                            )
                            Text(
                                text = "Browse and filter possible CCTV matches.",
                                fontSize = 11.sp,
                                color = InkMuted
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = InkSubtle,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ── 5. Officer FIR Queue Card ──
                if (uiState.isOfficer) {
                    Spacer(modifier = Modifier.height(14.dp))
                    PaperCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = PaperWhite,
                        borderColor = LineBorder,
                        onClick = onNavigateToFirQueue
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(AmberAlertBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    tint = AmberAlert,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FIR verification queue",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = InkText
                                )
                                Text(
                                    text = "For officers · review submitted documents",
                                    fontSize = 11.sp,
                                    color = InkMuted
                                )
                            }
                            Button(
                                onClick = onNavigateToFirQueue,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberAlert,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                // ── 6. Active Cases List (Horizontal Row) ──
                if (uiState.myCases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent cases",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkText
                        )
                        Text(
                            text = "View all",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BluePrimary,
                            modifier = Modifier.clickable { onNavigateToCases() }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.myCases.take(5)) { caseItem ->
                            CaseCarouselItem(
                                caseItem = caseItem,
                                onClick = { onNavigateToCaseDetail(caseItem.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun CaseCarouselItem(
    caseItem: MissingCase,
    onClick: () -> Unit
) {
    PaperCard(
        modifier = Modifier
            .width(200.dp)
            .clickable { onClick() },
        backgroundColor = PaperWhite
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BlueWash),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            StatusBadge(status = caseItem.status)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = caseItem.fullName,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = InkText,
            maxLines = 1
        )
        Text(
            text = "${caseItem.age ?: "?"} yrs • ${caseItem.gender ?: "Unknown"}",
            fontSize = 11.sp,
            color = InkMuted
        )
        val loc = caseItem.lastSeenLocation
        if (!loc.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = loc,
                fontSize = 10.sp,
                color = InkSubtle,
                maxLines = 1
            )
        }
    }
}
