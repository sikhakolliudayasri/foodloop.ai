package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.DonationStatus
import com.example.data.model.FoodCategory
import com.example.data.model.MissionStatus
import com.example.data.model.RecipientOrg
import com.example.data.model.RescueMission
import com.example.data.model.StorageCondition
import com.example.data.model.SurplusDonation
import com.example.data.model.SurplusPrediction

class Converters {
    @TypeConverter
    fun fromFoodCategory(value: FoodCategory): String = value.name

    @TypeConverter
    fun toFoodCategory(value: String): FoodCategory = FoodCategory.valueOf(value)

    @TypeConverter
    fun fromStorageCondition(value: StorageCondition): String = value.name

    @TypeConverter
    fun toStorageCondition(value: String): StorageCondition = StorageCondition.valueOf(value)

    @TypeConverter
    fun fromDonationStatus(value: DonationStatus): String = value.name

    @TypeConverter
    fun toDonationStatus(value: String): DonationStatus = DonationStatus.valueOf(value)

    @TypeConverter
    fun fromMissionStatus(value: MissionStatus): String = value.name

    @TypeConverter
    fun toMissionStatus(value: String): MissionStatus = MissionStatus.valueOf(value)
}

@Database(
    entities = [
        SurplusDonation::class,
        RecipientOrg::class,
        RescueMission::class,
        SurplusPrediction::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FoodLoopDatabase : RoomDatabase() {
    abstract fun foodLoopDao(): FoodLoopDao

    companion object {
        @Volatile
        private var INSTANCE: FoodLoopDatabase? = null

        fun getDatabase(context: Context): FoodLoopDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FoodLoopDatabase::class.java,
                    "foodloop_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
