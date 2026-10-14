package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DonationStatus
import com.example.data.model.FoodCategory
import com.example.data.model.StorageCondition
import com.example.ui.FoodLoopTab
import com.example.ui.theme.HarvestAmberSecondary
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.UrgencyRed
import com.example.ui.theme.UrgencyYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLoopTopBar(
    onOpenAiAssistant: () -> Unit,
    onLogSurplusClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Eco,
                        contentDescription = "FoodLoop Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "FoodLoop AI",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Predictive Food Rescue & Logistics",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = onOpenAiAssistant,
                modifier = Modifier.testTag("ai_assistant_top_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Ask AI Coordinator",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Button(
                onClick = onLogSurplusClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag("log_surplus_top_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Log Surplus",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun FoodLoopBottomNav(
    selectedTab: FoodLoopTab,
    onTabSelected: (FoodLoopTab) -> Unit,
    availableSurplusCount: Int,
    activeMissionsCount: Int
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier.testTag("foodloop_bottom_nav")
    ) {
        FoodLoopTab.values().forEach { tab ->
            val icon = when (tab) {
                FoodLoopTab.DISPATCH_HUB -> Icons.Default.Dashboard
                FoodLoopTab.SURPLUS_LOG -> Icons.Default.Inventory2
                FoodLoopTab.SMART_MATCH -> Icons.Default.AutoAwesome
                FoodLoopTab.MISSIONS -> Icons.Default.LocalShipping
                FoodLoopTab.IMPACT_AI -> Icons.Default.Insights
            }

            val isSelected = selectedTab == tab

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    if (tab == FoodLoopTab.SURPLUS_LOG && availableSurplusCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                ) {
                                    Text(text = availableSurplusCount.toString())
                                }
                            }
                        ) {
                            Icon(imageVector = icon, contentDescription = tab.title)
                        }
                    } else if (tab == FoodLoopTab.MISSIONS && activeMissionsCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = HarvestAmberSecondary,
                                    contentColor = Color.White
                                ) {
                                    Text(text = activeMissionsCount.toString())
                                }
                            }
                        ) {
                            Icon(imageVector = icon, contentDescription = tab.title)
                        }
                    } else {
                        Icon(imageVector = icon, contentDescription = tab.title)
                    }
                },
                label = {
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
fun PerishabilityBadge(expiryTimestamp: Long) {
    val now = System.currentTimeMillis()
    val remainingMillis = expiryTimestamp - now
    val remainingMinutes = (remainingMillis / (60 * 1000L)).toInt()

    val (badgeColor, textColor, text) = when {
        remainingMinutes <= 0 -> Triple(UrgencyRed, Color.White, "EXPIRED")
        remainingMinutes <= 120 -> {
            val h = remainingMinutes / 60
            val m = remainingMinutes % 60
            Triple(UrgencyRed, Color.White, "${h}h ${m}m URGENT")
        }
        remainingMinutes <= 240 -> {
            val h = remainingMinutes / 60
            val m = remainingMinutes % 60
            Triple(UrgencyYellow, Color.White, "${h}h ${m}m REMAINING")
        }
        remainingMinutes <= 600 -> {
            val h = remainingMinutes / 60
            Triple(HarvestAmberSecondary, Color.White, "${h}h REMAINING")
        }
        else -> {
            val h = remainingMinutes / 60
            Triple(SafetyGreen, Color.White, "${h}h SAFE WINDOW")
        }
    }

    Surface(
        color = badgeColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = textColor
            )
        }
    }
}

@Composable
fun StorageConditionChip(condition: StorageCondition) {
    val (bgColor, tintColor) = when (condition) {
        StorageCondition.REFRIGERATED, StorageCondition.FROZEN ->
            Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.tertiary)
        StorageCondition.HOT_HOLDING ->
            Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.secondary)
        StorageCondition.AMBIENT ->
            Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (condition.requiresColdChain) {
                Icon(
                    imageVector = Icons.Default.AcUnit,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = condition.displayName.substringBefore(" ("),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = tintColor
            )
        }
    }
}

@Composable
fun DonationCategoryIcon(category: FoodCategory) {
    val (icon, color) = when (category) {
        FoodCategory.PREPARED_MEALS -> Pair(Icons.Default.Restaurant, HarvestAmberSecondary)
        FoodCategory.BAKED_GOODS -> Pair(Icons.Default.BakeryDining, HarvestAmberSecondary)
        FoodCategory.FRESH_PRODUCE -> Pair(Icons.Default.Eco, SafetyGreen)
        FoodCategory.DAIRY_CHILLED -> Pair(Icons.Default.AcUnit, MaterialTheme.colorScheme.tertiary)
        FoodCategory.PACKAGED_PANTRY -> Pair(Icons.Default.Inventory2, MaterialTheme.colorScheme.primary)
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.displayName,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
