package com.example.data.repository

import android.content.Context
import com.example.data.local.FoodLoopDao
import com.example.data.local.FoodLoopDatabase
import com.example.data.model.DonationStatus
import com.example.data.model.FoodCategory
import com.example.data.model.ImpactSummary
import com.example.data.model.MatchingRecommendation
import com.example.data.model.MatchingScoreBreakdown
import com.example.data.model.MissionStatus
import com.example.data.model.RecipientOrg
import com.example.data.model.RescueMission
import com.example.data.model.StorageCondition
import com.example.data.model.SurplusDonation
import com.example.data.model.SurplusPrediction
import com.example.data.remote.GeminiPredictiveEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class FoodLoopRepository(private val dao: FoodLoopDao) {

    val allDonations: Flow<List<SurplusDonation>> = dao.getAllDonations()
    val availableDonations: Flow<List<SurplusDonation>> = dao.getAvailableDonations()
    val allRecipients: Flow<List<RecipientOrg>> = dao.getAllRecipients()
    val allMissions: Flow<List<RescueMission>> = dao.getAllMissions()
    val activeMissions: Flow<List<RescueMission>> = dao.getActiveMissions()
    val allPredictions: Flow<List<SurplusPrediction>> = dao.getAllPredictions()

    val impactSummary: Flow<ImpactSummary> = combine(
        dao.getAllDonations(),
        dao.getAllMissions()
    ) { donations, missions ->
        val deliveredOrMatched = donations.filter {
            it.status == DonationStatus.DELIVERED || it.status == DonationStatus.IN_TRANSIT || it.status == DonationStatus.MATCHED
        }
        val totalKg = deliveredOrMatched.sumOf { it.quantityKg }
        val totalPortions = deliveredOrMatched.sumOf { it.estimatedPortions }
        val totalCo2 = totalKg * 2.5
        val totalWater = (totalKg * 840.0).toLong()
        val activeCount = missions.count { it.status != MissionStatus.COMPLETED }
        val completedCount = missions.count { it.status == MissionStatus.COMPLETED }
        val availableCount = donations.count { it.status == DonationStatus.AVAILABLE }
        val financialSavings = totalKg * 3.85

        ImpactSummary(
            totalRescuedKg = totalKg,
            totalMealsServed = totalPortions,
            totalCo2AvoidedKg = totalCo2,
            totalWaterConservedLiters = totalWater,
            activeRescuesCount = activeCount,
            completedRescuesCount = completedCount,
            availableSurplusCount = availableCount,
            estimatedFinancialSavedUsd = financialSavings
        )
    }

    suspend fun initializeDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        val count = dao.getDonationCount()
        if (count > 0) return@withContext

        val now = System.currentTimeMillis()
        val hour = 3600 * 1000L

        // 1. Initial Surplus Donations
        val seedDonations = listOf(
            SurplusDonation(
                donorName = "Artisan Hearth Bakery",
                donorType = "Bakery",
                foodCategory = FoodCategory.BAKED_GOODS,
                foodItemName = "Sourdough Loaves, Brioche & Croissants",
                quantityKg = 34.5,
                estimatedPortions = 70,
                prepOrPackedTime = now - (3 * hour),
                expiryDeadline = now + (3 * hour),
                storageCondition = StorageCondition.AMBIENT,
                dietaryFlags = "Vegetarian, Nut-Free",
                pickupAddress = "450 Market St, Financial District",
                pickupLatitude = 37.7915,
                pickupLongitude = -122.3995,
                pickupWindow = "14:30 - 16:00",
                status = DonationStatus.AVAILABLE,
                specialHandlingNotes = "Packaged in food-grade kraft bags; keep dry."
            ),
            SurplusDonation(
                donorName = "Grand Regency Banquets",
                donorType = "Event Caterer",
                foodCategory = FoodCategory.PREPARED_MEALS,
                foodItemName = "Mediterranean Herb Chicken, Rice Pilaf & Roasted Veg",
                quantityKg = 52.0,
                estimatedPortions = 110,
                prepOrPackedTime = now - (1 * hour),
                expiryDeadline = now + (2 * hour), // High urgency!
                storageCondition = StorageCondition.HOT_HOLDING,
                dietaryFlags = "Halal-friendly, Gluten-aware",
                pickupAddress = "888 Howard St, SOMA Convention Center",
                pickupLatitude = 37.7818,
                pickupLongitude = -122.4048,
                pickupWindow = "Immediate Pickup (Next 45 min)",
                status = DonationStatus.AVAILABLE,
                specialHandlingNotes = "Keep above 60°C in insulated cambro hot boxes."
            ),
            SurplusDonation(
                donorName = "Pacific Ocean Bento & Bowl",
                donorType = "Restaurant",
                foodCategory = FoodCategory.PREPARED_MEALS,
                foodItemName = "Chilled Salmon Teriyaki & Veggie Soba Trays",
                quantityKg = 22.0,
                estimatedPortions = 45,
                prepOrPackedTime = now - (2 * hour),
                expiryDeadline = now + (2 * hour + (30 * 60 * 1000L)),
                storageCondition = StorageCondition.REFRIGERATED,
                dietaryFlags = "Pescatarian, Dairy-Free",
                pickupAddress = "1200 4th St, Mission Bay",
                pickupLatitude = 37.7735,
                pickupLongitude = -122.3912,
                pickupWindow = "15:00 - 16:30",
                status = DonationStatus.AVAILABLE,
                specialHandlingNotes = "Strict cold-chain: maintain between 2°C - 4°C."
            ),
            SurplusDonation(
                donorName = "Metro Supermarket Deli",
                donorType = "Supermarket",
                foodCategory = FoodCategory.FRESH_PRODUCE,
                foodItemName = "Crisp Gala Apples, Berries, Salad Greens & Squash",
                quantityKg = 68.0,
                estimatedPortions = 140,
                prepOrPackedTime = now - (5 * hour),
                expiryDeadline = now + (14 * hour),
                storageCondition = StorageCondition.AMBIENT,
                dietaryFlags = "Vegan, All-Natural",
                pickupAddress = "2300 16th St, Mission District",
                pickupLatitude = 37.7660,
                pickupLongitude = -122.4105,
                pickupWindow = "14:00 - 18:00",
                status = DonationStatus.AVAILABLE,
                specialHandlingNotes = "Crated in stackable plastic harvest bins."
            ),
            SurplusDonation(
                donorName = "Sunlight Dairy Co-op",
                donorType = "Wholesaler",
                foodCategory = FoodCategory.DAIRY_CHILLED,
                foodItemName = "Greek Yogurt, Organic Cheddar & Pasteurized Milk",
                quantityKg = 38.0,
                estimatedPortions = 85,
                prepOrPackedTime = now - (6 * hour),
                expiryDeadline = now + (8 * hour),
                storageCondition = StorageCondition.REFRIGERATED,
                dietaryFlags = "Vegetarian, High-Protein",
                pickupAddress = "150 Shoreline Way, Pier 30",
                pickupLatitude = 37.7892,
                pickupLongitude = -122.3880,
                pickupWindow = "15:30 - 17:30",
                status = DonationStatus.AVAILABLE,
                specialHandlingNotes = "Refrigerated vehicle cargo required."
            ),
            SurplusDonation(
                donorName = "Spice Garden Kitchen",
                donorType = "Restaurant",
                foodCategory = FoodCategory.PREPARED_MEALS,
                foodItemName = "Yellow Lentil Dal, Aloo Gobi & Jasmine Rice",
                quantityKg = 30.0,
                estimatedPortions = 65,
                prepOrPackedTime = now - (1 * hour),
                expiryDeadline = now + (3 * hour),
                storageCondition = StorageCondition.HOT_HOLDING,
                dietaryFlags = "Vegan, Halal, Nut-Free",
                pickupAddress = "720 Valencia St, Mission",
                pickupLatitude = 37.7612,
                pickupLongitude = -122.4215,
                pickupWindow = "15:00 - 16:30",
                status = DonationStatus.AVAILABLE,
                specialHandlingNotes = "Insulated thermal containers ready."
            )
        )
        dao.insertDonations(seedDonations)

        // 2. Initial Recipient Organizations
        val seedRecipients = listOf(
            RecipientOrg(
                name = "Hope House Homeless Shelter",
                orgType = "Homeless Shelter",
                currentDailyNeedPortions = 160,
                fulfilledTodayPortions = 45,
                storageCapabilities = "Walk-in Refrigerator, Deep Freezers, Heated Serving Line",
                dietaryRequirements = "Any (Halal and Diabetic accommodations welcome)",
                deliveryAddress = "543 Minna St, Central City",
                latitude = 37.7801,
                longitude = -122.4095,
                operatingHours = "Open 24/7",
                urgencyPriority = 9,
                contactPhone = "(415) 555-0192",
                mealDistributionTime = "Dinner Service 18:00"
            ),
            RecipientOrg(
                name = "St. Jude Community Kitchen",
                orgType = "Community Kitchen",
                currentDailyNeedPortions = 130,
                fulfilledTodayPortions = 40,
                storageCapabilities = "Walk-in Chiller, Large Dry Pantry",
                dietaryRequirements = "Vegetarian preference, High-Protein",
                deliveryAddress = "182 6th St, SOMA",
                latitude = 37.7811,
                longitude = -122.4072,
                operatingHours = "07:00 - 19:30",
                urgencyPriority = 8,
                contactPhone = "(415) 555-0144",
                mealDistributionTime = "Dinner Service 17:30"
            ),
            RecipientOrg(
                name = "Sunrise Senior Food Pantry",
                orgType = "Senior Day Center",
                currentDailyNeedPortions = 80,
                fulfilledTodayPortions = 30,
                storageCapabilities = "Reach-in Refrigerators, Dry Racks",
                dietaryRequirements = "Soft foods, Low sodium, Fresh fruits & Dairy",
                deliveryAddress = "1020 Geary Blvd, Tenderloin",
                latitude = 37.7865,
                longitude = -122.4210,
                operatingHours = "09:00 - 18:00",
                urgencyPriority = 7,
                contactPhone = "(415) 555-0178",
                mealDistributionTime = "Evening Grocery Bags 16:30"
            ),
            RecipientOrg(
                name = "Unity Youth Center Kitchen",
                orgType = "Youth & Family Hub",
                currentDailyNeedPortions = 95,
                fulfilledTodayPortions = 20,
                storageCapabilities = "Refrigerated Prep Table, Warm Holding Station",
                dietaryRequirements = "Nut-Free, Kid-friendly nutritious meals",
                deliveryAddress = "250 Potrero Ave, Mission",
                latitude = 37.7651,
                longitude = -122.4068,
                operatingHours = "11:00 - 20:00",
                urgencyPriority = 9,
                contactPhone = "(415) 555-0211",
                mealDistributionTime = "After-School Dinner 17:00"
            ),
            RecipientOrg(
                name = "Haven Refugee Care Hub",
                orgType = "Resettlement Center",
                currentDailyNeedPortions = 75,
                fulfilledTodayPortions = 15,
                storageCapabilities = "Dry Shelving, Medium Cold Storage",
                dietaryRequirements = "Strictly Halal / Vegetarian, Fresh Produce",
                deliveryAddress = "330 Ellis St, Civic Center",
                latitude = 37.7850,
                longitude = -122.4120,
                operatingHours = "08:30 - 19:00",
                urgencyPriority = 8,
                contactPhone = "(415) 555-0163",
                mealDistributionTime = "Family Meal Boxes 17:00"
            )
        )
        dao.insertRecipients(seedRecipients)

        // 3. Initial Active Rescue Missions
        val seedMissions = listOf(
            RescueMission(
                missionCode = "FL-MISSION-204",
                vehicleType = "Refrigerated Cargo Van",
                vehicleCapacityKg = 350.0,
                currentLoadKg = 45.0,
                donationIds = "3",
                donorNames = "Pacific Ocean Bento & Bowl",
                recipientOrgName = "Hope House Homeless Shelter",
                recipientAddress = "543 Minna St, Central City",
                totalDistanceKm = 3.2,
                estimatedDurationMin = 18,
                co2SavedKg = 112.5,
                status = MissionStatus.EN_ROUTE_PICKUP,
                driverOrVolunteerName = "Elena Vasquez (Van #02)",
                waypointsSummary = "Depot -> Pacific Ocean Bento -> Hope House",
                scheduledPickupTime = "15:00 Today"
            ),
            RescueMission(
                missionCode = "FL-MISSION-198",
                vehicleType = "Electric Cargo Van",
                vehicleCapacityKg = 250.0,
                currentLoadKg = 52.0,
                donationIds = "2",
                donorNames = "Grand Regency Banquets",
                recipientOrgName = "Unity Youth Center Kitchen",
                recipientAddress = "250 Potrero Ave, Mission",
                totalDistanceKm = 2.8,
                estimatedDurationMin = 15,
                co2SavedKg = 130.0,
                status = MissionStatus.OUT_FOR_DELIVERY,
                driverOrVolunteerName = "Marcus Brody (EcoVan #01)",
                waypointsSummary = "Grand Regency -> Unity Youth Hub",
                scheduledPickupTime = "14:15 Today"
            )
        )
        dao.insertMissions(seedMissions)

        // 4. Initial AI Surplus Predictions
        val seedPredictions = listOf(
            SurplusPrediction(
                donorName = "Metro Supermarket Deli",
                donorType = "Supermarket",
                predictedSurplusKg = 45.0,
                predictedPortions = 90,
                predictedCategory = FoodCategory.BAKED_GOODS,
                confidenceScore = 0.92f,
                primaryFactors = "Wednesday evening clearance + rain forecast lowering walk-in foot traffic by 24%",
                recommendedAction = "Pre-alert St. Jude Community Kitchen for 20:30 pickup",
                forecastPeriod = "Tonight 20:30"
            ),
            SurplusPrediction(
                donorName = "Moscone Convention Catering",
                donorType = "Event Caterer",
                predictedSurplusKg = 85.0,
                predictedPortions = 175,
                predictedCategory = FoodCategory.PREPARED_MEALS,
                confidenceScore = 0.88f,
                primaryFactors = "750-attendee tech summit concluding afternoon buffet service",
                recommendedAction = "Reserve 2 high-capacity vans for Hope House & Unity Hub",
                forecastPeriod = "Tomorrow 15:00"
            ),
            SurplusPrediction(
                donorName = "Artisan Hearth Bakery",
                donorType = "Bakery",
                predictedSurplusKg = 30.0,
                predictedPortions = 60,
                predictedCategory = FoodCategory.BAKED_GOODS,
                confidenceScore = 0.85f,
                primaryFactors = "Weekly surplus cycle from bulk sourdough levain bake",
                recommendedAction = "Auto-assign to Sunrise Senior Pantry breakfast bags",
                forecastPeriod = "Tomorrow 08:30"
            )
        )
        dao.insertPredictions(seedPredictions)
    }

    // --- Intelligent Matching Engine ---
    fun computeMatchingRecommendations(
        donations: List<SurplusDonation>,
        recipients: List<RecipientOrg>
    ): List<MatchingRecommendation> {
        val now = System.currentTimeMillis()
        val recommendations = mutableListOf<MatchingRecommendation>()

        val available = donations.filter { it.status == DonationStatus.AVAILABLE }
        if (available.isEmpty() || recipients.isEmpty()) return emptyList()

        for (donation in available) {
            // Find best matching recipient for this donation
            var bestRecipient: RecipientOrg? = null
            var bestBreakdown: MatchingScoreBreakdown? = null
            var highestScore = -1

            for (recipient in recipients) {
                val breakdown = calculateScoreBreakdown(donation, recipient, now)
                if (breakdown.totalScore > highestScore) {
                    highestScore = breakdown.totalScore
                    bestRecipient = recipient
                    bestBreakdown = breakdown
                }
            }

            if (bestRecipient != null && bestBreakdown != null) {
                recommendations.add(
                    MatchingRecommendation(
                        donation = donation,
                        recipient = bestRecipient,
                        breakdown = bestBreakdown
                    )
                )
            }
        }

        // Sort by urgency and score
        return recommendations.sortedByDescending { it.breakdown.totalScore }
    }

    private fun calculateScoreBreakdown(
        donation: SurplusDonation,
        recipient: RecipientOrg,
        currentTime: Long
    ): MatchingScoreBreakdown {
        // 1. Perishability & Expiry Urgency (0 - 100)
        val remainingHours = (donation.expiryDeadline - currentTime) / (3600.0 * 1000.0)
        val perishabilityScore = when {
            remainingHours <= 1.5 -> 98
            remainingHours <= 2.5 -> 92
            remainingHours <= 4.0 -> 82
            remainingHours <= 8.0 -> 70
            remainingHours <= 16.0 -> 55
            else -> 40
        }

        // 2. Distance and Transit Time
        val distKm = haversineDistance(
            donation.pickupLatitude, donation.pickupLongitude,
            recipient.latitude, recipient.longitude
        )
        val transitMinutes = (distKm * 3.5).roundToInt().coerceAtLeast(8) // urban city speed
        val distanceScore = when {
            distKm <= 2.0 -> 95
            distKm <= 4.0 -> 85
            distKm <= 7.0 -> 70
            distKm <= 12.0 -> 50
            else -> 30
        }

        // 3. Storage Compatibility (0 - 100)
        var storageScore = 80
        val caps = recipient.storageCapabilities.lowercase()
        when (donation.storageCondition) {
            StorageCondition.REFRIGERATED -> {
                storageScore = if (caps.contains("fridge") || caps.contains("chiller") || caps.contains("refrigerator")) 95 else 30
            }
            StorageCondition.FROZEN -> {
                storageScore = if (caps.contains("freeze")) 95 else 20
            }
            StorageCondition.HOT_HOLDING -> {
                // Hot meals need immediate serving or warming lines
                storageScore = if (caps.contains("heated") || caps.contains("warm") || recipient.mealDistributionTime.contains("Dinner")) 95 else 60
            }
            StorageCondition.AMBIENT -> {
                storageScore = 90
            }
        }

        // 4. Capacity & Need Deficit Match (0 - 100)
        val deficit = (recipient.currentDailyNeedPortions - recipient.fulfilledTodayPortions).coerceAtLeast(0)
        val capacityScore = when {
            deficit <= 0 -> 25
            donation.estimatedPortions <= deficit -> 95
            donation.estimatedPortions <= deficit * 1.3 -> 80
            else -> 60
        }

        // 5. Equity and Hunger Fairness Score (0 - 100)
        val deficitRatio = if (recipient.currentDailyNeedPortions > 0) {
            deficit.toDouble() / recipient.currentDailyNeedPortions
        } else 0.5
        val equityScore = (recipient.urgencyPriority * 5 + (deficitRatio * 50)).roundToInt().coerceIn(40, 100)

        // Weighted Total Score
        val total = (
            perishabilityScore * 0.35 +
            equityScore * 0.25 +
            distanceScore * 0.20 +
            storageScore * 0.10 +
            capacityScore * 0.10
        ).roundToInt().coerceIn(10, 99)

        val rationale = "Match calculated with ${(distKm * 10).roundToInt() / 10.0} km transit (~$transitMinutes mins). Recipient requires $deficit portions today. Cold/thermal storage rated at $storageScore% compatibility."

        return MatchingScoreBreakdown(
            totalScore = total,
            perishabilityScore = perishabilityScore,
            distanceScore = distanceScore,
            capacityMatchScore = capacityScore,
            storageSafetyScore = storageScore,
            equityFairnessScore = equityScore,
            distanceKm = (distKm * 10).roundToInt() / 10.0,
            estimatedTransitMin = transitMinutes,
            reasoningExplanation = rationale
        )
    }

    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // --- Dispatch & Mission Management ---
    suspend fun createAndDispatchMission(
        donation: SurplusDonation,
        recipient: RecipientOrg,
        vehicleType: String = "Refrigerated Cargo Van"
    ): Long = withContext(Dispatchers.IO) {
        val dist = haversineDistance(
            donation.pickupLatitude, donation.pickupLongitude,
            recipient.latitude, recipient.longitude
        )
        val distRounded = (dist * 10).roundToInt() / 10.0
        val transitMinutes = (dist * 3.5).roundToInt().coerceAtLeast(12)
        val missionCode = "FL-RES-${System.currentTimeMillis() % 10000}"

        val mission = RescueMission(
            missionCode = missionCode,
            vehicleType = vehicleType,
            vehicleCapacityKg = 300.0,
            currentLoadKg = donation.quantityKg,
            donationIds = donation.id.toString(),
            donorNames = donation.donorName,
            recipientOrgName = recipient.name,
            recipientAddress = recipient.deliveryAddress,
            totalDistanceKm = distRounded,
            estimatedDurationMin = transitMinutes,
            co2SavedKg = donation.quantityKg * 2.5,
            status = MissionStatus.SCHEDULED,
            driverOrVolunteerName = "Volunteer Dispatcher",
            waypointsSummary = "${donation.donorName} -> ${recipient.name}",
            scheduledPickupTime = "Immediately Scheduled"
        )
        val missionId = dao.insertMission(mission)

        // Update donation
        dao.updateDonationStatus(
            id = donation.id,
            status = DonationStatus.MATCHED,
            recipientId = recipient.id,
            recipientName = recipient.name,
            missionId = missionId
        )

        // Increment recipient fulfilled count
        dao.incrementFulfilledPortions(recipient.id, donation.estimatedPortions)

        missionId
    }

    suspend fun advanceMissionStatus(mission: RescueMission): MissionStatus = withContext(Dispatchers.IO) {
        val nextStatus = when (mission.status) {
            MissionStatus.SCHEDULED -> MissionStatus.EN_ROUTE_PICKUP
            MissionStatus.EN_ROUTE_PICKUP -> MissionStatus.COLLECTED
            MissionStatus.COLLECTED -> MissionStatus.OUT_FOR_DELIVERY
            MissionStatus.OUT_FOR_DELIVERY -> MissionStatus.COMPLETED
            MissionStatus.COMPLETED -> MissionStatus.COMPLETED
        }

        val updatedMission = mission.copy(status = nextStatus)
        dao.updateMission(updatedMission)

        // Update associated donations
        val donationIdList = mission.donationIds.split(",").mapNotNull { it.trim().toLongOrNull() }
        for (donId in donationIdList) {
            val donStatus = when (nextStatus) {
                MissionStatus.SCHEDULED, MissionStatus.EN_ROUTE_PICKUP -> DonationStatus.MATCHED
                MissionStatus.COLLECTED, MissionStatus.OUT_FOR_DELIVERY -> DonationStatus.IN_TRANSIT
                MissionStatus.COMPLETED -> DonationStatus.DELIVERED
            }
            dao.updateDonationStatus(
                id = donId,
                status = donStatus,
                recipientId = null,
                recipientName = mission.recipientOrgName,
                missionId = mission.id
            )
        }

        nextStatus
    }

    suspend fun addNewDonation(
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
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val deadline = now + (shelfLifeHours * 3600 * 1000L).toLong()

        val donation = SurplusDonation(
            donorName = donorName,
            donorType = donorType,
            foodCategory = category,
            foodItemName = itemName,
            quantityKg = quantityKg,
            estimatedPortions = portions,
            prepOrPackedTime = now,
            expiryDeadline = deadline,
            storageCondition = storageCondition,
            dietaryFlags = dietaryFlags.ifBlank { "Standard / General" },
            pickupAddress = pickupAddress.ifBlank { "Central Food District" },
            pickupLatitude = 37.7749 + (Math.random() - 0.5) * 0.04,
            pickupLongitude = -122.4194 + (Math.random() - 0.5) * 0.04,
            pickupWindow = "Next 2 Hours",
            status = DonationStatus.AVAILABLE,
            specialHandlingNotes = specialNotes
        )
        dao.insertDonation(donation)
    }

    suspend fun getGeminiRationale(
        donation: SurplusDonation,
        recipient: RecipientOrg,
        breakdown: MatchingScoreBreakdown
    ): String {
        return GeminiPredictiveEngine.explainMatchingRationale(donation, recipient, breakdown)
    }

    suspend fun queryAssistant(userQuery: String): String {
        val donations = availableDonations.first()
        val missions = activeMissions.first()
        val recipients = allRecipients.first()

        val summary = """
            Available Surplus: ${donations.size} batches (${donations.sumOf { it.quantityKg }} kg, ${donations.sumOf { it.estimatedPortions }} meals).
            Urgent (<2h remaining): ${donations.count { (it.expiryDeadline - System.currentTimeMillis()) < 2 * 3600 * 1000L }} batches.
            Active Missions: ${missions.size} running.
            Shelters in Highest Deficit: ${recipients.sortedByDescending { it.currentDailyNeedPortions - it.fulfilledTodayPortions }.take(2).joinToString { "${it.name} (${it.currentDailyNeedPortions - it.fulfilledTodayPortions} unfulfilled)" }}.
        """.trimIndent()

        return GeminiPredictiveEngine.queryCoordinatorAssistant(userQuery, summary)
    }

    companion object {
        @Volatile
        private var INSTANCE: FoodLoopRepository? = null

        fun getInstance(context: Context): FoodLoopRepository {
            return INSTANCE ?: synchronized(this) {
                val db = FoodLoopDatabase.getDatabase(context)
                val repo = FoodLoopRepository(db.foodLoopDao())
                INSTANCE = repo
                // Trigger non-blocking seed
                CoroutineScope(Dispatchers.IO).launch {
                    repo.initializeDatabaseIfEmpty()
                }
                repo
            }
        }
    }
}
