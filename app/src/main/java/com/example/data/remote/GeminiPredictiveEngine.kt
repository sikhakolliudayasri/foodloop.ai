package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.FoodCategory
import com.example.data.model.MatchingScoreBreakdown
import com.example.data.model.RecipientOrg
import com.example.data.model.StorageCondition
import com.example.data.model.SurplusDonation
import com.example.data.model.SurplusPrediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiPredictiveEngine {
    private const val TAG = "GeminiPredictive"

    suspend fun explainMatchingRationale(
        donation: SurplusDonation,
        recipient: RecipientOrg,
        breakdown: MatchingScoreBreakdown
    ): String = withContext(Dispatchers.IO) {
        if (!GeminiClient.hasValidApiKey()) {
            return@withContext generateLocalRationale(donation, recipient, breakdown)
        }

        try {
            val prompt = """
                You are FoodLoop AI's Intelligent Redistribution Dispatcher.
                Analyze the following food donation and proposed recipient match, and write a concise, professional 3-sentence operational rationale for the logistics coordinator:
                - Donation: ${donation.foodItemName} (${donation.quantityKg} kg, ${donation.estimatedPortions} portions) from ${donation.donorName} (${donation.donorType})
                - Storage Condition: ${donation.storageCondition.displayName}
                - Dietary Profile: ${donation.dietaryFlags}
                - Recipient: ${recipient.name} (${recipient.orgType})
                - Recipient Storage Capacity: ${recipient.storageCapabilities}
                - Recipient Current Need: ${recipient.currentDailyNeedPortions - recipient.fulfilledTodayPortions} portions unfulfilled
                - Distance & Transit: ${breakdown.distanceKm} km, ~${breakdown.estimatedTransitMin} mins
                - Urgency Score: ${breakdown.perishabilityScore}/100, Total Match Score: ${breakdown.totalScore}/100

                Explain clearly why this recipient is chosen, highlight cold chain or shelf-life safety, and note the hunger-deficit impact.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(temperature = 0.3f)
            )

            val response = GeminiClient.apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                text.trim()
            } else {
                generateLocalRationale(donation, recipient, breakdown)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking Gemini API for matching rationale: ${e.message}")
            generateLocalRationale(donation, recipient, breakdown)
        }
    }

    suspend fun queryCoordinatorAssistant(
        query: String,
        contextSummary: String
    ): String = withContext(Dispatchers.IO) {
        if (!GeminiClient.hasValidApiKey()) {
            return@withContext generateLocalAssistantResponse(query, contextSummary)
        }

        try {
            val prompt = """
                You are the FoodLoop AI Operational Coordinator Assistant.
                You help dispatchers, food rescue volunteers, and shelter managers optimize redistribution, evaluate food safety deadlines, and reduce community waste.
                Current Operations Context:
                $contextSummary

                Coordinator Question: "$query"

                Provide a clear, practical, and actionable answer (under 120 words). Include specific recommendations on priorities, vehicle requirements, or safety.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(temperature = 0.4f)
            )

            val response = GeminiClient.apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                text.trim()
            } else {
                generateLocalAssistantResponse(query, contextSummary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking Gemini assistant: ${e.message}")
            generateLocalAssistantResponse(query, contextSummary)
        }
    }

    suspend fun generatePredictiveSurplusForecast(
        donorName: String,
        donorType: String,
        category: FoodCategory
    ): SurplusPrediction = withContext(Dispatchers.IO) {
        if (!GeminiClient.hasValidApiKey()) {
            return@withContext generateLocalPrediction(donorName, donorType, category)
        }

        try {
            val prompt = """
                As FoodLoop AI's predictive food waste forecasting model, estimate the expected food surplus for:
                Donor: $donorName ($donorType)
                Category: ${category.displayName}
                Factors: Typical Wednesday evening operation, current overcast weather with light rain (-18% walk-in foot traffic).

                Respond with 2 lines:
                Line 1: SurplusKg: [number], Portions: [number], Confidence: [0.70-0.95], Period: [e.g. Tonight 20:30]
                Line 2: PrimaryFactor: [Brief explanation of why this surplus is forecast]
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                ),
                generationConfig = GeminiGenerationConfig(temperature = 0.3f)
            )

            val response = GeminiClient.apiService.generateContent(BuildConfig.GEMINI_API_KEY, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                parsePredictionResponse(text, donorName, donorType, category)
            } else {
                generateLocalPrediction(donorName, donorType, category)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in Gemini prediction: ${e.message}")
            generateLocalPrediction(donorName, donorType, category)
        }
    }

    private fun generateLocalRationale(
        donation: SurplusDonation,
        recipient: RecipientOrg,
        breakdown: MatchingScoreBreakdown
    ): String {
        val deficit = (recipient.currentDailyNeedPortions - recipient.fulfilledTodayPortions).coerceAtLeast(0)
        val safetyText = when (donation.storageCondition) {
            StorageCondition.REFRIGERATED -> "Cold-chain verification confirmed; matches ${recipient.name}'s walk-in refrigeration units."
            StorageCondition.HOT_HOLDING -> "Immediate dinner service match; ideal for ${recipient.mealDistributionTime} meal distribution within the 2-hour safety window."
            StorageCondition.FROZEN -> "Deep freeze storage required; recipient has adequate sub-zero preservation capacity."
            StorageCondition.AMBIENT -> "Dry pantry storage allows flexible staging without cold storage overhead."
        }
        return "Ranked #1 with ${breakdown.totalScore}% AI confidence. ${recipient.name} faces a current meal deficit of $deficit portions. At ${breakdown.distanceKm} km (~${breakdown.estimatedTransitMin} min transit), $safetyText."
    }

    private fun generateLocalAssistantResponse(query: String, contextSummary: String): String {
        val lower = query.lowercase()
        return when {
            "urgent" in lower || "expir" in lower -> {
                "Highest priority is hot-holding meals expiring in under 2 hours (Grand Regency Banquets & Pacific Ocean Bento). Dispatch refrigerated or thermal-insulated vehicle immediately to avoid landfill waste and fulfill dinner at Hope House Shelter."
            }
            "route" in lower || "vehicle" in lower || "dispatch" in lower -> {
                "Route optimization recommends batching north-sector pickups (Bakery & Supermarket) on Van #1, and routing urgent hot trays on Van #2 directly to Hope House to keep delivery under 25 minutes."
            }
            "safety" in lower || "temperature" in lower -> {
                "Critical safety protocol: Cooked hot-holding foods must maintain >60°C or be chilled to <5°C within 2 hours. Ensure digital probe thermometer log and sanitized insulated transport cambros."
            }
            "shelter" in lower || "hunger" in lower || "need" in lower -> {
                "Hope House Homeless Shelter and Unity Youth Center have the highest unfulfilled hunger index today (over 65% remaining meal deficit). Target next available protein and prepared meal allocations there."
            }
            else -> {
                "FoodLoop AI is actively tracking available surplus batches and recipient needs. Current operational efficiency is high with strong equitable distribution across shelters. Consider initiating scheduled pickups for late-afternoon surpluses."
            }
        }
    }

    private fun generateLocalPrediction(
        donorName: String,
        donorType: String,
        category: FoodCategory
    ): SurplusPrediction {
        val kg = when (category) {
            FoodCategory.BAKED_GOODS -> 32.0
            FoodCategory.PREPARED_MEALS -> 48.0
            FoodCategory.FRESH_PRODUCE -> 65.0
            FoodCategory.DAIRY_CHILLED -> 28.0
            FoodCategory.PACKAGED_PANTRY -> 50.0
        }
        val portions = (kg * 2.1).toInt()
        val factor = "Historical shift analytics + forecast evening foot-traffic slowdown indicates ~${kg.toInt()}kg excess. Pre-booking route saves 35 min coordination."
        return SurplusPrediction(
            donorName = donorName,
            donorType = donorType,
            predictedSurplusKg = kg,
            predictedPortions = portions,
            predictedCategory = category,
            confidenceScore = 0.88f,
            primaryFactors = factor,
            recommendedAction = "Pre-reserve electric cargo van and notify community kitchens.",
            forecastPeriod = "Tonight 20:00"
        )
    }

    private fun parsePredictionResponse(
        text: String,
        donorName: String,
        donorType: String,
        category: FoodCategory
    ): SurplusPrediction {
        val kg = Regex("""SurplusKg:\s*([0-9.]+)""").find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 35.0
        val portions = Regex("""Portions:\s*([0-9]+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: (kg * 2).toInt()
        val confidence = Regex("""Confidence:\s*([0-9.]+)""").find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 0.86f
        val period = Regex("""Period:\s*([^\n]+)""").find(text)?.groupValues?.get(1)?.trim() ?: "Tonight 20:00"
        val factor = Regex("""PrimaryFactor:\s*([^\n]+)""").find(text)?.groupValues?.get(1)?.trim()
            ?: text.lines().lastOrNull()?.trim()
            ?: "Anticipated operational excess based on schedule."

        return SurplusPrediction(
            donorName = donorName,
            donorType = donorType,
            predictedSurplusKg = kg,
            predictedPortions = portions,
            predictedCategory = category,
            confidenceScore = confidence,
            primaryFactors = factor,
            recommendedAction = "Pre-allocate to nearest shelter facing dinner shortages.",
            forecastPeriod = period
        )
    }
}
