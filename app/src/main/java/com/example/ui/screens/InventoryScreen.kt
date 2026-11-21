package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.model.Vaccine
import com.example.ui.components.VaccineItemCard
import com.example.ui.util.tr

@Composable
fun InventoryScreen(
    vaccines: List<Vaccine>,
    searchQuery: String,
    selectedCategory: String,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onOpenRestockDialog: (Vaccine) -> Unit,
    onOpenDispenseDialog: (Vaccine) -> Unit,
    onOpenEditVaccineDialog: (Vaccine) -> Unit,
    onOpenDeleteVaccineDialog: (Vaccine) -> Unit,
    onOpenAddVaccineDialog: () -> Unit,
    onOpenPurchaseFromVendorDialog: () -> Unit = {},
    onOpenScanBillDialog: () -> Unit = {},
    onDeleteOutOfStockVaccines: () -> Unit = {},
    onPurgeStaleCloudData: () -> Unit = {},
    onEraseAllVaccines: () -> Unit = {},
    onEraseAllData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val categories = listOf("All", "Low Stock", "Routine Pediatric", "Adult & Booster", "Seasonal", "Emergency", "Travel & Pediatric")

    var showManageDataDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteZeroStockDialog by remember { mutableStateOf(false) }
    var showConfirmEraseVaccinesDialog by remember { mutableStateOf(false) }
    var showConfirmEraseAllDialog by remember { mutableStateOf(false) }

    val filteredVaccines = vaccines.filter { vac ->
        val matchesSearch = vac.name.contains(searchQuery, ignoreCase = true) ||
                vac.code.contains(searchQuery, ignoreCase = true) ||
                vac.category.contains(searchQuery, ignoreCase = true)

        val matchesCategory = when (selectedCategory) {
            "All" -> true
            "Low Stock" -> vac.currentStock <= vac.minStockAlert
            else -> vac.category.equals(selectedCategory, ignoreCase = true)
        }

        matchesSearch && matchesCategory
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddVaccineDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_vaccine")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Vaccine")
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Add Vaccine", fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("inventory_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Search Bar & Category Filter Chips Together at the Top
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search vaccine by name, code or category...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inventory_search_field")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { category ->
                            val isSelected = selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCategorySelected(category) },
                                label = { Text(category, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // 2. Clean 1-Row Scan Bill & Restock Action Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenScanBillDialog,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF006874),
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_inventory_scan_bill")
                    ) {
                        Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("Scan Bill / PDF", "बिल स्कैन / PDF"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenPurchaseFromVendorDialog,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFCBD5E1)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_inventory_purchase_from_vendor")
                    ) {
                        Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("Manual Restock", "स्टॉक जोड़ें"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            // 3. Results Count & Clean Data Action Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Vaccines (${filteredVaccines.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val totalUnitsInView = filteredVaccines.sumOf { it.currentStock }
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$totalUnitsInView doses",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showManageDataDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFCBD5E1)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_open_inventory_data_options")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clean Data", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (filteredVaccines.isEmpty()) {
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
                                imageVector = Icons.Default.Vaccines,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No vaccines match the filter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try adjusting your search terms or add a new vaccine.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(filteredVaccines, key = { it.id }) { vaccine ->
                    VaccineItemCard(
                        vaccine = vaccine,
                        onRestockClick = { onOpenRestockDialog(vaccine) },
                        onDispenseClick = { onOpenDispenseDialog(vaccine) },
                        onEditClick = { onOpenEditVaccineDialog(vaccine) },
                        onDeleteClick = { onOpenDeleteVaccineDialog(vaccine) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Modal Dialog: Clean / Erase Data Options
    if (showManageDataDialog) {
        AlertDialog(
            onDismissRequest = { showManageDataDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clean & Erase Data Options", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose an option below to manage unwanted, old, or deleted vaccine records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option 1: Delete Zero-Stock Vaccines
                    OutlinedButton(
                        onClick = {
                            showManageDataDialog = false
                            showConfirmDeleteZeroStockDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("dialog_btn_delete_zero_stock"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete 0-Stock Old Vaccines", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Removes all vaccines with zero available doses completely.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Option 2: Purge Stale Cloud Data
                    OutlinedButton(
                        onClick = {
                            showManageDataDialog = false
                            onPurgeStaleCloudData()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("dialog_btn_purge_cloud"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Purge Stale Cloud Data", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Removes deleted vaccines from cloud backup so sync never revives them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Option 3: Clear All Vaccines from List
                    OutlinedButton(
                        onClick = {
                            showManageDataDialog = false
                            showConfirmEraseVaccinesDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("dialog_btn_clear_all_vaccines"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear All Vaccines from List", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Deletes all current vaccines from device & cloud inventory.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Option 4: Complete Reset (Erase All Old Data)
                    Button(
                        onClick = {
                            showManageDataDialog = false
                            showConfirmEraseAllDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_btn_erase_all_data"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Erase All Old Data (Complete Reset)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageDataDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Confirmation: Delete Zero-Stock Vaccines
    if (showConfirmDeleteZeroStockDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteZeroStockDialog = false },
            title = { Text("Delete Zero-Stock Vaccines?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all vaccines with 0 inventory from both this device and cloud storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteZeroStockDialog = false
                        onDeleteOutOfStockVaccines()
                    }
                ) {
                    Text("Delete 0-Stock Vaccines")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDeleteZeroStockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation: Clear All Vaccines
    if (showConfirmEraseVaccinesDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmEraseVaccinesDialog = false },
            title = { Text("Clear All Vaccines?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("This will permanently delete all vaccines from your inventory on this device and in your cloud backup. Are you sure?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmEraseVaccinesDialog = false
                        onEraseAllVaccines()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All Vaccines")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmEraseVaccinesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation: Erase All Data
    if (showConfirmEraseAllDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmEraseAllDialog = false },
            title = { Text("⚠️ Erase All Old Data Completely?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("This will permanently erase all vaccines, patients, and transactions from BOTH this device and cloud storage.\n\nAre you sure you want to reset everything?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmEraseAllDialog = false
                        onEraseAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Erase Everything")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmEraseAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
