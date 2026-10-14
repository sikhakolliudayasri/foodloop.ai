package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Co2
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MissionStatus
import com.example.data.model.RescueMission
import com.example.ui.theme.Co2Teal
import com.example.ui.theme.HarvestAmberSecondary
import com.example.ui.theme.SafetyGreen
import kotlin.math.roundToInt

@Composable
fun RouteMissionsScreen(
    missions: List<RescueMission>,
    onAdvanceMission: (RescueMission) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCompletedOnly by remember { mutableStateOf(false) }

    val filteredMissions = missions.filter {
        if (showCompletedOnly) it.status == MissionStatus.COMPLETED
        else it.status != MissionStatus.COMPLETED
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("route_missions_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header Filter Tabs ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Rescue Mission Logistics",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Real-world vehicle capacity & waypoint routing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !showCompletedOnly,
                        onClick = { showCompletedOnly = false },
                        label = { Text("Active (${missions.count { it.status != MissionStatus.COMPLETED }})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = showCompletedOnly,
                        onClick = { showCompletedOnly = true },
                        label = { Text("Delivered (${missions.count { it.status == MissionStatus.COMPLETED }})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        if (filteredMissions.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (showCompletedOnly) "No completed missions recorded yet." else "No active missions in transit.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            items(filteredMissions, key = { it.id }) { mission ->
                MissionDetailedCard(
                    mission = mission,
                    onAdvance = { onAdvanceMission(mission) }
                )
            }
        }
    }
}

@Composable
fun MissionDetailedCard(
    mission: RescueMission,
    onAdvance: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Mission Code & Vehicle Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = mission.missionCode,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${mission.vehicleType} • ${mission.driverOrVolunteerName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = when (mission.status) {
                        MissionStatus.SCHEDULED -> MaterialTheme.colorScheme.surfaceVariant
                        MissionStatus.EN_ROUTE_PICKUP, MissionStatus.COLLECTED -> HarvestAmberSecondary.copy(alpha = 0.2f)
                        MissionStatus.OUT_FOR_DELIVERY -> MaterialTheme.colorScheme.primaryContainer
                        MissionStatus.COMPLETED -> SafetyGreen.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = mission.status.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when (mission.status) {
                            MissionStatus.SCHEDULED -> MaterialTheme.colorScheme.onSurfaceVariant
                            MissionStatus.EN_ROUTE_PICKUP, MissionStatus.COLLECTED -> HarvestAmberSecondary
                            MissionStatus.OUT_FOR_DELIVERY -> MaterialTheme.colorScheme.onPrimaryContainer
                            MissionStatus.COMPLETED -> SafetyGreen
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Vehicle Capacity Utilization
            val loadFraction = (mission.currentLoadKg / mission.vehicleCapacityKg).toFloat().coerceIn(0f, 1f)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Vehicle Payload Capacity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${mission.currentLoadKg} kg / ${mission.vehicleCapacityKg} kg (${(loadFraction * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { loadFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Waypoints Route Sequence
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Stop 1: Pickup
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "1",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PICKUP: ${mission.donorNames}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Collect ${mission.currentLoadKg} kg surplus • Scheduled ${mission.scheduledPickupTime}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Vertical connector line
                    Box(
                        modifier = Modifier
                            .padding(start = 11.dp)
                            .width(2.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    )

                    // Stop 2: Delivery
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(SafetyGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "2",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DELIVER: ${mission.recipientOrgName}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = mission.recipientAddress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Route Metrics (Distance, Duration, CO2 Saved)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${mission.totalDistanceKm} km (${mission.estimatedDurationMin} mins)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Co2,
                        contentDescription = null,
                        tint = Co2Teal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${mission.co2SavedKg.roundToInt()} kg CO2e avoided",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Co2Teal
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button to Advance Mission Status
            if (mission.status != MissionStatus.COMPLETED) {
                Button(
                    onClick = onAdvance,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (mission.status) {
                            MissionStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
                            MissionStatus.EN_ROUTE_PICKUP -> HarvestAmberSecondary
                            MissionStatus.COLLECTED -> MaterialTheme.colorScheme.secondary
                            MissionStatus.OUT_FOR_DELIVERY -> SafetyGreen
                            MissionStatus.COMPLETED -> SafetyGreen
                        }
                    )
                ) {
                    Icon(
                        imageVector = when (mission.status) {
                            MissionStatus.OUT_FOR_DELIVERY -> Icons.Default.CheckCircle
                            else -> Icons.Default.LocalShipping
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (mission.status) {
                            MissionStatus.SCHEDULED -> "Start Mission -> En Route to Pickup"
                            MissionStatus.EN_ROUTE_PICKUP -> "Verify & Mark Food Collected"
                            MissionStatus.COLLECTED -> "Dispatch Vehicle -> Out for Delivery"
                            MissionStatus.OUT_FOR_DELIVERY -> "Confirm Delivery to Shelter"
                            MissionStatus.COMPLETED -> "Delivered"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
