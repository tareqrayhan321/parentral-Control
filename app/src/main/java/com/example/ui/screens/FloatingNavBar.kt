package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ParentTab

private val NavActive = Color(0xFFD2541B)
private val NavIdle = Color(0xFF4A3B33)

private data class FloatingNavItem(
    val tab: ParentTab,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

private val navItems = listOf(
    FloatingNavItem(ParentTab.DASHBOARD, "Home", Icons.Default.Home, "tab_overview"),
    FloatingNavItem(ParentTab.HABITS, "Habits", Icons.Default.TaskAlt, "tab_habits"),
    FloatingNavItem(ParentTab.SCHEDULES, "Schedules", Icons.Default.Schedule, "tab_schedules"),
    FloatingNavItem(ParentTab.AUDIT, "Activity", Icons.Default.History, "tab_audit")
)

/** Floating, translucent capsule navigation bar that sits above the screen content. */
@Composable
fun FloatingNavBar(
    currentTab: ParentTab,
    onSelectTab: (ParentTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(40.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(14.dp, shape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .clip(shape)
                .background(Color.White.copy(alpha = 0.62f))
                .border(1.dp, Color.White.copy(alpha = 0.75f), shape)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            navItems.forEachIndexed { index, item ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color(0x1F000000))
                    )
                }
                // Apps page is opened from the child card, so Home stays highlighted there.
                val selected = currentTab == item.tab ||
                    (item.tab == ParentTab.DASHBOARD && currentTab == ParentTab.APPS)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(30.dp))
                        .clickable { onSelectTab(item.tab) }
                        .padding(vertical = 6.dp)
                        .testTag(item.tag),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) NavActive else NavIdle,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) NavActive else NavIdle,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
