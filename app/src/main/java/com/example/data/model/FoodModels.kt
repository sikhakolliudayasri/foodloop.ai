package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FoodCategory(val displayName: String, val iconName: String) {
    PREPARED_MEALS("Prepared Meals", "restaurant"),
    BAKED_GOODS("Baked Goods", "bakery_dining"),
    FRESH_PRODUCE("Fresh Produce", "eco"),
    DAIRY_CHILLED("Dairy & Chilled", "ac_unit"),
    PACKAGED_PANTRY("Packaged Pantry", "inventory_2")
}

enum class StorageCondition(val displayName: String, val requiresColdChain: Boolean) {
    AMBIENT("Ambient / Dry", false),
    REFRIGERATED("Refrigerated (2°C - 4°C)", true),
    FROZEN("Frozen (-18°C)", true),
    HOT_HOLDING("Hot Holding (>60°C)", false)
}

enum class DonationStatus {
    AVAILABLE,
    MATCHED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}

enum class MissionStatus(val label: String) {
    SCHEDULED("Scheduled"),
    EN_ROUTE_PICKUP("En Route to Pickup"),
    COLLECTED("Collected"),
    OUT_FOR_DELIVERY("Out for Delivery"),
    COMPLETED("Completed & Verified")
}

@Entity(tableName = "surplus_donations")
data class SurplusDonation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val donorName: String,
    val donorType: String, // Restaurant, Supermarket, Bakery, Caterer, Farm
    val foodCategory: FoodCategory,
    val foodItemName: String,
    val quantityKg: Double,
    val estimatedPortions: Int,
    val prepOrPackedTime: Long,
    val expiryDeadline: Long, // timestamp
    val storageCondition: StorageCondition,
    val dietaryFlags: String, // "Vegetarian, Halal-friendly"
    val pickupAddress: String,
    val pickupLatitude: Double,
    val pickupLongitude: Double,
    val pickupWindow: String, // "14:00 - 15:30"
    val status: DonationStatus = DonationStatus.AVAILABLE,
    val matchedRecipientId: Long? = null,
    val matchedRecipientName: String? = null,
    val rescueMissionId: Long? = null,
    val specialHandlingNotes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recipient_orgs")
data class RecipientOrg(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val orgType: String, // Homeless Shelter, Community Kitchen, Food Pantry, Youth Hub
    val currentDailyNeedPortions: Int,
    val fulfilledTodayPortions: Int,
    val storageCapabilities: String, // "Walk-in Fridge, Freezer, Dry Shelves"
    val dietaryRequirements: String, // "Vegetarian, Halal, Diabetic-safe"
    val deliveryAddress: String,
    val latitude: Double,
    val longitude: Double,
    val operatingHours: String,
    val urgencyPriority: Int, // 1 to 10 (10 being urgent food deficit)
    val contactPhone: String,
    val mealDistributionTime: String // e.g. "Dinner 18:00"
)

@Entity(tableName = "rescue_missions")
data class RescueMission(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val missionCode: String,
    val vehicleType: String, // "Refrigerated Van", "Electric Cargo Van", "Volunteer Vehicle"
    val vehicleCapacityKg: Double,
    val currentLoadKg: Double,
    val donationIds: String, // Comma separated IDs
    val donorNames: String,
    val recipientOrgName: String,
    val recipientAddress: String,
    val totalDistanceKm: Double,
    val estimatedDurationMin: Int,
    val co2SavedKg: Double,
    val status: MissionStatus = MissionStatus.SCHEDULED,
    val driverOrVolunteerName: String,
    val waypointsSummary: String, // e.g. "GreenLeaf Bakery -> Hope House Shelter"
    val scheduledPickupTime: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "surplus_predictions")
data class SurplusPrediction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val donorName: String,
    val donorType: String,
    val predictedSurplusKg: Double,
    val predictedPortions: Int,
    val predictedCategory: FoodCategory,
    val confidenceScore: Float, // 0.0 - 1.0
    val primaryFactors: String, // "Rain forecast + Tuesday post-lunch historical 40% excess"
    val recommendedAction: String,
    val forecastPeriod: String // "Today 16:00", "Tomorrow Lunch"
)

data class MatchingScoreBreakdown(
    val totalScore: Int, // 0-100
    val perishabilityScore: Int, // 0-100
    val distanceScore: Int, // 0-100
    val capacityMatchScore: Int, // 0-100
    val storageSafetyScore: Int, // 0-100
    val equityFairnessScore: Int, // 0-100
    val distanceKm: Double,
    val estimatedTransitMin: Int,
    val reasoningExplanation: String
)

data class MatchingRecommendation(
    val donation: SurplusDonation,
    val recipient: RecipientOrg,
    val breakdown: MatchingScoreBreakdown
)

data class ImpactSummary(
    val totalRescuedKg: Double,
    val totalMealsServed: Int,
    val totalCo2AvoidedKg: Double,
    val totalWaterConservedLiters: Long,
    val activeRescuesCount: Int,
    val completedRescuesCount: Int,
    val availableSurplusCount: Int,
    val estimatedFinancialSavedUsd: Double
)
