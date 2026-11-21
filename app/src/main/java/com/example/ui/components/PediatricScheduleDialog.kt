package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.service.AppointmentAlarmScheduler
import com.example.ui.theme.LossRed
import com.example.ui.theme.LossRedContainer
import com.example.ui.theme.OnLossRedContainer
import com.example.ui.theme.OnProfitGreenContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.util.EvaluatedMilestone
import com.example.ui.util.EvaluatedScheduledVaccine
import com.example.ui.util.PatientVaccineEntry
import com.example.ui.util.PatientVaccineRecordHelper
import com.example.ui.util.PediatricVaccineScheduleEngine
import com.example.ui.util.ScheduleItemStatus
import com.example.ui.util.WhatsAppHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Interactive Pediatric Vaccine Scheduling Module Dialog.
 * Calculates upcoming due dates for a pediatric patient based on their Date of Birth (DOB),
 * recommended immunization schedules (IAP / NIS), and previously given vaccine dates.
 * Also allows adding or editing the date of any previously given vaccine directly from the schedule.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PediatricVaccineScheduleDialog(
    patient: PatientRegistration,
    clinicProfile: ClinicProfile,
    allPatients: List<PatientRegistration> = emptyList(),
    onSwitchPatient: ((PatientRegistration) -> Unit)? = null,
    onUpdatePatient: (PatientRegistration) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var scheduleFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, OVERDUE, COMPLETED
    var showDobPicker by remember { mutableStateOf(false) }

    // State for marking an uncompleted scheduled vaccine as previously given with a specific date
    var vaccineToMarkGiven by remember { mutableStateOf<EvaluatedScheduledVaccine?>(null) }

    // State for editing the date of an already-given vaccine entry
    var entryToEditDate by remember { mutableStateOf<PatientVaccineEntry?>(null) }

    // State for adding any custom past vaccine with date
    var showAddCustomPastWithDate by remember { mutableStateOf(false) }

    val scheduleSummary = remember(patient) {
        PediatricVaccineScheduleEngine.evaluatePatientSchedule(patient)
    }

    val filteredMilestones = remember(scheduleSummary, scheduleFilter) {
        when (scheduleFilter) {
            "PENDING" -> scheduleSummary.milestones.filter { it.status != ScheduleItemStatus.COMPLETED }
            "OVERDUE" -> scheduleSummary.milestones.filter { it.status == ScheduleItemStatus.OVERDUE || it.status == ScheduleItemStatus.DUE_SOON }
            "COMPLETED" -> scheduleSummary.milestones.filter { it.completedCount > 0 }
            else -> scheduleSummary.milestones
        }
    }

    val progressFraction = if (scheduleSummary.totalRecommendedDoses > 0) {
        scheduleSummary.completedDosesCount.toFloat() / scheduleSummary.totalRecommendedDoses.toFloat()
    } else 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .systemBarsPadding()
            .imePadding()
            .padding(vertical = 8.dp)
            .testTag("dialog_pediatric_vaccine_schedule"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF00796B),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Pediatric Vaccine Scheduler",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "DOB-Based Upcoming Due Date Calculator",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Optional Patient Switcher if opened from top hub with multiple patients
                if (allPatients.size > 1 && onSwitchPatient != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Select Pediatric Patient:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allPatients.forEach { p ->
                                val selected = p.id == patient.id
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSwitchPatient(p) },
                                    label = {
                                        Text(
                                            text = p.patientName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // 1. Vibrant DOB & Child Profile Card with 1-Click DOB Picker
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF00695C),
                                        Color(0xFF00897B),
                                        Color(0xFF0277BD)
                                    )
                                )
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${patient.patientName} (${patient.gender})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Cake,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "DOB: ${scheduleSummary.dobResolution.formattedDob}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFF59D)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (scheduleSummary.dobResolution.isExactDob) "Exact DOB" else "Est. from Age",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Current Age: ${scheduleSummary.dobResolution.calculatedAgeText} (${scheduleSummary.dobResolution.ageInWeeks} wks)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }

                            Button(
                                onClick = { showDobPicker = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF00695C)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("btn_schedule_set_dob")
                            ) {
                                Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (scheduleSummary.dobResolution.isExactDob) "Edit DOB" else "Set DOB",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Immunization Progress Bar
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Immunization Schedule Progress",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = "${scheduleSummary.completedDosesCount} / ${scheduleSummary.totalRecommendedDoses} Doses (${(progressFraction * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF69F0AE),
                                trackColor = Color.White.copy(alpha = 0.25f)
                            )
                        }
                    }
                }

                // 2. Quick Action Bar: Add Previously Given Vaccine with Date
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEDE7F6),
                    border = BorderStroke(1.dp, Color(0xFF7E57C2).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Record Previously Given Vaccine with Date",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4527A0)
                            )
                            Text(
                                text = "Add past doses with date administered to refine upcoming due dates",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color(0xFF5E35B1)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showAddCustomPastWithDate = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E35B1)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_schedule_add_past_vaccine_with_date")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Past + Date", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 3. Auto-Calculated Next Upcoming Milestone Card
                if (scheduleSummary.nextUpcomingMilestone != null) {
                    val nextMilestone = scheduleSummary.nextUpcomingMilestone
                    val isOverdue = nextMilestone.status == ScheduleItemStatus.OVERDUE
                    val cardBg = if (isOverdue) LossRedContainer.copy(alpha = 0.55f) else WarningAmberContainer.copy(alpha = 0.55f)
                    val borderColor = if (isOverdue) LossRed else Color(0xFFF57C00)

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.5.dp, borderColor.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_next_calculated_milestone")
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.EventAvailable,
                                        contentDescription = null,
                                        tint = borderColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "NEXT RECOMMENDED MILESTONE: ${nextMilestone.spec.title.uppercase()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = borderColor
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Calculated Due Date: ${nextMilestone.relativeDueLabel}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Pending Vaccines: ${scheduleSummary.nextRecommendedVaccineNames.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val plannedStr = scheduleSummary.nextRecommendedVaccineNames.joinToString(", ")
                                        val dueMillis = nextMilestone.calculatedDueDateMillis
                                        val dueLabel = nextMilestone.relativeDueLabel
                                        val updated = patient.copy(
                                            nextPlannedVaccine = plannedStr,
                                            nextDueDate = dueLabel,
                                            nextDueDateMillis = dueMillis,
                                            reminderStatus = "PENDING"
                                        )
                                        onUpdatePatient(updated)
                                        AppointmentAlarmScheduler.schedulePatientAppointmentAlarm(context, updated)
                                        Toast.makeText(
                                            context,
                                            "Scheduled '${nextMilestone.spec.title}' ($dueLabel) for ${patient.patientName}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_apply_calculated_schedule")
                                ) {
                                    Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Apply as Next Due Reminder",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val msg = PediatricVaccineScheduleEngine.formatScheduleForWhatsApp(
                                            patient = patient,
                                            clinicProfile = clinicProfile,
                                            summary = scheduleSummary
                                        )
                                        WhatsAppHelper.sendWhatsAppText(context, patient.mobileNumber, msg)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_whatsapp_full_schedule")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("WhatsApp", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 4. Filter Chips for Schedule Milestones
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = scheduleFilter == "ALL",
                        onClick = { scheduleFilter = "ALL" },
                        label = { Text("All Stages (${scheduleSummary.milestones.size})", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("chip_schedule_all")
                    )
                    FilterChip(
                        selected = scheduleFilter == "PENDING",
                        onClick = { scheduleFilter = "PENDING" },
                        label = { Text("⏳ Pending / Due", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("chip_schedule_pending")
                    )
                    FilterChip(
                        selected = scheduleFilter == "OVERDUE",
                        onClick = { scheduleFilter = "OVERDUE" },
                        label = {
                            Text(
                                "⚠️ Due Soon / Overdue (${scheduleSummary.overdueDosesCount + scheduleSummary.dueSoonDosesCount})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("chip_schedule_overdue")
                    )
                    FilterChip(
                        selected = scheduleFilter == "COMPLETED",
                        onClick = { scheduleFilter = "COMPLETED" },
                        label = { Text("✅ Given (${scheduleSummary.completedDosesCount})", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("chip_schedule_completed")
                    )
                }

                // 5. Milestone Cards List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filteredMilestones.forEach { milestone ->
                        MilestoneScheduleCard(
                            milestone = milestone,
                            dateFormat = dateFormat,
                            onScheduleThisMilestone = {
                                val pendingNames = milestone.vaccines
                                    .filter { it.status != ScheduleItemStatus.COMPLETED }
                                    .map { it.spec.shortName }
                                    .ifEmpty { milestone.vaccines.map { it.spec.shortName } }
                                    .joinToString(", ")
                                val updated = patient.copy(
                                    nextPlannedVaccine = pendingNames,
                                    nextDueDate = milestone.relativeDueLabel,
                                    nextDueDateMillis = milestone.calculatedDueDateMillis,
                                    reminderStatus = "PENDING"
                                )
                                onUpdatePatient(updated)
                                AppointmentAlarmScheduler.schedulePatientAppointmentAlarm(context, updated)
                                Toast.makeText(
                                    context,
                                    "Next reminder updated to ${milestone.spec.title} (${milestone.formattedDueDate})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onMarkVaccineGivenWithDate = { evaluatedVac ->
                                vaccineToMarkGiven = evaluatedVac
                            },
                            onEditGivenVaccineDate = { entry ->
                                entryToEditDate = entry
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("btn_close_pediatric_schedule")) {
                Text("Done")
            }
        }
    )

    // Date Picker for Setting / Updating Child's Exact Date of Birth
    if (showDobPicker) {
        AppDatePickerModal(
            title = "Select Child Date of Birth (DOB)",
            initialDateMillis = scheduleSummary.dobResolution.dobMillis,
            maxDateMillis = System.currentTimeMillis(),
            onDateSelected = { selectedDobMillis, formattedDob ->
                val ageText = calculateAgeFromDob(selectedDobMillis)
                val newDobOrAge = "$ageText (DOB: $formattedDob)"
                val tempPatient = patient.copy(dobOrAge = newDobOrAge)
                val newSummary = PediatricVaccineScheduleEngine.evaluatePatientSchedule(tempPatient, overrideDobMillis = selectedDobMillis)

                val updatedPatient = if (newSummary.nextUpcomingMilestone != null) {
                    tempPatient.copy(
                        nextPlannedVaccine = newSummary.nextRecommendedVaccineNames.joinToString(", "),
                        nextDueDate = newSummary.nextRecommendedDueDateLabel,
                        nextDueDateMillis = newSummary.nextRecommendedDueDateMillis ?: tempPatient.nextDueDateMillis
                    )
                } else {
                    tempPatient
                }
                onUpdatePatient(updatedPatient)
                showDobPicker = false
                Toast.makeText(
                    context,
                    "Updated DOB to $formattedDob & recalculated schedule!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showDobPicker = false }
        )
    }

    // Modal for Marking a Scheduled Vaccine as Previously Given with a Specific Date
    if (vaccineToMarkGiven != null) {
        val targetVac = vaccineToMarkGiven!!
        val defaultDateMillis = if (targetVac.recommendedDueDateMillis <= System.currentTimeMillis()) {
            targetVac.recommendedDueDateMillis
        } else {
            System.currentTimeMillis()
        }
        EditOrAddPastVaccineDateDialog(
            title = "Add Date for Previously Given Vaccine",
            vaccineName = targetVac.spec.shortName,
            initialDoseName = targetVac.spec.doseLabel,
            initialDateMillis = defaultDateMillis,
            initialBatch = "",
            initialNotes = "Recorded via Pediatric Schedule",
            dobMillis = scheduleSummary.dobResolution.dobMillis,
            onDismiss = { vaccineToMarkGiven = null },
            onConfirm = { doseName, selectedDateMillis, batch, notes ->
                val withHistory = PatientVaccineRecordHelper.appendVaccineToPatientHistory(
                    patient = patient,
                    newVaccineNames = targetVac.spec.shortName,
                    dose = doseName,
                    batch = batch,
                    dateMillis = selectedDateMillis,
                    notes = notes,
                    isHistorical = true
                )
                // Re-evaluate schedule to auto-update next due date if needed
                val reEval = PediatricVaccineScheduleEngine.evaluatePatientSchedule(withHistory)
                val finalUpdated = if (reEval.nextUpcomingMilestone != null) {
                    withHistory.copy(
                        nextPlannedVaccine = reEval.nextRecommendedVaccineNames.joinToString(", "),
                        nextDueDate = reEval.nextRecommendedDueDateLabel,
                        nextDueDateMillis = reEval.nextRecommendedDueDateMillis ?: withHistory.nextDueDateMillis
                    )
                } else {
                    withHistory
                }
                onUpdatePatient(finalUpdated)
                vaccineToMarkGiven = null
                Toast.makeText(
                    context,
                    "Recorded ${targetVac.spec.shortName} given on ${dateFormat.format(Date(selectedDateMillis))}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Modal for Editing the Date of an Already-Given Vaccine Entry
    if (entryToEditDate != null) {
        val entry = entryToEditDate!!
        EditOrAddPastVaccineDateDialog(
            title = "Edit Date of Previously Given Vaccine",
            vaccineName = entry.vaccineName,
            initialDoseName = entry.doseName,
            initialDateMillis = entry.dateAdministeredMillis,
            initialBatch = entry.batchNumber,
            initialNotes = entry.notes,
            dobMillis = scheduleSummary.dobResolution.dobMillis,
            onDismiss = { entryToEditDate = null },
            onConfirm = { doseName, selectedDateMillis, batch, notes ->
                val updated = PatientVaccineRecordHelper.updateVaccineDateInPatientHistory(
                    patient = patient,
                    entryId = entry.id,
                    newDateMillis = selectedDateMillis,
                    newDoseName = doseName,
                    newBatchNumber = batch,
                    newNotes = notes
                )
                // Recalculate schedule in case catch-up interval changed
                val reEval = PediatricVaccineScheduleEngine.evaluatePatientSchedule(updated)
                val finalUpdated = if (reEval.nextUpcomingMilestone != null) {
                    updated.copy(
                        nextPlannedVaccine = reEval.nextRecommendedVaccineNames.joinToString(", "),
                        nextDueDate = reEval.nextRecommendedDueDateLabel,
                        nextDueDateMillis = reEval.nextRecommendedDueDateMillis ?: updated.nextDueDateMillis
                    )
                } else {
                    updated
                }
                onUpdatePatient(finalUpdated)
                entryToEditDate = null
                Toast.makeText(
                    context,
                    "Updated ${entry.vaccineName} date to ${dateFormat.format(Date(selectedDateMillis))}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Modal for Adding Custom Past Vaccine with Date
    if (showAddCustomPastWithDate) {
        AddPastVaccineDialog(
            patient = patient,
            vaccines = emptyList(),
            dobMillis = scheduleSummary.dobResolution.dobMillis,
            onDismiss = { showAddCustomPastWithDate = false },
            onSavePastVaccine = { vName, dose, dateMillis, batch, notes ->
                val withHistory = PatientVaccineRecordHelper.appendVaccineToPatientHistory(
                    patient = patient,
                    newVaccineNames = vName,
                    dose = dose,
                    batch = batch,
                    dateMillis = dateMillis,
                    notes = notes,
                    isHistorical = true
                )
                val reEval = PediatricVaccineScheduleEngine.evaluatePatientSchedule(withHistory)
                val finalUpdated = if (reEval.nextUpcomingMilestone != null) {
                    withHistory.copy(
                        nextPlannedVaccine = reEval.nextRecommendedVaccineNames.joinToString(", "),
                        nextDueDate = reEval.nextRecommendedDueDateLabel,
                        nextDueDateMillis = reEval.nextRecommendedDueDateMillis ?: withHistory.nextDueDateMillis
                    )
                } else {
                    withHistory
                }
                onUpdatePatient(finalUpdated)
                showAddCustomPastWithDate = false
                Toast.makeText(
                    context,
                    "Added $vName (${dateFormat.format(Date(dateMillis))}) to patient record",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
private fun MilestoneScheduleCard(
    milestone: EvaluatedMilestone,
    dateFormat: SimpleDateFormat,
    onScheduleThisMilestone: () -> Unit,
    onMarkVaccineGivenWithDate: (EvaluatedScheduledVaccine) -> Unit,
    onEditGivenVaccineDate: (PatientVaccineEntry) -> Unit
) {
    val badgeColor = when (milestone.status) {
        ScheduleItemStatus.COMPLETED -> ProfitGreen
        ScheduleItemStatus.OVERDUE -> LossRed
        ScheduleItemStatus.DUE_SOON -> WarningAmber
        ScheduleItemStatus.UPCOMING -> Color(0xFF0288D1)
    }
    val containerBg = when (milestone.status) {
        ScheduleItemStatus.COMPLETED -> ProfitGreenContainer.copy(alpha = 0.25f)
        ScheduleItemStatus.OVERDUE -> LossRedContainer.copy(alpha = 0.28f)
        ScheduleItemStatus.DUE_SOON -> WarningAmberContainer.copy(alpha = 0.28f)
        ScheduleItemStatus.UPCOMING -> MaterialTheme.colorScheme.surface
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("milestone_card_${milestone.spec.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Milestone Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeColor
                    ) {
                        Text(
                            text = milestone.spec.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (milestone.status == ScheduleItemStatus.DUE_SOON) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = milestone.spec.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (milestone.status == ScheduleItemStatus.COMPLETED) {
                                "All ${milestone.totalCount} doses completed"
                            } else {
                                "Calculated Due: ${milestone.relativeDueLabel}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (milestone.status == ScheduleItemStatus.OVERDUE) LossRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (milestone.status != ScheduleItemStatus.COMPLETED) {
                    FilledTonalButton(
                        onClick = onScheduleThisMilestone,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("btn_schedule_stage_${milestone.spec.id}")
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set Due", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ProfitGreenContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ProfitGreen, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Completed", style = MaterialTheme.typography.labelSmall, color = OnProfitGreenContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Individual Vaccines inside this Milestone
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                milestone.vaccines.forEach { evalVac ->
                    val isDone = evalVac.status == ScheduleItemStatus.COMPLETED && evalVac.matchedRecord != null
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(
                            1.dp,
                            if (isDone) ProfitGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = evalVac.spec.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Protects against: ${evalVac.spec.diseaseProtection}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                if (isDone) {
                                    val givenDateStr = dateFormat.format(Date(evalVac.matchedRecord!!.dateAdministeredMillis))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = ProfitGreen,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Given on: $givenDateStr (${evalVac.matchedRecord.doseName})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = OnProfitGreenContainer
                                        )
                                    }
                                } else {
                                    val calcDateStr = dateFormat.format(Date(evalVac.calculatedDueDateMillis))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = badgeColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Due Date: $calcDateStr" + if (evalVac.isCatchUpAdjusted) " (Catch-up Adjusted)" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (evalVac.status == ScheduleItemStatus.OVERDUE) LossRed else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isDone) {
                                // Option to edit the date of this previously given vaccine
                                OutlinedButton(
                                    onClick = { onEditGivenVaccineDate(evalVac.matchedRecord!!) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("btn_edit_scheduled_vaccine_date_${evalVac.spec.code}")
                                ) {
                                    Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit Date", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                // Option to mark this vaccine as previously given and pick its date
                                FilledTonalButton(
                                    onClick = { onMarkVaccineGivenWithDate(evalVac) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("btn_mark_scheduled_vaccine_given_${evalVac.spec.code}")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Given + Date", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable dialog for adding or editing the date (and dose/batch/notes) of a previously given vaccine.
 * Includes a Calendar DatePicker AND 1-tap pediatric milestone date presets based on the child's DOB.
 */
@Composable
fun EditOrAddPastVaccineDateDialog(
    title: String,
    vaccineName: String,
    initialDoseName: String,
    initialDateMillis: Long,
    initialBatch: String = "",
    initialNotes: String = "",
    dobMillis: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (doseName: String, dateMillis: Long, batch: String, notes: String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    var selectedDateMillis by remember { mutableStateOf(initialDateMillis) }
    var formattedDate by remember(selectedDateMillis) {
        mutableStateOf(dateFormat.format(Date(selectedDateMillis)))
    }
    var doseName by remember { mutableStateOf(initialDoseName.ifBlank { "Dose 1" }) }
    var batchNumber by remember { mutableStateOf(initialBatch) }
    var notes by remember { mutableStateOf(initialNotes) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dosePresets = listOf("At Birth", "Dose 1", "Dose 2", "Dose 3", "Booster 1", "Booster 2")

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_edit_past_vaccine_date"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.EditCalendar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Vaccine: $vaccineName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
                // Prominent Date Given Picker Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .testTag("btn_pick_past_vaccine_date")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Date Vaccine Was Given:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Button(
                            onClick = { showDatePicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_open_past_date_calendar")
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick Date", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick DOB-based date presets if DOB is available
                if (dobMillis != null) {
                    Text(
                        text = "Quick Date Presets (from Child's DOB):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            "At Birth (DOB)" to 0,
                            "6 Wks" to 42,
                            "10 Wks" to 70,
                            "14 Wks" to 98,
                            "6 Mos" to 182,
                            "9 Mos" to 274,
                            "Today" to -1
                        )
                        presets.forEach { (label, offsetDays) ->
                            OutlinedButton(
                                onClick = {
                                    val targetMillis = if (offsetDays < 0) {
                                        System.currentTimeMillis()
                                    } else {
                                        val cal = Calendar.getInstance().apply {
                                            timeInMillis = dobMillis
                                            add(Calendar.DAY_OF_YEAR, offsetDays)
                                        }
                                        minOf(cal.timeInMillis, System.currentTimeMillis())
                                    }
                                    selectedDateMillis = targetMillis
                                    formattedDate = dateFormat.format(Date(targetMillis))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Dose stage presets
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = batchNumber,
                    onValueChange = { batchNumber = it },
                    label = { Text("Batch Number (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Hospital (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        doseName.trim().ifBlank { "Dose 1" },
                        selectedDateMillis,
                        batchNumber.trim(),
                        notes.trim()
                    )
                },
                modifier = Modifier.testTag("btn_save_past_vaccine_date")
            ) {
                Text("Save Vaccine Date", fontWeight = FontWeight.Bold)
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
            title = "Select Date Given for $vaccineName",
            initialDateMillis = selectedDateMillis,
            onDateSelected = { millis, formatted ->
                selectedDateMillis = millis
                formattedDate = formatted
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}
