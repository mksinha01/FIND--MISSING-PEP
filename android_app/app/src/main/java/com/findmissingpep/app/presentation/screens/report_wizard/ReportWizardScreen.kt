package com.findmissingpep.app.presentation.screens.report_wizard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.findmissingpep.app.R
import com.findmissingpep.app.presentation.common.PaperCard
import com.findmissingpep.app.presentation.theme.*
import com.findmissingpep.app.data.util.ImageCompressor
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportWizardScreen(
    onReportSubmitted: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ReportWizardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Trigger navigation on success
    LaunchedEffect(uiState.submittedCaseId) {
        uiState.submittedCaseId?.let { caseId ->
            onReportSubmitted(caseId)
        }
    }

    var isProcessingPhotos by remember { mutableStateOf(false) }

    val processUris: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isProcessingPhotos = true
                try {
                    val files = uris.map { uri ->
                        ImageCompressor.compress(context, uri)
                    }
                    if (files.isNotEmpty()) {
                        viewModel.addPhotos(files, uris.take(files.size))
                    }
                } catch (e: Exception) {
                    val fallbackFiles = uris.mapNotNull { uri ->
                        try {
                            val temp = File.createTempFile("photo_raw_", ".jpg", context.cacheDir)
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(temp).use { output -> input.copyTo(output) }
                            }
                            temp
                        } catch (_: Exception) { null }
                    }
                    if (fallbackFiles.isNotEmpty()) {
                        viewModel.addPhotos(fallbackFiles, uris.take(fallbackFiles.size))
                    }
                } finally {
                    isProcessingPhotos = false
                }
            }
        }
    }

    // Modern Visual Photo Picker (standard gallery grid)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris: List<Uri> ->
        processUris(uris)
    }

    // Fallback file/gallery picker
    val legacyPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        processUris(uris)
    }

    val onPickPhotos: () -> Unit = {
        try {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: Exception) {
            legacyPhotoPickerLauncher.launch("image/*")
        }
    }

    // FIR Document picker launcher
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val tempFile = File.createTempFile("fir_doc_", ".pdf", context.cacheDir)
                val outputStream = FileOutputStream(tempFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                viewModel.setFirDocument(tempFile, it)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    Scaffold(
        containerColor = CreamBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.report_missing_person),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = InkText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = InkText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CreamBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Step Progress Indicator
            StepProgressHeader(
                currentStep = uiState.currentStep,
                steps = listOf("Demographics", "FIR Legal", "Photos")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error display
            AnimatedVisibility(visible = uiState.error != null) {
                uiState.error?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RedDanger.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RedDanger.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RedDanger)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = err, color = RedDanger, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = RedDanger)
                            }
                        }
                    }
                }
            }

            // Step Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (uiState.currentStep) {
                    0 -> Step1Demographics(
                        state = uiState,
                        viewModel = viewModel
                    )
                    1 -> Step2FirDetails(
                        state = uiState,
                        viewModel = viewModel,
                        onAttachDocument = { docPickerLauncher.launch("*/*") }
                    )
                    2 -> Step3Photos(
                        state = uiState,
                        viewModel = viewModel,
                        isProcessingPhotos = isProcessingPhotos,
                        onAddPhotos = onPickPhotos
                    )
                }
            }

            // Bottom Navigation Controls
            StepNavigationButtons(
                currentStep = uiState.currentStep,
                isSubmitting = uiState.isSubmitting,
                canProceed = when (uiState.currentStep) {
                    0 -> uiState.fullName.isNotBlank()
                    1 -> true // FIR can be optional or validated
                    2 -> uiState.photoFiles.isNotEmpty()
                    else -> false
                },
                onPrev = { viewModel.previousStep() },
                onNext = { viewModel.nextStep() },
                onSubmit = { viewModel.submitReport() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─── Step Progress Header ───────────────────────────────────────────────────

@Composable
private fun StepProgressHeader(
    currentStep: Int,
    steps: List<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, title ->
            val isActive = index == currentStep
            val isCompleted = index < currentStep

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> GreenSuccess
                                isActive -> BluePrimary
                                else -> PaperWhite
                            }
                        )
                        .border(
                            width = 1.5.dp,
                            color = when {
                                isCompleted -> GreenSuccess
                                isActive -> BluePrimary
                                else -> LineBorder
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color.White else InkMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) BluePrimary else InkMuted,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(2.dp)
                        .background(if (index < currentStep) GreenSuccess else LineBorder)
                )
            }
        }
    }
}

// ─── Common Text Field Colors Helper ────────────────────────────────────────

