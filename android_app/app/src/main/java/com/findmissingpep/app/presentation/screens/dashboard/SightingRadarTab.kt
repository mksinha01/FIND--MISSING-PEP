package com.findmissingpep.app.presentation.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.findmissingpep.app.presentation.screens.dashboard.components.BiometricMatchArcGauge
import com.findmissingpep.app.presentation.screens.dashboard.components.FilterControlPills
import com.findmissingpep.app.presentation.screens.dashboard.components.IosHomeIndicator
import com.findmissingpep.app.presentation.screens.dashboard.components.IosStatusBar
import com.findmissingpep.app.presentation.theme.*

/**
 * Screen 1: Live Sighting Radar & Active Search Feed
 * Fully faithful to the design brief with claymorphic elevation and paper cards.
 */
@Composable
fun SightingRadarTab(
    heroMatch: GoldenHourHeroMatch,
    metrics: OperationsMetrics,
    recentSightings: List<RecentSightingItem>,
    onDispatchUnit: () -> Unit = {},
    onViewAllSightings: () -> Unit = {},
    onSightingClick: (RecentSightingItem) -> Unit = {},
    showDeviceChrome: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 1.5s breathing pulse on SSE LIVE indicator dot
    val infiniteTransition = rememberInfiniteTransition(label = "ssePulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        if (showDeviceChrome) {
            IosStatusBar()

            // ── Screen 1 Top Bar (Height: 64px, only in showcase preview) ──
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
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Hamburger drawer / Shield emblem
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BlueWash),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield Emblem",
                            tint = BluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Center: Title "Sightings Radar" + Pulsing green dot badge (SSE LIVE)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Sightings Radar",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText,
                            letterSpacing = (-0.3).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = GreenSuccessBg,
                            border = BorderStroke(0.5.dp, GreenSuccess.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(GreenSuccess.copy(alpha = dotAlpha))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SSE LIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenSuccess,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }

                    // Right: Notification bell with red badge counter (3)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PaperSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = RedDanger,
                                    contentColor = Color.White
                                ) {
                                    Text("3", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = InkText,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Filter & Control Pills ──
        FilterControlPills()

        // ── Scrollable Body ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── A. Rapid Operations Metric Strip ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = InkText.copy(alpha = 0.08f),
                        spotColor = InkText.copy(alpha = 0.04f)
                    ),
                shape = RoundedCornerShape(20.dp),
                color = PaperWhite,
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Col 1: Active Searches: 14
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${metrics.activeSearches}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueDeep
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Active Searches",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkMuted
                        )
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(LineBorderSubtle)
                    )

                    // Col 2: Verified Today: 6
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${metrics.verifiedToday}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Verified Today",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkMuted
                        )
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(LineBorderSubtle)
                    )

                    // Col 3: Edge Cams Online: 42/44
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = metrics.edgeCamsOnline,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Edge Cams Online",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── B. Hero Alert Card ("Critical Golden Hour Match") ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = BluePrimary.copy(alpha = 0.14f),
                        spotColor = RedDanger.copy(alpha = 0.12f)
                    ),
                shape = RoundedCornerShape(22.dp),
                color = PaperWhite,
                border = BorderStroke(1.5.dp, RedDanger.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Top Tag: Red urgent pill badge + Case ID Pill (Perfect horizontal alignment)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = RedDangerBg,
                            border = BorderStroke(1.dp, RedDanger.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(RedDanger)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = heroMatch.urgentTag,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RedDanger,
                                    letterSpacing = 0.3.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val caseShortId = if (heroMatch.personId.length > 8) {
                            heroMatch.personId.take(8).uppercase()
                        } else heroMatch.personId

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = PaperSubtle,
                            border = BorderStroke(1.dp, LineBorderSubtle)
                        ) {
                            Text(
                                text = "CASE #$caseShortId",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Balanced 3-Column Face Verification Block (Top aligned for exact horizontal leveling)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Col 1: Enrolled FIR Reference Portrait
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, LineBorder, RoundedCornerShape(12.dp))
                                    .background(PaperSubtle)
                            ) {
                                AsyncImage(
                                    model = heroMatch.firReferencePhoto,
                                    contentDescription = "Enrolled Reference",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth(),
                                    color = Color.Black.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = "FIR Photo",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        textAlign = TextAlign.Center,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = heroMatch.personName,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkText,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Age ${heroMatch.age} • Enrolled",
                                fontSize = 9.sp,
                                color = InkMuted,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Col 2: ArcFace Match Gauge
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BiometricMatchArcGauge(
                                percentage = heroMatch.similarityScore * 100,
                                subtext = "ArcFace Match",
                                diameter = 74.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Col 3: Real-time CCTV Face Crop
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F172A))
                            ) {
                                AsyncImage(
                                    model = heroMatch.cctvFaceCrop,
                                    contentDescription = "Real-time CCTV Face Crop",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Cyan Bounding Box reticle overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                                )

                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth(),
                                    color = Color(0xFF083344).copy(alpha = 0.85f)
                                ) {
                                    Text(
                                        text = "112×112 LIVE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF67E8F9),
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        textAlign = TextAlign.Center,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = "CCTV Crop",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkText,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            val camDisplay = heroMatch.cameraNode.split("•").firstOrNull()?.trim() ?: "CAM-01"
                            Text(
                                text = "$camDisplay Aligned",
                                fontSize = 9.sp,
                                color = BluePrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(11.dp))

                    // Metadata Div
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = PaperSubtle,
                        border = BorderStroke(1.dp, LineBorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = BluePrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = heroMatch.cameraNode,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = InkText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = GreenSuccess,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = heroMatch.detectionProtocol,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = InkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(11.dp))

                    // Quick Action Button Div: Dispatch Field Unit
                    Button(
                        onClick = onDispatchUnit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BluePrimary,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "Dispatch Field Unit",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── C. Recent Sightings Stream (List of Cards) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Edge Detections",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )

                Text(
                    text = "View All >",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary,
                    modifier = Modifier.clickable { onViewAllSightings() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stream items
            recentSightings.forEach { sighting ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSightingClick(sighting) },
                    shape = RoundedCornerShape(16.dp),
                    color = PaperWhite,
                    border = BorderStroke(1.dp, LineBorder),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Aligned thumbnail crop
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, LineBorderSubtle, RoundedCornerShape(10.dp))
                                .background(PaperSubtle)
                        ) {
                            AsyncImage(
                                model = sighting.imageUrl,
                                contentDescription = sighting.cameraName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sighting.cameraName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkText
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Status pill
                                val pillBg = if (sighting.isVerified) GreenSuccessBg else AmberAlertBg
                                val pillColor = if (sighting.isVerified) GreenSuccess else AmberAlert
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = pillBg
                                ) {
                                    Text(
                                        text = "${sighting.status} (${String.format("%.1f%%", sighting.confidence * 100)})",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = pillColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = sighting.timestamp,
                                    fontSize = 11.sp,
                                    color = InkMuted
                                )
                            }
                        }

                        // Trailing chevron (>)
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "View",
                            tint = InkSubtle,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(56.dp))
        }

        if (showDeviceChrome) {
            IosHomeIndicator()
        }
    }
}
