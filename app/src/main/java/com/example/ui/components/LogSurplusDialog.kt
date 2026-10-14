package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.FoodCategory
import com.example.data.model.StorageCondition

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogSurplusDialog(
    onDismiss: () -> Unit,
    onSubmit: (
        donorName: String,
        donorType: String,
        category: FoodCategory,
        itemName: String,
        quantityKg: Double,
        portions: Int,
        storageCondition: StorageCondition,
        dietaryFlags: String,
        pickupAddress: String,
        shelfLifeHours: Double,
        specialNotes: String
    ) -> Unit
) {
    var donorName by remember { mutableStateOf("Olive Grove Bistro") }
    var donorType by remember { mutableStateOf("Restaurant") }
    var selectedCategory by remember { mutableStateOf(FoodCategory.PREPARED_MEALS) }
    var itemName by remember { mutableStateOf("Baked Lasagna & Garden Salad Trays") }
    var quantityKgStr by remember { mutableStateOf("25.0") }
    var portionsStr by remember { mutableStateOf("50") }
    var selectedStorage by remember { mutableStateOf(StorageCondition.HOT_HOLDING) }
    var shelfLifeHours by remember { mutableDoubleStateOf(2.5) }
    var dietaryFlags by remember { mutableStateOf("Vegetarian option, Nut-free") }
    var pickupAddress by remember { mutableStateOf("512 Columbus Ave, North Beach") }
    var specialNotes by remember { mutableStateOf("Packed in sealed aluminum trays, hot box ready.") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Log Surplus Food",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Triggers AI Demand Matching & Route Plan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Donor Name & Type
                OutlinedTextField(
                    value = donorName,
                    onValueChange = { donorName = it },
                    label = { Text("Donor / Business Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_donor_name")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Food Category Chips
                Text(
                    text = "Food Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FoodCategory.values().forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Item description
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Food Item & Details") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_item_name")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quantity & Portions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = quantityKgStr,
                        onValueChange = { quantityKgStr = it },
                        label = { Text("Weight (kg)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_quantity_kg")
                    )
                    OutlinedTextField(
                        value = portionsStr,
                        onValueChange = { portionsStr = it },
                        label = { Text("Est. Portions") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_portions")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Storage Condition
                Text(
                    text = "Storage / Temperature Condition",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StorageCondition.values().forEach { storage ->
                        FilterChip(
                            selected = selectedStorage == storage,
                            onClick = { selectedStorage = storage },
                            label = { Text(storage.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Shelf Life / Deadline Hours
                Text(
                    text = "Perishability Safety Window: ${shelfLifeHours} Hours",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.secondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    listOf(1.5, 2.5, 4.0, 8.0, 16.0).forEach { hours ->
                        FilterChip(
                            selected = shelfLifeHours == hours,
                            onClick = { shelfLifeHours = hours },
                            label = { Text("${hours}h") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = dietaryFlags,
                    onValueChange = { dietaryFlags = it },
                    label = { Text("Dietary Info (e.g. Halal, Vegan)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pickupAddress,
                    onValueChange = { pickupAddress = it },
                    label = { Text("Pickup Street Address") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = specialNotes,
                    onValueChange = { specialNotes = it },
                    label = { Text("Special Handling / Vehicle Access") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val kg = quantityKgStr.toDoubleOrNull() ?: 20.0
                        val portions = portionsStr.toIntOrNull() ?: 40
                        onSubmit(
                            donorName,
                            donorType,
                            selectedCategory,
                            itemName,
                            kg,
                            portions,
                            selectedStorage,
                            dietaryFlags,
                            pickupAddress,
                            shelfLifeHours,
                            specialNotes
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_surplus_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Register Surplus & Compute AI Match",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
