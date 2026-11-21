package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.data.repository.PurchaseLineItem
import com.example.data.repository.SaleLineItem
import com.example.ui.theme.LossRed
import com.example.ui.theme.LossRedContainer
import com.example.ui.theme.OnLossRedContainer
import com.example.ui.theme.OnProfitGreenContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer

// --- 100% RELIABLE MODAL DIALOG PICKERS ---
// Eliminates Compose popup bugs inside modal dialogs and provides clean search & selection

@Composable
fun SelectVaccineDialog(
    vaccines: List<Vaccine>,
    selectedVaccineId: Long?,
    onVaccineSelected: (Vaccine) -> Unit,
    onDismiss: () -> Unit,
    showStockOnly: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("All") }

    val categories = remember(vaccines) {
        listOf("All") + vaccines.map { it.category }.distinct()
    }

    val filtered = remember(vaccines, searchQuery, categoryFilter, showStockOnly) {
        vaccines.filter { v ->
            val matchesSearch = v.name.contains(searchQuery, ignoreCase = true) ||
                    v.code.contains(searchQuery, ignoreCase = true) ||
                    v.category.contains(searchQuery, ignoreCase = true)
            val matchesCat = categoryFilter == "All" || v.category.equals(categoryFilter, ignoreCase = true)
            val matchesStock = !showStockOnly || v.currentStock > 0
            matchesSearch && matchesCat && matchesStock
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select Vaccine",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap any vaccine below to select it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close picker")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, code or type...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("select_vaccine_search_input")
                )

                // Category filter chips
                if (categories.size > 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = categoryFilter == cat,
                                onClick = { categoryFilter = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Vaccine list
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No vaccines found matching '$searchQuery'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { vac ->
                            val isSelected = vac.id == selectedVaccineId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onVaccineSelected(vac)
                                        onDismiss()
                                    }
                                    .testTag("picker_item_vac_${vac.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = vac.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${vac.category} • Cost: ₹${vac.purchasePrice} | Sell: ₹${vac.sellingPrice}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(horizontalAlignment = Alignment.End) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (vac.currentStock > 0) ProfitGreenContainer else LossRedContainer
                                        ) {
                                            Text(
                                                text = if (vac.currentStock > 0) "${vac.currentStock} ${vac.unit}" else "0 (Out)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (vac.currentStock > 0) OnProfitGreenContainer else OnLossRedContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Currently selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SelectVendorDialog(
    vendors: List<Vendor>,
    selectedVendorId: Long?,
    onVendorSelected: (Vendor?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(vendors, searchQuery) {
        vendors.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.contactPerson.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select Supplier / Vendor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap any vendor below to select",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search supplier / vendor...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedVendorId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (selectedVendorId == null) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onVendorSelected(null)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("None / Direct Purchase (Over the counter)", fontStyle = FontStyle.Italic)
                                if (selectedVendorId == null) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    items(filtered, key = { it.id }) { ven ->
                        val isSelected = ven.id == selectedVendorId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onVendorSelected(ven)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ven.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    if (ven.contactPerson.isNotBlank()) {
                                        Text("${ven.contactPerson} • ${ven.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                                if (isSelected) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun VaccineDropdownSelector(
    label: String,
    selectedVaccine: Vaccine?,
    vaccines: List<Vaccine>,
    onVaccineSelected: (Vaccine) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select a vaccine",
    showAvailableStock: Boolean = false,
    testTag: String = "vaccine_dropdown"
) {
    var showPickerDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPickerDialog = true }
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedVaccine?.name ?: placeholder,
                        fontWeight = if (selectedVaccine != null) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedVaccine != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                    if (selectedVaccine != null) {
                        Text(
                            text = if (showAvailableStock)
                                "Stock: ${selectedVaccine.currentStock} ${selectedVaccine.unit} • Sell: ₹${selectedVaccine.sellingPrice}"
                            else
                                "Cost: ₹${selectedVaccine.purchasePrice} • Sell: ₹${selectedVaccine.sellingPrice}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { showPickerDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (selectedVaccine != null) "Change ▾" else "Select ▾",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showPickerDialog) {
        SelectVaccineDialog(
            vaccines = vaccines,
            selectedVaccineId = selectedVaccine?.id,
            onVaccineSelected = onVaccineSelected,
            onDismiss = { showPickerDialog = false },
            showStockOnly = showAvailableStock
        )
    }
}

@Composable
fun VendorDropdownSelector(
    selectedVendor: Vendor?,
    vendors: List<Vendor>,
    onVendorSelected: (Vendor?) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "vendor_dropdown"
) {
    var showPickerDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth().testTag(testTag)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPickerDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedVendor?.name ?: "None / Direct Purchase",
                        fontWeight = if (selectedVendor != null) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (selectedVendor == null) FontStyle.Italic else FontStyle.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selectedVendor != null && selectedVendor.contactPerson.isNotBlank()) {
                        Text(
                            text = "${selectedVendor.contactPerson} • ${selectedVendor.phone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { showPickerDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Change ▾",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showPickerDialog) {
        SelectVendorDialog(
            vendors = vendors,
            selectedVendorId = selectedVendor?.id,
            onVendorSelected = onVendorSelected,
            onDismiss = { showPickerDialog = false }
        )
    }
}

// Helper models for multi-item UI forms
data class MultiPurchaseItemUi(
    val vaccineId: Long,
    val quantityText: String = "10",
    val priceText: String = "0.0",
    val batchNumber: String = "",
    val expiryDate: String = ""
)

data class MultiSaleItemUi(
    val vaccineId: Long,
    val quantityText: String = "1",
    val priceText: String = "0.0",
    val batchNumber: String = ""
)

// --- RECORD PURCHASE (RESTOCK) DIALOG ---
@Composable
fun AddPurchaseDialog(
    vaccines: List<Vaccine>,
    vendors: List<Vendor>,
    preSelectedVaccineId: Long? = null,
    preSelectedVendorId: Long? = null,
    onDismiss: () -> Unit,
    onOpenAddVendor: () -> Unit,
    onOpenScanBill: (() -> Unit)? = null,
    onConfirmPurchase: (
        vaccineId: Long,
        vendorId: Long?,
        quantity: Int,
        unitPurchasePrice: Double,
        batchNumber: String,
        expiryDate: String,
        invoiceNumber: String,
        notes: String
    ) -> Unit,
    onConfirmMultiPurchase: (
        vendorId: Long?,
        items: List<PurchaseLineItem>,
        invoiceNumber: String,
        notes: String
    ) -> Unit
) {
    var isMultiMode by remember { mutableStateOf(false) }

    var selectedVendor by remember {
        mutableStateOf(vendors.find { it.id == preSelectedVendorId })
    }
    var invoiceNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Single mode state
    var singleVaccine by remember {
        mutableStateOf(vaccines.find { it.id == preSelectedVaccineId } ?: vaccines.firstOrNull())
    }
    var singleQuantityText by remember { mutableStateOf("10") }
    var singlePriceText by remember(singleVaccine) {
        mutableStateOf(singleVaccine?.purchasePrice?.toString() ?: "0.0")
    }
    var singleBatchNumber by remember { mutableStateOf("") }
    var singleExpiryDate by remember { mutableStateOf("") }

    // Multi mode state
    val multiItems = remember {
        val initialVac = vaccines.find { it.id == preSelectedVaccineId } ?: vaccines.firstOrNull()
        val firstId = initialVac?.id ?: 1L
        val firstPrice = initialVac?.purchasePrice?.toString() ?: "0.0"
        mutableStateListOf(
            MultiPurchaseItemUi(
                vaccineId = firstId,
                quantityText = "10",
                priceText = firstPrice
            )
        )
    }

    LaunchedEffect(vaccines) {
        if (vaccines.isNotEmpty()) {
            if (singleVaccine == null) {
                singleVaccine = vaccines.find { it.id == preSelectedVaccineId } ?: vaccines.firstOrNull()
                singlePriceText = singleVaccine?.purchasePrice?.toString() ?: "0.0"
            }
            if (multiItems.isNotEmpty() && (multiItems[0].vaccineId == 0L || vaccines.none { it.id == multiItems[0].vaccineId })) {
                val initV = vaccines.find { it.id == preSelectedVaccineId } ?: vaccines.firstOrNull()
                if (initV != null) {
                    multiItems[0] = multiItems[0].copy(
                        vaccineId = initV.id,
                        priceText = initV.purchasePrice.toString()
                    )
                }
            }
        }
    }

    val singleQuantity = singleQuantityText.toIntOrNull() ?: 0
    val singlePrice = singlePriceText.toDoubleOrNull() ?: 0.0
    val singleTotalCost = singleQuantity * singlePrice

    // Multi totals
    val multiTotalCost = multiItems.sumOf { item ->
        val q = item.quantityText.toIntOrNull() ?: 0
        val p = item.priceText.toDoubleOrNull() ?: 0.0
        q * p
    }
    val multiTotalDoses = multiItems.sumOf { it.quantityText.toIntOrNull() ?: 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isMultiMode) "Restock Multiple Vaccines" else "Restock Vaccine (Purchase)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Stock will update automatically upon entry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onOpenScanBill != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = androidx.compose.ui.graphics.Color(0xFFE0F7FA),
                        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF00838F).copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onOpenScanBill()
                            }
                            .testTag("purchase_dialog_scan_bill_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scan Bill Photo or Upload PDF",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color(0xFF006064)
                                )
                                Text(
                                    text = "Auto-read vaccine items, batches & quantities from vendor invoice",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.ui.graphics.Color(0xFF00838F)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenScanBill()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.ui.graphics.Color(0xFF006874),
                                    contentColor = androidx.compose.ui.graphics.Color.White
                                )
                            ) {
                                Text("Scan Bill", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Mode Selector: Single vs Multiple
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isMultiMode,
                        onClick = { isMultiMode = false },
                        label = { Text("Single Vaccine") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    FilterChip(
                        selected = isMultiMode,
                        onClick = { isMultiMode = true },
                        label = { Text("Multiple Vaccines Order") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // Vendor / Supplier (Common to both modes)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vendor / Supplier",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = onOpenAddVendor,
                        modifier = Modifier.testTag("purchase_add_vendor_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("+ New Vendor", style = MaterialTheme.typography.labelSmall)
                    }
                }

                VendorDropdownSelector(
                    selectedVendor = selectedVendor,
                    vendors = vendors,
                    onVendorSelected = { selectedVendor = it },
                    testTag = "purchase_vendor_selector"
                )

                // Invoice #
                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text("Vendor Invoice / PO Number") },
                    placeholder = { Text("e.g. PO-8921 / INV-2026") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isMultiMode) {
                    // --- SINGLE VACCINE MODE ---
                    VaccineDropdownSelector(
                        label = "Select Vaccine *",
                        selectedVaccine = singleVaccine,
                        vaccines = vaccines,
                        onVaccineSelected = { vac ->
                            singleVaccine = vac
                            singlePriceText = vac.purchasePrice.toString()
                        },
                        testTag = "single_purchase_vaccine_select"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = singleQuantityText,
                            onValueChange = { singleQuantityText = it.filter { c -> c.isDigit() } },
                            label = { Text("Quantity (Doses) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purchase_quantity_input")
                        )

                        OutlinedTextField(
                            value = singlePriceText,
                            onValueChange = { singlePriceText = it },
                            label = { Text("Purchase Price (₹) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purchase_price_input")
                        )
                    }

                    // Total Cost Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Purchase Cost:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "₹${String.format("%.2f", singleTotalCost)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = singleBatchNumber,
                            onValueChange = { singleBatchNumber = it },
                            label = { Text("Batch / Lot #") },
                            placeholder = { Text("e.g. BT-8891") },
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = singleExpiryDate,
                            onValueChange = { singleExpiryDate = it },
                            label = { Text("Expiry Date") },
                            placeholder = { Text("e.g. 12/2027") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // --- MULTIPLE VACCINES ORDER MODE ---
                    Text(
                        text = "Order Items (${multiItems.size} Vaccines)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    multiItems.forEachIndexed { index, item ->
                        val vac = vaccines.find { it.id == item.vaccineId } ?: vaccines.firstOrNull()

                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Item #${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    if (multiItems.size > 1) {
                                        IconButton(
                                            onClick = { multiItems.removeAt(index) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove item",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Reliable Vaccine Selector for this line item
                                VaccineDropdownSelector(
                                    label = "Select Vaccine *",
                                    selectedVaccine = vac,
                                    vaccines = vaccines,
                                    onVaccineSelected = { selectedV ->
                                        multiItems[index] = item.copy(
                                            vaccineId = selectedV.id,
                                            priceText = selectedV.purchasePrice.toString()
                                        )
                                    },
                                    testTag = "multi_purchase_item_${index}_select"
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = item.quantityText,
                                        onValueChange = { newQ ->
                                            multiItems[index] = item.copy(quantityText = newQ.filter { c -> c.isDigit() })
                                        },
                                        label = { Text("Doses *") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = item.priceText,
                                        onValueChange = { newP ->
                                            multiItems[index] = item.copy(priceText = newP)
                                        },
                                        label = { Text("Cost (₹) *") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = item.batchNumber,
                                        onValueChange = { newB ->
                                            multiItems[index] = item.copy(batchNumber = newB)
                                        },
                                        label = { Text("Batch #") },
                                        placeholder = { Text("BT-101") },
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = item.expiryDate,
                                        onValueChange = { newE ->
                                            multiItems[index] = item.copy(expiryDate = newE)
                                        },
                                        label = { Text("Exp Date") },
                                        placeholder = { Text("12/2027") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                val q = item.quantityText.toIntOrNull() ?: 0
                                val p = item.priceText.toDoubleOrNull() ?: 0.0
                                Text(
                                    text = "Item Subtotal: ₹${String.format("%.2f", q * p)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Button to add another vaccine
                    Button(
                        onClick = {
                            val nextVac = vaccines.firstOrNull { v -> multiItems.none { it.vaccineId == v.id } }
                                ?: vaccines.getOrNull(multiItems.size)
                                ?: vaccines.firstOrNull()
                            val nextId = nextVac?.id ?: 1L
                            val nextPrice = nextVac?.purchasePrice?.toString() ?: "0.0"
                            multiItems.add(
                                MultiPurchaseItemUi(
                                    vaccineId = nextId,
                                    quantityText = "10",
                                    priceText = nextPrice
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Another Vaccine to Order")
                    }

                    // Multi Total Summary Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Vaccines in Order:", style = MaterialTheme.typography.bodySmall)
                                Text("${multiItems.size} types (${multiTotalDoses} doses)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Grand Total Purchase Cost:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "₹${String.format("%.2f", multiTotalCost)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // General Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Order Notes (Temperature check, invoice details)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Stock update guarantee banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ProfitGreenContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ProfitGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMultiMode)
                                "All ${multiItems.size} vaccines will automatically increment their stock in inventory."
                            else
                                "Current stock will automatically update upon entry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isMultiMode) {
                        val vac = singleVaccine ?: return@Button
                        if (singleQuantity <= 0 || singlePrice < 0.0) return@Button
                        onConfirmPurchase(
                            vac.id,
                            selectedVendor?.id,
                            singleQuantity,
                            singlePrice,
                            singleBatchNumber.trim(),
                            singleExpiryDate.trim(),
                            invoiceNumber.trim(),
                            notes.trim()
                        )
                    } else {
                        val validItems = multiItems.mapNotNull { item ->
                            val q = item.quantityText.toIntOrNull() ?: 0
                            val p = item.priceText.toDoubleOrNull() ?: 0.0
                            if (q > 0 && p >= 0.0) {
                                PurchaseLineItem(
                                    vaccineId = item.vaccineId,
                                    quantity = q,
                                    unitPurchasePrice = p,
                                    batchNumber = item.batchNumber.trim(),
                                    expiryDate = item.expiryDate.trim()
                                )
                            } else null
                        }
                        if (validItems.isEmpty()) return@Button
                        onConfirmMultiPurchase(
                            selectedVendor?.id,
                            validItems,
                            invoiceNumber.trim(),
                            notes.trim()
                        )
                    }
                },
                enabled = if (!isMultiMode) {
                    singleVaccine != null && singleQuantity > 0 && singlePrice >= 0.0
                } else {
                    multiItems.isNotEmpty() && multiItems.all {
                        (it.quantityText.toIntOrNull() ?: 0) > 0 && (it.priceText.toDoubleOrNull() ?: -1.0) >= 0.0
                    }
                },
                modifier = Modifier.testTag("purchase_confirm_btn")
            ) {
                Text(if (isMultiMode) "Confirm & Restock ${multiItems.size} Vaccines" else "Confirm & Restock")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- RECORD SALE (DISPENSE) DIALOG ---
@Composable
fun RecordSaleDialog(
    vaccines: List<Vaccine>,
    preSelectedVaccineId: Long? = null,
    onDismiss: () -> Unit,
    onConfirmSale: (
        vaccineId: Long,
        quantity: Int,
        unitSellingPrice: Double,
        customerOrPatient: String,
        batchNumber: String,
        notes: String
    ) -> Unit,
    onConfirmMultiSale: (
        items: List<SaleLineItem>,
        customerOrPatient: String,
        notes: String
    ) -> Unit
) {
    var isMultiMode by remember { mutableStateOf(false) }
    var patientOrCustomer by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Single mode state
    var singleVaccine by remember {
        mutableStateOf(vaccines.find { it.id == preSelectedVaccineId } ?: vaccines.firstOrNull { it.currentStock > 0 } ?: vaccines.firstOrNull())
    }
    var singleQuantityText by remember { mutableStateOf("1") }
    var singlePriceText by remember(singleVaccine) {
        mutableStateOf(singleVaccine?.sellingPrice?.toString() ?: "0.0")
    }
    var singleBatchNumber by remember { mutableStateOf("") }

    val singleQuantity = singleQuantityText.toIntOrNull() ?: 0
    val singleSellPrice = singlePriceText.toDoubleOrNull() ?: 0.0
    val singleCostPrice = singleVaccine?.purchasePrice ?: 0.0
    val singleRevenue = singleQuantity * singleSellPrice
    val singleCost = singleQuantity * singleCostPrice
    val singleProfit = singleRevenue - singleCost
    val isSingleStockExceeded = singleVaccine != null && singleQuantity > singleVaccine!!.currentStock

    // Multi mode state
    val multiItems = remember {
        val initialVac = vaccines.find { it.id == preSelectedVaccineId } ?: vaccines.firstOrNull { it.currentStock > 0 } ?: vaccines.firstOrNull()
        val firstId = initialVac?.id ?: 1L
        val firstPrice = initialVac?.sellingPrice?.toString() ?: "0.0"
        mutableStateListOf(
            MultiSaleItemUi(
                vaccineId = firstId,
                quantityText = "1",
                priceText = firstPrice
            )
        )
    }

    LaunchedEffect(vaccines) {
        if (vaccines.isNotEmpty()) {
            if (singleVaccine == null) {
                singleVaccine = vaccines.find { it.id == preSelectedVaccineId } ?: vaccines.firstOrNull { it.currentStock > 0 } ?: vaccines.firstOrNull()
                singlePriceText = singleVaccine?.sellingPrice?.toString() ?: "0.0"
            }
            if (multiItems.isNotEmpty() && (multiItems[0].vaccineId == 0L || vaccines.none { it.id == multiItems[0].vaccineId })) {
                val initV = vaccines.find { it.id == preSelectedVaccineId } ?: vaccines.firstOrNull { it.currentStock > 0 } ?: vaccines.firstOrNull()
                if (initV != null) {
                    multiItems[0] = multiItems[0].copy(
                        vaccineId = initV.id,
                        priceText = initV.sellingPrice.toString()
                    )
                }
            }
        }
    }

    // Multi totals
    val multiRevenue = multiItems.sumOf { item ->
        val q = item.quantityText.toIntOrNull() ?: 0
        val p = item.priceText.toDoubleOrNull() ?: 0.0
        q * p
    }
    val multiCost = multiItems.sumOf { item ->
        val q = item.quantityText.toIntOrNull() ?: 0
        val vac = vaccines.find { it.id == item.vaccineId }
        val costP = vac?.purchasePrice ?: 0.0
        q * costP
    }
    val multiProfit = multiRevenue - multiCost
    val multiTotalDoses = multiItems.sumOf { it.quantityText.toIntOrNull() ?: 0 }

    // Check if any item in multiItems exceeds on-hand stock
    val hasMultiStockError = multiItems.any { item ->
        val q = item.quantityText.toIntOrNull() ?: 0
        val vac = vaccines.find { it.id == item.vaccineId }
        val available = vac?.currentStock ?: 0
        q > available || q <= 0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isMultiMode) "Multi-Vaccine Patient Bill" else "Record Vaccine Sale / Dispense",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Stock will reduce & profit will calculate automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isMultiMode,
                        onClick = { isMultiMode = false },
                        label = { Text("Single Vaccine") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    FilterChip(
                        selected = isMultiMode,
                        onClick = { isMultiMode = true },
                        label = { Text("Multiple Vaccines Bill") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // Patient / Customer Name (Common)
                OutlinedTextField(
                    value = patientOrCustomer,
                    onValueChange = { patientOrCustomer = it },
                    label = { Text("Patient / Customer / Family Name") },
                    placeholder = { Text("e.g. Baby Aarav Sharma / Verma Family") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isMultiMode) {
                    // --- SINGLE VACCINE MODE ---
                    VaccineDropdownSelector(
                        label = "Vaccine Dispensed *",
                        selectedVaccine = singleVaccine,
                        vaccines = vaccines,
                        onVaccineSelected = { vac ->
                            singleVaccine = vac
                            singlePriceText = vac.sellingPrice.toString()
                        },
                        showAvailableStock = true,
                        testTag = "single_sale_vaccine_select"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = singleQuantityText,
                            onValueChange = { singleQuantityText = it.filter { c -> c.isDigit() } },
                            label = { Text("Quantity Sold *") },
                            isError = isSingleStockExceeded,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sale_quantity_input")
                        )

                        OutlinedTextField(
                            value = singlePriceText,
                            onValueChange = { singlePriceText = it },
                            label = { Text("Sell Price (₹) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sale_price_input")
                        )
                    }

                    if (isSingleStockExceeded) {
                        Text(
                            text = "Cannot sell more than available stock (${singleVaccine?.currentStock ?: 0})",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Single Profit Calculation Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ProfitGreenContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Sale Revenue:", style = MaterialTheme.typography.bodySmall)
                                Text("₹${String.format("%.2f", singleRevenue)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cost Basis (₹${String.format("%.2f", singleCostPrice)}/unit):", style = MaterialTheme.typography.bodySmall)
                                Text("−₹${String.format("%.2f", singleCost)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Instant Profit Generated:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ProfitGreen
                                )
                                Text(
                                    text = "+₹${String.format("%.2f", singleProfit)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ProfitGreen
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = singleBatchNumber,
                        onValueChange = { singleBatchNumber = it },
                        label = { Text("Batch Number Dispensed") },
                        placeholder = { Text("e.g. BT-9021") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // --- MULTIPLE VACCINES BILL MODE ---
                    Text(
                        text = "Dispensed Vaccines (${multiItems.size} items in bill)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    multiItems.forEachIndexed { index, item ->
                        val vac = vaccines.find { it.id == item.vaccineId } ?: vaccines.firstOrNull()
                        val availableStock = vac?.currentStock ?: 0
                        val q = item.quantityText.toIntOrNull() ?: 0
                        val p = item.priceText.toDoubleOrNull() ?: 0.0
                        val cost = vac?.purchasePrice ?: 0.0
                        val isOverStock = q > availableStock

                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Vaccine #${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    if (multiItems.size > 1) {
                                        IconButton(
                                            onClick = { multiItems.removeAt(index) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove from bill",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Reliable Vaccine Selector for this bill item
                                VaccineDropdownSelector(
                                    label = "Select Vaccine *",
                                    selectedVaccine = vac,
                                    vaccines = vaccines,
                                    onVaccineSelected = { selectedV ->
                                        multiItems[index] = item.copy(
                                            vaccineId = selectedV.id,
                                            priceText = selectedV.sellingPrice.toString()
                                        )
                                    },
                                    showAvailableStock = true,
                                    testTag = "multi_sale_item_${index}_select"
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = item.quantityText,
                                        onValueChange = { newQ ->
                                            multiItems[index] = item.copy(quantityText = newQ.filter { c -> c.isDigit() })
                                        },
                                        label = { Text("Quantity (Doses) *") },
                                        isError = isOverStock,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = item.priceText,
                                        onValueChange = { newP ->
                                            multiItems[index] = item.copy(priceText = newP)
                                        },
                                        label = { Text("Price (₹) *") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (isOverStock) {
                                    Text(
                                        text = "Exceeds available stock ($availableStock doses)",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                OutlinedTextField(
                                    value = item.batchNumber,
                                    onValueChange = { newB ->
                                        multiItems[index] = item.copy(batchNumber = newB)
                                    },
                                    label = { Text("Batch # (Optional)") },
                                    placeholder = { Text("e.g. BT-109") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                val lineTotal = q * p
                                val lineProfit = (p - cost) * q
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Subtotal: ₹${String.format("%.2f", lineTotal)}", style = MaterialTheme.typography.bodySmall)
                                        Text("Profit: +₹${String.format("%.2f", lineProfit)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = ProfitGreen)
                                    }
                                }
                            }
                        }
                    }

                    // Button to add another vaccine to bill
                    Button(
                        onClick = {
                            val nextVac = vaccines.firstOrNull { v -> v.currentStock > 0 && multiItems.none { it.vaccineId == v.id } }
                                ?: vaccines.firstOrNull { it.currentStock > 0 }
                                ?: vaccines.firstOrNull { v -> multiItems.none { it.vaccineId == v.id } }
                                ?: vaccines.firstOrNull()
                            val nextId = nextVac?.id ?: 1L
                            val nextPrice = nextVac?.sellingPrice?.toString() ?: "0.0"
                            multiItems.add(
                                MultiSaleItemUi(
                                    vaccineId = nextId,
                                    quantityText = "1",
                                    priceText = nextPrice
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Another Vaccine to Bill")
                    }

                    // Multi Total Summary Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ProfitGreenContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Bill Items:", style = MaterialTheme.typography.bodySmall)
                                Text("${multiItems.size} vaccines ($multiTotalDoses doses)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Bill Revenue:", style = MaterialTheme.typography.bodySmall)
                                Text("₹${String.format("%.2f", multiRevenue)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Cost Basis:", style = MaterialTheme.typography.bodySmall)
                                Text("−₹${String.format("%.2f", multiCost)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Bill Profit Generated:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ProfitGreen
                                )
                                Text(
                                    text = "+₹${String.format("%.2f", multiProfit)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ProfitGreen
                                )
                            }
                        }
                    }
                }

                // Clinical Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Clinical Notes / Vaccine Card ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isMultiMode) {
                        val vac = singleVaccine ?: return@Button
                        if (singleQuantity <= 0 || isSingleStockExceeded) return@Button
                        onConfirmSale(
                            vac.id,
                            singleQuantity,
                            singleSellPrice,
                            patientOrCustomer.trim(),
                            singleBatchNumber.trim(),
                            notes.trim()
                        )
                    } else {
                        val validItems = multiItems.mapNotNull { item ->
                            val q = item.quantityText.toIntOrNull() ?: 0
                            val p = item.priceText.toDoubleOrNull() ?: 0.0
                            if (q > 0) {
                                SaleLineItem(
                                    vaccineId = item.vaccineId,
                                    quantity = q,
                                    unitSellingPrice = p,
                                    batchNumber = item.batchNumber.trim()
                                )
                            } else null
                        }
                        if (validItems.isEmpty() || hasMultiStockError) return@Button
                        onConfirmMultiSale(
                            validItems,
                            patientOrCustomer.trim(),
                            notes.trim()
                        )
                    }
                },
                enabled = if (!isMultiMode) {
                    singleVaccine != null && singleQuantity > 0 && !isSingleStockExceeded && singleSellPrice >= 0.0
                } else {
                    multiItems.isNotEmpty() && !hasMultiStockError && multiItems.all {
                        (it.quantityText.toIntOrNull() ?: 0) > 0 && (it.priceText.toDoubleOrNull() ?: -1.0) >= 0.0
                    }
                },
                modifier = Modifier.testTag("sale_confirm_btn")
            ) {
                Text(if (isMultiMode) "Confirm Bill & Reduce Stock (${multiItems.size})" else "Confirm Sale & Reduce Stock")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- DIRECT STOCK ADJUSTMENT DIALOG ---
@Composable
fun AdjustStockDialog(
    vaccines: List<Vaccine>,
    preSelectedVaccineId: Long? = null,
    onDismiss: () -> Unit,
    onConfirmAdjustment: (vaccineId: Long, isAddition: Boolean, quantity: Int, reason: String) -> Unit
) {
    var selectedVaccine by remember {
        mutableStateOf(vaccines.find { it.id == preSelectedVaccineId } ?: vaccines.firstOrNull())
    }
    var isAddition by remember { mutableStateOf(true) }
    var quantityText by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("Physical count audit adjustment") }

    val quantity = quantityText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Manual Stock Adjustment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VaccineDropdownSelector(
                    label = "Select Vaccine *",
                    selectedVaccine = selectedVaccine,
                    vaccines = vaccines,
                    onVaccineSelected = { selectedVaccine = it },
                    showAvailableStock = true,
                    testTag = "adjust_vaccine_select"
                )

                // Add or Reduce toggle
                Text(
                    text = "Adjustment Action *",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isAddition = true }
                    ) {
                        RadioButton(selected = isAddition, onClick = { isAddition = true })
                        Text("+ Add Stock", fontWeight = FontWeight.SemiBold, color = ProfitGreen)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isAddition = false }
                    ) {
                        RadioButton(selected = !isAddition, onClick = { isAddition = false })
                        Text("− Reduce Stock", fontWeight = FontWeight.SemiBold, color = LossRed)
                    }
                }

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { char -> char.isDigit() } },
                    label = { Text("Quantity (Doses) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Reasons
                Text(
                    text = "Reason *",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Adjustment Reason") },
                    placeholder = { Text("e.g. Cold chain excursion, damaged vial, physical inventory audit") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { reason = "Cold chain failure / Spoilage" }) {
                        Text("Spoilage", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { reason = "Physical count audit" }) {
                        Text("Audit", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { reason = "Broken vial / Damage" }) {
                        Text("Broken", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val vac = selectedVaccine ?: return@Button
                    if (quantity <= 0) return@Button
                    onConfirmAdjustment(vac.id, isAddition, quantity, reason.trim())
                },
                enabled = selectedVaccine != null && quantity > 0
            ) {
                Text("Confirm Adjustment")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- VACCINE FORM (ADD / EDIT / DELETE) DIALOG ---
@Composable
fun VaccineFormDialog(
    initialVaccine: Vaccine? = null,
    onDismiss: () -> Unit,
    onSave: (Vaccine) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialVaccine?.name ?: "") }
    var category by remember { mutableStateOf(initialVaccine?.category ?: "Routine Pediatric") }
    var code by remember { mutableStateOf(initialVaccine?.code ?: "") }
    var currentStockText by remember { mutableStateOf(initialVaccine?.currentStock?.toString() ?: "0") }
    var purchasePriceText by remember { mutableStateOf(initialVaccine?.purchasePrice?.toString() ?: "350.0") }
    var sellingPriceText by remember { mutableStateOf(initialVaccine?.sellingPrice?.toString() ?: "750.0") }
    var minStockText by remember { mutableStateOf(initialVaccine?.minStockAlert?.toString() ?: "10") }
    var storageTemp by remember { mutableStateOf(initialVaccine?.storageTemp ?: "2°C to 8°C") }
    var unit by remember { mutableStateOf(initialVaccine?.unit ?: "doses") }
    var notes by remember { mutableStateOf(initialVaccine?.notes ?: "") }

    val isEditing = initialVaccine != null && initialVaccine.id != 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Vaccine" else "Add New Vaccine",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (isEditing && onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete this vaccine",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Vaccine Name *") },
                    placeholder = { Text("e.g. Hepatitis B, MMR, Rabies") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vaccine_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        placeholder = { Text("e.g. Pediatric, Adult") },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Product Code") },
                        placeholder = { Text("e.g. VAC-001") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = purchasePriceText,
                        onValueChange = { purchasePriceText = it },
                        label = { Text("Purchase Price (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = sellingPriceText,
                        onValueChange = { sellingPriceText = it },
                        label = { Text("Selling Price (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = currentStockText,
                        onValueChange = { currentStockText = it.filter { c -> c.isDigit() } },
                        label = { Text("Initial Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = minStockText,
                        onValueChange = { minStockText = it.filter { c -> c.isDigit() } },
                        label = { Text("Low Stock Alert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = storageTemp,
                        onValueChange = { storageTemp = it },
                        label = { Text("Storage Temp") },
                        placeholder = { Text("2°C to 8°C") },
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        placeholder = { Text("doses, vials") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Specifications") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val vac = (initialVaccine ?: Vaccine(name = name)).copy(
                        name = name.trim(),
                        category = category.trim().ifBlank { "Routine" },
                        code = code.trim(),
                        currentStock = currentStockText.toIntOrNull() ?: 0,
                        purchasePrice = purchasePriceText.toDoubleOrNull() ?: 0.0,
                        sellingPrice = sellingPriceText.toDoubleOrNull() ?: 0.0,
                        minStockAlert = minStockText.toIntOrNull() ?: 10,
                        storageTemp = storageTemp.trim().ifBlank { "2°C to 8°C" },
                        unit = unit.trim().ifBlank { "doses" },
                        notes = notes.trim()
                    )
                    onSave(vac)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_vaccine_btn")
            ) {
                Text(if (isEditing) "Save Changes" else "Create Vaccine")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- VENDOR FORM (ADD / EDIT) DIALOG ---
@Composable
fun VendorFormDialog(
    initialVendor: Vendor? = null,
    onDismiss: () -> Unit,
    onSave: (Vendor) -> Unit
) {
    var name by remember { mutableStateOf(initialVendor?.name ?: "") }
    var contactPerson by remember { mutableStateOf(initialVendor?.contactPerson ?: "") }
    var phone by remember { mutableStateOf(initialVendor?.phone ?: "") }
    var email by remember { mutableStateOf(initialVendor?.email ?: "") }
    var address by remember { mutableStateOf(initialVendor?.address ?: "") }
    var notes by remember { mutableStateOf(initialVendor?.notes ?: "") }

    val isEditing = initialVendor != null && initialVendor.id != 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Vendor" else "Add New Vendor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Vendor / Company Name *") },
                    placeholder = { Text("e.g. Serum Bio Supplies Ltd") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vendor_name_input")
                )

                OutlinedTextField(
                    value = contactPerson,
                    onValueChange = { contactPerson = it },
                    label = { Text("Contact Person / Representative") },
                    placeholder = { Text("e.g. Dr. Jane Smith") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+91 98000 00000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("orders@vendor.in") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Physical / Warehouse Address") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Delivery terms, lead time)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val ven = (initialVendor ?: Vendor(name = name)).copy(
                        name = name.trim(),
                        contactPerson = contactPerson.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        address = address.trim(),
                        notes = notes.trim()
                    )
                    onSave(ven)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_vendor_btn")
            ) {
                Text(if (isEditing) "Save Changes" else "Add Vendor")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
