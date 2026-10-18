package com.findmissingpep.app.presentation.screens.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findmissingpep.app.presentation.screens.dashboard.WaypointItem
import com.findmissingpep.app.presentation.theme.*

/**
 * High-fidelity GIS Trajectory Canvas matching the brief:
 * - Directional breadcrumb vector path (pathColor #014BAA, pathWidth 3dp)
 * - Waypoint #1 (14:10): Park Exit Camera (Muted gray pin)
 * - Waypoint #2 (14:32): Transit Footbridge Camera (Blue pin)
 * - Waypoint #3 (15:04): Central Metro Concourse (Pulsing Red halo — "Latest Verified")
 * - Dashed forward projection cone indicating predicted travel heading and search radius
 * - Vector / Satellite style toggle
 */
@Composable
fun GisCanvasView(
    waypoints: List<WaypointItem>,
    isSatelliteMode: Boolean,
    selectedWaypointId: Int,
    onSelectWaypoint: (WaypointItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // Breathing pulse for latest waypoint halo
    val infiniteTransition = rememberInfiniteTransition(label = "gisPulse")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = 38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloSize"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (isSatelliteMode) Color(0xFF1B2330) else Color(0xFFEDE7E1))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Draw stylized GIS urban map background
            drawUrbanGrid(width, height, isSatelliteMode)

            // 2. Positions for 3 waypoints mapped across canvas
            val pt1 = Offset(width * 0.22f, height * 0.22f) // Waypoint 1 (Park exit)
            val pt2 = Offset(width * 0.48f, height * 0.45f) // Waypoint 2 (Footbridge)
            val pt3 = Offset(width * 0.74f, height * 0.72f) // Waypoint 3 (Metro Gate 2)

            // 3. Draw Forward Projection Search Cone (from Waypoint #3 extending toward West/Terminal 3)
            val conePath = Path().apply {
                moveTo(pt3.x, pt3.y)
                lineTo(width * 0.95f, height * 0.58f)
                lineTo(width * 0.88f, height * 0.94f)
                close()
            }
            drawPath(
                path = conePath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        BluePrimary.copy(alpha = 0.20f),
                        BluePrimary.copy(alpha = 0.02f)
                    ),
                    center = pt3,
                    radius = width * 0.35f
                )
            )

            // Dashed outline of search cone
            drawPath(
                path = conePath,
                color = BluePrimary.copy(alpha = 0.6f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )

            // 4. Directional Breadcrumb Vector Path (#014BAA, 3dp)
            val breadcrumbPath = Path().apply {
                moveTo(pt1.x, pt1.y)
                // Smooth curved bezier spline between waypoints
                cubicTo(
                    pt1.x + 30f, pt1.y + 70f,
                    pt2.x - 40f, pt2.y - 50f,
                    pt2.x, pt2.y
                )
                cubicTo(
                    pt2.x + 40f, pt2.y + 60f,
                    pt3.x - 50f, pt3.y - 40f,
                    pt3.x, pt3.y
                )
            }

            // Path glow
            drawPath(
                path = breadcrumbPath,
                color = BluePrimary.copy(alpha = 0.25f),
                style = Stroke(width = 7.dp.toPx())
            )
            // Crisp Royal Blue vector line
            drawPath(
                path = breadcrumbPath,
                color = BluePrimary,
                style = Stroke(width = 3.dp.toPx())
            )

            // Directional arrowhead indicators along path
            drawDirectionalArrow(Offset(width * 0.35f, height * 0.33f), angle = 45f)
            drawDirectionalArrow(Offset(width * 0.61f, height * 0.58f), angle = 45f)

            // 5. Waypoint #1 Pin (Muted gray pin)
            drawCircle(
                color = InkSubtle,
                radius = 8.dp.toPx(),
                center = pt1
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = pt1
            )

            // 6. Waypoint #2 Pin (Official Royal Blue pin)
            drawCircle(
                color = BluePrimary,
                radius = 8.dp.toPx(),
                center = pt2
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = pt2
            )

            // 7. Waypoint #3 Pin (Pulsing Red Halo — "Latest Verified")
            // Breathing halo: activeHaloColor rgba(168, 59, 80, 0.25)
            drawCircle(
                color = RedDanger.copy(alpha = haloAlpha),
                radius = haloPulse.dp.toPx(),
                center = pt3
            )
            drawCircle(
                color = RedDanger.copy(alpha = 0.35f),
                radius = 16.dp.toPx(),
                center = pt3
            )
            drawCircle(
                color = RedDanger,
                radius = 9.dp.toPx(),
                center = pt3
            )
            drawCircle(
                color = Color.White,
                radius = 4.5.dp.toPx(),
                center = pt3
            )
        }

        // Overlay Waypoint callout chips
        // Waypoint 1 Chip
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 20.dp)
                .clickable { waypoints.getOrNull(0)?.let(onSelectWaypoint) },
            shape = RoundedCornerShape(8.dp),
            color = PaperWhite.copy(alpha = 0.94f),
            border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(InkSubtle))
                Spacer(modifier = Modifier.width(5.dp))
                Text("14:10 Park Exit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = InkText)
            }
        }

        // Waypoint 2 Chip
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 80.dp, bottom = 20.dp)
                .clickable { waypoints.getOrNull(1)?.let(onSelectWaypoint) },
            shape = RoundedCornerShape(8.dp),
            color = PaperWhite.copy(alpha = 0.94f),
            border = androidx.compose.foundation.BorderStroke(1.dp, BluePrimary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(BluePrimary))
                Spacer(modifier = Modifier.width(5.dp))
                Text("14:32 Footbridge", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BluePrimary)
            }
        }

        // Waypoint 3 Pulse Badge (Latest Verified)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 48.dp)
                .clickable { waypoints.getOrNull(2)?.let(onSelectWaypoint) },
            shape = RoundedCornerShape(8.dp),
            color = PaperWhite.copy(alpha = 0.96f),
            border = androidx.compose.foundation.BorderStroke(1.dp, RedDanger.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(RedDanger))
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("15:04 IST • LATEST", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = RedDanger)
                    Text("Central Metro Gate 2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = InkText)
                }
            }
        }

        // Map Legend / Heading Watermark
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = Color(0xFF67E8F9),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "HEADING: 275° W",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

