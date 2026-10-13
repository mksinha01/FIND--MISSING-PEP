package com.findmissingpep.app.presentation.screens.cases

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.findmissingpep.app.domain.model.CaseStatus
import com.findmissingpep.app.domain.model.MissingCase
import com.findmissingpep.app.presentation.common.PaperCard
import com.findmissingpep.app.presentation.common.StatusBadge
import com.findmissingpep.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCasesScreen(
    onNavigateToCaseDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CasesViewModel = hiltViewModel()
) {
    val uiState by viewModel.listState.collectAsState()

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
                            text = "My cases",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = InkText
                        )
                        Text(
                            text = "Reported cases",
                            fontSize = 11.sp,
                            color = InkMuted
                        )
                    }
                    IconButton(onClick = { viewModel.refreshCases() }) {
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
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.refreshCases() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                // Heading Block
                Text(
                    text = "Your cases",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Review reports and their current status.",
                    fontSize = 12.sp,
                    color = InkMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status Filter Chips
                Text(
                    text = "Filter by status",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkText
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isAllSelected = uiState.selectedStatusFilter == null
                    Surface(
                        onClick = { viewModel.setStatusFilter(null) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAllSelected) BluePrimary else PaperWhite,
                        border = BorderStroke(1.dp, if (isAllSelected) BluePrimary else LineBorder)
                    ) {
                        Text(
                            text = "All cases",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAllSelected) Color.White else InkText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }

                    CaseStatus.entries.forEach { status ->
                        val isSelected = uiState.selectedStatusFilter == status
                        val label = when (status) {
                            CaseStatus.PROCESSING -> "Processing"
                            CaseStatus.PENDING_FIR_REVIEW -> "Pending FIR"
                            CaseStatus.ACTIVE -> "Active search"
                            CaseStatus.FOUND -> "Found"
                            CaseStatus.CLOSED -> "Closed"
                            CaseStatus.FIR_REJECTED -> "FIR rejected"
                            CaseStatus.REJECTED_NO_FACE -> "Photo unclear"
                        }
                        Surface(
                            onClick = { viewModel.setStatusFilter(status) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) BluePrimary else PaperWhite,
                            border = BorderStroke(1.dp, if (isSelected) BluePrimary else LineBorder)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else InkText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Loading / Empty / Cases List
                if (uiState.isLoading && uiState.cases.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BluePrimary)
                    }
                } else if (uiState.cases.isEmpty()) {
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
                                    .background(PaperSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = InkSubtle,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No cases found in this category",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = InkMuted
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(uiState.cases, key = { it.id }) { missingCase ->
                            CaseCardItem(
                                missingCase = missingCase,
                                onClick = { onNavigateToCaseDetail(missingCase.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaseCardItem(
    missingCase: MissingCase,
    onClick: () -> Unit
) {
    val primaryPhoto = missingCase.photos.firstOrNull { it.isPrimary } ?: missingCase.photos.firstOrNull()
    val photoUrl = primaryPhoto?.faceCropPath ?: primaryPhoto?.originalPath

    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        backgroundColor = PaperWhite
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo thumbnail
            Box(
                modifier = Modifier
                    .size(68.dp)
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
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
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
                        color = InkText,
                        maxLines = 1
                    )
                    StatusBadge(status = missingCase.status)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    missingCase.age?.let {
                        Text(
                            text = "$it yrs",
                            fontSize = 11.sp,
                            color = InkMuted
                        )
                    }
                    missingCase.gender?.let {
                        Text(
                            text = "• $it",
                            fontSize = 11.sp,
                            color = InkMuted
                        )
                    }
                    missingCase.firNumber?.let {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BlueWash
                        ) {
                            Text(
                                text = "FIR: $it",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueDeep,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                missingCase.lastSeenLocation?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = it,
                            fontSize = 11.sp,
                            color = InkMuted,
                            maxLines = 1
                        )
                    }
                }

                if (missingCase.sightingsCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${missingCase.sightingsCount} sighting match(es) recorded",
                        fontSize = 10.sp,
                        color = GreenSuccess,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = InkSubtle,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
