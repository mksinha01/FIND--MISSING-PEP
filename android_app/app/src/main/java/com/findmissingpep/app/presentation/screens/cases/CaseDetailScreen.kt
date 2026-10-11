package com.findmissingpep.app.presentation.screens.cases

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.findmissingpep.app.BuildConfig
import com.findmissingpep.app.domain.model.CaseStatus
import com.findmissingpep.app.domain.model.MissingCase
import com.findmissingpep.app.domain.model.Sighting
import com.findmissingpep.app.presentation.common.GlassCard
import com.findmissingpep.app.presentation.common.SimilarityGauge
import com.findmissingpep.app.presentation.common.StatusBadge
import com.findmissingpep.app.presentation.theme.*
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    caseId: String,
    onNavigateToTimeline: () -> Unit,
    onNavigateToSighting: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CasesViewModel = hiltViewModel()
) {
    val uiState by viewModel.detailState.collectAsState()
    val context = LocalContext.current
    var showCloseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(caseId) {
        viewModel.loadCaseDetail(caseId)
    }

    // Photo picker for uploading additional reference photos
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val tempFile = File.createTempFile("add_photo_", ".jpg", context.cacheDir)
                val outputStream = FileOutputStream(tempFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                viewModel.uploadAdditionalPhoto(caseId, tempFile)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.case?.fullName ?: "Case Details",
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
                    if (uiState.case != null && uiState.case?.status != CaseStatus.CLOSED && uiState.case?.status != CaseStatus.FOUND) {
                        IconButton(onClick = { showCloseDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = "Close Case",
                                tint = EmeraldPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.case == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyanPrimary)
            }
        } else if (uiState.case != null) {
            val caseItem = uiState.case!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Success / Error message
                uiState.successMessage?.let {
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

                // Status Stepper
                CaseStatusStepper(status = caseItem.status)

                // Photos Section (Side-by-side Enrolled vs Face Crop)
                PhotosSection(
                    case = caseItem,
                    isUploading = uiState.isUploadingPhoto,
                    onAddPhoto = { photoPickerLauncher.launch("image/*") }
                )

                // Demographics & Last Seen
                DemographicsCard(case = caseItem)

                // FIR Legal Record
                FirLegalCard(case = caseItem)

                // Timeline / Trajectory CTA
                TrajectoryActionCard(
                    sightingsCount = uiState.sightings.size,
                    onNavigateToTimeline = onNavigateToTimeline
                )

                // Associated CCTV Sightings
                if (uiState.sightings.isNotEmpty()) {
                    Text(
                        text = "Associated CCTV Sightings (${uiState.sightings.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    uiState.sightings.forEach { sighting ->
                        SightingMiniCard(
                            sighting = sighting,
                            onClick = { onNavigateToSighting(sighting.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Confirmation Dialog for closing case
    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text("Close Missing Case", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to resolve or close this case? This will deactivate live ArcFace CCTV scanning across the network.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseDialog = false
                        viewModel.closeCase(caseId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Confirm Closure", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

// ─── Status Stepper ─────────────────────────────────────────────────────────

@Composable
private fun CaseStatusStepper(status: CaseStatus) {
    val steps = listOf("Submitted", "Face Extracted", "FIR Verified", "CCTV Search", "Resolved")
    val activeIndex = when (status) {
        CaseStatus.PROCESSING -> 1
        CaseStatus.PENDING_FIR_REVIEW -> 1
        CaseStatus.ACTIVE -> 3
        CaseStatus.FOUND, CaseStatus.CLOSED -> 4
        CaseStatus.FIR_REJECTED, CaseStatus.REJECTED_NO_FACE -> 1
    }

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Investigation Status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            StatusBadge(status = status)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, title ->
                val isDone = index <= activeIndex
                val isCurrent = index == activeIndex

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isDone -> EmeraldPrimary
                                    else -> Slate800
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        } else {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Slate600))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = if (isCurrent) CyanPrimary else if (isDone) TextPrimary else TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ─── Photos Section ─────────────────────────────────────────────────────────

@Composable
private fun PhotosSection(
    case: MissingCase,
    isUploading: Boolean,
    onAddPhoto: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Biometric Reference Photos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary
            )
            if (case.photos.size < 5) {
                TextButton(onClick = onAddPhoto, enabled = !isUploading) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = CyanPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Add Photo", color = CyanPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (case.photos.isEmpty()) {
            Text("No reference photos attached", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(case.photos) { photo ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val photoUrl = if (photo.faceCropPath != null) {
                            if (photo.faceCropPath.startsWith("http")) photo.faceCropPath else "${BuildConfig.API_BASE_URL}${photo.faceCropPath}"
                        } else {
                            if (photo.originalPath.startsWith("http")) photo.originalPath else "${BuildConfig.API_BASE_URL}${photo.originalPath}"
                        }

                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, if (photo.isPrimary) EmeraldPrimary else GlassBorder, RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Face",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (photo.faceCropPath != null) "112x112 Crop" else "Original",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = if (photo.faceCropPath != null) CyanPrimary else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ─── Demographics Card ──────────────────────────────────────────────────────

@Composable
private fun DemographicsCard(case: MissingCase) {
    GlassCard {
        Text(
            text = "Person Demographics & Details",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CyanPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        DetailRow(label = "Full Name", value = case.fullName)
        case.age?.let { DetailRow(label = "Age", value = "$it years") }
        case.gender?.let { DetailRow(label = "Gender", value = it) }
        case.heightCm?.let { DetailRow(label = "Height", value = "$it cm") }
        case.lastSeenLocation?.let { DetailRow(label = "Last Seen Area", value = it) }
        case.lastSeenTime?.let { DetailRow(label = "Last Seen Time", value = it) }
        case.contactInfo?.let { DetailRow(label = "Family Contact", value = it) }
        case.description?.let { DetailRow(label = "Physical Description", value = it) }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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

// ─── FIR Legal Record Card ──────────────────────────────────────────────────

@Composable
private fun FirLegalCard(case: MissingCase) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Police FIR Verification",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary
            )
            case.firStatus?.let {
                Surface(
                    color = if (it == "VERIFIED") EmeraldPrimary.copy(alpha = 0.2f) else AmberPrimary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (it == "VERIFIED") EmeraldPrimary else AmberPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (case.firNumber != null) {
            DetailRow(label = "FIR Number", value = case.firNumber)
            case.firPoliceStation?.let { DetailRow(label = "Police Station", value = it) }
            case.firDate?.let { DetailRow(label = "Date of Filing", value = it) }
            case.firRejectionReason?.let { DetailRow(label = "Rejection Notes", value = it) }
        } else {
            Text(
                text = "No FIR registered. Cases with official FIRs receive priority CCTV scanning.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

// ─── Trajectory Action Card ─────────────────────────────────────────────────

@Composable
private fun TrajectoryActionCard(
    sightingsCount: Int,
    onNavigateToTimeline: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToTimeline() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CyanPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Interactive Sighting Trajectory",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (sightingsCount > 0) "$sightingsCount verified chronological location points" else "View CCTV map coverage area",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = CyanPrimary
            )
        }
    }
}

// ─── Sighting Mini Card ─────────────────────────────────────────────────────

@Composable
private fun SightingMiniCard(
    sighting: Sighting,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SimilarityGauge(score = sighting.similarityScore, size = 48)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sighting.cameraLocation ?: "CCTV Node ${sighting.cameraId}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Detected at ${sighting.detectedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Slate500
            )
        }
    }
}
