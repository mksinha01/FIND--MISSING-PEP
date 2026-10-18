package com.findmissingpep.app.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.findmissingpep.app.presentation.theme.*

/**
 * Top-level Bottom Navigation Tabs matching the Web App navigation:
 * 1. Dashboard
 * 2. My Reports
 * 3. Report Missing Person
 * 4. Alerts
 * 5. Profile
 */
enum class BottomNavTab(
    val route: String,
    val title: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val isPrimaryAction: Boolean = false
) {
    DASHBOARD(
        route = Screen.Dashboard.route,
        title = "Dashboard",
        activeIcon = Icons.Filled.Dashboard,
        inactiveIcon = Icons.Outlined.Dashboard
    ),
    MY_REPORTS(
        route = Screen.MyCases.route,
        title = "My Reports",
        activeIcon = Icons.AutoMirrored.Filled.Assignment,
        inactiveIcon = Icons.AutoMirrored.Outlined.Assignment
    ),
    REPORT_PERSON(
        route = Screen.ReportWizard.route,
        title = "Report",
        activeIcon = Icons.Filled.PersonAdd,
        inactiveIcon = Icons.Outlined.PersonAdd,
        isPrimaryAction = true
    ),
    ALERTS(
        route = Screen.Notifications.route,
        title = "Alerts",
        activeIcon = Icons.Filled.Notifications,
        inactiveIcon = Icons.Outlined.Notifications
    ),
    PROFILE(
        route = Screen.Profile.route,
        title = "Profile",
        activeIcon = Icons.Filled.Person,
        inactiveIcon = Icons.Outlined.Person
    )
}

/**
 * Enterprise-grade Bottom Navigation Footer for FIND-MISSING-PEP.
 * Implements clean government / civic safety design with active pills and live badges.
 */
@Composable
fun AppBottomNavBar(
    currentRoute: String?,
    unreadAlertsCount: Int = 0,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                ambientColor = InkText.copy(alpha = 0.08f),
                spotColor = InkText.copy(alpha = 0.05f)
            ),
        color = PaperWhite,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        border = BorderStroke(1.dp, LineBorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab.entries.forEach { tab ->
                val isSelected = currentRoute == tab.route

                if (tab.isPrimaryAction) {
                    // Elevated Center Action Item: Report Missing Person
                    CenterReportActionButton(
                        tab = tab,
                        isSelected = isSelected,
                        onClick = { onTabSelected(tab.route) },
                        modifier = Modifier.weight(1.1f)
                    )
                } else {
                    // Standard Nav Item
                    StandardNavTabItem(
                        tab = tab,
                        isSelected = isSelected,
                        badgeCount = if (tab == BottomNavTab.ALERTS) unreadAlertsCount else 0,
                        onClick = { onTabSelected(tab.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardNavTabItem(
    tab: BottomNavTab,
    isSelected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) BluePrimary else InkMuted,
        animationSpec = tween(durationMillis = 200),
        label = "tab_color"
    )

    val pillBackground by animateColorAsState(
        targetValue = if (isSelected) BlueWash else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "pill_bg"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon Container with optional pill and badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(pillBackground)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            containerColor = RedDanger,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = if (isSelected) tab.activeIcon else tab.inactiveIcon,
                    contentDescription = tab.title,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = tab.title,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CenterReportActionButton(
    tab: BottomNavTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .size(width = 46.dp, height = 30.dp)
                .shadow(
                    elevation = if (isSelected) 4.dp else 1.dp,
                    shape = RoundedCornerShape(999.dp),
                    ambientColor = BluePrimary.copy(alpha = 0.25f),
                    spotColor = BluePrimary.copy(alpha = 0.15f)
                ),
            shape = RoundedCornerShape(999.dp),
            color = if (isSelected) BluePrimary else BlueWash,
            border = BorderStroke(1.dp, if (isSelected) BluePrimary else BlueBorder.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = tab.title,
                    tint = if (isSelected) Color.White else BluePrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = "Report Person",
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (isSelected) BluePrimary else InkText,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
