package com.findmissingpep.app.presentation.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.findmissingpep.app.presentation.screens.dashboard.components.GisCanvasView
import com.findmissingpep.app.presentation.screens.dashboard.components.IosHomeIndicator
import com.findmissingpep.app.presentation.screens.dashboard.components.IosStatusBar
import com.findmissingpep.app.presentation.theme.*

/**
 * Screen 2: AI Breadcrumb GIS Trajectory & Camera Route
 * Features GIS canvas map, connected directional waypoints, forward projection cone,
 * and elevated floating bottom summary sheet.
 */
@Composable
fun BreadcrumbRouteTab(
    waypoints: List<WaypointItem>,
    caseId: String = "PEP-2024-8891",
    onNavigateBack: () -> Unit = {},
    onNavigateToLastSeen: (WaypointItem) -> Unit = {},
    onAlertBeats: () -> Unit = {},
    showDeviceChrome: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isSatelliteMode by remember { mutableStateOf(false) }
    var selectedWaypoint by remember(waypoints) {
        mutableStateOf(waypoints.find { it.isLatest } ?: waypoints.lastOrNull() ?: DashboardMockData.waypoints.first())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        if (showDeviceChrome) {
            IosStatusBar()

            // ── Screen 2 Top Bar (Height: 64px, only in showcase preview) ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                color = PaperWhite,
                border = BorderStroke(1.dp, LineBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PaperSubtle)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = InkText,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Breadcrumb Route",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = "Case #$caseId",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkMuted
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { isSatelliteMode = !isSatelliteMode },
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSatelliteMode) BlueDeep else BlueWash,
                        border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isSatelliteMode) Color.White else BluePrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isSatelliteMode) "Satellite" else "Vector",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSatelliteMode) Color.White else BluePrimary
                            )
                        }
                    }
                }
            }
        }

        // ── Main Body: GIS Canvas + Floating Docked Sheet ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // A. Interactive GIS Map Canvas (fills upper area)
            GisCanvasView(
                waypoints = waypoints,
                isSatelliteMode = isSatelliteMode,
                selectedWaypointId = selectedWaypoint.id,
                onSelectWaypoint = { selectedWaypoint = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 260.dp, start = 12.dp, end = 12.dp, top = 8.dp)
            )

            // Floating Layer Toggle Button for standard app mode
            if (!showDeviceChrome) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { isSatelliteMode = !isSatelliteMode },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSatelliteMode) BlueDeep else PaperWhite,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, if (isSatelliteMode) BluePrimary else LineBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (isSatelliteMode) Color.White else BluePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isSatelliteMode) "Satellite" else "Vector",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSatelliteMode) Color.White else InkText
                        )
                    }
                }
            }

            // B. Floating Waypoint Summary Sheet (Lower Card docked to bottom, 24px top radius)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        ambientColor = InkText.copy(alpha = 0.18f),
                        spotColor = BluePrimary.copy(alpha = 0.12f)
                    ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = PaperWhite,
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    // Small sheet drag handle
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(LineBorder)
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Header row: Waypoint title & Time tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedWaypoint.isLatest) RedDanger else BluePrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedWaypoint.name,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = selectedWaypoint.subtitle,
                                fontSize = 11.sp,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selectedWaypoint.isLatest) RedDangerBg else BlueWash,
                            border = BorderStroke(
                                0.5.dp,
                                if (selectedWaypoint.isLatest) RedDanger.copy(alpha = 0.4f) else BluePrimary.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = selectedWaypoint.time,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedWaypoint.isLatest) RedDanger else BluePrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // CCTV Context Snapshot: 16:9 full HD context frame showing pedestrian crowd
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, LineBorder, RoundedCornerShape(14.dp))
                            .background(Color(0xFF0F172A))
                    ) {
                        val contextFrameUrl = selectedWaypoint.snapshotUrl?.takeIf { it.isNotBlank() }
                            ?: "${com.findmissingpep.app.BuildConfig.API_BASE_URL}/uploads/evidence/3530494478f444d79a179704e2b914c1.jpg"
                        AsyncImage(
                            model = contextFrameUrl,
                            contentDescription = "CCTV HD Context Frame",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Highlight box overlay around subject
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 54.dp)
                                .align(Alignment.Center)
                                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(3.dp))
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f))
                        )

                        // Camera OSD Overlay
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "1080p CAM-04 CP-METRO • 15:04:12",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Movement Telemetry: Heading + Velocity
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = PaperSubtle,
                        border = BorderStroke(1.dp, LineBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = BluePrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedWaypoint.heading,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = InkText
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                    contentDescription = null,
                                    tint = InkMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedWaypoint.velocity,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action CTAs:
                    // Primary button: "Navigate to Last Seen Point" (Royal Blue with GPS arrow)
                    // Secondary button: "Alert Surrounding Beats" (Outline pill)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigateToLastSeen(selectedWaypoint) },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(46.dp),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BluePrimary,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NearMe,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Navigate to Point",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onAlertBeats,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, LineBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = InkText)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = AmberAlert,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Alert Beats",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
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

        if (showDeviceChrome) {
            IosHomeIndicator()
        }
    }
}
