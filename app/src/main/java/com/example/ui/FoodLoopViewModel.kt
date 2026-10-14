package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FoodCategory
import com.example.data.model.ImpactSummary
import com.example.data.model.MatchingRecommendation
import com.example.data.model.RecipientOrg
import com.example.data.model.RescueMission
import com.example.data.model.StorageCondition
import com.example.data.model.SurplusDonation
import com.example.data.model.SurplusPrediction
import com.example.data.repository.FoodLoopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FoodLoopTab(val title: String, val icon: String) {
    DISPATCH_HUB("Hub", "dashboard"),
    SURPLUS_LOG("Surplus", "inventory_2"),
    SMART_MATCH("AI Match", "auto_awesome"),
    MISSIONS("Missions", "local_shipping"),
    IMPACT_AI("Impact", "insights")
}

class FoodLoopViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FoodLoopRepository.getInstance(application)

    private val _currentTab = MutableStateFlow(FoodLoopTab.DISPATCH_HUB)
    val currentTab: StateFlow<FoodLoopTab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow<FoodCategory?>(null)
    val categoryFilter: StateFlow<FoodCategory?> = _categoryFilter.asStateFlow()

    val allDonations: StateFlow<List<SurplusDonation>> = repository.allDonations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableDonations: StateFlow<List<SurplusDonation>> = repository.availableDonations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecipients: StateFlow<List<RecipientOrg>> = repository.allRecipients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMissions: StateFlow<List<RescueMission>> = repository.allMissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMissions: StateFlow<List<RescueMission>> = repository.activeMissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPredictions: StateFlow<List<SurplusPrediction>> = repository.allPredictions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val impactSummary: StateFlow<ImpactSummary> = repository.impactSummary
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ImpactSummary(
                totalRescuedKg = 0.0,
                totalMealsServed = 0,
                totalCo2AvoidedKg = 0.0,
                totalWaterConservedLiters = 0,
                activeRescuesCount = 0,
                completedRescuesCount = 0,
                availableSurplusCount = 0,
                estimatedFinancialSavedUsd = 0.0
            )
        )

    val matchingRecommendations: StateFlow<List<MatchingRecommendation>> = combine(
        repository.availableDonations,
        repository.allRecipients
    ) { donations, recipients ->
        repository.computeMatchingRecommendations(donations, recipients)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered donations for Surplus Tab
    val filteredDonations: StateFlow<List<SurplusDonation>> = combine(
        allDonations,
        _searchQuery,
        _categoryFilter
    ) { donations, query, cat ->
        donations.filter { donation ->
            val matchesCategory = cat == null || donation.foodCategory == cat
            val matchesQuery = query.isBlank() ||
                    donation.foodItemName.contains(query, ignoreCase = true) ||
                    donation.donorName.contains(query, ignoreCase = true) ||
                    donation.dietaryFlags.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Dialog States
    private val _selectedRecommendation = MutableStateFlow<MatchingRecommendation?>(null)
    val selectedRecommendation: StateFlow<MatchingRecommendation?> = _selectedRecommendation.asStateFlow()

    private val _aiRationaleText = MutableStateFlow<String?>(null)
    val aiRationaleText: StateFlow<String?> = _aiRationaleText.asStateFlow()

    private val _isLoadingRationale = MutableStateFlow(false)
    val isLoadingRationale: StateFlow<Boolean> = _isLoadingRationale.asStateFlow()

    private val _isLogSurplusDialogVisible = MutableStateFlow(false)
    val isLogSurplusDialogVisible: StateFlow<Boolean> = _isLogSurplusDialogVisible.asStateFlow()

    private val _isAssistantDialogVisible = MutableStateFlow(false)
    val isAssistantDialogVisible: StateFlow<Boolean> = _isAssistantDialogVisible.asStateFlow()

    private val _assistantResponse = MutableStateFlow<String?>(null)
    val assistantResponse: StateFlow<String?> = _assistantResponse.asStateFlow()

    private val _isLoadingAssistant = MutableStateFlow(false)
    val isLoadingAssistant: StateFlow<Boolean> = _isLoadingAssistant.asStateFlow()

    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage: StateFlow<String?> = _userFeedbackMessage.asStateFlow()

    fun selectTab(tab: FoodLoopTab) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: FoodCategory?) {
        _categoryFilter.value = category
    }

    fun showLogSurplusDialog(show: Boolean) {
        _isLogSurplusDialogVisible.value = show
    }

    fun showAssistantDialog(show: Boolean) {
        _isAssistantDialogVisible.value = show
    }

    fun inspectAiRationale(recommendation: MatchingRecommendation) {
        _selectedRecommendation.value = recommendation
        _isLoadingRationale.value = true
        _aiRationaleText.value = null

        viewModelScope.launch {
            val text = repository.getGeminiRationale(
                recommendation.donation,
                recommendation.recipient,
                recommendation.breakdown
            )
            _aiRationaleText.value = text
            _isLoadingRationale.value = false
        }
    }

    fun dismissRationaleDialog() {
        _selectedRecommendation.value = null
        _aiRationaleText.value = null
        _isLoadingRationale.value = false
    }

    fun dispatchRescueMission(
        recommendation: MatchingRecommendation,
        vehicleType: String = "Refrigerated Cargo Van"
    ) {
        viewModelScope.launch {
            val missionId = repository.createAndDispatchMission(
                recommendation.donation,
                recommendation.recipient,
                vehicleType
            )
            _userFeedbackMessage.value = "Rescue Mission Dispatched! (#$missionId assigned to ${recommendation.recipient.name})"
            dismissRationaleDialog()
            _currentTab.value = FoodLoopTab.MISSIONS
        }
    }

    fun advanceMission(mission: RescueMission) {
        viewModelScope.launch {
            val next = repository.advanceMissionStatus(mission)
            _userFeedbackMessage.value = "${mission.missionCode} updated to ${next.label}"
        }
    }

    fun logNewSurplus(
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
    ) {
        viewModelScope.launch {
            repository.addNewDonation(
                donorName = donorName,
                donorType = donorType,
                category = category,
                itemName = itemName,
                quantityKg = quantityKg,
                portions = portions,
                storageCondition = storageCondition,
                dietaryFlags = dietaryFlags,
                pickupAddress = pickupAddress,
                shelfLifeHours = shelfLifeHours,
                specialNotes = specialNotes
            )
            _isLogSurplusDialogVisible.value = false
            _userFeedbackMessage.value = "Surplus logged! AI matching algorithms triggered."
        }
    }

    fun askCoordinatorAssistant(query: String) {
        if (query.isBlank()) return
        _isLoadingAssistant.value = true
        _assistantResponse.value = null

        viewModelScope.launch {
            val result = repository.queryAssistant(query)
            _assistantResponse.value = result
            _isLoadingAssistant.value = false
        }
    }

    fun clearFeedbackMessage() {
        _userFeedbackMessage.value = null
    }
}
