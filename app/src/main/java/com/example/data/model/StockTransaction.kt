package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

object TransactionType {
    const val PURCHASE = "PURCHASE"
    const val SALE = "SALE"
    const val ADJUSTMENT_ADD = "ADJUSTMENT_ADD"
    const val ADJUSTMENT_REDUCE = "ADJUSTMENT_REDUCE"
}

@Entity(tableName = "stock_transactions")
data class StockTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String = TransactionType.SALE, // PURCHASE, SALE, ADJUSTMENT_ADD, ADJUSTMENT_REDUCE
    val vaccineId: Long = 0L,
    val vaccineName: String = "",
    val vendorId: Long? = null,
    val vendorName: String? = null,
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,         // Purchase price per unit for purchase, or Selling price per unit for sale
    val costPricePerUnit: Double = 0.0,  // The cost basis per unit for profit calculation
    val totalAmount: Double = 0.0,       // unitPrice * quantity
    val profit: Double = 0.0,      // (unitPrice - costPricePerUnit) * quantity for sales, 0.0 for purchases
    val batchNumber: String = "",
    val expiryDate: String = "",
    val referenceOrCustomer: String = "", // Invoice # for purchase, or Patient/Customer for sale
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)
