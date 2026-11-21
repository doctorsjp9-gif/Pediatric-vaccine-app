package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Vaccine
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    @Query("SELECT * FROM vaccines ORDER BY name ASC")
    fun getAllVaccines(): Flow<List<Vaccine>>

    @Query("SELECT * FROM vaccines ORDER BY name ASC")
    suspend fun getAllVaccinesList(): List<Vaccine>

    @Query("SELECT * FROM vaccines WHERE id = :id LIMIT 1")
    suspend fun getVaccineById(id: Long): Vaccine?

    @Query("SELECT * FROM vaccines WHERE currentStock <= minStockAlert ORDER BY currentStock ASC")
    fun getLowStockVaccines(): Flow<List<Vaccine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccine(vaccine: Vaccine): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vaccines: List<Vaccine>): List<Long>

    @Update
    suspend fun updateVaccine(vaccine: Vaccine)

    @Query("UPDATE vaccines SET currentStock = :newStock WHERE id = :vaccineId")
    suspend fun updateStock(vaccineId: Long, newStock: Int)

    @Delete
    suspend fun deleteVaccine(vaccine: Vaccine)

    @Query("DELETE FROM vaccines")
    suspend fun deleteAll()

    @Query("DELETE FROM vaccines WHERE currentStock <= 0")
    suspend fun deleteOutOfStockVaccines()
}
