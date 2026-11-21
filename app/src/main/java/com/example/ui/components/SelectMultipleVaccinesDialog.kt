package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Vaccine
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer

data class PediatricVaccineCombo(
    val milestone: String,
    val vaccines: List<String>
)

val STANDARD_PEDIATRIC_COMBOS = listOf(
    PediatricVaccineCombo("At Birth (0m)", listOf("BCG", "OPV-0", "Hepatitis B-0")),
    PediatricVaccineCombo("6 Weeks (1.5m)", listOf("Pentavalent-1", "Rotavirus-1", "PCV-1", "IPV-1")),
    PediatricVaccineCombo("10 Weeks (2.5m)", listOf("Pentavalent-2", "Rotavirus-2", "PCV-2")),
    PediatricVaccineCombo("14 Weeks (3.5m)", listOf("Pentavalent-3", "Rotavirus-3", "PCV-3", "IPV-2")),
    PediatricVaccineCombo("6 Months", listOf("Influenza-1", "OPV-1")),
    PediatricVaccineCombo("9 Months", listOf("MMR-1", "Typhoid Conjugate (TCV)")),
    PediatricVaccineCombo("12 Months (1y)", listOf("Hepatitis A-1", "Japanese Encephalitis")),
    PediatricVaccineCombo("15-18 Months", listOf("MMR-2", "Varicella-1 (Chickenpox)", "PCV Booster", "DTP Booster 1")),
    PediatricVaccineCombo("2 Years", listOf("Typhoid Booster", "Hepatitis A-2")),
    PediatricVaccineCombo("4-5 Years", listOf("DTP Booster 2", "MMR-3", "Varicella-2", "OPV Booster"))
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectMultipleVaccinesDialog(
    title: String,
    subtitle: String,
    vaccines: List<Vaccine>,
    initialSelectedVaccines: List<String>,
    onConfirmSelection: (selectedNames: List<String>, selectedIds: List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    // Current list of selected vaccine names (ordered)
    val selectedNames = remember {
        mutableStateListOf<String>().apply {
            addAll(initialSelectedVaccines.filter { it.isNotBlank() })
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var customVaccineInput by remember { mutableStateOf("") }

    val categories = remember(vaccines) {
        listOf("All") + vaccines.map { it.category }.distinct()
    }

    val filteredCatalog = remember(vaccines, searchQuery, selectedCategoryFilter) {
        vaccines.filter { v ->
            val matchesSearch = v.name.contains(searchQuery, ignoreCase = true) ||
                    v.code.contains(searchQuery, ignoreCase = true) ||
                    v.category.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategoryFilter == "All" || v.category.equals(selectedCategoryFilter, ignoreCase = true)
            matchesSearch && matchesCat
        }
    }

    fun toggleVaccineName(name: String) {
        val existingIndex = selectedNames.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (existingIndex >= 0) {
            selectedNames.removeAt(existingIndex)
        } else {
            selectedNames.add(name)
        }
    }

    fun addCustomVaccine() {
        val trimmed = customVaccineInput.trim()
        if (trimmed.isNotBlank() && selectedNames.none { it.equals(trimmed, ignoreCase = true) }) {
            selectedNames.add(trimmed)
            customVaccineInput = ""
        }
    }

    fun applyCombo(combo: PediatricVaccineCombo) {
        for (vName in combo.vaccines) {
            // Find existing matching vaccine from catalog or use combo name
            val match = vaccines.find {
                it.name.contains(vName, ignoreCase = true) || vName.contains(it.name, ignoreCase = true)
            }
            val targetName = match?.name ?: vName
            if (selectedNames.none { it.equals(targetName, ignoreCase = true) }) {
                selectedNames.add(targetName)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(vertical = 12.dp)
            .testTag("multi_vaccine_select_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Vaccines,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
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
                    .heightIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top Selection Status & Quick Actions Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedNames.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedNames.size} Selected",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedNames.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (filteredCatalog.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    filteredCatalog.forEach { vac ->
                                        if (selectedNames.none { it.equals(vac.name, ignoreCase = true) }) {
                                            selectedNames.add(vac.name)
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("btn_select_all_vaccines")
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select All", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (selectedNames.isNotEmpty()) {
                            TextButton(
                                onClick = { selectedNames.clear() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("btn_clear_all_vaccines")
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear All", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Selected Vaccines Chips Area
                if (selectedNames.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Selected for this visit (Tap ✕ to remove):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                selectedNames.forEach { name ->
                                    InputChip(
                                        selected = true,
                                        onClick = { toggleVaccineName(name) },
                                        label = { Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove $name",
                                                modifier = Modifier.size(12.dp)
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Pediatric Combos Preset Bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Quick Pediatric Age Combos (1-Tap Selection):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        STANDARD_PEDIATRIC_COMBOS.forEach { combo ->
                            val isComboFullySelected = combo.vaccines.all { v ->
                                selectedNames.any { it.contains(v, ignoreCase = true) || v.contains(it, ignoreCase = true) }
                            }
                            FilterChip(
                                selected = isComboFullySelected,
                                onClick = { applyCombo(combo) },
                                label = { Text(combo.milestone, fontSize = 11.sp) },
                                leadingIcon = if (isComboFullySelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Search & Filter
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search vaccine by name, disease or code...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("multi_vaccine_search_input")
                )

                // Category Chips
                if (categories.size > 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Add Custom / Unlisted Vaccine Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customVaccineInput,
                        onValueChange = { customVaccineInput = it },
                        placeholder = { Text("Or add other vaccine name...") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("input_custom_vaccine_name")
                    )
                    OutlinedButton(
                        onClick = { addCustomVaccine() },
                        enabled = customVaccineInput.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.testTag("btn_add_custom_vaccine")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Add", fontSize = 12.sp)
                    }
                }

                // Vaccine Catalog List with Multi-Select Checkboxes
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredCatalog, key = { it.id }) { vac ->
                        val isSelected = selectedNames.any {
                            it.equals(vac.name, ignoreCase = true) || it.contains(vac.name, ignoreCase = true) || vac.name.contains(it, ignoreCase = true)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggleVaccineName(vac.name) }
                                .testTag("vaccine_item_check_${vac.id}"),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { toggleVaccineName(vac.name) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = vac.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = vac.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            text = "• Stock: ${vac.currentStock} ${vac.unit}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (vac.currentStock > 0) ProfitGreen else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "• ₹${vac.sellingPrice.toInt()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filteredCatalog.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching vaccines in catalog. Use the 'Add' box above to enter custom vaccine.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val matchingIds = selectedNames.mapNotNull { name ->
                        vaccines.find { it.name.equals(name, ignoreCase = true) || it.name.contains(name, ignoreCase = true) }?.id
                    }.distinct()
                    onConfirmSelection(selectedNames.toList(), matchingIds)
                },
                modifier = Modifier.testTag("btn_confirm_multi_vaccine_selection")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedNames.isNotEmpty()) "Apply (${selectedNames.size} Selected)" else "Done (0 Selected)",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
