package com.findmissingpep.app.presentation.screens.sightings

import androidx.compose.animation.*
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
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Live CCTV Matches",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        PulsingDot(color = EmeraldPrimary)
                    }
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
                    IconButton(onClick = { viewModel.refreshSightings() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = CyanPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Status Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedFilter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("All Matches") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = Slate800,
                        labelColor = TextSecondary
                    )
                )

                FilterChip(
                    selected = uiState.selectedFilter == SightingStatus.PENDING,
                    onClick = { viewModel.setFilter(SightingStatus.PENDING) },
                    label = { Text("Pending Review") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = Slate800,
                        labelColor = TextSecondary
                    )
                )

                FilterChip(
                    selected = uiState.selectedFilter == SightingStatus.CONFIRMED,
                    onClick = { viewModel.setFilter(SightingStatus.CONFIRMED) },
                    label = { Text("Confirmed Matches") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldPrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = Slate800,
                        labelColor = TextSecondary
                    )
                )

                FilterChip(
                    selected = uiState.selectedFilter == SightingStatus.REJECTED,
                    onClick = { viewModel.setFilter(SightingStatus.REJECTED) },
                    label = { Text("Rejected (False Alarm)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RosePrimary,
                        selectedLabelColor = Color.Black,
                        containerColor = Slate800,
                        labelColor = TextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sightings Content
            if (uiState.isLoading && uiState.sightings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CyanPrimary)
                }
            } else if (uiState.sightings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = Slate500,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No CCTV detections recorded yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                        Text(
                            text = "Matching runs automatically across live edge feeds",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
private fun SightingFeedCard(
    sighting: Sighting,
    onClick: () -> Unit
) {
    val faceUrl = sighting.faceCropPath?.let {
        if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it"
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CCTV Face Crop Thumbnail
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .background(Slate800),
                contentAlignment = Alignment.Center
            ) {
                if (faceUrl != null) {
                    AsyncImage(
                        model = faceUrl,
                        contentDescription = "CCTV Face Crop",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Slate600,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Middle Column: Person Name, Camera info, Timestamp
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sighting.personName ?: "Suspect Match",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    SightingStatusBadge(status = sighting.status)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sighting.cameraLocation ?: "Camera ${sighting.cameraId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate300,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sighting.detectedAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                sighting.numFramesMatched?.let { frames ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "● $frames temporal frames verified (ByteTrack)",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = EmeraldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Column: Similarity Gauge
            SimilarityGauge(score = sighting.similarityScore, size = 52)
        }
    }
}
