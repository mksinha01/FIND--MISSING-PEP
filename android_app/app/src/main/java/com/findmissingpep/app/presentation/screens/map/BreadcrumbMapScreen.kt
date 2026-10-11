package com.findmissingpep.app.presentation.screens.map

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.findmissingpep.app.domain.model.TimelineEntry
import com.findmissingpep.app.presentation.common.GlassCard
import com.findmissingpep.app.presentation.common.SimilarityGauge
import com.findmissingpep.app.presentation.theme.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreadcrumbMapScreen(
    caseId: String,
    onNavigateBack: () -> Unit,
    viewModel: BreadcrumbMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(caseId) {
        viewModel.loadTimeline(caseId)
    }

    // Default to Delhi central coordinates
    val defaultPos = LatLng(28.5245, 77.2066)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPos, 13f)
    }

    // Animate camera to the latest waypoint when loaded
    LaunchedEffect(uiState.timeline) {
        uiState.timeline?.entries?.lastOrNull()?.let { lastEntry ->
            if (lastEntry.latitude != null && lastEntry.longitude != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(lastEntry.latitude, lastEntry.longitude),
                        14f
                    )
                )
            }
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.timeline?.personName ?: "Movement Trajectory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${uiState.timeline?.entries?.size ?: 0} CCTV Breadcrumbs Recorded",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary
                        )
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900.copy(alpha = 0.85f))
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val validPoints = uiState.timeline?.entries?.filter { it.latitude != null && it.longitude != null } ?: emptyList()
            val polylinePoints = validPoints.map { LatLng(it.latitude!!, it.longitude!!) }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = true,
                    myLocationButtonEnabled = false
                ),
                properties = MapProperties(
                    isBuildingEnabled = true
                )
            ) {
                // Directional polyline connecting CCTV camera locations
                if (polylinePoints.size > 1) {
                    Polyline(
                        points = polylinePoints,
                        color = CyanPrimary,
                        width = 8f
                    )
                }

                // Numbered Waypoint Markers
                validPoints.forEachIndexed { index, entry ->
                    val pos = LatLng(entry.latitude!!, entry.longitude!!)
                    val isSelected = uiState.selectedEntry?.sightingId == entry.sightingId
                    val isLatest = index == validPoints.size - 1

                    Marker(
                        state = rememberMarkerState(position = pos),
                        title = "${index + 1}. ${entry.cameraName}",
                        snippet = "${entry.detectedAt} • ${(entry.similarityScore * 100).toInt()}% match",
                        onClick = {
                            viewModel.selectEntry(entry)
                            false
                        }
                    )
                }
            }

            // Top Status Overlay
            Surface(
                color = Slate900.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chronological CCTV Path",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Bottom Selected Waypoint Card
            uiState.selectedEntry?.let { selected ->
                SelectedWaypointCard(
                    entry = selected,
                    onNavigateToCamera = {
                        if (selected.latitude != null && selected.longitude != null) {
                            val uri = Uri.parse("google.navigation:q=${selected.latitude},${selected.longitude}")
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                // Fallback browser maps
                                val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${selected.latitude},${selected.longitude}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectedWaypointCard(
    entry: TimelineEntry,
    onNavigateToCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SimilarityGauge(score = entry.similarityScore, size = 48)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.cameraName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                entry.cameraLocation?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanPrimary
                    )
                }
                Text(
                    text = "Detected at ${entry.detectedAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateToCamera,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = Color.Black
            )
        ) {
            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Navigate to Camera Location", fontWeight = FontWeight.Bold)
        }
    }
}
