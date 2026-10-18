package com.findmissingpep.app.presentation.screens.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findmissingpep.app.presentation.theme.*

/**
 * Tamper-Evident Video Snippet Player Card matching:
 * - 5-second pre/post-match 1080p surveillance player
 * - Centered play button icon with frosted glass blur
 * - Cryptographic timestamp overlay (2026-10-17 15:04:12.441 UTC)
 * - Hash badge: SHA-256 Verified • Tamper Proof
 * - Interactive playback toggle with dynamic scanline and progress scrub
 */
@Composable
fun VideoSnippetPlayerCard(
    cryptographicTimestamp: String = "2026-10-17 15:04:12.441 UTC",
    sha256Badge: String = "SHA-256 Verified • Tamper Proof",
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }

    // Smooth scrub progress animation when playing
    val scrubTransition = rememberInfiniteTransition(label = "videoPlay")
    val playbackProgress by scrubTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scrub"
    )

    // Camera scanline sweep
    val scanlineOffset by scrubTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, LineBorder, RoundedCornerShape(18.dp))
            .background(Color(0xFF0F172A))
            .clickable { isPlaying = !isPlaying }
    ) {
        // CCTV Camera Visual Simulation (Concourse Pedestrians with Cyan Bounding Box)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    )
                )
        ) {
            // CCTV Grid lines
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "● REC [CAM-04 CP-METRO-G2]",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = RedDanger,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "1080p • 30 FPS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Cyan Target Bounding Box in Center Right
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 76.dp)
                        .align(Alignment.CenterHorizontally)
                        .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                        .background(Color(0xFF00E5FF).copy(alpha = 0.08f)),
                    contentAlignment = Alignment.TopStart
                ) {
                    Surface(
                        color = Color(0xFF00E5FF),
                        shape = RoundedCornerShape(bottomEnd = 3.dp)
                    ) {
                        Text(
                            text = "PEP #402",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            // Animated scanline
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopStart)
                        .offset(y = (scanlineOffset * 180).dp)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.35f))
                )
            }
        }

        // Center Play / Pause Button with Frosted Glass Blur
        Surface(
            modifier = Modifier
                .size(46.dp)
                .align(Alignment.Center),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.22f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Top-Right SHA-256 Tamper-Proof Badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFF052E16).copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, GreenSuccess)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = sha256Badge,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF86EFAC),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Bottom Cryptographic Timestamp Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Seek progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isPlaying) playbackProgress else 0.42f)
                        .fillMaxHeight()
                        .background(BluePrimary)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = cryptographicTimestamp,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = if (isPlaying) "5.0s LOOP" else "TAP TO PLAY",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