private fun DrawScope.drawUrbanGrid(width: Float, height: Float, isSatellite: Boolean) {
    val roadColor = if (isSatellite) Color(0xFF283446) else Color(0xFFDFD7CF)
    val parkColor = if (isSatellite) Color(0xFF152822) else Color(0xFFE2EFE7)
    val riverColor = if (isSatellite) Color(0xFF10283B) else Color(0xFFDFEAF7)

    // Park / Green Zone
    drawRect(
        color = parkColor,
        topLeft = Offset(width * 0.05f, height * 0.05f),
        size = androidx.compose.ui.geometry.Size(width * 0.35f, height * 0.28f)
    )

    // River / Water feature
    val riverPath = Path().apply {
        moveTo(0f, height * 0.82f)
        cubicTo(width * 0.3f, height * 0.88f, width * 0.7f, height * 0.75f, width, height * 0.85f)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    drawPath(path = riverPath, color = riverColor)

    // Major road arteries
    drawLine(
        color = roadColor,
        start = Offset(0f, height * 0.35f),
        end = Offset(width, height * 0.38f),
        strokeWidth = 14f
    )
    drawLine(
        color = roadColor,
        start = Offset(width * 0.5f, 0f),
        end = Offset(width * 0.55f, height),
        strokeWidth = 14f
    )
    drawLine(
        color = roadColor,
        start = Offset(width * 0.1f, height * 0.1f),
        end = Offset(width * 0.9f, height * 0.85f),
        strokeWidth = 10f
    )

    // Secondary street grid
    for (i in 1..6) {
        val y = height * (i / 7f)
        drawLine(
            color = roadColor.copy(alpha = 0.5f),
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 4f
        )
    }
}

private fun DrawScope.drawDirectionalArrow(center: Offset, angle: Float) {
    drawCircle(
        color = Color.White,
        radius = 5.dp.toPx(),
        center = center
    )
    drawCircle(
        color = BluePrimary,
        radius = 3.dp.toPx(),
        center = center
    )
}
