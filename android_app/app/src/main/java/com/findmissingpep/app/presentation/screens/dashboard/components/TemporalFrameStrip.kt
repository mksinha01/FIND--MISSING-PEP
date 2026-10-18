package com.findmissingpep.app.presentation.screens.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findmissingpep.app.presentation.screens.dashboard.FrameMatch
import com.findmissingpep.app.presentation.theme.*

/**
 * M-of-N Sliding-Window Frame Strip matching the design token specs:
 * - frameWidth: 64dp
 * - frameHeight: 64dp
 * - borderRadius: 12dp
 * - activeBorder: #187757 (GreenSuccess)
 * - Persistent ByteTrack ID #TK-402 tag
 * - Green checkmark badge with confidence percentages
 */
@Composable
fun TemporalFrameStrip(
    frames: List<FrameMatch>,
    byteTrackId: String = "#TK-402",
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            frames.forEach { frame ->
                TemporalFrameItem(
                    frame = frame,
                    byteTrackId = byteTrackId
                )
            }
        }
    }
}

@Composable
private fun TemporalFrameItem(
    frame: FrameMatch,
    byteTrackId: String
) {
    val borderColor = if (frame.passed) GreenSuccess else LineBorder
    val activeBorderShape = RoundedCornerShape(12.dp)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 64.dp)
                .clip(activeBorderShape)
                .border(1.5.dp, borderColor, activeBorderShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2A364F),
                            Color(0xFF17243A)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Simulated CCTV Face Crop / Wireframe
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top ID tag
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "F${frame.frameIndex} $byteTrackId",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF67E8F9),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }

                // Center Avatar Silhouette / Crop indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PEP",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Bottom checkmark badge
                if (frame.passed) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(GreenSuccess),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Matched",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Confidence score pill
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (frame.passed) GreenSuccessBg else PaperSubtle,
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                if (frame.passed) GreenSuccess.copy(alpha = 0.3f) else LineBorder
            )
        ) {
            Text(
                text = "${frame.confidence}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (frame.passed) GreenSuccess else InkMuted,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
            )
        }
    }
}
