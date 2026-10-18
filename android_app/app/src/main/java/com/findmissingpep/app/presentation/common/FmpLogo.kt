package com.findmissingpep.app.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findmissingpep.app.R
import com.findmissingpep.app.presentation.theme.*

/**
 * Official FIND-MISSING-PEP brand shield logo.
 * Renders the clean vector emblem matching the website.
 */
@Composable
fun FmpLogo(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(BlueWash),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_fmp_logo),
            contentDescription = "FIND-MISSING-PEP Official Logo",
            modifier = Modifier.size(size * 0.78f)
        )
    }
}

/**
 * Official Brand Header displaying the shield emblem with institutional typography.
 */
@Composable
fun FmpBrandHeader(
    modifier: Modifier = Modifier,
    logoSize: Dp = 38.dp,
    title: String = "FIND-MISSING-PEP",
    subtitle: String = "Command Center",
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FmpLogo(size = logoSize)

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.5.sp,
                letterSpacing = 0.5.sp,
                color = BlueDeep
            )
            Text(
                text = subtitle,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                color = InkMuted
            )
        }
    }
}
