package com.findmissingpep.app.presentation.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.findmissingpep.app.presentation.screens.dashboard.components.IosHomeIndicator
import com.findmissingpep.app.presentation.screens.dashboard.components.IosStatusBar
import com.findmissingpep.app.presentation.screens.dashboard.components.TemporalFrameStrip
import com.findmissingpep.app.presentation.screens.dashboard.components.VideoSnippetPlayerCard
import com.findmissingpep.app.presentation.theme.*

/**
 * Screen 3: Forensic Match Dossier & Temporal Verification
 * Implements Official Case Dossier Card, M-of-N temporal sliding window validator,
 * tamper-evident video player, and operational escalation buttons.
 */
@Composable
fun ForensicDossierTab(
    dossier: ForensicDossierData,
    onNavigateBack: () -> Unit = {},
    onExportPdf: () -> Unit = {},
    onBroadcastAlert: () -> Unit = {},
    onCallOfficer: () -> Unit = {},
    showDeviceChrome: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        if (showDeviceChrome) {
            IosStatusBar()

            // ── Screen 3 Top Bar (Height: 64px, only in showcase preview) ──
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
                            text = "Forensic Dossier",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText,
                            letterSpacing = (-0.3).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = GreenSuccessBg,
                            border = BorderStroke(0.5.dp, GreenSuccess.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(GreenSuccess)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verified Match",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenSuccess
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onExportPdf,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PaperSubtle)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Export PDF Evidence",
                            tint = BluePrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        // ── Scrollable Body ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ── A. Missing Person Official Case Dossier Card ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = InkText.copy(alpha = 0.08f),
                        spotColor = InkText.copy(alpha = 0.04f)
                    ),
                shape = RoundedCornerShape(22.dp),
                color = PaperWhite,
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Legal FIR Header Row with Export PDF Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 8.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = PaperSubtle,
                            border = BorderStroke(1.dp, LineBorderSubtle)
                        ) {
                            Text(
                                text = dossier.firNumber,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueDeep,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Export Evidence PDF Action Pill
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = BlueWash,
                            border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.3f)),
                            modifier = Modifier.clickable { onExportPdf() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = BluePrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Export PDF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BluePrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Enrolled portrait
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, LineBorder, RoundedCornerShape(12.dp))
                                .background(PaperSubtle)
                        ) {
                            val photoModel = dossier.personPhotoUrl?.takeIf { it.isNotBlank() }
                                ?: "${com.findmissingpep.app.BuildConfig.API_BASE_URL}/uploads/faces/8474460fa67a45cab82111078705e902.jpg"
                            AsyncImage(
                                model = photoModel,
                                contentDescription = dossier.personName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "${dossier.personName}, ${dossier.age} yrs",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkText
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Medical Alert Tag: Alzheimer's / High Vulnerability (Soft red pill)
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = RedDangerBg,
                                border = BorderStroke(0.5.dp, RedDanger.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(RedDanger)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = dossier.medicalAlert,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RedDanger
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Reported date info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PaperSubtle)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reported Missing:",
                            fontSize = 11.sp,
                            color = InkMuted
                        )
                        Text(
                            text = dossier.reportedMissingDate,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── B. M-of-N Temporal Sliding-Window Validation Card ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = GreenSuccess.copy(alpha = 0.08f),
                        spotColor = InkText.copy(alpha = 0.04f)
                    ),
                shape = RoundedCornerShape(22.dp),
                color = PaperWhite,
                border = BorderStroke(1.dp, LineBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Temporal Window Validation (Anti-False-Positive)",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkText
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Verification state
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GreenSuccessBg,
                        border = BorderStroke(0.5.dp, GreenSuccess.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = dossier.validationWindowText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Frame Thumbnail Strip (4 consecutive video crops showing #TK-402)
                    TemporalFrameStrip(
                        frames = dossier.frameMatches,
                        byteTrackId = dossier.byteTrackId
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── C. Tamper-Evident Video Snippet Card ──
            VideoSnippetPlayerCard(
                cryptographicTimestamp = dossier.cryptographicTimestamp,
                sha256Badge = dossier.sha256Hash
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ── D. Operational Escalation Bar ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top CTA: "Broadcast City-Wide Sighting Alert" (Solid Royal Blue pill)
                Button(
                    onClick = onBroadcastAlert,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BluePrimary,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Broadcast City-Wide Sighting Alert",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom CTA: "Call Assigned Investigating Officer" (Clean white elevated pill with phone icon)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onCallOfficer() },
                    shape = RoundedCornerShape(999.dp),
                    color = PaperWhite,
                    border = BorderStroke(1.dp, LineBorder),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Call Assigned Investigating Officer",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkText
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
