package com.findmissingpep.app.presentation.screens.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findmissingpep.app.presentation.theme.InkText

/**
 * Standard Status Bar (9:41, Cellular signal bars, Wi-Fi curves, Battery indicator)
 */
@Composable
fun IosStatusBar(
    textColor: Color = InkText,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time 9:41
        Text(
            text = "9:41",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = (-0.2).sp
        )

        // Status Icons (Cellular, Wi-Fi, Battery)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Cellular Bars (4 signal bars)
            Canvas(modifier = Modifier.size(width = 17.dp, height = 11.dp)) {
                val barWidth = 2.5.dp.toPx()
                val spacing = 1.8.dp.toPx()
                for (i in 0..3) {
                    val barHeight = (4 + i * 2.3f).dp.toPx()
                    drawRoundRect(
                        color = textColor,
                        topLeft = Offset(i * (barWidth + spacing), size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }
            }

            // Wi-Fi Curves
            Canvas(modifier = Modifier.size(width = 15.dp, height = 11.dp)) {
                // Wifi dot
                drawCircle(
                    color = textColor,
                    radius = 1.3.dp.toPx(),
                    center = Offset(size.width / 2f, size.height - 1.5.dp.toPx())
                )
                // Mid arc
                drawArc(
                    color = textColor,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.2f, size.height * 0.35f),
                    size = Size(size.width * 0.6f, size.height * 0.6f),
                    style = Stroke(width = 1.8.dp.toPx())
                )
                // Outer arc
                drawArc(
                    color = textColor,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height * 0.95f),
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }

            // Battery Indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 11.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Transparent)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Outer pill outline
                        drawRoundRect(
                            color = textColor.copy(alpha = 0.45f),
                            topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                            size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        // Battery fill
                        drawRoundRect(
                            color = textColor,
                            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                            size = Size((size.width - 4.dp.toPx()) * 0.85f, size.height - 4.dp.toPx()),
                            cornerRadius = CornerRadius(1.5.dp.toPx())
                        )
                    }
                }
                // Battery terminal nub
                Box(
                    modifier = Modifier
                        .size(width = 1.5.dp, height = 4.dp)
                        .clip(RoundedCornerShape(topEnd = 1.dp, bottomEnd = 1.dp))
                        .background(textColor.copy(alpha = 0.45f))
                )
            }
        }
    }
}

/**
 * Standard bottom Home Indicator Bar
 */
@Composable
fun IosHomeIndicator(
    color: Color = InkText.copy(alpha = 0.3f),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 134.dp, height = 5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color)
        )
    }
}
