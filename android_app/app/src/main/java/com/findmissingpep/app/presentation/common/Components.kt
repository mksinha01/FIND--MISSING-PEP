package com.findmissingpep.app.presentation.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findmissingpep.app.domain.model.CaseStatus
import com.findmissingpep.app.domain.model.SightingStatus
import com.findmissingpep.app.presentation.theme.*

// ─── Status Badge ───────────────────────────────────────────────────────────

@Composable
fun StatusBadge(
    status: CaseStatus,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (status) {
        CaseStatus.PROCESSING -> VioletPrimary to "Processing"
        CaseStatus.PENDING_FIR_REVIEW -> AmberPrimary to "Pending FIR"
        CaseStatus.ACTIVE -> EmeraldPrimary to "Active Search"
        CaseStatus.FOUND -> CyanPrimary to "Found"
        CaseStatus.CLOSED -> Slate500 to "Closed"
        CaseStatus.FIR_REJECTED -> RosePrimary to "FIR Rejected"
        CaseStatus.REJECTED_NO_FACE -> RosePrimary to "No Face"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

// ─── Sighting Status Badge ──────────────────────────────────────────────────

@Composable
fun SightingStatusBadge(
    status: SightingStatus,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (status) {
        SightingStatus.PENDING -> AmberPrimary to "Pending Review"
        SightingStatus.CONFIRMED -> EmeraldPrimary to "Confirmed"
        SightingStatus.REJECTED -> RosePrimary to "Rejected"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        contentColor = color
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

// ─── Similarity Score Gauge ─────────────────────────────────────────────────

@Composable
fun SimilarityGauge(
    score: Double,
    modifier: Modifier = Modifier,
    size: Int = 56
) {
    val percentage = (score * 100).toInt()
    val color = when {
        score >= 0.75 -> ConfidenceHigh
        score >= 0.60 -> ConfidenceMedium
        else -> ConfidenceLow
    }

    val animatedProgress by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "gauge"
    )

    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = Slate700,
            strokeWidth = 4.dp,
        )
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = if (size > 48) 14.sp else 10.sp
        )
    }
}

// ─── Glass Card ─────────────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

// ─── Shimmer Loading Effect ─────────────────────────────────────────────────

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Slate800.copy(alpha = 0.6f),
            Slate700.copy(alpha = 0.3f),
            Slate800.copy(alpha = 0.6f)
        ),
        start = Offset(translateAnim - 200, translateAnim - 200),
        end = Offset(translateAnim, translateAnim)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
    )
}

// ─── Pulsing Dot (for live status) ──────────────────────────────────────────

@Composable
fun PulsingDot(
    color: Color = EmeraldPrimary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ─── Metric Stat Card ───────────────────────────────────────────────────────

@Composable
fun MetricCard(
    label: String,
    value: String,
    accentColor: Color = CyanPrimary,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
