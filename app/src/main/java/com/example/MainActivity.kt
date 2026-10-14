package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.FoodLoopTab
import com.example.ui.FoodLoopViewModel
import com.example.ui.components.AiAssistantDialog
import com.example.ui.components.AiRationaleDialog
import com.example.ui.components.FoodLoopBottomNav
import com.example.ui.components.FoodLoopTopBar
import com.example.ui.components.LogSurplusDialog
import com.example.ui.screens.DispatchHubScreen
import com.example.ui.screens.ImpactAnalyticsScreen
import com.example.ui.screens.RouteMissionsScreen
import com.example.ui.screens.SmartMatchingScreen
import com.example.ui.screens.SurplusDonationsScreen
import com.example.ui.theme.FoodLoopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoodLoopTheme {
                FoodLoopApp()
            }
        }
    }
}

@Composable
fun FoodLoopApp(viewModel: FoodLoopViewModel = viewModel()) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val availableDonations by viewModel.availableDonations.collectAsStateWithLifecycle()
    val filteredDonations by viewModel.filteredDonations.collectAsStateWithLifecycle()
    val allRecipients by viewModel.allRecipients.collectAsStateWithLifecycle()
    val allMissions by viewModel.allMissions.collectAsStateWithLifecycle()
    val activeMissions by viewModel.activeMissions.collectAsStateWithLifecycle()
    val predictions by viewModel.allPredictions.collectAsStateWithLifecycle()
    val impactSummary by viewModel.impactSummary.collectAsStateWithLifecycle()
    val matchingRecommendations by viewModel.matchingRecommendations.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()

    val selectedRecommendation by viewModel.selectedRecommendation.collectAsStateWithLifecycle()
    val aiRationaleText by viewModel.aiRationaleText.collectAsStateWithLifecycle()
    val isLoadingRationale by viewModel.isLoadingRationale.collectAsStateWithLifecycle()

    val isLogSurplusDialogVisible by viewModel.isLogSurplusDialogVisible.collectAsStateWithLifecycle()
    val isAssistantDialogVisible by viewModel.isAssistantDialogVisible.collectAsStateWithLifecycle()
    val assistantResponse by viewModel.assistantResponse.collectAsStateWithLifecycle()
    val isLoadingAssistant by viewModel.isLoadingAssistant.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.userFeedbackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Handle feedback notifications
    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    // Predictive Back navigation to Hub
    BackHandler(enabled = currentTab != FoodLoopTab.DISPATCH_HUB) {
        viewModel.selectTab(FoodLoopTab.DISPATCH_HUB)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            FoodLoopTopBar(
                onOpenAiAssistant = { viewModel.showAssistantDialog(true) },
                onLogSurplusClick = { viewModel.showLogSurplusDialog(true) }
            )
        },
        bottomBar = {
            FoodLoopBottomNav(
                selectedTab = currentTab,
                onTabSelected = { viewModel.selectTab(it) },
                availableSurplusCount = availableDonations.size,
                activeMissionsCount = activeMissions.size
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                FoodLoopTab.DISPATCH_HUB -> {
                    val now = System.currentTimeMillis()
                    val urgentDonations = availableDonations.filter {
                        (it.expiryDeadline - now) <= (3 * 3600 * 1000L)
                    }

                    DispatchHubScreen(
                        impact = impactSummary,
                        urgentDonations = urgentDonations,
                        topRecommendations = matchingRecommendations,
                        activeMissions = activeMissions,
                        predictions = predictions,
                        onInspectMatch = { viewModel.inspectAiRationale(it) },
                        onInstantDispatch = { viewModel.dispatchRescueMission(it) },
                        onViewAllSurplus = { viewModel.selectTab(FoodLoopTab.SURPLUS_LOG) },
                        onViewAllMissions = { viewModel.selectTab(FoodLoopTab.MISSIONS) },
                        onAdvanceMission = { viewModel.advanceMission(it) }
                    )
                }

                FoodLoopTab.SURPLUS_LOG -> {
                    SurplusDonationsScreen(
                        donations = filteredDonations,
                        searchQuery = searchQuery,
                        selectedCategory = categoryFilter,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onCategorySelect = { viewModel.setCategoryFilter(it) },
                        onLogSurplusClick = { viewModel.showLogSurplusDialog(true) },
                        onMatchDonationClick = { donation ->
                            // Find match for this donation if available
                            val rec = matchingRecommendations.firstOrNull { it.donation.id == donation.id }
                            if (rec != null) {
                                viewModel.inspectAiRationale(rec)
                            } else {
                                viewModel.selectTab(FoodLoopTab.SMART_MATCH)
                            }
                        }
                    )
                }

                FoodLoopTab.SMART_MATCH -> {
                    SmartMatchingScreen(
                        recommendations = matchingRecommendations,
                        onInspectMatch = { viewModel.inspectAiRationale(it) },
                        onInstantDispatch = { viewModel.dispatchRescueMission(it) }
                    )
                }

                FoodLoopTab.MISSIONS -> {
                    RouteMissionsScreen(
                        missions = allMissions,
                        onAdvanceMission = { viewModel.advanceMission(it) }
                    )
                }

                FoodLoopTab.IMPACT_AI -> {
                    ImpactAnalyticsScreen(
                        impact = impactSummary,
                        recipients = allRecipients,
                        predictions = predictions,
                        onOpenAiAssistant = { viewModel.showAssistantDialog(true) }
                    )
                }
            }
        }
    }

    // --- Interactive Dialogs ---
    if (isLogSurplusDialogVisible) {
        LogSurplusDialog(
            onDismiss = { viewModel.showLogSurplusDialog(false) },
            onSubmit = { donorName, donorType, category, itemName, quantityKg, portions, storageCondition, dietaryFlags, pickupAddress, shelfLifeHours, specialNotes ->
                viewModel.logNewSurplus(
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
            }
        )
    }

    selectedRecommendation?.let { recommendation ->
        AiRationaleDialog(
            recommendation = recommendation,
            rationaleText = aiRationaleText,
            isLoading = isLoadingRationale,
            onDismiss = { viewModel.dismissRationaleDialog() },
            onDispatch = { vehicle ->
                viewModel.dispatchRescueMission(recommendation, vehicle)
            }
        )
    }

    if (isAssistantDialogVisible) {
        AiAssistantDialog(
            response = assistantResponse,
            isLoading = isLoadingAssistant,
            onDismiss = { viewModel.showAssistantDialog(false) },
            onAsk = { query ->
                viewModel.askCoordinatorAssistant(query)
            }
        )
    }
}
