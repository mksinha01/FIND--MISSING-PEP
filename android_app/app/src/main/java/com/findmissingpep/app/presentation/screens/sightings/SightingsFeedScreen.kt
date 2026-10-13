package com.findmissingpep.app.presentation.screens.sightings

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
import com.findmissingpep.app.domain.model.Sighting
import com.findmissingpep.app.domain.model.SightingStatus
import com.findmissingpep.app.presentation.common.*
import com.findmissingpep.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SightingsFeedScreen(
    onNavigateToInspector: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SightingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                                text = "Sightings feed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = InkText
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            PulsingDot(color = GreenSuccess)
                        }
                        Text(
                            text = "Surveillance observations",
                            fontSize = 11.sp,
                            color = InkMuted
                        )
                    }
                    IconButton(onClick = { viewModel.refreshSightings() }) {
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
            onRefresh = { viewModel.refreshSightings() },
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
                    text = "Possible sightings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = InkText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Review camera observations and match leads.",
                    fontSize = 12.sp,
                    color = InkMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isAllSelected = uiState.selectedFilter == null
                    Surface(
                        onClick = { viewModel.setFilter(null) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAllSelected) BluePrimary else PaperWhite,
                        border = BorderStroke(1.dp, if (isAllSelected) BluePrimary else LineBorder)
                    ) {
                        Text(
                            text = "All matches",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAllSelected) Color.White else InkText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }

                    val filters = listOf(
                        SightingStatus.PENDING to "Pending review",
                        SightingStatus.CONFIRMED to "Confirmed",
                        SightingStatus.REJECTED to "Rejected · False alarm"
                    )

                    filters.forEach { (status, label) ->
                        val isSelected = uiState.selectedFilter == status
                        Surface(
                            onClick = { viewModel.setFilter(status) },
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

                // Sightings Content
                if (uiState.isLoading && uiState.sightings.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BluePrimary)
                    }
                } else if (uiState.sightings.isEmpty()) {
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PaperSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = InkSubtle,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No camera sightings recorded yet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = InkMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Observations will appear here when detected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSubtle
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(uiState.sightings, key = { it.id }) { sighting ->
                            SightingFeedCard(
                                sighting = sighting,
                                onClick = { onNavigateToInspector(sighting.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SightingFeedCard(
    sighting: Sighting,
    onClick: () -> Unit
) {
    val faceUrl = sighting.faceCropPath?.let {
        if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it"
    }

    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        backgroundColor = PaperWhite
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CCTV Face Crop Thumbnail
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, LineBorderSubtle, RoundedCornerShape(10.dp))
                    .background(BlueWash),
                contentAlignment = Alignment.Center
            ) {
                if (faceUrl != null) {
                    AsyncImage(
                        model = faceUrl,
                        contentDescription = "CCTV observation",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Information Block
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sighting.personName ?: "Identified subject",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = InkText,
                        maxLines = 1
                    )
                    SightingStatusBadge(status = sighting.status)
                }

                Spacer(modifier = Modifier.height(3.dp))

                sighting.cameraLocation?.let {
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

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "Time: ${sighting.detectedAt}",
                    fontSize = 10.sp,
                    color = InkSubtle
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Review sighting evidence →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            SimilarityGauge(score = sighting.similarityScore, size = 42)
        }
    }
}
