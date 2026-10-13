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
import com.findmissingpep.app.presentation.common.PaperCard
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
        containerColor = CreamBackground,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = PaperWhite,
                border = BorderStroke(1.dp, LineBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = InkText
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Review sighting",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkText
                        )
                        Text(
                            text = "CCTV evidence verification",
                            fontSize = 11.sp,
                            color = InkMuted
                        )
                    }
                    uiState.sighting?.let { s ->
                        IconButton(onClick = { onNavigateToMap(s.personId) }) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "View Location Map",
                                tint = BluePrimary
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.sighting == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BluePrimary)
            }
        } else if (uiState.sighting != null) {
            val sighting = uiState.sighting!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Action notification message
                uiState.actionSuccessMessage?.let {
                    Surface(
                        color = GreenSuccessBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = GreenSuccess,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Match Lead Banner
                MatchLeadCard(sighting = sighting)

                // Split Comparison: Reference Photo vs CCTV Crop
                FaceComparisonSection(
                    sighting = sighting,
                    refPhotoUrl = uiState.associatedCase?.photos?.firstOrNull()?.let { p ->
                        p.faceCropPath ?: p.originalPath
                    }
                )

                // Video Clip Buffer (if available)
                sighting.videoClipPath?.let { clipPath ->
                    VideoClipCard(clipPath = clipPath)
                }

                // Full Surveillance Scene (if available)
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
                    PaperCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Verification status",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = InkText
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
            title = { Text("Confirm verified sighting", color = InkText, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "Confirming this match verifies that the missing person was observed at this location.",
                        color = InkMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reviewNotes,
                        onValueChange = { reviewNotes = it },
                        label = { Text("Verification notes (optional)") },
                        placeholder = { Text("e.g. Visual confirmation by Officer Sharma", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = humanTextFieldColors(),
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
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm match", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = PaperWhite,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Reject Dialog
    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject false alarm", color = InkText, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "Mark this observation as a false positive / unrelated individual.",
                        color = InkMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reviewNotes,
                        onValueChange = { reviewNotes = it },
                        label = { Text("Reason (optional)") },
                        placeholder = { Text("e.g. Unrelated pedestrian", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = humanTextFieldColors(),
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
                    colors = ButtonDefaults.buttonColors(containerColor = RedDanger, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reject observation", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = PaperWhite,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

// ─── Match Lead Card ────────────────────────────────────────────────────────

@Composable
private fun MatchLeadCard(sighting: Sighting) {
    val percentage = (sighting.similarityScore * 100).toInt()
    val color = when {
        sighting.similarityScore >= 0.75 -> GreenSuccess
        sighting.similarityScore >= 0.60 -> AmberAlert
        else -> RedDanger
    }
    val leadLabel = when {
        sighting.similarityScore >= 0.75 -> "HIGH MATCH CONFIDENCE"
        sighting.similarityScore >= 0.60 -> "PROBABLE MATCH"
        else -> "UNCONFIRMED SIMILARITY"
    }

    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = leadLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Verification score: $percentage%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkText
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f))
                    .border(2.dp, color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$percentage%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { sighting.similarityScore.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = LineBorderSubtle
        )
    }
}

// ─── Face Comparison Section ────────────────────────────────────────────────

@Composable
private fun FaceComparisonSection(
    sighting: Sighting,
    refPhotoUrl: String?
) {
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Visual evidence comparison",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = InkText
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Reference Photo
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, LineBorder, RoundedCornerShape(10.dp))
                        .background(BlueWash),
                    contentAlignment = Alignment.Center
                ) {
                    if (refPhotoUrl != null) {
                        val fullUrl = if (refPhotoUrl.startsWith("http")) refPhotoUrl else "${BuildConfig.API_BASE_URL}$refPhotoUrl"
                        AsyncImage(
                            model = fullUrl,
                            contentDescription = "Official reference portrait",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Reference portrait",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )
                Text(
                    text = "From registered case",
                    fontSize = 9.sp,
                    color = InkMuted
                )
            }

            // CCTV Sighting Crop
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val sightingCrop = sighting.faceCropPath?.let {
                    if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, LineBorder, RoundedCornerShape(10.dp))
                        .background(BlueWash),
                    contentAlignment = Alignment.Center
                ) {
                    if (sightingCrop != null) {
                        AsyncImage(
                            model = sightingCrop,
                            contentDescription = "CCTV observation frame",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "CCTV observation",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )
                Text(
                    text = "Camera frame crop",
                    fontSize = 9.sp,
                    color = InkMuted
                )
            }
        }
    }
}

// ─── Video Clip Player Card ─────────────────────────────────────────────────

@Composable
private fun VideoClipCard(clipPath: String) {
    val context = LocalContext.current
    val fullUrl = if (clipPath.startsWith("http")) clipPath else "${BuildConfig.API_BASE_URL}$clipPath"
    var isPlaying by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(fullUrl)))
            prepare()
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Camera video sequence",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = InkText
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PaperSubtle),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ─── Full Surveillance Scene Card ───────────────────────────────────────────

@Composable
private fun FullSceneCard(scenePath: String) {
    val fullUrl = if (scenePath.startsWith("http")) scenePath else "${BuildConfig.API_BASE_URL}$scenePath"

    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Full surveillance frame",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = InkText
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, LineBorder, RoundedCornerShape(10.dp))
                .background(PaperSubtle)
        ) {
            AsyncImage(
                model = fullUrl,
                contentDescription = "Full surveillance frame",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ─── Evidence & Camera Details Card ─────────────────────────────────────────

@Composable
private fun EvidenceDetailsCard(sighting: Sighting) {
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Observation metadata",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = InkText
        )
        Spacer(modifier = Modifier.height(8.dp))

        DetailRow(label = "Camera Location", value = sighting.cameraLocation ?: "Public Camera")
        DetailRow(label = "Camera ID", value = sighting.cameraId)
        DetailRow(label = "Observation Time", value = sighting.detectedAt)
        DetailRow(label = "Verification Status", value = sighting.status.name)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = InkMuted)
        Text(text = value, fontSize = 11.sp, color = InkText, fontWeight = FontWeight.Bold)
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
        Button(
            onClick = onConfirm,
            enabled = !isActing,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GreenSuccess,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Confirm match", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        OutlinedButton(
            onClick = onReject,
            enabled = !isActing,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = PaperWhite,
                contentColor = RedDanger
            ),
            border = BorderStroke(1.dp, RedDanger.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = RedDanger)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reject false alarm", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RedDanger)
        }
    }
}

@Composable
private fun humanTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BluePrimary,
    unfocusedBorderColor = LineBorder,
    focusedLabelColor = BluePrimary,
    unfocusedLabelColor = InkMuted,
    cursorColor = BluePrimary,
    focusedTextColor = InkText,
    unfocusedTextColor = InkText,
    focusedContainerColor = PaperWhite,
    unfocusedContainerColor = PaperWhite
)
