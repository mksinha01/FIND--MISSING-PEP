package com.findmissingpep.app.presentation.screens.fir

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.findmissingpep.app.BuildConfig
import com.findmissingpep.app.domain.model.MissingCase
import com.findmissingpep.app.presentation.common.PaperCard
import com.findmissingpep.app.presentation.common.StatusBadge
import com.findmissingpep.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirQueueScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCaseDetail: (String) -> Unit,
    viewModel: FirQueueViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedCaseForApprove by remember { mutableStateOf<MissingCase?>(null) }
    var selectedCaseForReject by remember { mutableStateOf<MissingCase?>(null) }
    var notesText by remember { mutableStateOf("") }

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "FIR verification queue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = InkText
                            )
                            if (uiState.pendingCases.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = AmberAlertBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${uiState.pendingCases.size} pending",
                                        fontSize = 10.sp,
                                        color = AmberAlert,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Officer document review",
                            fontSize = 11.sp,
                            color = InkMuted
                        )
                    }
                    IconButton(onClick = { viewModel.refreshQueue() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = BluePrimary
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 18.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Heading block
            Text(
                text = "Review submitted FIRs",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = InkText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Verify police station documents to activate search priority.",
                fontSize = 12.sp,
                color = InkMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Feedback Banners
            uiState.successMessage?.let { msg ->
                Surface(
                    color = GreenSuccessBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = msg,
                        color = GreenSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            uiState.error?.let { err ->
                Surface(
                    color = RedDangerBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = err,
                        color = RedDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Queue Content
            if (uiState.isLoading && uiState.pendingCases.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BluePrimary)
                }
            } else if (uiState.pendingCases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(GreenSuccessBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = GreenSuccess,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Queue is clear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All submitted FIRs have been verified.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(uiState.pendingCases, key = { it.id }) { missingCase ->
                        FirQueueCardItem(
                            missingCase = missingCase,
                            onCardClick = { onNavigateToCaseDetail(missingCase.id) },
                            onApprove = { selectedCaseForApprove = missingCase },
                            onReject = { selectedCaseForReject = missingCase }
                        )
                    }
                }
            }
        }
    }

    // Approve Dialog
    selectedCaseForApprove?.let { c ->
        AlertDialog(
            onDismissRequest = { selectedCaseForApprove = null },
            title = { Text("Approve FIR verification", color = InkText, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "Approving FIR '${c.firNumber}' for ${c.fullName} verifies the official police report and activates search priority.",
                        color = InkMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Verification notes (optional)") },
                        placeholder = { Text("e.g. Confirmed with ${c.firPoliceStation ?: "Station"}", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = humanTextFieldColors(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val caseId = c.id
                        selectedCaseForApprove = null
                        viewModel.verifyFir(caseId, notesText)
                        notesText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Approve FIR", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCaseForApprove = null }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = PaperWhite,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Reject Dialog
    selectedCaseForReject?.let { c ->
        AlertDialog(
            onDismissRequest = { selectedCaseForReject = null },
            title = { Text("Reject FIR submission", color = InkText, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "Please provide a reason for the citizen reporter so they can submit a valid FIR copy.",
                        color = InkMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Rejection reason *") },
                        placeholder = { Text("e.g. Unreadable document or invalid FIR number", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = humanTextFieldColors(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val caseId = c.id
                        selectedCaseForReject = null
                        viewModel.rejectFir(caseId, notesText)
                        notesText = ""
                    },
                    enabled = notesText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = RedDanger, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reject submission", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCaseForReject = null }) {
                    Text("Cancel", color = InkMuted)
                }
            },
            containerColor = PaperWhite,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun FirQueueCardItem(
    missingCase: MissingCase,
    onCardClick: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val primaryPhoto = missingCase.photos.firstOrNull { it.isPrimary } ?: missingCase.photos.firstOrNull()
    val photoUrl = primaryPhoto?.faceCropPath ?: primaryPhoto?.originalPath

    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onCardClick,
        backgroundColor = PaperWhite
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Photo thumbnail
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, LineBorderSubtle, RoundedCornerShape(10.dp))
                    .background(BlueWash),
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl != null) {
                    val fullUrl = if (photoUrl.startsWith("http")) photoUrl else "${BuildConfig.API_BASE_URL}$photoUrl"
                    AsyncImage(
                        model = fullUrl,
                        contentDescription = missingCase.fullName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = missingCase.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = InkText
                    )
                    StatusBadge(status = missingCase.status)
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "FIR Number: ${missingCase.firNumber ?: "Pending"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BlueDeep
                )

                missingCase.firPoliceStation?.let {
                    Text(
                        text = "Station: $it",
                        fontSize = 11.sp,
                        color = InkMuted
                    )
                }

                missingCase.lastSeenLocation?.let {
                    Text(
                        text = "Last seen: $it",
                        fontSize = 10.sp,
                        color = InkSubtle
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onApprove,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenSuccess,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Approve FIR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = PaperWhite,
                    contentColor = RedDanger
                ),
                border = BorderStroke(1.dp, RedDanger.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = RedDanger)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reject", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedDanger)
            }

            OutlinedButton(
                onClick = onCardClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = PaperWhite,
                    contentColor = BlueDeep
                ),
                border = BorderStroke(1.dp, LineBorder),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text("Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
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
