package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vaccines")
data class Vaccine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val category: String = "Routine",
    val code: String = "",
    val currentStock: Int = 0,
    val purchasePrice: Double = 0.0, // Default cost price per unit
    val sellingPrice: Double = 0.0,  // Default sell price per unit
    val minStockAlert: Int = 10,     // Low stock reorder threshold
    val storageTemp: String = "2°C to 8°C",
    val unit: String = "doses",
    val notes: String = ""
)
