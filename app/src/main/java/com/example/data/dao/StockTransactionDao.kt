package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.StockTransaction
import com.example.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface StockTransactionDao {
    @Query("SELECT * FROM stock_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsList(): List<StockTransaction>

    @Query("SELECT * FROM stock_transactions WHERE type = :type ORDER BY timestamp DESC")
    fun getTransactionsByType(type: String): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions WHERE vaccineId = :vaccineId ORDER BY timestamp DESC")
    fun getTransactionsForVaccine(vaccineId: Long): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions WHERE type = 'SALE' ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions WHERE type = 'PURCHASE' ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<StockTransaction>>

    @Query("SELECT * FROM stock_transactions WHERE type = 'SALE' AND timestamp >= :sinceTimestamp")
    suspend fun getSalesSince(sinceTimestamp: Long): List<StockTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: StockTransaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<StockTransaction>)

    @Query("DELETE FROM stock_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("DELETE FROM stock_transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM stock_transactions WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldTransactions(beforeTimestamp: Long)
}
