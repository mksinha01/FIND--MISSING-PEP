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

// ─── Status Badge (Blue & Cream Human Design) ───────────────────────────────

@Composable
fun StatusBadge(
    status: CaseStatus,
    modifier: Modifier = Modifier
) {
    val (textColor, bgColor, label) = when (status) {
        CaseStatus.PROCESSING -> Triple(AmberAlert, AmberAlertBg, "Processing")
        CaseStatus.PENDING_FIR_REVIEW -> Triple(AmberAlert, AmberAlertBg, "Pending FIR")
        CaseStatus.ACTIVE -> Triple(GreenSuccess, GreenSuccessBg, "Active Search")
        CaseStatus.FOUND -> Triple(BluePrimary, BlueWash, "Found")
        CaseStatus.CLOSED -> Triple(InkMuted, LineBorderSubtle, "Closed")
        CaseStatus.FIR_REJECTED -> Triple(RedDanger, RedDangerBg, "FIR Rejected")
        CaseStatus.REJECTED_NO_FACE -> Triple(RedDanger, RedDangerBg, "Photo Unclear")
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        contentColor = textColor,
        border = null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontSize = 11.sp
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
    val (textColor, bgColor, label) = when (status) {
        SightingStatus.PENDING -> Triple(AmberAlert, AmberAlertBg, "Pending Review")
        SightingStatus.CONFIRMED -> Triple(GreenSuccess, GreenSuccessBg, "Confirmed Match")
        SightingStatus.REJECTED -> Triple(RedDanger, RedDangerBg, "Rejected · False Alarm")
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        contentColor = textColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontSize = 11.sp
        )
    }
}

// ─── Verification Match Gauge (Human CCTV Review) ───────────────────────────

@Composable
fun SimilarityGauge(
    score: Double,
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    val percentage = (score * 100).toInt()
    val color = when {
        score >= 0.75 -> GreenSuccess
        score >= 0.60 -> AmberAlert
        else -> RedDanger
    }

    val animatedProgress by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
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
            trackColor = LineBorderSubtle,
            strokeWidth = 3.5.dp,
        )
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = InkText,
            fontSize = if (size > 48) 13.sp else 10.sp
        )
    }
}

// ─── Paper Card (Blue & Cream Design System) ────────────────────────────────

@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = PaperWhite,
    borderColor: Color = LineBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

// Backward compatibility alias for GlassCard
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    PaperCard(
        modifier = modifier,
        content = content
    )
}

// ─── Shimmer Loading Effect (Cream Palette) ─────────────────────────────────

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
            PaperSubtle,
            LineBorderSubtle,
            PaperSubtle
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

// ─── Pulsing Dot ────────────────────────────────────────────────────────────

@Composable
fun PulsingDot(
    color: Color = GreenSuccess,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ─── Metric Stat Card (Clean Human Layout) ──────────────────────────────────

@Composable
fun MetricCard(
    label: String,
    value: String,
    accentColor: Color = BluePrimary,
    modifier: Modifier = Modifier
) {
    PaperCard(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = accentColor
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            fontWeight = FontWeight.Medium
        )
    }
}
