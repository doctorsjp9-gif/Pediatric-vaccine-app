package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ClinicProfile
import com.example.data.model.StockTransaction
import com.example.ui.components.BillDialog
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.OnProfitGreenContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.util.BillData
import com.example.ui.util.BillItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SalesScreen(
    sales: List<StockTransaction>,
    clinicProfile: ClinicProfile,
    onOpenRecordSaleDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeBillData by remember { mutableStateOf<BillData?>(null) }

    val totalDosesSold = sales.sumOf { it.quantity }
    val totalSalesRevenue = sales.sumOf { it.totalAmount }
    val totalProfitEarned = sales.sumOf { it.profit }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenRecordSaleDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_sale")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Record Sale")
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Record Sale", fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("sales_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profit Summary Header Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ProfitGreenContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Sales & Profit Ledger",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnProfitGreenContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Administered doses to patients or clinics. Stock automatically reduces on each sale.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnProfitGreenContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Profit Realized",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnProfitGreenContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "+₹${String.format("%.2f", totalProfitEarned)}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ProfitGreen
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Gross Revenue",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnProfitGreenContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "₹${String.format("%.2f", totalSalesRevenue)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = OnProfitGreenContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Total Doses Dispensed: $totalDosesSold doses",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = OnProfitGreenContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Notice
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Stock deduction: Recording a sale automatically reduces available inventory doses.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Dispensation History (${sales.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (sales.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No sales recorded yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Dispense a vaccine to record your first sale and generate profit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(sales, key = { it.id }) { sale ->
                    TransactionItemCard(
                        transaction = sale,
                        onGenerateBill = { tx ->
                            val billItem = BillItem(
                                description = tx.vaccineName,
                                batchNumber = tx.batchNumber.ifBlank { "LOT-${tx.id}" },
                                quantity = tx.quantity,
                                unitPrice = tx.unitPrice,
                                totalAmount = tx.totalAmount
                            )
                            val invoiceNo = "INV-${SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date(tx.timestamp))}-${tx.id}"
                            activeBillData = BillData(
                                invoiceNumber = invoiceNo,
                                timestamp = tx.timestamp,
                                patientName = tx.referenceOrCustomer.ifBlank { "Patient" },
                                parentName = "",
                                mobileNumber = "",
                                items = listOf(billItem),
                                notes = tx.notes
                            )
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (activeBillData != null) {
        BillDialog(
            billData = activeBillData!!,
            clinic = clinicProfile,
            onDismiss = { activeBillData = null }
        )
    }
}