@Composable
private fun wizardFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BluePrimary,
    unfocusedBorderColor = LineBorder,
    focusedTextColor = InkText,
    unfocusedTextColor = InkText,
    cursorColor = BluePrimary,
    focusedLabelColor = BluePrimary,
    unfocusedLabelColor = InkMuted,
    focusedContainerColor = PaperWhite,
    unfocusedContainerColor = PaperWhite
)

// ─── Step 1: Demographics ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step1Demographics(
    state: ReportWizardState,
    viewModel: ReportWizardViewModel
) {
    var genderExpanded by remember { mutableStateOf(false) }
    val genderOptions = listOf("Male", "Female", "Other", "Unknown")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PaperCard {
            Text(
                text = "Missing Person Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = InkText
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Full Name
            OutlinedTextField(
                value = state.fullName,
                onValueChange = { viewModel.updateFullName(it) },
                label = { Text("Full Legal Name *") },
                placeholder = { Text("Enter official name", color = InkMuted.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = wizardFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Age Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Age: ${state.age} years", color = InkText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Slider(
                value = state.age.toFloat(),
                onValueChange = { viewModel.updateAge(it.toInt()) },
                valueRange = 1f..100f,
                steps = 99,
                colors = SliderDefaults.colors(
                    thumbColor = BluePrimary,
                    activeTrackColor = BluePrimary,
                    inactiveTrackColor = LineBorder
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Gender Dropdown
            ExposedDropdownMenuBox(
                expanded = genderExpanded,
                onExpandedChange = { genderExpanded = it }
            ) {
                OutlinedTextField(
                    value = state.gender.ifBlank { "Select Gender" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Gender") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = wizardFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = genderExpanded,
                    onDismissRequest = { genderExpanded = false },
                    modifier = Modifier.background(PaperWhite)
                ) {
                    genderOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = InkText) },
                            onClick = {
                                viewModel.updateGender(option)
                                genderExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Height Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Approximate Height: ${state.heightCm} cm", color = InkText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Slider(
                value = state.heightCm.toFloat(),
                onValueChange = { viewModel.updateHeightCm(it.toInt()) },
                valueRange = 60f..220f,
                steps = 160,
                colors = SliderDefaults.colors(
                    thumbColor = BluePrimary,
                    activeTrackColor = BluePrimary,
                    inactiveTrackColor = LineBorder
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Physical Description
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Physical Description & Distinct Features") },
                placeholder = { Text("e.g. Blue denim shirt, black shoes, birthmark on neck", color = InkMuted.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                colors = wizardFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        PaperCard {
            Text(
                text = "Last Known Circumstances",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = InkText
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Last Seen Location
            OutlinedTextField(
                value = state.lastSeenLocation,
                onValueChange = { viewModel.updateLastSeenLocation(it) },
                label = { Text("Last Seen Location") },
                placeholder = { Text("e.g. Saket Metro Station, Gate 2", color = InkMuted.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = BluePrimary) },
                colors = wizardFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Last Seen Time
            OutlinedTextField(
                value = state.lastSeenTime,
                onValueChange = { viewModel.updateLastSeenTime(it) },
                label = { Text("Last Seen Date & Time") },
                placeholder = { Text("e.g. 2026-10-10 14:30 IST", color = InkMuted.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = BluePrimary) },
                colors = wizardFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Family Contact Info
            OutlinedTextField(
                value = state.contactInfo,
                onValueChange = { viewModel.updateContactInfo(it) },
                label = { Text("Emergency Contact Phone") },
                placeholder = { Text("+91 98765 43210", color = InkMuted.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = BluePrimary) },
                colors = wizardFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

// ─── Step 2: FIR Verification & Legal Details ───────────────────────────────

@Composable
private fun Step2FirDetails(
    state: ReportWizardState,
    viewModel: ReportWizardViewModel,
    onAttachDocument: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PaperCard {
            Text(
                text = "Police FIR Verification",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = InkText
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Attaching an official First Information Report (FIR) verifies case legality and expedites official response.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
            Spacer(modifier = Modifier.height(14.dp))

            // FIR Number Input
            OutlinedTextField(
                value = state.firNumber,
                onValueChange = { viewModel.updateFirNumber(it) },
                label = { Text("FIR Number (e.g. FIR/2026/1234)") },
                placeholder = { Text("FIR/YYYY/XXXX", color = InkMuted.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (state.isValidatingFir) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = BluePrimary,
                            strokeWidth = 2.dp
                        )
                    } else if (state.firValidation != null) {
                        if (state.firValidation.isValid) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Valid", tint = GreenSuccess)
                        } else {
                            Icon(Icons.Default.Error, contentDescription = "Invalid", tint = RedDanger)
                        }
                    }
                },
                colors = wizardFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            // Dynamic Validation message
            state.firValidation?.let { validation ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = validation.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (validation.isValid) GreenSuccess else RedDanger,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Police Station
            OutlinedTextField(
                value = state.firPoliceStation,
                onValueChange = { viewModel.updateFirPoliceStation(it) },
                label = { Text("Police Station Name") },
                placeholder = { Text("e.g. Saket Police Station, New Delhi", color = InkMuted.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = BluePrimary) },
                colors = wizardFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // FIR Date
            OutlinedTextField(
                value = state.firDate,
                onValueChange = { viewModel.updateFirDate(it) },
                label = { Text("Date of Filing FIR") },
                placeholder = { Text("YYYY-MM-DD", color = InkMuted.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = BluePrimary) },
                colors = wizardFieldColors(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        PaperCard {
            Text(
                text = "Scanned FIR Document",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = InkText
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Attach a PDF or clear photograph of the stamped police FIR.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (state.firDocument != null) {
                Surface(
                    color = CreamBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = BluePrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.firDocument.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkText,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${state.firDocument.length() / 1024} KB attached",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted
                            )
                        }
                        IconButton(onClick = { viewModel.setFirDocument(null, null) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = RedDanger)
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onAttachDocument,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BluePrimary),
                    border = BorderStroke(1.dp, BluePrimary.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Attach Stamped FIR File")
                }
            }
        }
    }
}

// ─── Step 3: Reference Photos Intake ────────────────────────────────────────

@Composable
private fun Step3Photos(
    state: ReportWizardState,
    viewModel: ReportWizardViewModel,
    isProcessingPhotos: Boolean = false,
    onAddPhotos: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PaperCard {
            Text(
                text = "Reference Photos (1 to 5 Photos)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = InkText
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Provide clear, front-facing photographs of the missing individual to help search teams identify them accurately.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Photo Grid / Picker
            if (isProcessingPhotos) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CreamBackground)
                        .border(1.5.dp, LineBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BluePrimary, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Processing photos...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText
                        )
                    }
                }
            } else if (state.photoFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CreamBackground)
                        .border(1.5.dp, LineBorder, RoundedCornerShape(14.dp))
                        .clickable { onAddPhotos() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap to Select Photos (Max 5)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = InkText
                        )
                        Text(
                            text = "JPEG / PNG format • Clear portraits recommended",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    itemsIndexed(state.photoFiles) { index, file ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.5.dp, if (index == 0) BluePrimary else LineBorder, RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Photo $index",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            if (index == 0) {
                                Surface(
                                    color = BlueDeep,
                                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "Primary",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Remove button
                            IconButton(
                                onClick = { viewModel.removePhoto(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (state.photoFiles.size < 5) {
                        item {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CreamBackground)
                                    .border(1.dp, LineBorder, RoundedCornerShape(10.dp))
                                    .clickable { onAddPhotos() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add More",
                                    tint = BluePrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Summary Card
        PaperCard {
            Text(
                text = "Registration Checklist",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = InkText
            )
            Spacer(modifier = Modifier.height(8.dp))
            ChecklistItem(
                label = "Demographics completed",
                isDone = state.fullName.isNotBlank()
            )
            ChecklistItem(
                label = if (state.firValidation?.isValid == true) "Official FIR verified" else "FIR reference added",
                isDone = state.firNumber.isNotBlank()
            )
            ChecklistItem(
                label = "${state.photoFiles.size} of 5 reference photos ready",
                isDone = state.photoFiles.isNotEmpty()
            )
        }
    }
}

@Composable
private fun ChecklistItem(label: String, isDone: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Icon(
            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isDone) GreenSuccess else LineBorder,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isDone) InkText else InkMuted
        )
    }
}

// ─── Step Navigation Buttons ────────────────────────────────────────────────

@Composable
private fun StepNavigationButtons(
    currentStep: Int,
    isSubmitting: Boolean,
    canProceed: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (currentStep > 0) {
            OutlinedButton(
                onClick = onPrev,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, LineBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = InkText)
            ) {
                Text("Previous")
            }
        }

        if (currentStep < 2) {
            Button(
                onClick = onNext,
                enabled = canProceed,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BluePrimary,
                    contentColor = Color.White,
                    disabledContainerColor = LineBorder,
                    disabledContentColor = InkMuted
                )
            ) {
                Text("Next Step", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onSubmit,
                enabled = canProceed && !isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenSuccess,
                    contentColor = Color.White,
                    disabledContainerColor = LineBorder,
                    disabledContentColor = InkMuted
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit Report", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
