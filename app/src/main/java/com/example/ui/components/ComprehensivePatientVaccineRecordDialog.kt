package com.example.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.Vaccine
import com.example.ui.theme.OnProfitGreenContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.util.PatientVaccineEntry
import com.example.ui.util.PatientVaccineRecordHelper
import com.example.ui.util.PdfBillGenerator
import com.example.ui.util.PediatricVaccineScheduleEngine
import com.example.ui.util.SignatureManager
import com.example.ui.util.WhatsAppHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Comprehensive Vaccine Record Dialog showing ALL previous vaccines taken by the patient.
 * Displays every administered dose chronologically, allows adding past/historical vaccines,
 * and allows 1-click sharing of the complete certificate via WhatsApp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComprehensivePatientVaccineRecordDialog(
    patient: PatientRegistration,
    clinicProfile: ClinicProfile,
    vaccines: List<Vaccine> = emptyList(),
    onDismiss: () -> Unit,
    onUpdatePatient: (PatientRegistration) -> Unit = {},
    onShareRecordWhatsApp: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var searchQuery by remember { mutableStateOf("") }
    var sortNewestFirst by remember { mutableStateOf(false) }
    var filterCategory by remember { mutableStateOf("ALL") }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var generatedCertificateFile by remember { mutableStateOf<File?>(null) }
    var showAddPastDialog by remember { mutableStateOf(false) }
    var showPediatricScheduleDialog by remember { mutableStateOf(false) }
    var entryToEditDate by remember { mutableStateOf<PatientVaccineEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<PatientVaccineEntry?>(null) }

    val dobResolution = remember(patient.dobOrAge, patient.dateGivenMillis) {
        PediatricVaccineScheduleEngine.resolvePatientDob(
            dobOrAge = patient.dobOrAge,
            referenceMillis = if (patient.dateGivenMillis > 0) patient.dateGivenMillis else System.currentTimeMillis()
        )
    }

    // Retrieve ALL previous vaccines taken by patient
    val allTakenVaccines = remember(patient) {
        PatientVaccineRecordHelper.getAllTakenVaccines(patient)
    }

    val clinicCount = remember(allTakenVaccines) { allTakenVaccines.count { !it.isHistorical } }
    val pastCount = remember(allTakenVaccines) { allTakenVaccines.count { it.isHistorical } }

    val filteredVaccines = remember(allTakenVaccines, searchQuery, sortNewestFirst, filterCategory) {
        val baseList = when (filterCategory) {
            "CLINIC" -> allTakenVaccines.filter { !it.isHistorical }
            "HISTORICAL" -> allTakenVaccines.filter { it.isHistorical }
            else -> allTakenVaccines
        }
        val filtered = if (searchQuery.isBlank()) {
            baseList
        } else {
            val q = searchQuery.trim().lowercase()
            baseList.filter {
                it.vaccineName.lowercase().contains(q) ||
                        it.doseName.lowercase().contains(q) ||
                        it.batchNumber.lowercase().contains(q) ||
                        it.notes.lowercase().contains(q)
            }
        }
        if (sortNewestFirst) filtered.sortedByDescending { it.dateAdministeredMillis }
        else filtered.sortedBy { it.dateAdministeredMillis }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .systemBarsPadding()
            .imePadding()
            .padding(vertical = 8.dp)
            .testTag("dialog_comprehensive_vaccine_record"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Comprehensive Vaccine Record",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${patient.patientName} • ${patient.dobOrAge} (${patient.gender})",
                            style = MaterialTheme.typography.bodySmall,
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
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Stat Banner: Total Doses Administered
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ProfitGreenContainer.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, ProfitGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = ProfitGreen,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${allTakenVaccines.size} Vaccines Taken",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = OnProfitGreenContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Complete chronological record of all previous doses administered",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnProfitGreenContainer.copy(alpha = 0.85f)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Button to add historical / past dose with date
                            FilledTonalButton(
                                onClick = { showAddPastDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_add_past_vaccine_record")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Past + Date", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }

                            // Button to open DOB Immunization Schedule & Upcoming Due Date Calculator
                            OutlinedButton(
                                onClick = { showPediatricScheduleDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_open_schedule_from_record")
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("DOB Schedule", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Patient Details Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Child: ${patient.patientName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Parent: ${patient.parentName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Age / DOB: ${patient.dobOrAge}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "📞 ${patient.mobileNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Search & Sort Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search previous vaccines...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_search_vaccine_record")
                    )

                    OutlinedButton(
                        onClick = { sortNewestFirst = !sortNewestFirst },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Sort", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }

                // Category Filter Chips: All, Clinic Doses, Historical
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filterCategory == "ALL",
                        onClick = { filterCategory = "ALL" },
                        label = { Text("All (${allTakenVaccines.size})", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("chip_filter_all_doses")
                    )
                    FilterChip(
                        selected = filterCategory == "CLINIC",
                        onClick = { filterCategory = "CLINIC" },
                        label = { Text("✅ Administered at Clinic ($clinicCount)", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("chip_filter_clinic_doses")
                    )
                    FilterChip(
                        selected = filterCategory == "HISTORICAL",
                        onClick = { filterCategory = "HISTORICAL" },
                        label = { Text("🏥 Birth & Past ($pastCount)", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("chip_filter_past_doses")
                    )
                }

                // Section: ALL PREVIOUS VACCINES TAKEN (The core list)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Administered Vaccines (${filteredVaccines.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Chronological History",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (filteredVaccines.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vaccines,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No previous vaccines recorded yet" else "No vaccines match '$searchQuery'",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Add Past Dose' above or administer a vaccine during a visit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredVaccines.forEachIndexed { index, entry ->
                            val dateStr = dateFormat.format(Date(entry.dateAdministeredMillis))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vaccine_history_item_${entry.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                                        // Number badge
                                        Surface(
                                            shape = CircleShape,
                                            color = ProfitGreenContainer,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${index + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OnProfitGreenContainer
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = entry.vaccineName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                ) {
                                                    Text(
                                                        text = entry.doseName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = ProfitGreenContainer.copy(alpha = 0.55f),
                                                    border = BorderStroke(1.dp, ProfitGreen.copy(alpha = 0.4f)),
                                                    modifier = Modifier
                                                        .clickable { entryToEditDate = entry }
                                                        .testTag("chip_edit_vaccine_date_${entry.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CalendarMonth,
                                                            contentDescription = null,
                                                            tint = ProfitGreen,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = dateStr,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = OnProfitGreenContainer
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.EditCalendar,
                                                            contentDescription = "Edit Date",
                                                            tint = ProfitGreen,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }

                                                if (entry.batchNumber.isNotBlank()) {
                                                    Text(
                                                        text = "• Batch: ${entry.batchNumber}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }

                                            if (entry.notes.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Note: ${entry.notes}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Status tag
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (entry.isHistorical) Color(0xFFE0F2FE) else ProfitGreenContainer
                                            ) {
                                                Text(
                                                    text = if (entry.isHistorical) "🏥 Past / Hospital Record" else "✅ Administered at Clinic",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (entry.isHistorical) Color(0xFF0369A1) else OnProfitGreenContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Actions: Edit Date & Delete Entry
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { entryToEditDate = entry },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .height(28.dp)
                                                .testTag("btn_edit_vaccine_date_${entry.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EditCalendar,
                                                contentDescription = "Add or Edit Date",
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Set Date", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = { entryToDelete = entry },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove entry",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section: Next Scheduled Vaccine
                if (patient.nextPlannedVaccine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Upcoming Scheduled Next Vaccine",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = patient.nextPlannedVaccine,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Scheduled Due: ${patient.nextDueDate} • Status: ${patient.reminderStatus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // 1. Generate & Preview Official PDF Vaccination Certificate
                FilledTonalButton(
                    onClick = {
                        isGeneratingPdf = true
                        val savedSig = SignatureManager.getSavedSignature(context)
                        val sigId = if (savedSig != null) SignatureManager.generateSignatureId() else ""
                        val res = PdfBillGenerator.generateVaccineCertificatePdf(
                            context = context,
                            patient = patient,
                            clinic = clinicProfile,
                            entries = allTakenVaccines,
                            signatureBitmap = savedSig,
                            signerName = if (savedSig != null) clinicProfile.doctorName else "",
                            signatureId = sigId
                        )
                        isGeneratingPdf = false
                        res.onSuccess { pdfFile ->
                            PdfBillGenerator.savePdfToDownloads(context, pdfFile, pdfFile.name)
                            generatedCertificateFile = pdfFile
                            Toast.makeText(
                                context,
                                "PDF Certificate generated (${allTakenVaccines.size} doses)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { ex ->
                            Toast.makeText(
                                context,
                                "Failed to generate PDF certificate: ${ex.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_generate_vaccine_record_pdf")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF Certificate", fontWeight = FontWeight.Bold)
                }

                // 2. Share on WhatsApp
                Button(
                    onClick = onShareRecordWhatsApp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_share_vaccine_record_whatsapp")
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    // PDF Certificate Preview, Sign, Download, Print & Share Dialog
    if (generatedCertificateFile != null) {
        VaccineCertificatePdfDialog(
            initialPdfFile = generatedCertificateFile!!,
            patient = patient,
            clinicProfile = clinicProfile,
            entries = allTakenVaccines,
            onDismiss = { generatedCertificateFile = null }
        )
    }

    // Add Past / Historical Vaccine Dialog
    if (showAddPastDialog) {
        AddPastVaccineDialog(
            patient = patient,
            vaccines = vaccines,
            dobMillis = dobResolution.dobMillis,
            onDismiss = { showAddPastDialog = false },
            onSavePastVaccine = { vName, dose, dateMillis, batch, notes ->
                val updated = PatientVaccineRecordHelper.appendVaccineToPatientHistory(
                    patient = patient,
                    newVaccineNames = vName,
                    dose = dose,
                    batch = batch,
                    dateMillis = dateMillis,
                    notes = notes,
                    isHistorical = true
                )
                onUpdatePatient(updated)
                showAddPastDialog = false
                Toast.makeText(context, "Added '$vName' (${dateFormat.format(Date(dateMillis))}) to record", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Edit Date of Any Previously Given Vaccine Dialog
    if (entryToEditDate != null) {
        val entry = entryToEditDate!!
        EditOrAddPastVaccineDateDialog(
            title = "Set / Edit Date of Previously Given Vaccine",
            vaccineName = entry.vaccineName,
            initialDoseName = entry.doseName,
            initialDateMillis = entry.dateAdministeredMillis,
            initialBatch = entry.batchNumber,
            initialNotes = entry.notes,
            dobMillis = dobResolution.dobMillis,
            onDismiss = { entryToEditDate = null },
            onConfirm = { newDose, newDateMillis, newBatch, newNotes ->
                val updated = PatientVaccineRecordHelper.updateVaccineDateInPatientHistory(
                    patient = patient,
                    entryId = entry.id,
                    newDateMillis = newDateMillis,
                    newDoseName = newDose,
                    newBatchNumber = newBatch,
                    newNotes = newNotes
                )
                onUpdatePatient(updated)
                entryToEditDate = null
                Toast.makeText(
                    context,
                    "Updated ${entry.vaccineName} date to ${dateFormat.format(Date(newDateMillis))}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Pediatric Vaccine Schedule Dialog
    if (showPediatricScheduleDialog) {
        PediatricVaccineScheduleDialog(
            patient = patient,
            clinicProfile = clinicProfile,
            onUpdatePatient = { updated ->
                onUpdatePatient(updated)
            },
            onDismiss = { showPediatricScheduleDialog = false }
        )
    }

    // Confirmation for deleting an entry
    if (entryToDelete != null) {
        val entry = entryToDelete!!
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Vaccine Record Entry?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Remove '${entry.vaccineName} (${entry.doseName})' from ${patient.patientName}'s immunization record?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = PatientVaccineRecordHelper.removeVaccineFromPatientHistory(patient, entry.id)
                        onUpdatePatient(updated)
                        entryToDelete = null
                        Toast.makeText(context, "Record entry removed", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { entryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dedicated dialog that displays the generated PDF Vaccination Certificate preview,
 * allows signing with the doctor's digital signature or official clinic seal,
 * saving to Downloads, printing/saving via Android PrintManager, or sharing on WhatsApp.
 */
@Composable
fun VaccineCertificatePdfDialog(
    initialPdfFile: File,
    patient: PatientRegistration,
    clinicProfile: ClinicProfile,
    entries: List<PatientVaccineEntry>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentPdfFile by remember { mutableStateOf(initialPdfFile) }
    var isSigned by remember { mutableStateOf(SignatureManager.hasSavedSignature(context)) }
    var signerName by remember {
        mutableStateOf(if (SignatureManager.hasSavedSignature(context)) clinicProfile.doctorName else "")
    }
    var signatureId by remember {
        mutableStateOf(if (SignatureManager.hasSavedSignature(context)) SignatureManager.generateSignatureId() else "")
    }
    var savedDownloadPath by remember { mutableStateOf("Downloads/${initialPdfFile.name}") }
    var showSignatureDialog by remember { mutableStateOf(false) }

    val pageBitmaps = remember(currentPdfFile) {
        PdfBillGenerator.renderPdfPagesToBitmaps(currentPdfFile)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(vertical = 12.dp)
            .testTag("dialog_vaccine_certificate_pdf"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PDF Vaccination Certificate",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${patient.patientName} • ${entries.size} Doses Recorded",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close PDF Preview")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Status Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ProfitGreenContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, ProfitGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ProfitGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "PDF Certificate Generated & Saved",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = OnProfitGreenContainer
                                )
                                Text(
                                    text = savedDownloadPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnProfitGreenContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                        if (isSigned) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ProfitGreen
                            ) {
                                Text(
                                    text = "✔ SIGNED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Rendered PDF Document Pages Preview
                if (pageBitmaps.isNotEmpty()) {
                    pageBitmaps.forEachIndexed { index, bmp ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pdf_certificate_page_preview_$index")
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "PDF Certificate Page ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Digital Signature / Seal Bar
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSigned) "✔ Digitally Signed by $signerName" else "Doctor Signature & Clinic Seal",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isSigned) "ID: $signatureId" else "Add official signature or stamp to PDF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val newSigId = SignatureManager.generateSignatureId()
                                    val sealBmp = SignatureManager.createCertifiedSealBitmap(
                                        doctorName = clinicProfile.doctorName,
                                        registrationNumber = clinicProfile.registrationNumber,
                                        clinicName = clinicProfile.clinicName,
                                        signatureId = newSigId
                                    )
                                    PdfBillGenerator.generateVaccineCertificatePdf(
                                        context = context,
                                        patient = patient,
                                        clinic = clinicProfile,
                                        entries = entries,
                                        signatureBitmap = sealBmp,
                                        signerName = clinicProfile.doctorName,
                                        signatureId = newSigId
                                    ).onSuccess { newFile ->
                                        currentPdfFile = newFile
                                        isSigned = true
                                        signerName = clinicProfile.doctorName
                                        signatureId = newSigId
                                        PdfBillGenerator.savePdfToDownloads(context, newFile, newFile.name)
                                        Toast.makeText(context, "Official clinic seal applied to PDF", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_stamp_certificate_pdf")
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Seal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = { showSignatureDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_sign_certificate_pdf")
                            ) {
                                Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sign", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Primary Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            PdfBillGenerator.savePdfToDownloads(context, currentPdfFile, currentPdfFile.name)
                                .onSuccess { path ->
                                    savedDownloadPath = path
                                    Toast.makeText(context, "Saved PDF to $path", Toast.LENGTH_LONG).show()
                                }
                                .onFailure { e ->
                                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_certificate_downloads")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save PDF", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            PdfBillGenerator.printPdfFile(
                                context = context,
                                pdfFile = currentPdfFile,
                                jobName = "Vaccine_Certificate_${patient.patientName}"
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_print_certificate_pdf")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print / PDF", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        WhatsAppHelper.shareVaccineCertificatePdf(
                            context = context,
                            pdfFile = currentPdfFile,
                            patientPhone = patient.mobileNumber,
                            patientName = patient.patientName,
                            clinicName = clinicProfile.clinicName
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_share_certificate_whatsapp")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share PDF Certificate on WhatsApp",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("btn_close_certificate_pdf")) {
                Text("Done")
            }
        }
    )

    if (showSignatureDialog) {
        DigitalSignatureDialog(
            clinic = clinicProfile,
            onDismiss = { showSignatureDialog = false },
            onSignatureConfirmed = { bitmap, _, confirmedSigner, confirmedId ->
                PdfBillGenerator.generateVaccineCertificatePdf(
                    context = context,
                    patient = patient,
                    clinic = clinicProfile,
                    entries = entries,
                    signatureBitmap = bitmap,
                    signerName = confirmedSigner,
                    signatureId = confirmedId
                ).onSuccess { newFile ->
                    currentPdfFile = newFile
                    isSigned = true
                    signerName = confirmedSigner
                    signatureId = confirmedId
                    PdfBillGenerator.savePdfToDownloads(context, newFile, newFile.name)
                    showSignatureDialog = false
                    Toast.makeText(context, "PDF Certificate digitally signed!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

/**
 * Modal dialog for recording a past vaccine taken by the child (e.g. at birth, previous hospital, or native clinic).
 */
@Composable
fun AddPastVaccineDialog(
    patient: PatientRegistration,
    vaccines: List<Vaccine>,
    dobMillis: Long? = null,
    onDismiss: () -> Unit,
    onSavePastVaccine: (vaccineName: String, doseName: String, dateMillis: Long, batch: String, notes: String) -> Unit
) {
    val resolvedDobMillis = remember(patient.dobOrAge, dobMillis) {
        dobMillis ?: PediatricVaccineScheduleEngine.resolvePatientDob(patient.dobOrAge).dobMillis
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var vaccineName by remember { mutableStateOf("") }
    var doseName by remember { mutableStateOf("Dose 1") }
    var batchNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var dateAdministeredMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var dateString by remember(dateAdministeredMillis) {
        mutableStateOf(dateFormat.format(Date(dateAdministeredMillis)))
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val routinePresets = listOf(
        "BCG", "OPV-0", "Hepatitis B-0", "Pentavalent-1", "Rotavirus-1", "PCV-1",
        "Pentavalent-2", "Rotavirus-2", "Pentavalent-3", "Influenza", "MMR", "Typhoid",
        "Hepatitis A", "Varicella", "DTP Booster"
    )

    val dosePresets = listOf(
        "At Birth", "Dose 1", "Dose 2", "Dose 3", "Booster 1", "Booster 2", "Annual Booster"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Add Previous / Past Vaccine + Date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("For: ${patient.patientName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
                Text(
                    text = "Record vaccines taken previously (e.g. at birth, hospital, or prior visit) along with the date administered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Routine Vaccine Suggestion Chips
                Text("Tap Quick Vaccine Suggestion:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    routinePresets.forEach { preset ->
                        FilterChip(
                            selected = vaccineName.equals(preset, ignoreCase = true),
                            onClick = {
                                vaccineName = preset
                                when {
                                    preset.contains("Birth", ignoreCase = true) || preset == "BCG" || preset.contains("-0") -> {
                                        doseName = "At Birth"
                                        dateAdministeredMillis = minOf(resolvedDobMillis, System.currentTimeMillis())
                                    }
                                    preset.endsWith("-1") -> {
                                        doseName = "Dose 1"
                                        dateAdministeredMillis = minOf(resolvedDobMillis + 42L * 86400000L, System.currentTimeMillis())
                                    }
                                    preset.endsWith("-2") -> {
                                        doseName = "Dose 2"
                                        dateAdministeredMillis = minOf(resolvedDobMillis + 70L * 86400000L, System.currentTimeMillis())
                                    }
                                    preset.endsWith("-3") -> {
                                        doseName = "Dose 3"
                                        dateAdministeredMillis = minOf(resolvedDobMillis + 98L * 86400000L, System.currentTimeMillis())
                                    }
                                    preset.contains("Booster", ignoreCase = true) -> {
                                        doseName = "Booster 1"
                                    }
                                }
                            },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = vaccineName,
                    onValueChange = { vaccineName = it },
                    label = { Text("Vaccine Name *") },
                    placeholder = { Text("e.g. BCG, Pentavalent, MMR") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_past_vaccine_name")
                )

                // Dose presets
                Text("Dose Stage:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dosePresets.forEach { preset ->
                        FilterChip(
                            selected = doseName.equals(preset, ignoreCase = true),
                            onClick = { doseName = preset },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = doseName,
                    onValueChange = { doseName = it },
                    label = { Text("Dose Label") },
                    placeholder = { Text("e.g. Dose 1, At Birth") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Administered Date Picker (Prominent)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .testTag("btn_add_past_vaccine_date_picker")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Date Taken / Administered:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(dateString, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        }
                        Button(
                            onClick = { showDatePicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select Date", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick DOB-based Date Presets
                Text("Quick Date Presets (from Child's DOB):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val datePresets = listOf(
                        "At Birth (DOB)" to 0,
                        "6 Wks" to 42,
                        "10 Wks" to 70,
                        "14 Wks" to 98,
                        "6 Mos" to 182,
                        "9 Mos" to 274,
                        "Today" to -1
                    )
                    datePresets.forEach { (label, daysOffset) ->
                        OutlinedButton(
                            onClick = {
                                val target = if (daysOffset < 0) {
                                    System.currentTimeMillis()
                                } else {
                                    val cal = Calendar.getInstance().apply {
                                        timeInMillis = resolvedDobMillis
                                        add(Calendar.DAY_OF_YEAR, daysOffset)
                                    }
                                    minOf(cal.timeInMillis, System.currentTimeMillis())
                                }
                                dateAdministeredMillis = target
                                dateString = dateFormat.format(Date(target))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = batchNumber,
                    onValueChange = { batchNumber = it },
                    label = { Text("Batch Number (Optional)") },
                    placeholder = { Text("e.g. BCG-902") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Remarks / Hospital (Optional)") },
                    placeholder = { Text("e.g. Given at Maternity Hospital, verified card") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (vaccineName.isBlank()) return@Button
                    onSavePastVaccine(
                        vaccineName.trim(),
                        doseName.trim().ifBlank { "Dose 1" },
                        dateAdministeredMillis,
                        batchNumber.trim(),
                        notes.trim()
                    )
                },
                enabled = vaccineName.isNotBlank(),
                modifier = Modifier.testTag("btn_confirm_add_past_vaccine")
            ) {
                Text("Add to Record", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        AppDatePickerModal(
            title = "Select Date Administered",
            initialDateMillis = dateAdministeredMillis,
            onDateSelected = { selectedMillis, formattedStr ->
                dateAdministeredMillis = selectedMillis
                dateString = formattedStr
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}
