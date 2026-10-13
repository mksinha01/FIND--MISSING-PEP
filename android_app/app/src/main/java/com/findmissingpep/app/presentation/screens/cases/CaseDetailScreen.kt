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
import com.findmissingpep.app.presentation.common.PaperCard
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
                            text = uiState.case?.fullName ?: "Case details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkText,
                            maxLines = 1
                        )
                        Text(
                            text = "Investigation record",
                            fontSize = 11.sp,
                            color = InkMuted
                        )
                    }
                    if (uiState.case != null && uiState.case?.status != CaseStatus.CLOSED && uiState.case?.status != CaseStatus.FOUND) {
                        TextButton(onClick = { showCloseDialog = true }) {
                            Text("Close case", color = BluePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.case == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BluePrimary)
            }
        } else if (uiState.case != null) {
            val caseItem = uiState.case!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Success Message Banner
                uiState.successMessage?.let {
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

                // ── Case Hero Card ──
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = caseItem.fullName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkText
                        )
                        StatusBadge(status = caseItem.status)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        caseItem.age?.let {
                            Text(text = "$it years old", fontSize = 12.sp, color = InkMuted)
                        }
                        caseItem.gender?.let {
                            Text(text = "• $it", fontSize = 12.sp, color = InkMuted)
                        }
                        caseItem.firNumber?.let {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BlueWash
                            ) {
                                Text(
                                    text = "FIR: $it",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BlueDeep,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    caseItem.lastSeenLocation?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = it, fontSize = 11.sp, color = InkMuted)
                        }
                    }
                }

                // ── Status Stepper ──
                CaseStatusStepper(status = caseItem.status)

                // ── Reference Photos Section ──
                PhotosSection(
                    case = caseItem,
                    isUploading = uiState.isUploadingPhoto,
                    onAddPhoto = { photoPickerLauncher.launch("image/*") }
                )

                // ── Demographics & Last Seen Details ──
                DemographicsCard(case = caseItem)

                // ── Police FIR Record ──
                FirLegalCard(case = caseItem)

                // ── Sighting Map Action Card ──
                TrajectoryActionCard(
                    sightingsCount = uiState.sightings.size,
                    onNavigateToTimeline = onNavigateToTimeline
                )

                // ── Associated CCTV Sightings ──
                if (uiState.sightings.isNotEmpty()) {
                    Text(
                        text = "Associated CCTV sightings (${uiState.sightings.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = InkText
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
            title = { Text("Close missing case", color = InkText, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "Are you sure you want to resolve or close this case? This marks the case as officially concluded in the registry.",
                    color = InkMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseDialog = false
                        viewModel.closeCase(caseId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm closure", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = PaperWhite,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

// ─── Status Stepper ─────────────────────────────────────────────────────────

@Composable
private fun CaseStatusStepper(status: CaseStatus) {
    val steps = listOf("Report Submitted", "Photos Verified", "FIR Approved", "Active Search", "Resolved")
    val activeIndex = when (status) {
        CaseStatus.PROCESSING -> 1
        CaseStatus.PENDING_FIR_REVIEW -> 1
        CaseStatus.ACTIVE -> 3
        CaseStatus.FOUND, CaseStatus.CLOSED -> 4
        CaseStatus.FIR_REJECTED, CaseStatus.REJECTED_NO_FACE -> 1
    }

    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Investigation progress",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = InkText
            )
            StatusBadge(status = status)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
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
                            .background(if (isDone) GreenSuccess else PaperSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        } else {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(InkSubtle))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        fontSize = 8.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) BluePrimary else if (isDone) InkText else InkSubtle,
                        maxLines = 2,
                        lineHeight = 10.sp
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
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Reference photos",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = InkText
            )
            if (case.photos.size < 5) {
                TextButton(onClick = onAddPhoto, enabled = !isUploading) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = BluePrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(15.dp), tint = BluePrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add photo", color = BluePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (case.photos.isEmpty()) {
            Text("No reference photos attached", color = InkMuted, fontSize = 12.sp)
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                .size(84.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, if (photo.isPrimary) BluePrimary else LineBorder, RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Reference photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (photo.faceCropPath != null) "Verified crop" else "Original",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (photo.faceCropPath != null) BluePrimary else InkMuted
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
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Person details & description",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = InkText
        )
        Spacer(modifier = Modifier.height(8.dp))

        DetailRow(label = "Full Name", value = case.fullName)
        case.age?.let { DetailRow(label = "Age", value = "$it years") }
        case.gender?.let { DetailRow(label = "Gender", value = it) }
        case.heightCm?.let { DetailRow(label = "Height", value = "$it cm") }
        case.lastSeenLocation?.let { DetailRow(label = "Last Seen Area", value = it) }
        case.lastSeenTime?.let { DetailRow(label = "Last Seen Time", value = it) }
        case.contactInfo?.let { DetailRow(label = "Contact Info", value = it) }
        case.description?.let { DetailRow(label = "Physical Description", value = it) }
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
        Text(
            text = value,
            fontSize = 11.sp,
            color = InkText,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── FIR Legal Record Card ──────────────────────────────────────────────────

@Composable
private fun FirLegalCard(case: MissingCase) {
    PaperCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Police FIR record",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = InkText
            )
            case.firStatus?.let {
                Surface(
                    color = if (it == "VERIFIED") GreenSuccessBg else AmberAlertBg,
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Text(
                        text = if (it == "VERIFIED") "Verified" else it,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (it == "VERIFIED") GreenSuccess else AmberAlert,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (case.firNumber != null) {
            DetailRow(label = "FIR Number", value = case.firNumber)
            case.firPoliceStation?.let { DetailRow(label = "Police Station", value = it) }
            case.firDate?.let { DetailRow(label = "Date Filed", value = it) }
            case.firRejectionReason?.let { DetailRow(label = "Review Notes", value = it) }
        } else {
            Text(
                text = "No FIR registered. Cases with registered FIRs receive priority support.",
                fontSize = 11.sp,
                color = InkMuted
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
    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onNavigateToTimeline
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(BlueWash),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sighting location map",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )
                Text(
                    text = if (sightingsCount > 0) "$sightingsCount verified chronological sighting(s)" else "View camera observation map",
                    fontSize = 11.sp,
                    color = InkMuted
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = InkSubtle,
                modifier = Modifier.size(18.dp)
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
    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SimilarityGauge(score = sighting.similarityScore, size = 44)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sighting.cameraLocation ?: "CCTV Node ${sighting.cameraId}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )
                Text(
                    text = "Observed at ${sighting.detectedAt}",
                    fontSize = 11.sp,
                    color = InkMuted
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = InkSubtle,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
