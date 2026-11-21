package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Vendor
import kotlinx.coroutines.flow.Flow

@Dao
interface VendorDao {
    @Query("SELECT * FROM vendors ORDER BY name ASC")
    fun getAllVendors(): Flow<List<Vendor>>

    @Query("SELECT * FROM vendors ORDER BY name ASC")
    suspend fun getAllVendorsList(): List<Vendor>

    @Query("SELECT * FROM vendors WHERE id = :id LIMIT 1")
    suspend fun getVendorById(id: Long): Vendor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendor(vendor: Vendor): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vendors: List<Vendor>): List<Long>

    @Update
    suspend fun updateVendor(vendor: Vendor)

    @Delete
    suspend fun deleteVendor(vendor: Vendor)

    @Query("DELETE FROM vendors")
    suspend fun deleteAll()
}
