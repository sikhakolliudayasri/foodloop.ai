package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FoodCategory
import com.example.data.model.RecipientOrg
import com.example.data.model.StorageCondition
import com.example.data.model.SurplusDonation
import com.example.data.repository.FoodLoopRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FoodLoop AI", appName)
    }

    @Test
    fun `matching algorithm computes perishability and distance scores`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FoodLoopRepository.getInstance(context)

        val now = System.currentTimeMillis()
        val donation = SurplusDonation(
            donorName = "Test Bakery",
            donorType = "Bakery",
            foodCategory = FoodCategory.BAKED_GOODS,
            foodItemName = "Bread",
            quantityKg = 20.0,
            estimatedPortions = 40,
            prepOrPackedTime = now - 3600000,
            expiryDeadline = now + 7200000, // 2 hours remaining -> high urgency
            storageCondition = StorageCondition.AMBIENT,
            dietaryFlags = "Vegan",
            pickupAddress = "Market St",
            pickupLatitude = 37.78,
            pickupLongitude = -122.41,
            pickupWindow = "Now"
        )

        val recipient = RecipientOrg(
            name = "Test Shelter",
            orgType = "Shelter",
            currentDailyNeedPortions = 100,
            fulfilledTodayPortions = 20,
            storageCapabilities = "Pantry",
            dietaryRequirements = "Any",
            deliveryAddress = "Mission St",
            latitude = 37.782,
            longitude = -122.412,
            operatingHours = "24/7",
            urgencyPriority = 9,
            contactPhone = "555-0100",
            mealDistributionTime = "18:00"
        )

        val recommendations = repo.computeMatchingRecommendations(
            listOf(donation),
            listOf(recipient)
        )

        assertTrue(recommendations.isNotEmpty())
        val topMatch = recommendations.first()
        assertEquals(recipient.name, topMatch.recipient.name)
        assertTrue("Total score should be high", topMatch.breakdown.totalScore >= 70)
        assertTrue("Urgency score should reflect near expiry", topMatch.breakdown.perishabilityScore >= 80)
    }
}
