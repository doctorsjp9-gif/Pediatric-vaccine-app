package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ClinicProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ClinicProfileDao {
    @Query("SELECT * FROM clinic_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<ClinicProfile?>

    @Query("SELECT * FROM clinic_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileSync(): ClinicProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: ClinicProfile)

    @Update
    suspend fun update(profile: ClinicProfile)
}
