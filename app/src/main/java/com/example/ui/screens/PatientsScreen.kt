package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import com.example.service.AppointmentAlarmScheduler
import com.example.service.NotificationHelper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.Vaccine
import com.example.ui.components.BillDialog
import com.example.ui.components.SelectVaccineDialog
import com.example.ui.components.AppDatePickerModal
import com.example.ui.components.calculateAgeFromDob
import com.example.ui.components.formatTimestampToDate
import com.example.ui.theme.LossRed
import com.example.ui.theme.LossRedContainer
import com.example.ui.theme.OnLossRedContainer
import com.example.ui.theme.OnProfitGreenContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.util.BillData
import com.example.ui.util.BillItem
import com.example.ui.util.WhatsAppHelper
import com.example.ui.util.PatientVaccineRecordHelper
import com.example.ui.util.PatientVaccineEntry
import com.example.ui.util.PediatricVaccineScheduleEngine
import com.example.ui.components.AddPastVaccineDialog
import com.example.ui.components.EditOrAddPastVaccineDateDialog
import com.example.ui.components.LanguageAndWhatsAppBanner
import com.example.ui.components.PediatricVaccineScheduleDialog
import com.example.ui.components.WhatsAppReminderDialog
import com.example.ui.util.AppLanguage
import com.example.ui.util.AppLanguageManager
import com.example.ui.util.ReminderLanguage
import com.example.ui.util.tr
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.example.ui.components.SelectMultipleVaccinesDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val STANDARD_ROUTINE_VACCINE_SUGGESTIONS = listOf(
    "Hepatitis B",
    "Pentavalent",
    "Rotavirus",
    "PCV",
    "OPV",
    "IPV",
    "Influenza",
    "MMR",
    "Typhoid (TCV)",
    "Hepatitis A",
    "Varicella",
    "DTP Booster"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatientsScreen(
    patients: List<PatientRegistration>,
    vaccines: List<Vaccine>,
    clinicProfile: ClinicProfile,
    onSavePatient: (PatientRegistration, Boolean, Long?, Double, List<Long>) -> Unit,
    onDeletePatient: (PatientRegistration) -> Unit,
    onMarkReminderSent: (Long) -> Unit,
    onUpdateReminderDate: ((Long, String, Long) -> Unit)? = null,
    onDeleteCompletedPatients: () -> Unit = {},
    onDeleteOldOverduePatients: () -> Unit = {},
    onPurgeStaleCloudPatients: () -> Unit = {},
    onDeleteAllPatients: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // All, Upcoming, Overdue, Reminded

    var showRegisterDialog by remember { mutableStateOf(false) }
    var editingPatient by remember { mutableStateOf<PatientRegistration?>(null) }
    var patientToDelete by remember { mutableStateOf<PatientRegistration?>(null) }
    var patientForReminderDatePicker by remember { mutableStateOf<PatientRegistration?>(null) }
    var patientForGivenVaccineSelect by remember { mutableStateOf<PatientRegistration?>(null) }
    var patientForNextVaccineSelect by remember { mutableStateOf<PatientRegistration?>(null) }
    var patientForSubsequentVisit by remember { mutableStateOf<PatientRegistration?>(null) }
    var patientForComprehensiveRecord by remember { mutableStateOf<PatientRegistration?>(null) }
    var patientForPediatricSchedule by remember { mutableStateOf<PatientRegistration?>(null) }
    var patientAndEntryForEditDate by remember { mutableStateOf<Pair<PatientRegistration, PatientVaccineEntry>?>(null) }
    var patientForAddPastWithDate by remember { mutableStateOf<PatientRegistration?>(null) }
    var patientForWhatsAppReminder by remember { mutableStateOf<PatientRegistration?>(null) }

    var showManagePatientsDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteRemindedDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteOverdueDialog by remember { mutableStateOf(false) }
    var showConfirmPurgeCloudPatientsDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteAllPatientsDialog by remember { mutableStateOf(false) }

    // Notification permission & AlarmManager state
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Notifications enabled! Appointment alarms active.", Toast.LENGTH_SHORT).show()
            AppointmentAlarmScheduler.rescheduleAllPatientAlarms(context, patients)
        } else {
            Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Bill Dialog state
    var activeBillData by remember { mutableStateOf<BillData?>(null) }

    val now = System.currentTimeMillis()
    val thirtyDaysLater = now + (30L * 24 * 60 * 60 * 1000)

    val filteredPatients = patients.filter { patient ->
        val matchesSearch = patient.patientName.contains(searchQuery, ignoreCase = true) ||
                patient.parentName.contains(searchQuery, ignoreCase = true) ||
                patient.mobileNumber.contains(searchQuery) ||
                patient.dobOrAge.contains(searchQuery, ignoreCase = true) ||
                patient.nextPlannedVaccine.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "Upcoming" -> patient.nextDueDateMillis in now..thirtyDaysLater
            "Overdue" -> patient.nextDueDateMillis < now && patient.reminderStatus != "SENT"
            "Reminded" -> patient.reminderStatus == "SENT"
            else -> true
        }

        matchesSearch && matchesFilter
    }

    val overdueCount = patients.count { it.nextDueDateMillis < now && it.reminderStatus != "SENT" }
    val upcomingCount = patients.count { it.nextDueDateMillis in now..thirtyDaysLater }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingPatient = null
                    showRegisterDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_register_patient")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("Register Patient", "नया मरीज़ जोड़ें"), fontWeight = FontWeight.Bold)
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("patients_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Search Bar & Filter Chips at the Top for Instant Access
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                tr(
                                    "Search child, parent, mobile, or vaccine...",
                                    "बच्चे, अभिभावक, मोबाइल या टीके का नाम खोजें..."
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_patients_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val filterOptions = listOf("All", "Upcoming", "Overdue", "Reminded")
                        items(filterOptions) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                shape = RoundedCornerShape(10.dp),
                                label = {
                                    Text(
                                        when (filter) {
                                            "All" -> tr("All (${patients.size})", "सभी (${patients.size})")
                                            "Upcoming" -> tr("Due Soon ($upcomingCount)", "आगामी ($upcomingCount)")
                                            "Overdue" -> tr("Overdue ($overdueCount)", "बाकी ($overdueCount)")
                                            "Reminded" -> tr("Reminded", "रिमाइंडर भेजा गया")
                                            else -> filter
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                modifier = Modifier.testTag("filter_chip_$filter")
                            )
                        }
                    }

                    // 1b. Hindi / English App & WhatsApp Reminder Language Switcher Banner
                    LanguageAndWhatsAppBanner()
                }
            }

            // 2. Compact Clinical Tools Row: DOB Vaccine Scheduler + 9 AM Alarms
            item {
                var showAlarmTools by remember { mutableStateOf(false) }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pediatric_schedule_module_banner")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // DOB Vaccine Scheduler Button
                            FilledTonalButton(
                                onClick = {
                                    val target = patients.firstOrNull() ?: PatientRegistration(
                                        id = 0L,
                                        patientName = "Pediatric Schedule Preview",
                                        dobOrAge = "6 Weeks (DOB: ${formatTimestampToDate(System.currentTimeMillis() - 42L * 86400000L)})",
                                        gender = "Male",
                                        parentName = "Parent",
                                        mobileNumber = "+91 ",
                                        vaccineGivenToday = "BCG, OPV-0, Hepatitis B-0",
                                        vaccineGivenDose = "At Birth"
                                    )
                                    patientForPediatricSchedule = target
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFE0F2F1),
                                    contentColor = Color(0xFF004D40)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp)
                                    .testTag("btn_open_pediatric_schedule_calculator")
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tr("DOB Vaccine Scheduler", "जन्म तिथि टीका चार्ट"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            // 9 AM Alarm Status / Expand Toggle
                            OutlinedButton(
                                onClick = { showAlarmTools = !showAlarmTools },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (hasNotificationPermission) Color(0xFFBBF7D0) else WarningAmber.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .testTag("appointment_notification_card")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = if (hasNotificationPermission) ProfitGreen else WarningAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (hasNotificationPermission) tr("9 AM Alerts ✓", "9 AM अलर्ट ✓") else tr("Enable Alerts", "अलर्ट चालू करें"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasNotificationPermission) OnProfitGreenContainer else WarningAmber,
                                    maxLines = 1
                                )
                            }
                        }

                        if (showAlarmTools || !hasNotificationPermission) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!hasNotificationPermission) {
                                    Button(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                hasNotificationPermission = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.heightIn(min = 34.dp).testTag("btn_enable_notifications")
                                    ) {
                                        Text("Enable Alert", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            NotificationHelper.sendTestNotification(context)
                                            Toast.makeText(context, "Test notification triggered! Check your status bar.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier.weight(1f).heightIn(min = 34.dp).testTag("btn_test_notification"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(tr("Test Alert", "टेस्ट अलर्ट"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        AppointmentAlarmScheduler.rescheduleAllPatientAlarms(context, patients)
                                        AppointmentAlarmScheduler.runImmediateAppointmentCheck(context) { dueCount ->
                                            Toast.makeText(
                                                context,
                                                "Checked ${patients.size} records. $dueCount appointment(s) due today. Alarms synced!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 34.dp).testTag("btn_reschedule_alarms"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(tr("Sync Alarms", "सिंक अलार्म"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Results Count & Clean Old Records Action Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Patient Records (${filteredPatients.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    OutlinedButton(
                        onClick = { showManagePatientsDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 36.dp).testTag("btn_manage_clean_patients")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clean Records", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }

            // Patient List
            if (filteredPatients.isEmpty()) {
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
                                imageVector = Icons.Default.ChildCare,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching patients found" else "No patients registered yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Register your first child patient to track vaccines and send automated WhatsApp reminders.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(filteredPatients, key = { it.id }) { patient ->
                    PatientCard(
                        patient = patient,
                        clinicProfile = clinicProfile,
                        onSendReminderWhatsApp = {
                            patientForWhatsAppReminder = patient
                        },
                        onQuickSendHindiWhatsApp = {
                            AppLanguageManager.setWhatsAppReminderLanguage(context, ReminderLanguage.HINDI)
                            val msg = WhatsAppHelper.buildVaccineReminderMessage(
                                patient = patient,
                                clinic = clinicProfile,
                                language = ReminderLanguage.HINDI
                            )
                            WhatsAppHelper.sendWhatsAppText(context, patient.mobileNumber, msg)
                            onMarkReminderSent(patient.id)
                        },
                        onGenerateBill = {
                            val rawGiven = patient.vaccineGivenToday.trim()
                            val vaccineNames = if (rawGiven.contains(",")) {
                                rawGiven.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            } else if (rawGiven.isNotBlank()) {
                                listOf(rawGiven)
                            } else {
                                emptyList()
                            }

                            val billItems = if (vaccineNames.isNotEmpty()) {
                                vaccineNames.mapIndexed { index, name ->
                                    val matchedVac = vaccines.find {
                                        it.name.equals(name, ignoreCase = true) ||
                                                it.name.contains(name, ignoreCase = true) ||
                                                name.contains(it.name, ignoreCase = true)
                                    }
                                    val unitPrice = matchedVac?.sellingPrice ?: 750.0
                                    val batch = if (patient.vaccineGivenBatch.isNotBlank()) {
                                        patient.vaccineGivenBatch
                                    } else {
                                        matchedVac?.code ?: "VAC-BAT-0${index + 1}"
                                    }
                                    BillItem(
                                        description = "$name (${patient.vaccineGivenDose})",
                                        batchNumber = batch,
                                        quantity = 1,
                                        unitPrice = unitPrice
                                    )
                                }
                            } else {
                                listOf(
                                    BillItem(
                                        description = "Vaccination (${patient.vaccineGivenDose})",
                                        batchNumber = patient.vaccineGivenBatch.ifBlank { "VAC-BAT-01" },
                                        quantity = 1,
                                        unitPrice = 750.0
                                    )
                                )
                            }
                            val invoiceNumber = "INV-${SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date())}-${patient.id}"

                            activeBillData = BillData(
                                invoiceNumber = invoiceNumber,
                                timestamp = patient.dateGivenMillis,
                                patientName = patient.patientName,
                                parentName = patient.parentName,
                                mobileNumber = patient.mobileNumber,
                                ageOrDob = patient.dobOrAge,
                                items = billItems,
                                nextVaccine = patient.nextPlannedVaccine,
                                nextDueDate = patient.nextDueDate
                            )
                        },
                        onEdit = {
                            editingPatient = patient
                            showRegisterDialog = true
                        },
                        onDelete = {
                            patientToDelete = patient
                        },
                        onPickReminderDate = {
                            patientForReminderDatePicker = patient
                        },
                        onSelectGivenVaccines = {
                            patientForGivenVaccineSelect = patient
                        },
                        onSelectNextVaccines = {
                            patientForNextVaccineSelect = patient
                        },
                        onGiveSubsequentVaccine = {
                            patientForSubsequentVisit = patient
                        },
                        onViewComprehensiveRecord = {
                            patientForComprehensiveRecord = patient
                        },
                        onOpenPediatricSchedule = {
                            patientForPediatricSchedule = patient
                        },
                        onEditPastVaccineDate = { entry ->
                            patientAndEntryForEditDate = patient to entry
                        },
                        onAddPastVaccineWithDate = {
                            patientForAddPastWithDate = patient
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    // Direct DatePicker Modal for Rescheduling/Setting Reminder Date on Patient Card
    if (patientForReminderDatePicker != null) {
        val p = patientForReminderDatePicker!!
        AppDatePickerModal(
            title = "Set Reminder Date for ${p.patientName}",
            initialDateMillis = p.nextDueDateMillis,
            onDateSelected = { selectedMillis, formattedDate ->
                val nowCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val selCal = Calendar.getInstance().apply {
                    timeInMillis = selectedMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val daysDiff = ((selCal.timeInMillis - nowCal.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
                val label = when {
                    daysDiff < 0 -> "$formattedDate (Overdue)"
                    daysDiff == 0 -> "Today ($formattedDate)"
                    daysDiff == 1 -> "Tomorrow ($formattedDate)"
                    daysDiff in 2..6 -> "$formattedDate (In $daysDiff days)"
                    daysDiff in 7..13 -> "$formattedDate (In 1 week)"
                    daysDiff in 14..27 -> "$formattedDate (In ${daysDiff / 7} weeks)"
                    else -> "$formattedDate (In ${daysDiff / 30} mos)"
                }
                if (onUpdateReminderDate != null) {
                    onUpdateReminderDate(p.id, label, selectedMillis)
                } else {
                    onSavePatient(
                        p.copy(
                            nextDueDate = label,
                            nextDueDateMillis = selectedMillis,
                            reminderStatus = "PENDING"
                        ),
                        false,
                        null,
                        0.0,
                        emptyList()
                    )
                }
                AppointmentAlarmScheduler.schedulePatientAppointmentAlarm(
                    context,
                    p.copy(nextDueDateMillis = selectedMillis, nextDueDate = label)
                )
                patientForReminderDatePicker = null
            },
            onDismiss = { patientForReminderDatePicker = null }
        )
    }

    // Direct Multi-Vaccine Picker for Given Vaccines from Patient Card
    if (patientForGivenVaccineSelect != null) {
        val p = patientForGivenVaccineSelect!!
        val currentGivenList = remember(p.vaccineGivenToday) {
            p.vaccineGivenToday.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        SelectMultipleVaccinesDialog(
            title = "Administered Vaccines: ${p.patientName}",
            subtitle = "Select multiple vaccines administered to child during this visit",
            vaccines = vaccines,
            initialSelectedVaccines = currentGivenList,
            onConfirmSelection = { names, ids ->
                val newGivenString = names.joinToString(", ")
                val updated = p.copy(vaccineGivenToday = newGivenString)
                onSavePatient(updated, false, null, 0.0, ids)
                patientForGivenVaccineSelect = null
            },
            onDismiss = { patientForGivenVaccineSelect = null }
        )
    }

    // Direct Multi-Vaccine Picker for Scheduled Next Vaccines from Patient Card
    if (patientForNextVaccineSelect != null) {
        val p = patientForNextVaccineSelect!!
        val currentNextList = remember(p.nextPlannedVaccine) {
            p.nextPlannedVaccine.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        SelectMultipleVaccinesDialog(
            title = "Schedule Next Vaccines: ${p.patientName}",
            subtitle = "Select multiple vaccines planned for upcoming appointment reminder",
            vaccines = vaccines,
            initialSelectedVaccines = currentNextList,
            onConfirmSelection = { names, _ ->
                val newNextString = names.joinToString(", ")
                val updated = p.copy(nextPlannedVaccine = newNextString, reminderStatus = "PENDING")
                onSavePatient(updated, false, null, 0.0, emptyList())
                AppointmentAlarmScheduler.schedulePatientAppointmentAlarm(context, updated)
                patientForNextVaccineSelect = null
            },
            onDismiss = { patientForNextVaccineSelect = null }
        )
    }

    // Register / Edit Patient Dialog
    if (showRegisterDialog) {
        RegisterPatientDialog(
            patient = editingPatient,
            vaccines = vaccines,
            onDismiss = { showRegisterDialog = false },
            onSave = { savedPatient, alsoRecordSale, vaccineId, price, multipleIds ->
                onSavePatient(savedPatient, alsoRecordSale, vaccineId, price, multipleIds)
                showRegisterDialog = false
            }
        )
    }

    // Delete confirmation dialog
    if (patientToDelete != null) {
        val p = patientToDelete!!
        AlertDialog(
            onDismissRequest = { patientToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) },
            title = { Text("Delete Patient Completely?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete '${p.patientName}'?\n\nThis will remove the patient record completely from BOTH this device and online cloud backup, so on syncing this record will never be repeated.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePatient(p)
                        patientToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_patient")
                ) {
                    Text("Delete from Device & Cloud")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { patientToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Manage & Clean Old Patient Records Modal Dialog
    if (showManagePatientsDialog) {
        AlertDialog(
            onDismissRequest = { showManagePatientsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clean & Delete Patient Records", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Manage old or completed patient records. Deletions apply to both this device and online cloud so old records never repeat on sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option 1: Delete Reminded / Completed Patient Records
                    OutlinedButton(
                        onClick = {
                            showManagePatientsDialog = false
                            showConfirmDeleteRemindedDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_delete_reminded_patients"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete Reminded Patient Records", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Removes all patients whose reminders have been sent, from both device & cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Option 2: Delete Old Overdue Records (>90 days)
                    OutlinedButton(
                        onClick = {
                            showManagePatientsDialog = false
                            showConfirmDeleteOverdueDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_delete_overdue_old_patients"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete Overdue Records (>3 Months)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Removes inactive overdue records older than 90 days from device & cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Option 3: Purge Stale Cloud Patient Records
                    OutlinedButton(
                        onClick = {
                            showManagePatientsDialog = false
                            showConfirmPurgeCloudPatientsDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_purge_stale_cloud_patients"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Purge Stale Cloud Patient Records", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Deletes all orphaned patient records from cloud so syncing never restores old deleted patients.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Option 4: Delete All Patient Records Completely
                    Button(
                        onClick = {
                            showManagePatientsDialog = false
                            showConfirmDeleteAllPatientsDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("btn_delete_all_patients"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete All Patient Records Completely")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManagePatientsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Confirmation 1: Delete Reminded Patients
    if (showConfirmDeleteRemindedDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteRemindedDialog = false },
            title = { Text("Delete Reminded Patient Records?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all patient records marked as 'Reminder Sent' from both this device and online cloud backup?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteRemindedDialog = false
                        onDeleteCompletedPatients()
                    }
                ) {
                    Text("Delete Reminded Records")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDeleteRemindedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation 2: Delete Overdue Patients
    if (showConfirmDeleteOverdueDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteOverdueDialog = false },
            title = { Text("Delete Inactive Overdue Records?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all overdue patient records older than 90 days completely from this device and online cloud backup?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteOverdueDialog = false
                        onDeleteOldOverduePatients()
                    }
                ) {
                    Text("Delete Overdue Records")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDeleteOverdueDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation 3: Purge Stale Cloud Patients
    if (showConfirmPurgeCloudPatientsDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmPurgeCloudPatientsDialog = false },
            title = { Text("Purge Stale Cloud Patient Records?", fontWeight = FontWeight.Bold) },
            text = { Text("This will scan Firebase Firestore and delete any patient records that are no longer in your local list, guaranteeing that old deleted patient records will NEVER repeat when syncing.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmPurgeCloudPatientsDialog = false
                        onPurgeStaleCloudPatients()
                    }
                ) {
                    Text("Purge Cloud Records")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmPurgeCloudPatientsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation 4: Delete All Patient Records
    if (showConfirmDeleteAllPatientsDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteAllPatientsDialog = false },
            title = { Text("⚠️ Delete All Patient Records Completely?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("This will permanently ERASE ALL registered patient records from BOTH this device and online cloud storage.\n\nAre you sure you want a clean slate for patients?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteAllPatientsDialog = false
                        onDeleteAllPatients()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All Patients")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDeleteAllPatientsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bill Dialog
    if (activeBillData != null) {
        BillDialog(
            billData = activeBillData!!,
            clinic = clinicProfile,
            onDismiss = { activeBillData = null }
        )
    }

    // Subsequent Visit Vaccination Dialog
    if (patientForSubsequentVisit != null) {
        val p = patientForSubsequentVisit!!
        SubsequentVisitDialog(
            patient = p,
            vaccines = vaccines,
            onDismiss = { patientForSubsequentVisit = null },
            onSaveVisit = { updatedPatient, deductStock, vaccineIds, autoOpenBill ->
                // Save updated patient with new dose and chronological trail, deducting stock if selected
                onSavePatient(updatedPatient, deductStock, vaccineIds.firstOrNull(), 0.0, vaccineIds)
                AppointmentAlarmScheduler.schedulePatientAppointmentAlarm(context, updatedPatient)

                if (autoOpenBill) {
                    val rawGiven = updatedPatient.vaccineGivenToday.trim()
                    val vaccineNames = if (rawGiven.contains(",")) {
                        rawGiven.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    } else if (rawGiven.isNotBlank()) {
                        listOf(rawGiven)
                    } else {
                        emptyList()
                    }

                    val billItems = if (vaccineNames.isNotEmpty()) {
                        vaccineNames.mapIndexed { index, name ->
                            val matchedVac = vaccines.find {
                                it.name.equals(name, ignoreCase = true) ||
                                        it.name.contains(name, ignoreCase = true) ||
                                        name.contains(it.name, ignoreCase = true)
                            }
                            val unitPrice = matchedVac?.sellingPrice ?: 750.0
                            val batch = if (updatedPatient.vaccineGivenBatch.isNotBlank()) {
                                updatedPatient.vaccineGivenBatch
                            } else {
                                matchedVac?.code ?: "VAC-BAT-0${index + 1}"
                            }
                            BillItem(
                                description = "$name (${updatedPatient.vaccineGivenDose})",
                                batchNumber = batch,
                                quantity = 1,
                                unitPrice = unitPrice
                            )
                        }
                    } else {
                        listOf(
                            BillItem(
                                description = "Vaccination (${updatedPatient.vaccineGivenDose})",
                                batchNumber = updatedPatient.vaccineGivenBatch.ifBlank { "VAC-BAT-01" },
                                quantity = 1,
                                unitPrice = 750.0
                            )
                        )
                    }
                    val invoiceNumber = "INV-${SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date())}-${updatedPatient.id}"

                    activeBillData = BillData(
                        invoiceNumber = invoiceNumber,
                        timestamp = updatedPatient.dateGivenMillis,
                        patientName = updatedPatient.patientName,
                        parentName = updatedPatient.parentName,
                        mobileNumber = updatedPatient.mobileNumber,
                        ageOrDob = updatedPatient.dobOrAge,
                        items = billItems,
                        nextVaccine = updatedPatient.nextPlannedVaccine,
                        nextDueDate = updatedPatient.nextDueDate
                    )
                }

                patientForSubsequentVisit = null
            }
        )
    }

    // Comprehensive Patient Vaccine Record Dialog
    if (patientForComprehensiveRecord != null) {
        val p = patientForComprehensiveRecord!!
        com.example.ui.components.ComprehensivePatientVaccineRecordDialog(
            patient = p,
            clinicProfile = clinicProfile,
            vaccines = vaccines,
            onDismiss = { patientForComprehensiveRecord = null },
            onUpdatePatient = { updated ->
                onSavePatient(updated, false, null, 0.0, emptyList())
                patientForComprehensiveRecord = updated
            },
            onShareRecordWhatsApp = {
                val fullMessage = PatientVaccineRecordHelper.formatAllVaccinesForWhatsApp(p, clinicProfile)
                WhatsAppHelper.sendWhatsAppText(context, p.mobileNumber, fullMessage)
            }
        )
    }

    // Pediatric Vaccine Scheduling Module Dialog (DOB-based Due Date Calculator)
    if (patientForPediatricSchedule != null) {
        val p = patientForPediatricSchedule!!
        PediatricVaccineScheduleDialog(
            patient = p,
            clinicProfile = clinicProfile,
            allPatients = patients,
            onSwitchPatient = { switched ->
                patientForPediatricSchedule = switched
            },
            onUpdatePatient = { updated ->
                if (updated.id != 0L) {
                    onSavePatient(updated, false, null, 0.0, emptyList())
                }
                patientForPediatricSchedule = updated
            },
            onDismiss = { patientForPediatricSchedule = null }
        )
    }

    // Direct Edit Date Modal for any Previously Given Vaccine from PatientCard
    if (patientAndEntryForEditDate != null) {
        val (p, entry) = patientAndEntryForEditDate!!
        val dobMillis = remember(p.dobOrAge) {
            PediatricVaccineScheduleEngine.resolvePatientDob(p.dobOrAge).dobMillis
        }
        EditOrAddPastVaccineDateDialog(
            title = "Set / Edit Date of Previously Given Vaccine",
            vaccineName = entry.vaccineName,
            initialDoseName = entry.doseName,
            initialDateMillis = entry.dateAdministeredMillis,
            initialBatch = entry.batchNumber,
            initialNotes = entry.notes,
            dobMillis = dobMillis,
            onDismiss = { patientAndEntryForEditDate = null },
            onConfirm = { newDose, newDateMillis, newBatch, newNotes ->
                val updated = PatientVaccineRecordHelper.updateVaccineDateInPatientHistory(
                    patient = p,
                    entryId = entry.id,
                    newDateMillis = newDateMillis,
                    newDoseName = newDose,
                    newBatchNumber = newBatch,
                    newNotes = newNotes
                )
                onSavePatient(updated, false, null, 0.0, emptyList())
                patientAndEntryForEditDate = null
                Toast.makeText(
                    context,
                    "Updated ${entry.vaccineName} date to ${formatTimestampToDate(newDateMillis)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Direct Add Past Vaccine with Date Modal from PatientCard
    if (patientForAddPastWithDate != null) {
        val p = patientForAddPastWithDate!!
        val dobMillis = remember(p.dobOrAge) {
            PediatricVaccineScheduleEngine.resolvePatientDob(p.dobOrAge).dobMillis
        }
        AddPastVaccineDialog(
            patient = p,
            vaccines = vaccines,
            dobMillis = dobMillis,
            onDismiss = { patientForAddPastWithDate = null },
            onSavePastVaccine = { vName, dose, dateMillis, batch, notes ->
                val updated = PatientVaccineRecordHelper.appendVaccineToPatientHistory(
                    patient = p,
                    newVaccineNames = vName,
                    dose = dose,
                    batch = batch,
                    dateMillis = dateMillis,
                    notes = notes,
                    isHistorical = true
                )
                onSavePatient(updated, false, null, 0.0, emptyList())
                patientForAddPastWithDate = null
                Toast.makeText(
                    context,
                    "Added '$vName' (${formatTimestampToDate(dateMillis)}) to ${p.patientName}'s record",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // WhatsApp Vaccine Reminder Dialog with Hindi / English / Bilingual selector & live preview
    if (patientForWhatsAppReminder != null) {
        val p = patientForWhatsAppReminder!!
        WhatsAppReminderDialog(
            patient = p,
            clinicProfile = clinicProfile,
            onDismiss = { patientForWhatsAppReminder = null },
            onSendConfirmed = { customMessage, _ ->
                WhatsAppHelper.sendWhatsAppText(context, p.mobileNumber, customMessage)
                onMarkReminderSent(p.id)
                patientForWhatsAppReminder = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatientCard(
    patient: PatientRegistration,
    clinicProfile: ClinicProfile,
    onSendReminderWhatsApp: () -> Unit,
    onQuickSendHindiWhatsApp: () -> Unit = onSendReminderWhatsApp,
    onGenerateBill: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPickReminderDate: () -> Unit,
    onSelectGivenVaccines: () -> Unit,
    onSelectNextVaccines: () -> Unit,
    onGiveSubsequentVaccine: () -> Unit = {},
    onViewComprehensiveRecord: () -> Unit = {},
    onOpenPediatricSchedule: () -> Unit = {},
    onEditPastVaccineDate: (PatientVaccineEntry) -> Unit = {},
    onAddPastVaccineWithDate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val isOverdue = patient.nextDueDateMillis < now
    val isUpcoming = patient.nextDueDateMillis in now..(now + 30L * 24 * 60 * 60 * 1000)

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedGivenDate = remember(patient.dateGivenMillis) {
        dateFormat.format(Date(patient.dateGivenMillis))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("patient_card_${patient.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Child Name, Gender/Age Pills, Parent Contact & Edit/Delete
            val isGirl = patient.gender.equals("Female", ignoreCase = true) || patient.gender.equals("Girl", ignoreCase = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isGirl) Color(0xFFFDF0F5) else Color(0xFFEBF5FF),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ChildCare,
                                contentDescription = null,
                                tint = if (isGirl) Color(0xFFD81B60) else Color(0xFF1976D2),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = patient.patientName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = patient.gender,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (patient.dobOrAge.contains("DOB", ignoreCase = true)) Icons.Default.Cake else Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = patient.dobOrAge.ifBlank { "Age not set" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "${patient.parentName} (${patient.mobileNumber})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Clean Administered Vaccine Box (Full-Width Layout)
            val administeredList = remember(patient.vaccineGivenToday) {
                patient.vaccineGivenToday.split(",").map { it.trim() }.filter { it.isNotBlank() }
            }
            val allTakenVaccines = remember(patient) {
                PatientVaccineRecordHelper.getAllTakenVaccines(patient)
            }
            var showAllTakenVaccines by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF6FBF8),
                border = BorderStroke(1.dp, Color(0xFFCEEBD8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectGivenVaccines() }
                    .testTag("patient_given_vaccine_box_${patient.id}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    // Header Row: Title + Date Pill + Select Button (Wraps cleanly on narrow Android 14 screens)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vaccines,
                                contentDescription = null,
                                tint = ProfitGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Administered (${patient.vaccineGivenDose})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ProfitGreen
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, ProfitGreen.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clickable {
                                        val primaryEntry = allTakenVaccines.lastOrNull { !it.isHistorical }
                                            ?: allTakenVaccines.lastOrNull()
                                            ?: PatientVaccineEntry(
                                                id = "current_${patient.id}_${patient.vaccineGivenToday.hashCode()}",
                                                vaccineName = patient.vaccineGivenToday.ifBlank { "Vaccine" },
                                                doseName = patient.vaccineGivenDose.ifBlank { "Dose 1" },
                                                dateAdministeredMillis = patient.dateGivenMillis,
                                                batchNumber = patient.vaccineGivenBatch
                                            )
                                        onEditPastVaccineDate(primaryEntry)
                                    }
                                    .testTag("btn_card_edit_given_date_${patient.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditCalendar,
                                        contentDescription = "Edit Given Date",
                                        tint = ProfitGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "$formattedGivenDate ✎",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnProfitGreenContainer
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ProfitGreenContainer,
                                modifier = Modifier
                                    .clickable { onSelectGivenVaccines() }
                                    .testTag("btn_select_given_vaccines_${patient.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Given Vaccines",
                                        tint = OnProfitGreenContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        "Select",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OnProfitGreenContainer
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Full-Width Vaccine Chips
                    if (administeredList.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            administeredList.forEach { vName ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                                ) {
                                    Text(
                                        text = vName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnProfitGreenContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No vaccine recorded. Tap to select.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Expandable Previous Doses History Bar
                    if (allTakenVaccines.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color(0xFFD1FAE5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAllTakenVaccines = !showAllTakenVaccines }
                                .testTag("patient_total_taken_summary_${patient.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = ProfitGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "History: ${allTakenVaccines.size} Doses Taken",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnProfitGreenContainer
                                    )
                                }
                                Text(
                                    text = if (showAllTakenVaccines) "Hide ▲" else "View Dates ▼",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ProfitGreen
                                )
                            }
                        }

                        if (showAllTakenVaccines) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Administered Doses (${allTakenVaccines.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    TextButton(
                                        onClick = onAddPastVaccineWithDate,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .heightIn(min = 28.dp)
                                            .testTag("btn_card_add_past_vaccine_${patient.id}")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Add Past + Date", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                allTakenVaccines.forEachIndexed { idx, entry ->
                                    val dateStr = formatTimestampToDate(entry.dateAdministeredMillis)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${idx + 1}. ${entry.vaccineName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFF1F5F9)
                                            ) {
                                                Text(
                                                    text = entry.doseName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ProfitGreenContainer,
                                            modifier = Modifier
                                                .clickable { onEditPastVaccineDate(entry) }
                                                .testTag("chip_card_edit_entry_date_${entry.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.EditCalendar,
                                                    contentDescription = "Edit Date",
                                                    tint = ProfitGreen,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "$dateStr ✎",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OnProfitGreenContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Clean Next Planned Vaccine & Due Date Box (Full-Width Layout)
            val dueBgColor = when {
                isOverdue -> Color(0xFFFEF2F2)
                isUpcoming -> Color(0xFFFFFBEB)
                else -> Color(0xFFF8FAFC)
            }
            val dueBorderColor = when {
                isOverdue -> Color(0xFFFECACA)
                isUpcoming -> Color(0xFFFDE68A)
                else -> Color(0xFFE2E8F0)
            }
            val dueTextColor = when {
                isOverdue -> LossRed
                isUpcoming -> WarningAmber
                else -> MaterialTheme.colorScheme.primary
            }

            val plannedList = remember(patient.nextPlannedVaccine) {
                patient.nextPlannedVaccine.split(",").map { it.trim() }.filter { it.isNotBlank() }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = dueBgColor,
                border = BorderStroke(1.dp, dueBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectNextVaccines() }
                    .testTag("patient_next_vaccine_box_${patient.id}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    // Header Row: Next Due Label + Action Pills (Vaccines / Date)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = dueTextColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Next Due: ${patient.nextDueDate}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = dueTextColor,
                                maxLines = 1
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (patient.reminderStatus == "SENT") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ProfitGreenContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ProfitGreen, modifier = Modifier.size(10.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Sent", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = OnProfitGreenContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, dueBorderColor),
                                modifier = Modifier
                                    .clickable { onSelectNextVaccines() }
                                    .testTag("btn_select_next_vaccines_${patient.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Vaccines, contentDescription = "Select Next", tint = dueTextColor, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Vaccines", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = dueTextColor)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, dueBorderColor),
                                modifier = Modifier
                                    .clickable { onPickReminderDate() }
                                    .testTag("btn_pick_reminder_date_${patient.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Event, contentDescription = "Pick Date", tint = dueTextColor, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Date", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = dueTextColor)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (plannedList.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            plannedList.forEach { vName ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, dueBorderColor)
                                ) {
                                    Text(
                                        text = vName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "None scheduled. Tap to schedule next dose.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 1: Primary Clinical Actions (Give Vaccine + WhatsApp Reminder in Hindi/English)
            val currentWaLang by AppLanguageManager.whatsappLanguageFlow.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onGiveSubsequentVaccine,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .testTag("btn_give_subsequent_vaccine_${patient.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = tr("Give Vaccine", "टीका लगाएं"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onSendReminderWhatsApp,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1.15f)
                        .heightIn(min = 38.dp)
                        .testTag("btn_whatsapp_reminder_${patient.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA851))
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (patient.reminderStatus == "SENT") {
                            tr("Resend (${currentWaLang.shortLabel})", "पुनः भेजें (${currentWaLang.shortLabel})")
                        } else {
                            tr("WhatsApp (${currentWaLang.shortLabel})", "WhatsApp रिमाइंडर")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                // 1-Tap Direct Hindi WhatsApp Reminder Pill
                FilledTonalButton(
                    onClick = onQuickSendHindiWhatsApp,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFF0FDF4),
                        contentColor = Color(0xFF15803D)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("btn_quick_hindi_whatsapp_${patient.id}")
                ) {
                    Text(
                        text = "हिंदी",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Calm Neutral Secondary Actions (DOB Schedule, Record & PDF, Bill & PDF)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenPediatricSchedule,
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF8FAFC),
                        contentColor = Color(0xFF334155)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                        .testTag("btn_open_dob_schedule_${patient.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF006874),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tr("Schedule", "टीका चार्ट"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onViewComprehensiveRecord,
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF8FAFC),
                        contentColor = Color(0xFF334155)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                        .testTag("btn_view_comprehensive_record_${patient.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tr("Record PDF", "रिकॉर्ड PDF"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onGenerateBill,
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF8FAFC),
                        contentColor = Color(0xFF334155)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                        .testTag("btn_generate_bill_${patient.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tr("Bill PDF", "बिल PDF"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterPatientDialog(
    patient: PatientRegistration?,
    vaccines: List<Vaccine>,
    onDismiss: () -> Unit,
    onSave: (PatientRegistration, Boolean, Long?, Double, List<Long>) -> Unit
) {
    var patientName by remember { mutableStateOf(patient?.patientName ?: "") }
    var ageMode by remember {
        mutableStateOf(
            if (patient?.dobOrAge?.contains("DOB", ignoreCase = true) == true) "DATE" else "MONTH"
        )
    }
    var ageOrDob by remember { mutableStateOf(patient?.dobOrAge ?: "6 Months") }
    var selectedDobMillis by remember { mutableStateOf<Long?>(null) }
    var showDobDatePicker by remember { mutableStateOf(false) }
    var showReminderDatePicker by remember { mutableStateOf(false) }

    var gender by remember { mutableStateOf(patient?.gender ?: "Male") }
    var parentName by remember { mutableStateOf(patient?.parentName ?: "") }
    var mobileNumber by remember { mutableStateOf(patient?.mobileNumber ?: "+91 ") }

    // Multi-vaccine state for Given Vaccines (starts empty so doctor explicitly selects)
    val initialGivenList = remember(patient) {
        val list = patient?.vaccineGivenToday?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        if (!list.isNullOrEmpty()) list else emptyList()
    }
    val selectedGivenVaccines = remember { mutableStateListOf<String>().apply { addAll(initialGivenList) } }
    val selectedGivenVaccineIds = remember {
        mutableStateListOf<Long>().apply {
            val ids = selectedGivenVaccines.mapNotNull { name ->
                vaccines.find { it.name.equals(name, ignoreCase = true) || it.name.contains(name, ignoreCase = true) || name.contains(it.name, ignoreCase = true) }?.id
            }.distinct()
            addAll(ids)
        }
    }

    // Multi-vaccine state for Next Planned Vaccines (starts empty so doctor explicitly selects)
    val initialNextList = remember(patient) {
        val list = patient?.nextPlannedVaccine?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
        if (!list.isNullOrEmpty()) list else emptyList()
    }
    val selectedNextVaccines = remember { mutableStateListOf<String>().apply { addAll(initialNextList) } }

    var vaccineGivenToday by remember { mutableStateOf(selectedGivenVaccines.joinToString(", ")) }
    var vaccineGivenBatch by remember { mutableStateOf(patient?.vaccineGivenBatch ?: "") }
    var vaccineGivenDose by remember { mutableStateOf(patient?.vaccineGivenDose ?: "Dose 1") }

    var nextPlannedVaccine by remember { mutableStateOf(selectedNextVaccines.joinToString(", ")) }
    var nextDueDate by remember { mutableStateOf(patient?.nextDueDate ?: "In 30 Days") }
    var nextDueDateMillis by remember {
        mutableStateOf(patient?.nextDueDateMillis ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
    }

    var notes by remember { mutableStateOf(patient?.notes ?: "") }

    // Option to also record this vaccination as a sale in inventory
    var alsoRecordSale by remember { mutableStateOf(patient == null) }
    var sellingPrice by remember {
        val initialVac = vaccines.find { it.name.equals(selectedGivenVaccines.firstOrNull(), ignoreCase = true) } ?: vaccines.firstOrNull()
        mutableStateOf(initialVac?.sellingPrice ?: 750.0)
    }

    val initialPastEntries = remember(patient) {
        if (patient != null) {
            PatientVaccineRecordHelper.getAllTakenVaccines(patient).filter { it.isHistorical }
        } else emptyList()
    }
    val initialPastList = remember(initialPastEntries) {
        initialPastEntries.map { it.vaccineName }
    }
    val selectedPastVaccines = remember { mutableStateListOf<String>().apply { addAll(initialPastList) } }
    val pastVaccineDates = remember {
        mutableStateMapOf<String, Long>().apply {
            initialPastEntries.forEach { entry ->
                put(entry.vaccineName, entry.dateAdministeredMillis)
            }
        }
    }
    var pastVaccineBeingEditedForDate by remember { mutableStateOf<String?>(null) }
    var customPastVaccineInput by remember { mutableStateOf("") }
    var showPastVaccinesSection by remember { mutableStateOf(true) }

    var givenTodayDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showGivenTodayDatePicker by remember { mutableStateOf(false) }

    var showMultiVaccinePickerForGiven by remember { mutableStateOf(false) }
    var showMultiVaccinePickerForNext by remember { mutableStateOf(false) }

    // Quick due date helper
    fun setDueDateOffset(days: Int, label: String) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        nextDueDateMillis = cal.timeInMillis
        val formatted = formatTimestampToDate(cal.timeInMillis)
        nextDueDate = "$label ($formatted)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ChildCare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (patient == null) "Patient Registration" else "Edit Patient Record",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Child & Parent Details", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    label = { Text("Child / Patient Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_patient_name")
                )

                // AGE SELECTION: Select Date (DOB) vs In Months Toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Patient Age / Date of Birth *",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Mode Toggle: Select Date vs In Months
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = ageMode == "DATE",
                                onClick = {
                                    ageMode = "DATE"
                                    if (!ageOrDob.contains("DOB", ignoreCase = true)) {
                                        showDobDatePicker = true
                                    }
                                },
                                label = { Text("📅 Select Date (DOB)") },
                                modifier = Modifier.weight(1f).testTag("chip_age_mode_date")
                            )
                            FilterChip(
                                selected = ageMode == "MONTH",
                                onClick = {
                                    ageMode = "MONTH"
                                    if (ageOrDob.contains("DOB", ignoreCase = true)) {
                                        val regex = "(\\d+)\\s*Months?".toRegex(RegexOption.IGNORE_CASE)
                                        val match = regex.find(ageOrDob)
                                        ageOrDob = match?.groupValues?.get(0) ?: "6 Months"
                                    }
                                },
                                label = { Text("👶 In Months") },
                                modifier = Modifier.weight(1f).testTag("chip_age_mode_months")
                            )
                        }

                        if (ageMode == "DATE") {
                            // Option 1: Select Date (DOB) via Date Picker
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDobDatePicker = true }
                                    .testTag("btn_pick_dob_date")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = Icons.Default.Cake,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Date of Birth (Tap to Select Date):",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = ageOrDob.ifBlank { "Tap to pick Date of Birth" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { showDobDatePicker = true },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("btn_open_dob_picker")
                                    ) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pick Date")
                                    }
                                }
                            }
                        } else {
                            // Option 2: In Months selector with Stepper and Pediatric Milestones
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Milestones in Months (Pediatric Schedule):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val monthPresets = listOf(
                                        "At Birth",
                                        "1.5 Months (6w)",
                                        "2.5 Months (10w)",
                                        "3.5 Months (14w)",
                                        "6 Months",
                                        "9 Months",
                                        "12 Months (1y)",
                                        "15 Months",
                                        "18 Months",
                                        "24 Months (2y)",
                                        "3 Years",
                                        "5 Years"
                                    )
                                    monthPresets.forEach { preset ->
                                        FilterChip(
                                            selected = ageOrDob.trim().equals(preset, ignoreCase = true),
                                            onClick = { ageOrDob = preset },
                                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val currentVal = ageOrDob.filter { it.isDigit() }.toIntOrNull() ?: 6
                                            if (currentVal > 1) {
                                                ageOrDob = "${currentVal - 1} Months"
                                            } else {
                                                ageOrDob = "At Birth"
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("1 Mo", style = MaterialTheme.typography.labelSmall)
                                    }

                                    OutlinedTextField(
                                        value = ageOrDob,
                                        onValueChange = { ageOrDob = it },
                                        label = { Text("Age in Months") },
                                        placeholder = { Text("e.g. 6 Months") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("input_patient_age")
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            val currentVal = ageOrDob.filter { it.isDigit() }.toIntOrNull() ?: 5
                                            ageOrDob = "${currentVal + 1} Months"
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("1 Mo", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Gender selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Gender:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    listOf("Male", "Female", "Other").forEach { g ->
                        FilterChip(
                            selected = gender.equals(g, ignoreCase = true),
                            onClick = { gender = g },
                            label = { Text(g) },
                            modifier = Modifier.testTag("chip_gender_$g")
                        )
                    }
                }

                OutlinedTextField(
                    value = parentName,
                    onValueChange = { parentName = it },
                    label = { Text("Parent / Guardian Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_parent_name")
                )

                OutlinedTextField(
                    value = mobileNumber,
                    onValueChange = { mobileNumber = it },
                    label = { Text("Parent Mobile (for WhatsApp) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_patient_mobile")
                )

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider()

                // Section: Previous / Past Vaccines Taken by Child (Birth / Prior Doses)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPastVaccinesSection = !showPastVaccinesSection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Previous Vaccines Taken by Patient (${selectedPastVaccines.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Record birth vaccines or prior doses from hospitals",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                text = if (showPastVaccinesSection) "Hide ▲" else "Select Past Doses ▼",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (showPastVaccinesSection) {
                            Text(
                                text = "1. Tap vaccines child already received previously:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            val commonPast = listOf(
                                "BCG", "OPV-0", "Hepatitis B-0",
                                "Pentavalent-1", "Rotavirus-1", "PCV-1",
                                "Pentavalent-2", "Rotavirus-2", "PCV-2",
                                "Pentavalent-3", "Rotavirus-3", "PCV-3",
                                "MMR", "Typhoid", "Influenza", "Varicella", "Hepatitis A"
                            )
                            val resolvedDobForPast = selectedDobMillis
                                ?: PediatricVaccineScheduleEngine.resolvePatientDob(ageOrDob, System.currentTimeMillis()).dobMillis
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                commonPast.forEach { pastVac ->
                                    val isSelected = selectedPastVaccines.contains(pastVac)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                selectedPastVaccines.remove(pastVac)
                                                pastVaccineDates.remove(pastVac)
                                            } else {
                                                selectedPastVaccines.add(pastVac)
                                                // Auto-suggest milestone date from DOB if available
                                                val defaultMillis = when {
                                                    pastVac.contains("0") || pastVac.equals("BCG", ignoreCase = true) -> resolvedDobForPast
                                                    pastVac.contains("-1") -> resolvedDobForPast + 42L * 24 * 60 * 60 * 1000L
                                                    pastVac.contains("-2") -> resolvedDobForPast + 70L * 24 * 60 * 60 * 1000L
                                                    pastVac.contains("-3") -> resolvedDobForPast + 98L * 24 * 60 * 60 * 1000L
                                                    else -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
                                                }
                                                pastVaccineDates[pastVac] = defaultMillis.coerceAtMost(System.currentTimeMillis())
                                            }
                                        },
                                        label = { Text(pastVac, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            // Option to add custom past vaccine name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customPastVaccineInput,
                                    onValueChange = { customPastVaccineInput = it },
                                    label = { Text("Add Other Past Vaccine") },
                                    placeholder = { Text("e.g. JE-1, Pneumococcal") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val clean = customPastVaccineInput.trim()
                                        if (clean.isNotBlank() && !selectedPastVaccines.contains(clean)) {
                                            selectedPastVaccines.add(clean)
                                            pastVaccineDates[clean] = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
                                            customPastVaccineInput = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Add", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            // Per-vaccine Date Given list for selected past vaccines!
                            if (selectedPastVaccines.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "2. Set / Edit Date Given for Each Previous Vaccine:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    selectedPastVaccines.forEach { pastVac ->
                                        val dateMillis = pastVaccineDates[pastVac] ?: (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L)
                                        val formattedDate = formatTimestampToDate(dateMillis)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = pastVac,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Given on: $formattedDate",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    FilledTonalButton(
                                                        onClick = { pastVaccineBeingEditedForDate = pastVac },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier
                                                            .height(28.dp)
                                                            .testTag("btn_set_past_vaccine_date_$pastVac")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.EditCalendar,
                                                            contentDescription = "Set Date",
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Set Date", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            selectedPastVaccines.remove(pastVac)
                                                            pastVaccineDates.remove(pastVac)
                                                        },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Remove",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                // Vaccine given today
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vaccines Administered Today (${selectedGivenVaccines.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { showMultiVaccinePickerForGiven = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("btn_select_multi_given_vaccines")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedGivenVaccines.isEmpty()) "Select Vaccines" else "Edit Selection", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Interactive selector container & chips for given vaccines
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMultiVaccinePickerForGiven = true }
                        .testTag("select_vaccine_given_btn")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Administered Doses (Tap to add/change):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Select Multiple ▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedGivenVaccines.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                selectedGivenVaccines.forEach { name ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            selectedGivenVaccines.remove(name)
                                            val vacId = vaccines.find { it.name.equals(name, ignoreCase = true) }?.id
                                            if (vacId != null) selectedGivenVaccineIds.remove(vacId)
                                            vaccineGivenToday = selectedGivenVaccines.joinToString(", ")
                                        },
                                        label = { Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove $name",
                                                modifier = Modifier.size(12.dp)
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No vaccine selected yet. Tap to select one or more vaccines.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Quick 1-Tap Routine Vaccine Toggles for Administered Vaccines
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Quick Routine Vaccine Toggles (Tap to select/deselect):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        STANDARD_ROUTINE_VACCINE_SUGGESTIONS.forEach { vName ->
                            val isSelected = selectedGivenVaccines.any {
                                it.equals(vName, ignoreCase = true) || it.contains(vName, ignoreCase = true) || vName.contains(it, ignoreCase = true)
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val existing = selectedGivenVaccines.find { gName ->
                                        gName.equals(vName, ignoreCase = true) || gName.contains(vName, ignoreCase = true) || vName.contains(gName, ignoreCase = true)
                                    }
                                    if (existing != null) {
                                        selectedGivenVaccines.remove(existing)
                                        val vacId = vaccines.find { vac -> vac.name.equals(existing, ignoreCase = true) }?.id
                                        if (vacId != null) selectedGivenVaccineIds.remove(vacId)
                                    } else {
                                        val catalogMatch = vaccines.find { vac ->
                                            vac.name.contains(vName, ignoreCase = true) || vName.contains(vac.name, ignoreCase = true)
                                        }
                                        val toAdd = catalogMatch?.name ?: vName
                                        selectedGivenVaccines.add(toAdd)
                                        if (catalogMatch != null && !selectedGivenVaccineIds.contains(catalogMatch.id)) {
                                            selectedGivenVaccineIds.add(catalogMatch.id)
                                        }
                                    }
                                    vaccineGivenToday = selectedGivenVaccines.joinToString(", ")
                                },
                                label = { Text(vName, fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vaccineGivenDose,
                        onValueChange = { vaccineGivenDose = it },
                        label = { Text("Dose") },
                        placeholder = { Text("e.g. Dose 1, 0.5ml") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_vaccine_dose")
                    )
                    OutlinedTextField(
                        value = vaccineGivenBatch,
                        onValueChange = { vaccineGivenBatch = it },
                        label = { Text("Batch No") },
                        placeholder = { Text("e.g. HPB-X1") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_vaccine_batch")
                    )
                }

                // Checkbox to also deduct stock & record sale
                if (patient == null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = alsoRecordSale,
                                onCheckedChange = { alsoRecordSale = it },
                                modifier = Modifier.testTag("checkbox_also_record_sale")
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(
                                    text = if (selectedGivenVaccineIds.size > 1) {
                                        "Deduct ${selectedGivenVaccineIds.size} doses from stock & record sale"
                                    } else {
                                        "Deduct 1 dose from stock & record sale"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("Automatically logs transactions and updates available inventory", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                // DOB-Based Recommended Schedule Auto-Calculator Card
                val previewTakenNames = (selectedPastVaccines + selectedGivenVaccines).distinct()
                val previewDobMillis = selectedDobMillis
                    ?: PediatricVaccineScheduleEngine.resolvePatientDob(ageOrDob, System.currentTimeMillis()).dobMillis
                val previewSummary = remember(previewDobMillis, previewTakenNames) {
                    val tempPatient = PatientRegistration(
                        patientName = patientName.ifBlank { "Child" },
                        dobOrAge = ageOrDob,
                        parentName = parentName,
                        mobileNumber = mobileNumber,
                        vaccineGivenToday = previewTakenNames.joinToString(", ")
                    )
                    PediatricVaccineScheduleEngine.evaluatePatientSchedule(
                        patient = tempPatient,
                        overrideDobMillis = previewDobMillis
                    )
                }
                val nextMilestoneSuggestion = previewSummary.nextUpcomingMilestone

                if (nextMilestoneSuggestion != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "DOB Schedule Recommendation: ${nextMilestoneSuggestion.spec.title}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Text(
                                            text = "Due: ${nextMilestoneSuggestion.relativeDueLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        val pendingNames = previewSummary.nextRecommendedVaccineNames
                                        selectedNextVaccines.clear()
                                        selectedNextVaccines.addAll(pendingNames)
                                        nextPlannedVaccine = selectedNextVaccines.joinToString(", ")
                                        nextDueDateMillis = nextMilestoneSuggestion.calculatedDueDateMillis
                                        nextDueDate = "${nextMilestoneSuggestion.spec.title} (${nextMilestoneSuggestion.formattedDueDate})"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .testTag("btn_auto_apply_dob_schedule")
                                ) {
                                    Text("Auto-Fill Due Date", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "Recommended Doses: ${previewSummary.nextRecommendedVaccineNames.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Next planned vaccine and due date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Next Planned Vaccines (${selectedNextVaccines.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { showMultiVaccinePickerForNext = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("btn_select_multi_next_vaccines")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedNextVaccines.isEmpty()) "Select Vaccines" else "Edit Selection", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMultiVaccinePickerForNext = true }
                        .testTag("select_next_vaccine_btn")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scheduled Upcoming Doses (Tap to add/change):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Select Multiple ▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedNextVaccines.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                selectedNextVaccines.forEach { name ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            selectedNextVaccines.remove(name)
                                            nextPlannedVaccine = selectedNextVaccines.joinToString(", ")
                                        },
                                        label = { Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove $name",
                                                modifier = Modifier.size(12.dp)
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No vaccine selected yet. Tap to select one or more planned vaccines.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Quick 1-Tap Routine Vaccine Toggles for Next Planned Vaccines
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Quick Routine Vaccine Toggles (Tap to select/deselect):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        STANDARD_ROUTINE_VACCINE_SUGGESTIONS.forEach { vName ->
                            val isSelected = selectedNextVaccines.any {
                                it.equals(vName, ignoreCase = true) || it.contains(vName, ignoreCase = true) || vName.contains(it, ignoreCase = true)
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val existing = selectedNextVaccines.find { nName ->
                                        nName.equals(vName, ignoreCase = true) || nName.contains(vName, ignoreCase = true) || vName.contains(nName, ignoreCase = true)
                                    }
                                    if (existing != null) {
                                        selectedNextVaccines.remove(existing)
                                    } else {
                                        val catalogMatch = vaccines.find { vac ->
                                            vac.name.contains(vName, ignoreCase = true) || vName.contains(vac.name, ignoreCase = true)
                                        }
                                        val toAdd = catalogMatch?.name ?: vName
                                        selectedNextVaccines.add(toAdd)
                                    }
                                    nextPlannedVaccine = selectedNextVaccines.joinToString(", ")
                                },
                                label = { Text(vName, fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // REMINDER DUE DATE WITH DIRECT DATE PICKER
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showReminderDatePicker = true }
                        .testTag("btn_select_reminder_date_picker")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Reminder Schedule (Select Date from Calendar):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = nextDueDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Button(
                            onClick = { showReminderDatePicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_pick_reminder_date_dialog")
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Date Picker", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick preset buttons for due date
                Text("Quick Due Date Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { setDueDateOffset(28, "In 4 Weeks") },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("+4 Wks", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { setDueDateOffset(42, "In 6 Weeks") },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("+6 Wks", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { setDueDateOffset(60, "In 2 Months") },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("+2 Mos", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { setDueDateOffset(180, "In 6 Months") },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("+6 Mos", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedTextField(
                    value = nextDueDate,
                    onValueChange = { nextDueDate = it },
                    label = { Text("Next Due Date / Schedule Note *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_next_due_date")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Clinical Notes / Remarks") },
                    placeholder = { Text("e.g. Advised Paracetamol drops if mild fever, cold compress") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("input_patient_notes")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (patientName.isBlank() || parentName.isBlank() || mobileNumber.isBlank()) {
                        return@Button
                    }
                    val finalGiven = if (selectedGivenVaccines.isNotEmpty()) selectedGivenVaccines.joinToString(", ") else vaccineGivenToday
                    val finalNext = if (selectedNextVaccines.isNotEmpty()) selectedNextVaccines.joinToString(", ") else nextPlannedVaccine

                    val basePatient = (patient ?: PatientRegistration(
                        patientName = patientName,
                        dobOrAge = ageOrDob,
                        parentName = parentName,
                        mobileNumber = mobileNumber,
                        vaccineGivenToday = finalGiven,
                        nextPlannedVaccine = finalNext,
                        nextDueDate = nextDueDate
                    ))
                    var historyUpdated = basePatient

                    // 1. Append selected past / birth vaccines with their exact selected Date Given
                    for (pastVac in selectedPastVaccines) {
                        val isBirth = pastVac.contains("0") || pastVac.equals("BCG", ignoreCase = true)
                        val pastDateMillis = pastVaccineDates[pastVac]
                            ?: (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L)
                        historyUpdated = PatientVaccineRecordHelper.appendVaccineToPatientHistory(
                            patient = historyUpdated,
                            newVaccineNames = pastVac,
                            dose = if (isBirth) "At Birth" else "Past / Prior Dose",
                            batch = "",
                            dateMillis = pastDateMillis,
                            notes = "Recorded in patient history",
                            isHistorical = true
                        )
                    }

                    // 2. Append vaccine administered today if present
                    if (finalGiven.isNotBlank()) {
                        historyUpdated = PatientVaccineRecordHelper.appendVaccineToPatientHistory(
                            patient = historyUpdated,
                            newVaccineNames = finalGiven,
                            dose = vaccineGivenDose,
                            batch = vaccineGivenBatch,
                            dateMillis = givenTodayDateMillis,
                            notes = "Registration visit"
                        )
                    }

                    val updated = basePatient.copy(
                        patientName = patientName.trim(),
                        dobOrAge = ageOrDob.trim(),
                        gender = gender.trim(),
                        parentName = parentName.trim(),
                        mobileNumber = mobileNumber.trim(),
                        vaccineGivenToday = finalGiven.trim(),
                        vaccineGivenBatch = vaccineGivenBatch.trim(),
                        vaccineGivenDose = vaccineGivenDose.trim(),
                        nextPlannedVaccine = finalNext.trim(),
                        nextDueDate = nextDueDate.trim(),
                        nextDueDateMillis = nextDueDateMillis,
                        notes = notes.trim(),
                        previousVaccinesHistory = historyUpdated.previousVaccinesHistory
                    )

                    onSave(updated, alsoRecordSale, selectedGivenVaccineIds.firstOrNull(), sellingPrice, selectedGivenVaccineIds.toList())
                },
                modifier = Modifier.testTag("btn_save_patient")
            ) {
                Text(if (patient == null) "Register & Save" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Date picker for setting/editing the exact Date Given of a Past / Previously Given Vaccine
    pastVaccineBeingEditedForDate?.let { vacName ->
        val currentMillis = pastVaccineDates[vacName] ?: (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L)
        AppDatePickerModal(
            title = "Select Date Given for $vacName",
            initialDateMillis = currentMillis,
            maxDateMillis = System.currentTimeMillis(),
            onDismiss = { pastVaccineBeingEditedForDate = null },
            onDateSelected = { pickedMillis, _ ->
                pastVaccineDates[vacName] = pickedMillis
                pastVaccineBeingEditedForDate = null
            }
        )
    }

    // Picker for Child Date of Birth (DOB)
    if (showDobDatePicker) {
        AppDatePickerModal(
            title = "Select Child Date of Birth (DOB)",
            initialDateMillis = selectedDobMillis ?: (System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000)),
            maxDateMillis = System.currentTimeMillis(),
            onDateSelected = { selectedMillis, formattedDate ->
                selectedDobMillis = selectedMillis
                val calculatedAge = calculateAgeFromDob(selectedMillis)
                ageOrDob = "$calculatedAge (DOB: $formattedDate)"
                showDobDatePicker = false
            },
            onDismiss = { showDobDatePicker = false }
        )
    }

    // Picker for Reminder Due Date
    if (showReminderDatePicker) {
        AppDatePickerModal(
            title = "Select Reminder Due Date",
            initialDateMillis = nextDueDateMillis,
            onDateSelected = { selectedMillis, formattedDate ->
                val nowCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val selCal = Calendar.getInstance().apply {
                    timeInMillis = selectedMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val daysDiff = ((selCal.timeInMillis - nowCal.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
                val label = when {
                    daysDiff < 0 -> "$formattedDate (Overdue)"
                    daysDiff == 0 -> "Today ($formattedDate)"
                    daysDiff == 1 -> "Tomorrow ($formattedDate)"
                    daysDiff in 2..6 -> "$formattedDate (In $daysDiff days)"
                    daysDiff in 7..13 -> "$formattedDate (In 1 week)"
                    daysDiff in 14..27 -> "$formattedDate (In ${daysDiff / 7} weeks)"
                    else -> "$formattedDate (In ${daysDiff / 30} mos)"
                }
                nextDueDate = label
                nextDueDateMillis = selectedMillis
                showReminderDatePicker = false
            },
            onDismiss = { showReminderDatePicker = false }
        )
    }

    // Multi-Vaccine Picker for Vaccines Given Today
    if (showMultiVaccinePickerForGiven) {
        SelectMultipleVaccinesDialog(
            title = "Select Vaccines Given Today",
            subtitle = "Pick all doses administered to the child during this visit",
            vaccines = vaccines,
            initialSelectedVaccines = selectedGivenVaccines,
            onConfirmSelection = { names, ids ->
                selectedGivenVaccines.clear()
                selectedGivenVaccines.addAll(names)
                selectedGivenVaccineIds.clear()
                selectedGivenVaccineIds.addAll(ids)
                vaccineGivenToday = names.joinToString(", ")
                showMultiVaccinePickerForGiven = false
            },
            onDismiss = { showMultiVaccinePickerForGiven = false }
        )
    }

    // Multi-Vaccine Picker for Next Planned Vaccines
    if (showMultiVaccinePickerForNext) {
        SelectMultipleVaccinesDialog(
            title = "Schedule Next Vaccines",
            subtitle = "Select all vaccines planned for the upcoming appointment",
            vaccines = vaccines,
            initialSelectedVaccines = selectedNextVaccines,
            onConfirmSelection = { names, _ ->
                selectedNextVaccines.clear()
                selectedNextVaccines.addAll(names)
                nextPlannedVaccine = names.joinToString(", ")
                showMultiVaccinePickerForNext = false
            },
            onDismiss = { showMultiVaccinePickerForNext = false }
        )
    }
}

/**
 * Dialog for giving vaccines during a subsequent visit without re-registering the patient.
 * Allows doctor to pick new vaccines given today, updates history, deducts inventory,
 * schedules next visit/dose and can auto-open Bill & PDF.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubsequentVisitDialog(
    patient: PatientRegistration,
    vaccines: List<Vaccine>,
    onDismiss: () -> Unit,
    onSaveVisit: (PatientRegistration, Boolean, List<Long>, Boolean) -> Unit
) {
    // Vaccines administered today during this subsequent visit (starts empty so doctor explicitly selects)
    val suggestedInitial = remember(patient) {
        emptyList<String>()
    }
    val selectedGivenVaccines = remember { mutableStateListOf<String>().apply { addAll(suggestedInitial) } }
    val selectedGivenVaccineIds = remember {
        mutableStateListOf<Long>().apply {
            val ids = selectedGivenVaccines.mapNotNull { name ->
                vaccines.find { it.name.equals(name, ignoreCase = true) || it.name.contains(name, ignoreCase = true) || name.contains(it.name, ignoreCase = true) }?.id
            }.distinct()
            addAll(ids)
        }
    }

    var vaccineGivenBatch by remember { mutableStateOf("") }
    var vaccineGivenDose by remember { mutableStateOf("Dose 2") }

    // Next planned vaccines for future appointment
    val selectedNextVaccines = remember { mutableStateListOf<String>() }
    var nextPlannedVaccine by remember { mutableStateOf("") }
    var nextDueDate by remember { mutableStateOf("In 30 Days") }
    var nextDueDateMillis by remember {
        mutableStateOf(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
    }

    var visitNotes by remember { mutableStateOf("") }
    var alsoDeductStock by remember { mutableStateOf(true) }
    var alsoGenerateBill by remember { mutableStateOf(true) }

    var showMultiPickerForGiven by remember { mutableStateOf(false) }
    var showMultiPickerForNext by remember { mutableStateOf(false) }
    var showReminderDatePicker by remember { mutableStateOf(false) }

    fun setDueDateOffset(days: Int, label: String) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        nextDueDateMillis = cal.timeInMillis
        val formatted = formatTimestampToDate(cal.timeInMillis)
        nextDueDate = "$label ($formatted)"
    }

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
                    Text(
                        text = "Subsequent Visit Vaccination",
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
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Banner with Registered Patient Info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Registered Patient (No re-registration needed)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Parent: ${patient.parentName} • Mobile: ${patient.mobileNumber}", style = MaterialTheme.typography.bodySmall)
                            if (patient.nextPlannedVaccine.isNotBlank()) {
                                Text("Previously Scheduled: ${patient.nextPlannedVaccine}", style = MaterialTheme.typography.labelSmall, color = ProfitGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Previous Vaccines History Summary Card in Subsequent Visit
                val allPreviousTaken = remember(patient) {
                    PatientVaccineRecordHelper.getAllTakenVaccines(patient)
                }
                var showHistoryExpandedInVisit by remember { mutableStateOf(false) }

                if (allPreviousTaken.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ProfitGreenContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, ProfitGreen.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHistoryExpandedInVisit = !showHistoryExpandedInVisit }
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = ProfitGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Previous Vaccines Taken (${allPreviousTaken.size} Doses)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OnProfitGreenContainer
                                    )
                                }
                                Text(
                                    text = if (showHistoryExpandedInVisit) "Hide ▲" else "View All ▼",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ProfitGreen
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                allPreviousTaken.forEach { entry ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (entry.isHistorical) Color(0xFFE0F2FE) else ProfitGreenContainer
                                    ) {
                                        Text(
                                            text = "✅ ${entry.vaccineName} (${entry.doseName})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (entry.isHistorical) Color(0xFF0369A1) else OnProfitGreenContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (showHistoryExpandedInVisit) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    allPreviousTaken.forEachIndexed { i, entry ->
                                        val dStr = formatTimestampToDate(entry.dateAdministeredMillis)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${i + 1}. ${entry.vaccineName} (${entry.doseName})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "$dStr ${if (entry.batchNumber.isNotBlank()) "• ${entry.batchNumber}" else ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }

                // Section 1: Vaccines Administered Today
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Administering Today (${selectedGivenVaccines.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { showMultiPickerForGiven = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedGivenVaccines.isEmpty()) "Select" else "Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMultiPickerForGiven = true }
                        .testTag("subsequent_select_given_btn")
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vaccines Administered This Visit:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("Multi-Select ▾", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        if (selectedGivenVaccines.isNotEmpty()) {
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                selectedGivenVaccines.forEach { name ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ProfitGreenContainer.copy(alpha = 0.8f)
                                    ) {
                                        Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = OnProfitGreenContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        } else {
                            Text("Tap to select vaccines administered today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Dose & Batch Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vaccineGivenDose,
                        onValueChange = { vaccineGivenDose = it },
                        label = { Text("Dose / Stage") },
                        placeholder = { Text("e.g. Dose 2, Booster") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_subsequent_dose")
                    )
                    OutlinedTextField(
                        value = vaccineGivenBatch,
                        onValueChange = { vaccineGivenBatch = it },
                        label = { Text("Batch No") },
                        placeholder = { Text("e.g. VAC-992") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_subsequent_batch")
                    )
                }

                // Inventory & Bill Checkboxes
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alsoDeductStock,
                                onCheckedChange = { alsoDeductStock = it },
                                modifier = Modifier.testTag("checkbox_subsequent_deduct")
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text("Deduct doses from clinic inventory & log sale", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Automatically reduces current vaccine stock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alsoGenerateBill,
                                onCheckedChange = { alsoGenerateBill = it },
                                modifier = Modifier.testTag("checkbox_subsequent_bill")
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text("Open Bill & PDF receipt after recording visit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Instantly share digital invoice to WhatsApp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Section 2: Next Planned Vaccines & Schedule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Next Planned Vaccines (${selectedNextVaccines.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { showMultiPickerForNext = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedNextVaccines.isEmpty()) "Select" else "Edit", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMultiPickerForNext = true }
                        .testTag("subsequent_select_next_btn")
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Scheduled Future Doses:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("Multi-Select ▾", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        if (selectedNextVaccines.isNotEmpty()) {
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                selectedNextVaccines.forEach { name ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            selectedNextVaccines.remove(name)
                                            nextPlannedVaccine = selectedNextVaccines.joinToString(", ")
                                        },
                                        label = { Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        trailingIcon = {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                        }
                                    )
                                }
                            }
                        } else {
                            Text("Tap to schedule next vaccines (optional)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                // Quick Due Date Offsets
                Text("Schedule Next Due Date:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = { setDueDateOffset(30, "In 1 Month") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("+1 Mo", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = { setDueDateOffset(60, "In 2 Months") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("+2 Mos", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = { setDueDateOffset(90, "In 3 Months") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("+3 Mos", style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalButton(onClick = { setDueDateOffset(180, "In 6 Months") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("+6 Mos", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedTextField(
                    value = nextDueDate,
                    onValueChange = { nextDueDate = it },
                    label = { Text("Next Due Date Note *") },
                    trailingIcon = {
                        IconButton(onClick = { showReminderDatePicker = true }) {
                            Icon(Icons.Default.Event, contentDescription = "Pick Date")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_subsequent_due_date")
                )

                OutlinedTextField(
                    value = visitNotes,
                    onValueChange = { visitNotes = it },
                    label = { Text("Visit Notes / Advice") },
                    placeholder = { Text("e.g. Child active, no fever, advised paracetamol") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("input_subsequent_notes")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedGivenVaccines.isEmpty()) return@Button
                    val finalGiven = selectedGivenVaccines.joinToString(", ")
                    val finalNext = if (selectedNextVaccines.isNotEmpty()) selectedNextVaccines.joinToString(", ") else nextPlannedVaccine

                    // Append previous given dose to clinical notes as a comprehensive record trail
                    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                    val visitRecordEntry = "[$dateStr: $finalGiven ($vaccineGivenDose)${if (vaccineGivenBatch.isNotBlank()) " Batch: $vaccineGivenBatch" else ""}${if (visitNotes.isNotBlank()) " - $visitNotes" else ""}]"

                    val combinedNotes = if (patient.notes.isBlank()) {
                        visitRecordEntry
                    } else {
                        "${patient.notes}\n$visitRecordEntry"
                    }

                    val nowMillis = System.currentTimeMillis()
                    var updatedWithHistory = patient

                    // Preserve existing vaccineGivenToday in previousVaccinesHistory if not empty
                    if (patient.vaccineGivenToday.isNotBlank()) {
                        updatedWithHistory = PatientVaccineRecordHelper.appendVaccineToPatientHistory(
                            patient = updatedWithHistory,
                            newVaccineNames = patient.vaccineGivenToday,
                            dose = patient.vaccineGivenDose,
                            batch = patient.vaccineGivenBatch,
                            dateMillis = patient.dateGivenMillis,
                            notes = "Clinic visit dose"
                        )
                    }

                    // Append this new subsequent dose into previousVaccinesHistory
                    updatedWithHistory = PatientVaccineRecordHelper.appendVaccineToPatientHistory(
                        patient = updatedWithHistory,
                        newVaccineNames = finalGiven,
                        dose = vaccineGivenDose,
                        batch = vaccineGivenBatch,
                        dateMillis = nowMillis,
                        notes = visitNotes
                    )

                    val updatedPatient = updatedWithHistory.copy(
                        vaccineGivenToday = finalGiven.trim(),
                        vaccineGivenBatch = vaccineGivenBatch.trim(),
                        vaccineGivenDose = vaccineGivenDose.trim(),
                        dateGivenMillis = nowMillis,
                        nextPlannedVaccine = finalNext.trim(),
                        nextDueDate = nextDueDate.trim(),
                        nextDueDateMillis = nextDueDateMillis,
                        reminderStatus = "PENDING",
                        notes = combinedNotes.trim(),
                        previousVaccinesHistory = updatedWithHistory.previousVaccinesHistory
                    )

                    onSaveVisit(updatedPatient, alsoDeductStock, selectedGivenVaccineIds.toList(), alsoGenerateBill)
                },
                modifier = Modifier.testTag("btn_confirm_subsequent_visit")
            ) {
                Text("Record Vaccination & Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showMultiPickerForGiven) {
        SelectMultipleVaccinesDialog(
            title = "Administer Vaccines This Visit",
            subtitle = "Select all vaccines given to ${patient.patientName} during today's visit",
            vaccines = vaccines,
            initialSelectedVaccines = selectedGivenVaccines,
            onConfirmSelection = { names, ids ->
                selectedGivenVaccines.clear()
                selectedGivenVaccines.addAll(names)
                selectedGivenVaccineIds.clear()
                selectedGivenVaccineIds.addAll(ids)
                showMultiPickerForGiven = false
            },
            onDismiss = { showMultiPickerForGiven = false }
        )
    }

    if (showMultiPickerForNext) {
        SelectMultipleVaccinesDialog(
            title = "Schedule Next Vaccines",
            subtitle = "Select all vaccines planned for the next appointment",
            vaccines = vaccines,
            initialSelectedVaccines = selectedNextVaccines,
            onConfirmSelection = { names, _ ->
                selectedNextVaccines.clear()
                selectedNextVaccines.addAll(names)
                nextPlannedVaccine = names.joinToString(", ")
                showMultiPickerForNext = false
            },
            onDismiss = { showMultiPickerForNext = false }
        )
    }

    if (showReminderDatePicker) {
        AppDatePickerModal(
            title = "Select Next Visit Due Date",
            initialDateMillis = nextDueDateMillis,
            onDateSelected = { selectedMillis, formattedDate ->
                nextDueDate = "Due: $formattedDate"
                nextDueDateMillis = selectedMillis
                showReminderDatePicker = false
            },
            onDismiss = { showReminderDatePicker = false }
        )
    }
}
