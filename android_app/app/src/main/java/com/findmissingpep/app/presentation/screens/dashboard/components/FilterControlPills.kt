package com.findmissingpep.app.presentation.screens.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findmissingpep.app.presentation.theme.*

/**
 * Filter & Control Pills matching the brief:
 * - height: 38dp, borderRadius: 999dp
 * - neutral wash background: #F4EFEA (PaperSubtle)
 * - Left: Filter: All Zones ▾
 * - Right: Confidence: ≥ 80% ▾ (active with Royal Blue tint + clear tag ✕)
 */
@Composable
fun FilterControlPills(
    selectedZone: String = "All Zones",
    confidenceThreshold: String = "≥ 80%",
    isActiveConfidence: Boolean = true,
    onZoneClick: () -> Unit = {},
    onConfidenceClick: () -> Unit = {},
    onClearConfidence: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Selector: Filter: All Zones ▾
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable { onZoneClick() },
            shape = RoundedCornerShape(999.dp),
            color = PaperSubtle,
            border = BorderStroke(1.dp, LineBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter: $selectedZone",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InkText,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = InkMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Right Selector: Confidence: ≥ 80% ▾ (with Royal Blue tint and clear ✕)
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable { onConfidenceClick() },
            shape = RoundedCornerShape(999.dp),
            color = if (isActiveConfidence) BlueWash else PaperSubtle,
            border = BorderStroke(
                1.dp,
                if (isActiveConfidence) BluePrimary else LineBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confidence: $confidenceThreshold",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActiveConfidence) BluePrimary else InkText,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                if (isActiveConfidence) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(BluePrimary.copy(alpha = 0.15f))
                            .clickable { onClearConfidence() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = BluePrimary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = InkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
