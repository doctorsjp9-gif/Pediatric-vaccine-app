package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Vaccine
import com.example.ui.theme.CategoryAdultContainer
import com.example.ui.theme.CategoryAdultOn
import com.example.ui.theme.CategoryEmergencyContainer
import com.example.ui.theme.CategoryEmergencyOn
import com.example.ui.theme.CategoryRoutineContainer
import com.example.ui.theme.CategoryRoutineOn
import com.example.ui.theme.CategorySeasonalContainer
import com.example.ui.theme.CategorySeasonalOn
import com.example.ui.theme.CategoryTravelContainer
import com.example.ui.theme.CategoryTravelOn
import com.example.ui.theme.LossRedContainer
import com.example.ui.theme.OnLossRedContainer
import com.example.ui.theme.OnProfitGreenContainer
import com.example.ui.theme.OnWarningAmberContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmberContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaccineItemCard(
    vaccine: Vaccine,
    onRestockClick: () -> Unit,
    onDispenseClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOutOfStock = vaccine.currentStock <= 0
    val isLowStock = vaccine.currentStock in 1..vaccine.minStockAlert
    val marginPerDose = vaccine.sellingPrice - vaccine.purchasePrice

    val stockBadgeColor = when {
        isOutOfStock -> LossRedContainer
        isLowStock -> WarningAmberContainer
        else -> ProfitGreenContainer
    }

    val stockTextColor = when {
        isOutOfStock -> OnLossRedContainer
        isLowStock -> OnWarningAmberContainer
        else -> OnProfitGreenContainer
    }

    val stockLabel = when {
        isOutOfStock -> "Out of Stock"
        isLowStock -> "Low: ${vaccine.currentStock} ${vaccine.unit}"
        else -> "${vaccine.currentStock} ${vaccine.unit}"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("vaccine_card_${vaccine.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Name, Category, Stock Status Badge & Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vaccine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val (catContainer, catOnColor) = when {
                        vaccine.category.contains("Routine", ignoreCase = true) || vaccine.category.contains("Pediatric", ignoreCase = true) ->
                            Pair(CategoryRoutineContainer, CategoryRoutineOn)
                        vaccine.category.contains("Seasonal", ignoreCase = true) || vaccine.category.contains("Flu", ignoreCase = true) ->
                            Pair(CategorySeasonalContainer, CategorySeasonalOn)
                        vaccine.category.contains("Emergency", ignoreCase = true) || vaccine.category.contains("Rabies", ignoreCase = true) ->
                            Pair(CategoryEmergencyContainer, CategoryEmergencyOn)
                        vaccine.category.contains("Adult", ignoreCase = true) || vaccine.category.contains("Booster", ignoreCase = true) ->
                            Pair(CategoryAdultContainer, CategoryAdultOn)
                        vaccine.category.contains("Travel", ignoreCase = true) ->
                            Pair(CategoryTravelContainer, CategoryTravelOn)
                        else ->
                            Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = catContainer
                        ) {
                            Text(
                                text = vaccine.category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = catOnColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (vaccine.code.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = vaccine.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AcUnit,
                                contentDescription = "Cold Storage",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = vaccine.storageTemp,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = stockBadgeColor
                    ) {
                        Text(
                            text = stockLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = stockTextColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("edit_vaccine_${vaccine.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Vaccine",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("delete_vaccine_${vaccine.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Vaccine",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Clean Tonal Pricing Strip: Cost, Selling, Margin
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Cost Price",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "₹${String.format("%.2f", vaccine.purchasePrice)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Column {
                        Text(
                            text = "Selling Price",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "₹${String.format("%.2f", vaccine.sellingPrice)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Profit Margin",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "+₹${String.format("%.2f", marginPerDose)}/dose",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ProfitGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Actions: Restock (+ Purchase) and Dispense (- Sale)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onRestockClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .testTag("btn_restock_${vaccine.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Purchase and Add Stock",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Purchase (Stock)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onDispenseClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .testTag("btn_dispense_${vaccine.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    enabled = vaccine.currentStock > 0,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Dispense",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Dispense / Sell",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
