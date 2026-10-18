package com.findmissingpep.app.presentation.screens.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findmissingpep.app.presentation.theme.GreenSuccess
import com.findmissingpep.app.presentation.theme.InkMuted
import com.findmissingpep.app.presentation.theme.InkText
import com.findmissingpep.app.presentation.theme.LineBorder

/**
 * Biometric Semi-Circular Radial Arc Match Gauge according to the design tokens:
 * - arcDiameter: 80dp
 * - strokeWidth: 8dp
 * - trackColor: #E5DDD6 (LineBorder)
 * - fillColor: #187757 (GreenSuccess)
 * - textColor: #17243A (InkText)
 * - Semi-circular (180-degree or 220-degree arc) with centered percentage & subtext
 */
@Composable
fun BiometricMatchArcGauge(
    percentage: Double = 94.8,
    subtext: String = "ArcFace Cosine Match",
    diameter: Dp = 80.dp,
    strokeWidth: Dp = 8.dp,
    fillColor: Color = GreenSuccess,
    trackColor: Color = LineBorder,
    textColor: Color = InkText,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (percentage / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "arcProgress"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(diameter),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = strokeWidth.toPx()
                val inset = strokePx / 2f
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                val topLeft = Offset(inset, inset)

                // 240-degree sweep from 150 degrees to 390 degrees for a balanced gauge arc
                val startAngle = 150f
                val totalSweep = 240f

                // Background track
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Active filled arc
                drawArc(
                    color = fillColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // Numeric Center Display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = String.format("%.1f%%", percentage),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    letterSpacing = (-0.5).sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtext,
            fontWeight = FontWeight.Bold,
            color = InkText,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = "512-D Cosine",
            fontWeight = FontWeight.Medium,
            color = InkMuted,
            fontSize = 9.5.sp,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
