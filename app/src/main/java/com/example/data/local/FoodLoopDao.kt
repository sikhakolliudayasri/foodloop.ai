package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DonationStatus
import com.example.data.model.RecipientOrg
import com.example.data.model.RescueMission
import com.example.data.model.SurplusDonation
import com.example.data.model.SurplusPrediction
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLoopDao {

    // --- Surplus Donations ---
    @Query("SELECT * FROM surplus_donations ORDER BY expiryDeadline ASC")
    fun getAllDonations(): Flow<List<SurplusDonation>>

    @Query("SELECT * FROM surplus_donations WHERE status = 'AVAILABLE' ORDER BY expiryDeadline ASC")
    fun getAvailableDonations(): Flow<List<SurplusDonation>>

    @Query("SELECT * FROM surplus_donations WHERE id = :id LIMIT 1")
    suspend fun getDonationById(id: Long): SurplusDonation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDonation(donation: SurplusDonation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDonations(donations: List<SurplusDonation>)

    @Update
    suspend fun updateDonation(donation: SurplusDonation)

    @Query("UPDATE surplus_donations SET status = :status, matchedRecipientId = :recipientId, matchedRecipientName = :recipientName, rescueMissionId = :missionId WHERE id = :id")
    suspend fun updateDonationStatus(
        id: Long,
        status: DonationStatus,
        recipientId: Long?,
        recipientName: String?,
        missionId: Long?
    )

    // --- Recipient Organizations ---
    @Query("SELECT * FROM recipient_orgs ORDER BY urgencyPriority DESC")
    fun getAllRecipients(): Flow<List<RecipientOrg>>

    @Query("SELECT * FROM recipient_orgs WHERE id = :id LIMIT 1")
    suspend fun getRecipientById(id: Long): RecipientOrg?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipients(recipients: List<RecipientOrg>)

    @Update
    suspend fun updateRecipient(recipient: RecipientOrg)

    @Query("UPDATE recipient_orgs SET fulfilledTodayPortions = fulfilledTodayPortions + :addedPortions WHERE id = :id")
    suspend fun incrementFulfilledPortions(id: Long, addedPortions: Int)

    // --- Rescue Missions ---
    @Query("SELECT * FROM rescue_missions ORDER BY id DESC")
    fun getAllMissions(): Flow<List<RescueMission>>

    @Query("SELECT * FROM rescue_missions WHERE status != 'COMPLETED' ORDER BY id DESC")
    fun getActiveMissions(): Flow<List<RescueMission>>

    @Query("SELECT * FROM rescue_missions WHERE id = :id LIMIT 1")
    suspend fun getMissionById(id: Long): RescueMission?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMission(mission: RescueMission): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMissions(missions: List<RescueMission>)

    @Update
    suspend fun updateMission(mission: RescueMission)

    // --- Surplus Predictions ---
    @Query("SELECT * FROM surplus_predictions ORDER BY confidenceScore DESC")
    fun getAllPredictions(): Flow<List<SurplusPrediction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPredictions(predictions: List<SurplusPrediction>)

    @Query("DELETE FROM surplus_predictions")
    suspend fun clearPredictions()

    // Counts for initialization check
    @Query("SELECT COUNT(*) FROM surplus_donations")
    suspend fun getDonationCount(): Int
}
