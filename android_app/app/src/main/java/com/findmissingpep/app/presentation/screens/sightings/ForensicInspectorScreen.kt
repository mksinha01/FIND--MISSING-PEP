package com.findmissingpep.app.presentation.screens.sightings

import android.net.Uri
import androidx.annotation.OptIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.findmissingpep.app.BuildConfig
import com.findmissingpep.app.domain.model.ConfidenceLevel
import com.findmissingpep.app.domain.model.Sighting
import com.findmissingpep.app.domain.model.SightingStatus
import com.findmissingpep.app.presentation.common.GlassCard
import com.findmissingpep.app.presentation.common.SightingStatusBadge
import com.findmissingpep.app.presentation.theme.*

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun ForensicInspectorScreen(
    sightingId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMap: (String) -> Unit,
    viewModel: ForensicInspectorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var reviewNotes by remember { mutableStateOf("") }

    LaunchedEffect(sightingId) {
        viewModel.loadSighting(sightingId)
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Forensic Inspector",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    uiState.sighting?.let { s ->
                        IconButton(onClick = { onNavigateToMap(s.personId) }) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "View Trajectory Map",
                                tint = CyanPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.sighting == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyanPrimary)
            }
        } else if (uiState.sighting != null) {
            val sighting = uiState.sighting!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Action notification message
                uiState.actionSuccessMessage?.let {
                    Surface(
                        color = EmeraldPrimary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = EmeraldPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Confidence Banner
                ConfidenceGaugeCard(sighting = sighting)

                // Split Comparison: Reference Photo vs CCTV Crop
                FaceComparisonSection(
                    sighting = sighting,
                    refPhotoUrl = uiState.associatedCase?.photos?.firstOrNull()?.let { p ->
                        p.faceCropPath ?: p.originalPath
                    }
                )

                // 5-Second Video Clip Buffer (ExoPlayer)
                sighting.videoClipPath?.let { clipPath ->
                    VideoClipCard(clipPath = clipPath)
                }

                // 1080p Full Surveillance Scene
                sighting.fullFramePath?.let { scenePath ->
                    FullSceneCard(scenePath = scenePath)
                }

                // Evidence & Camera Details
                EvidenceDetailsCard(sighting = sighting)

                // Disposition Controls (Only enabled if pending)
                if (sighting.status == SightingStatus.PENDING) {
                    DispositionActionButtons(
                        isActing = uiState.isActing,
                        onConfirm = { showConfirmDialog = true },
                        onReject = { showRejectDialog = true }
                    )
                } else {
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Disposition Status",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            SightingStatusBadge(status = sighting.status)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Confirm Match Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Verified Sighting", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Confirming this match will update the missing person status to FOUND and alert search units across the network.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reviewNotes,
                        onValueChange = { reviewNotes = it },
                        label = { Text("Officer Verification Notes", color = TextSecondary) },
                        placeholder = { Text("e.g. Visual confirmation by Constable Sharma", color = Slate500) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.confirmSighting(reviewNotes)
                        reviewNotes = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Confirm Match", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Reject Dialog
    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject False Alarm", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Flag this CCTV match as a false positive. Rejection helps refine neural edge thresholds.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reviewNotes,
                        onValueChange = { reviewNotes = it },
                        label = { Text("Rejection Reason", color = TextSecondary) },
                        placeholder = { Text("e.g. Distinct facial features / false match", color = Slate500) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RosePrimary,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRejectDialog = false
                        viewModel.rejectSighting(reviewNotes)
                        reviewNotes = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RosePrimary)
                ) {
                    Text("Reject Alert", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

// ─── Confidence Gauge Card ──────────────────────────────────────────────────

@Composable
private fun ConfidenceGaugeCard(sighting: Sighting) {
    val percentage = (sighting.similarityScore * 100).toInt()
    val color = when {
        sighting.similarityScore >= 0.75 -> ConfidenceHigh
        sighting.similarityScore >= 0.60 -> ConfidenceMedium
        else -> ConfidenceLow
    }
    val confidenceLabel = when {
        sighting.similarityScore >= 0.75 -> "HIGH CONFIDENCE MATCH"
        sighting.similarityScore >= 0.60 -> "PROBABLE MATCH"
        else -> "UNCERTAIN SIMILARITY"
    }

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = confidenceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$percentage% ArcFace Cosine Similarity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f))
                    .border(2.dp, color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Linear Progress Bar
        LinearProgressIndicator(
            progress = { sighting.similarityScore.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Slate800
        )
    }
}

// ─── Face Comparison Section ────────────────────────────────────────────────

@Composable
private fun FaceComparisonSection(
    sighting: Sighting,
    refPhotoUrl: String?
) {
    GlassCard {
        Text(
            text = "Side-by-Side Facial Verification",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CyanPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Enrolled Reference Photo
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .background(Slate800),
                    contentAlignment = Alignment.Center
                ) {
                    if (refPhotoUrl != null) {
                        val fullUrl = if (refPhotoUrl.startsWith("http")) refPhotoUrl else "${BuildConfig.API_BASE_URL}$refPhotoUrl"
                        AsyncImage(
                            model = fullUrl,
                            contentDescription = "Enrolled Reference",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Slate600)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enrolled Reference",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // CCTV Face Crop (112x112)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, EmeraldPrimary, RoundedCornerShape(12.dp))
                        .background(Slate800),
                    contentAlignment = Alignment.Center
                ) {
                    sighting.faceCropPath?.let { cropPath ->
                        val fullUrl = if (cropPath.startsWith("http")) cropPath else "${BuildConfig.API_BASE_URL}$cropPath"
                        AsyncImage(
                            model = fullUrl,
                            contentDescription = "CCTV Face Crop",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "CCTV 112×112 Aligned",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ─── 5-Second Video Clip Buffer (ExoPlayer) ─────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
private fun VideoClipCard(clipPath: String) {
    val context = LocalContext.current
    val fullVideoUrl = if (clipPath.startsWith("http")) clipPath else "${BuildConfig.API_BASE_URL}$clipPath"

    val exoPlayer = remember(context, fullVideoUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(fullVideoUrl))
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "5-Second CCTV Video Clip Buffer",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary
            )
            Surface(
                color = EmeraldPrimary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Looping Gait",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ─── 1080p Full Surveillance Scene ──────────────────────────────────────────

@Composable
private fun FullSceneCard(scenePath: String) {
    val fullSceneUrl = if (scenePath.startsWith("http")) scenePath else "${BuildConfig.API_BASE_URL}$scenePath"

    GlassCard {
        Text(
            text = "1080p Surveillance Scene Context",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CyanPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Full camera frame captures subject surroundings, clothing, and companions.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                .background(Slate800)
        ) {
            AsyncImage(
                model = fullSceneUrl,
                contentDescription = "Full Scene",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ─── Evidence & Camera Details ──────────────────────────────────────────────

@Composable
private fun EvidenceDetailsCard(sighting: Sighting) {
    GlassCard {
        Text(
            text = "Camera & Detection Telemetry",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CyanPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        TelemetryRow(label = "Camera Location", value = sighting.cameraLocation ?: "Node ${sighting.cameraId}")
        TelemetryRow(label = "CCTV Agent ID", value = sighting.agentId)
        TelemetryRow(label = "Detection Timestamp", value = sighting.detectedAt)
        sighting.numFramesMatched?.let {
            TelemetryRow(label = "ByteTrack Verified Frames", value = "$it matched frames")
        }
        if (sighting.latitude != null && sighting.longitude != null) {
            TelemetryRow(label = "GPS Coordinates", value = "${sighting.latitude}, ${sighting.longitude}")
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Disposition Action Buttons ─────────────────────────────────────────────

@Composable
private fun DispositionActionButtons(
    isActing: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onReject,
            enabled = !isActing,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, RosePrimary.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RosePrimary)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reject Alarm")
        }

        Button(
            onClick = onConfirm,
            enabled = !isActing,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldPrimary,
                contentColor = Color.Black
            )
        ) {
            if (isActing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirm Match", fontWeight = FontWeight.Bold)
            }
        }
    }
}
