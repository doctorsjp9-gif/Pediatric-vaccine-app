package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.service.AppointmentAlarmScheduler
import com.example.service.NotificationHelper
import com.example.ui.components.AddPurchaseDialog
import com.example.ui.components.AdjustStockDialog
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.AuthSyncDialog
import com.example.ui.components.BillScanStockDialog
import com.example.ui.components.ClinicLogoAvatar
import com.example.ui.components.CloudTransferDirection
import com.example.ui.components.RecordSaleDialog
import com.example.ui.components.VaccineFormDialog
import com.example.ui.components.VendorFormDialog
import com.example.ui.screens.ClinicProfileScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.PatientsScreen
import com.example.ui.screens.PurchasesScreen
import com.example.ui.screens.SalesScreen
import com.example.ui.screens.VendorsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppLanguage
import com.example.ui.util.AppLanguageManager
import com.example.ui.util.AppUpdateManager
import com.example.ui.util.ReminderLanguage
import com.example.ui.util.tr
import com.example.ui.viewmodel.UiEvent
import com.example.ui.viewmodel.VaccineStockViewModel
import com.example.util.configureAppCheck
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AppScreen(val title: String, val titleHi: String, val icon: ImageVector) {
    DASHBOARD("Home", "होम", Icons.Default.Dashboard),
    INVENTORY("Vaccines", "वैक्सीन", Icons.Default.Vaccines),
    PATIENTS("Patients", "मरीज़", Icons.Default.ChildCare),
    SALES("Sales", "बिक्री", Icons.Default.TrendingUp),
    PROFILE("Clinic", "क्लिनिक", Icons.Default.Storefront),
    PURCHASES("Restock", "स्टॉक", Icons.Default.ShoppingCart),
    VENDORS("Vendors", "वेंडर्स", Icons.Default.Business)
}

class MainActivity : ComponentActivity() {

    private val viewModel: VaccineStockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize App & WhatsApp Reminder Language Preferences + App Update Manager
        AppLanguageManager.init(this)
        AppUpdateManager.init(this)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        // Initialize local notification channel and appointment alarm scheduler
        NotificationHelper.createNotificationChannel(this)
        AppointmentAlarmScheduler.scheduleDailyAppointmentCheckAlarm(this)

        val targetScreen = if (intent?.getStringExtra(NotificationHelper.EXTRA_TARGET_SCREEN) == NotificationHelper.TARGET_PATIENTS_SCREEN) {
            AppScreen.PATIENTS
        } else {
            AppScreen.DASHBOARD
        }

        setContent {
            val clinicProfile by viewModel.clinicProfile.collectAsStateWithLifecycle()
            MyApplicationTheme(clinicTheme = clinicProfile.themeColor) {
                VaccineStockApp(viewModel = viewModel, initialScreen = targetScreen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineStockApp(viewModel: VaccineStockViewModel, initialScreen: AppScreen = AppScreen.DASHBOARD) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    val context = LocalContext.current

    // Dialog states
    var showPurchaseDialog by rememberSaveable { mutableStateOf(false) }
    var preSelectedPurchaseVaccineId by rememberSaveable { mutableStateOf<Long?>(null) }
    var preSelectedPurchaseVendorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showBillScanDialog by rememberSaveable { mutableStateOf(false) }

    var showSaleDialog by remember { mutableStateOf(false) }
    var preSelectedSaleVaccineId by remember { mutableStateOf<Long?>(null) }

    var showAdjustDialog by remember { mutableStateOf(false) }
    var preSelectedAdjustVaccineId by remember { mutableStateOf<Long?>(null) }

    var showVaccineFormDialog by remember { mutableStateOf(false) }
    var editingVaccine by remember { mutableStateOf<Vaccine?>(null) }
    var vaccineToDelete by remember { mutableStateOf<Vaccine?>(null) }

    var showVendorFormDialog by remember { mutableStateOf(false) }
    var editingVendor by remember { mutableStateOf<Vendor?>(null) }

    var showAuthSyncDialog by remember { mutableStateOf(false) }
    var authSyncOpenTransferTab by remember { mutableStateOf(false) }
    var authSyncTransferDirection by remember { mutableStateOf(CloudTransferDirection.UPLOAD) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAppUpdateDialog by rememberSaveable { mutableStateOf(false) }

    val updateState by AppUpdateManager.updateState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Observe state from ViewModel
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val cloudSnapshot by viewModel.cloudSnapshot.collectAsStateWithLifecycle()
    val isLoadingCloudPreview by viewModel.isLoadingCloudPreview.collectAsStateWithLifecycle()
    val vaccines by viewModel.allVaccines.collectAsStateWithLifecycle()
    val lowStockVaccines by viewModel.lowStockVaccines.collectAsStateWithLifecycle()
    val vendors by viewModel.allVendors.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val purchases by viewModel.allPurchases.collectAsStateWithLifecycle()
    val sales by viewModel.allSales.collectAsStateWithLifecycle()
    val patients by viewModel.allPatients.collectAsStateWithLifecycle()
    val clinicProfile by viewModel.clinicProfile.collectAsStateWithLifecycle()
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val searchQuery by viewModel.vaccineSearchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    val overduePatientsCount = remember(patients) {
        val now = System.currentTimeMillis()
        patients.count { it.nextDueDateMillis < now && it.reminderStatus != "SENT" }
    }

    // Automatically reschedule and ensure all patient vaccination appointment alarms are synced
    LaunchedEffect(patients) {
        if (patients.isNotEmpty()) {
            AppointmentAlarmScheduler.rescheduleAllPatientAlarms(context, patients)
        }
    }

    // Check configured remote APK update source (Cloud release link / GitHub / Manifest) on startup or sign-in
    LaunchedEffect(sessionState.isAuthenticated) {
        val current = AppUpdateManager.updateState.value
        val hasRemoteSource = sessionState.isAuthenticated ||
            current.githubRepo.isNotBlank() ||
            current.manifestJsonUrl.isNotBlank() ||
            current.apkDownloadUrl.isNotBlank()
        if (current.autoCheckOnStartup && hasRemoteSource) {
            AppUpdateManager.checkForUpdates(context)
        }
    }

    // Listen to UI feedback events
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowMessage -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
                }
                is UiEvent.ShowError -> {
                    scope.launch { snackbarHostState.showSnackbar("Error: ${event.error}") }
                }
            }
        }
    }

    // Predictive Back Handling: Return to Dashboard if on a sub-screen
    if (currentScreen != AppScreen.DASHBOARD) {
        BackHandler {
            currentScreen = AppScreen.DASHBOARD
        }
    }

    val currentAppLanguage by AppLanguageManager.appLanguageFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                androidx.compose.material3.TopAppBar(
                    title = {
                        Column(modifier = Modifier.padding(end = 4.dp)) {
                            Text(
                                text = when (currentScreen) {
                                    AppScreen.DASHBOARD -> clinicProfile.clinicName.ifBlank { "Dr Priyank Vaccine Clinic" }
                                    AppScreen.INVENTORY -> tr("Vaccine Catalog", "वैक्सीन कैटलॉग (Catalog)")
                                    AppScreen.PATIENTS -> tr("Patient Records", "मरीज़ रिकॉर्ड (Patients)")
                                    AppScreen.SALES -> tr("Sales & Invoices", "बिक्री और बिल (Sales)")
                                    AppScreen.PROFILE -> tr("Clinic Profile", "क्लिनिक प्रोफ़ाइल")
                                    AppScreen.PURCHASES -> tr("Restock Ledger", "स्टॉक खरीद लेजर")
                                    AppScreen.VENDORS -> tr("Vaccine Vendors", "वैक्सीन सप्लायर (Vendors)")
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = when (currentScreen) {
                                    AppScreen.DASHBOARD -> clinicProfile.doctorName
                                    AppScreen.INVENTORY -> tr(
                                        "${vaccines.size} vaccines • ${dashboardSummary.totalStockUnits} doses",
                                        "${vaccines.size} टीके • कुल ${dashboardSummary.totalStockUnits} खुराक"
                                    )
                                    AppScreen.PATIENTS -> tr(
                                        "${patients.size} registered patients",
                                        "${patients.size} पंजीकृत बच्चे"
                                    )
                                    AppScreen.SALES -> tr(
                                        "${sales.size} dispensed invoices",
                                        "${sales.size} जारी किए गए बिल"
                                    )
                                    AppScreen.PROFILE -> tr(
                                        "Settings, Language & Registration",
                                        "सेटिंग्स, भाषा और पंजीकरण"
                                    )
                                    AppScreen.PURCHASES -> tr(
                                        "${purchases.size} purchase orders",
                                        "${purchases.size} स्टॉक ऑर्डर"
                                    )
                                    AppScreen.VENDORS -> tr(
                                        "${vendors.size} authorized suppliers",
                                        "${vendors.size} अधिकृत वेंडर"
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = { currentScreen = AppScreen.PROFILE },
                            modifier = Modifier.testTag("app_logo_btn")
                        ) {
                            ClinicLogoAvatar(
                                profile = clinicProfile,
                                size = 34.dp
                            )
                        }
                    },
                    actions = {
                        // 1-Tap Hindi / English Language Toggle Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentAppLanguage == AppLanguage.HINDI) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                            border = BorderStroke(
                                1.dp,
                                if (currentAppLanguage == AppLanguage.HINDI) Color(0xFF22C55E) else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .padding(end = 2.dp)
                                .clickable {
                                    val newLang = AppLanguageManager.toggleAppLanguage(context)
                                    val toastText = if (newLang == AppLanguage.HINDI) {
                                        "भाषा हिंदी (Hindi) में बदल दी गई है! WhatsApp रिमाइंडर भी हिंदी में भेजे जाएंगे।"
                                    } else {
                                        "Language switched to English"
                                    }
                                    android.widget.Toast.makeText(context, toastText, android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .testTag("topbar_language_toggle_btn")
                        ) {
                            Text(
                                text = if (currentAppLanguage == AppLanguage.HINDI) "हिंदी ✓" else "हिंदी/EN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (currentAppLanguage == AppLanguage.HINDI) Color(0xFF15803D) else Color(0xFF0F172A),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }

                        // Direct App Update Button in TopBar (with badge when update is available)
                        IconButton(
                            onClick = { showAppUpdateDialog = true },
                            modifier = Modifier.size(36.dp).testTag("topbar_app_update_btn")
                        ) {
                            if (updateState.isUpdateAvailable) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = Color(0xFF16A34A))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = "Update App",
                                        tint = Color(0xFF0D7A3E),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Update App",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Multi-Device Cloud Sync & Account
                        IconButton(
                            onClick = { showAuthSyncDialog = true },
                            modifier = Modifier.size(36.dp).testTag("topbar_sync_account_btn")
                        ) {
                            if (syncState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (sessionState.isAuthenticated) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                    contentDescription = "Sync & Login",
                                    tint = if (sessionState.isAuthenticated) Color(0xFF0D7A3E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Direct Logout Option when authenticated
                        if (sessionState.isAuthenticated) {
                            IconButton(
                                onClick = { showLogoutDialog = true },
                                modifier = Modifier.size(34.dp).testTag("topbar_logout_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Log Out",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Quick navigation to Vendors
                        IconButton(
                            onClick = { currentScreen = AppScreen.VENDORS },
                            modifier = Modifier.size(36.dp).testTag("topbar_vendors_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = "Vendors",
                                tint = if (currentScreen == AppScreen.VENDORS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Quick navigation to Restock
                        IconButton(
                            onClick = { currentScreen = AppScreen.PURCHASES },
                            modifier = Modifier.size(36.dp).testTag("topbar_purchases_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Restock Ledger",
                                tint = if (currentScreen == AppScreen.PURCHASES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Quick Action button in TopBar
                        IconButton(
                            onClick = {
                                preSelectedPurchaseVaccineId = null
                                preSelectedPurchaseVendorId = null
                                showPurchaseDialog = true
                            },
                            modifier = Modifier.size(36.dp).testTag("topbar_quick_restock")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Quick Restock",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                )
                androidx.compose.material3.HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFE2E8F0)
                )
            }
        },
        bottomBar = {
            val bottomScreens = listOf(
                AppScreen.DASHBOARD,
                AppScreen.INVENTORY,
                AppScreen.PATIENTS,
                AppScreen.SALES,
                AppScreen.PROFILE
            )

            Column {
                androidx.compose.material3.HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFE2E8F0)
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    bottomScreens.forEach { screen ->
                        val isSelected = currentScreen == screen
                        val navAccentColor = when (screen) {
                            AppScreen.DASHBOARD -> Color(0xFF006874)
                            AppScreen.INVENTORY -> Color(0xFF0277BD)
                            AppScreen.PATIENTS -> Color(0xFFC2185B)
                            AppScreen.SALES -> Color(0xFF0D7A3E)
                            AppScreen.PROFILE -> Color(0xFF5E35B1)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val navIndicatorColor = when (screen) {
                            AppScreen.DASHBOARD -> Color(0xFFE0F7FA)
                            AppScreen.INVENTORY -> Color(0xFFE8F6FE)
                            AppScreen.PATIENTS -> Color(0xFFFDF0F5)
                            AppScreen.SALES -> Color(0xFFE8F8EE)
                            AppScreen.PROFILE -> Color(0xFFF3EFFC)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentScreen = screen },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = navAccentColor,
                                selectedTextColor = navAccentColor,
                                indicatorColor = navIndicatorColor,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            icon = {
                                when (screen) {
                                    AppScreen.INVENTORY -> {
                                        if (dashboardSummary.lowStockCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge { Text("${dashboardSummary.lowStockCount}") }
                                                }
                                            ) {
                                                Icon(imageVector = screen.icon, contentDescription = screen.title)
                                            }
                                        } else {
                                            Icon(imageVector = screen.icon, contentDescription = screen.title)
                                        }
                                    }
                                    AppScreen.PATIENTS -> {
                                        if (overduePatientsCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge { Text("$overduePatientsCount") }
                                                }
                                            ) {
                                                Icon(imageVector = screen.icon, contentDescription = screen.title)
                                            }
                                        } else {
                                            Icon(imageVector = screen.icon, contentDescription = screen.title)
                                        }
                                    }
                                    else -> {
                                        Icon(imageVector = screen.icon, contentDescription = screen.title)
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = if (currentAppLanguage == AppLanguage.HINDI) screen.titleHi else screen.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.DASHBOARD -> {
                    DashboardScreen(
                        summary = dashboardSummary,
                        recentTransactions = transactions,
                        lowStockVaccines = lowStockVaccines,
                        clinicProfile = clinicProfile,
                        sessionState = sessionState,
                        syncState = syncState,
                        onOpenAuthSyncDialog = {
                            authSyncOpenTransferTab = false
                            showAuthSyncDialog = true
                        },
                        onOpenSelectiveUpload = {
                            authSyncTransferDirection = CloudTransferDirection.UPLOAD
                            authSyncOpenTransferTab = true
                            showAuthSyncDialog = true
                        },
                        onOpenSelectiveDownload = {
                            authSyncTransferDirection = CloudTransferDirection.DOWNLOAD
                            authSyncOpenTransferTab = true
                            showAuthSyncDialog = true
                        },
                        onSyncRequested = { viewModel.syncDataNow() },
                        onSignOutRequested = { showLogoutDialog = true },
                        onOpenPurchaseDialog = {
                            preSelectedPurchaseVaccineId = null
                            preSelectedPurchaseVendorId = null
                            showPurchaseDialog = true
                        },
                        onOpenScanBillDialog = {
                            showBillScanDialog = true
                        },
                        onOpenSaleDialog = {
                            preSelectedSaleVaccineId = null
                            showSaleDialog = true
                        },
                        onOpenAdjustDialog = {
                            preSelectedAdjustVaccineId = null
                            showAdjustDialog = true
                        },
                        onOpenAddVaccineDialog = {
                            editingVaccine = null
                            showVaccineFormDialog = true
                        },
                        onNavigateToInventory = { currentScreen = AppScreen.INVENTORY },
                        onNavigateToPurchases = { currentScreen = AppScreen.PURCHASES },
                        onNavigateToSales = { currentScreen = AppScreen.SALES },
                        onNavigateToPatients = { currentScreen = AppScreen.PATIENTS },
                        onNavigateToProfile = { currentScreen = AppScreen.PROFILE },
                        onOpenAppUpdateDialog = { showAppUpdateDialog = true }
                    )
                }

                AppScreen.INVENTORY -> {
                    InventoryScreen(
                        vaccines = vaccines,
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        onSearchQueryChange = viewModel::updateVaccineSearch,
                        onCategorySelected = viewModel::updateCategoryFilter,
                        onOpenRestockDialog = { vaccine ->
                            preSelectedPurchaseVaccineId = vaccine.id
                            showPurchaseDialog = true
                        },
                        onOpenDispenseDialog = { vaccine ->
                            preSelectedSaleVaccineId = vaccine.id
                            showSaleDialog = true
                        },
                        onOpenEditVaccineDialog = { vaccine ->
                            editingVaccine = vaccine
                            showVaccineFormDialog = true
                        },
                        onOpenDeleteVaccineDialog = { vaccine ->
                            vaccineToDelete = vaccine
                        },
                        onOpenAddVaccineDialog = {
                            editingVaccine = null
                            showVaccineFormDialog = true
                        },
                        onOpenPurchaseFromVendorDialog = {
                            preSelectedPurchaseVaccineId = null
                            preSelectedPurchaseVendorId = null
                            showPurchaseDialog = true
                        },
                        onOpenScanBillDialog = {
                            showBillScanDialog = true
                        },
                        onDeleteOutOfStockVaccines = { viewModel.deleteOutOfStockVaccines() },
                        onPurgeStaleCloudData = { viewModel.purgeStaleCloudData() },
                        onEraseAllVaccines = { viewModel.deleteAllVaccines() },
                        onEraseAllData = { viewModel.eraseAllData() }
                    )
                }

                AppScreen.PATIENTS -> {
                    PatientsScreen(
                        patients = patients,
                        vaccines = vaccines,
                        clinicProfile = clinicProfile,
                        onSavePatient = { patient, alsoRecordSale, vaccineId, price, multipleIds ->
                            viewModel.savePatient(patient, alsoRecordSale, vaccineId, price, multipleIds)
                            AppointmentAlarmScheduler.schedulePatientAppointmentAlarm(context, patient)
                        },
                        onDeletePatient = { patient ->
                            viewModel.deletePatient(patient)
                            AppointmentAlarmScheduler.cancelPatientAppointmentAlarm(context, patient.id)
                        },
                        onMarkReminderSent = { id ->
                            viewModel.markPatientReminderSent(id)
                            AppointmentAlarmScheduler.cancelPatientAppointmentAlarm(context, id)
                        },
                        onUpdateReminderDate = { id, nextDate, nextMillis ->
                            viewModel.updatePatientReminderDate(id, nextDate, nextMillis)
                            val p = patients.find { it.id == id }
                            if (p != null) {
                                AppointmentAlarmScheduler.schedulePatientAppointmentAlarm(
                                    context,
                                    p.copy(nextDueDateMillis = nextMillis, nextDueDate = nextDate)
                                )
                            }
                        }
                    )
                }

                AppScreen.SALES -> {
                    SalesScreen(
                        sales = sales,
                        clinicProfile = clinicProfile,
                        onOpenRecordSaleDialog = {
                            preSelectedSaleVaccineId = null
                            showSaleDialog = true
                        }
                    )
                }

                AppScreen.PROFILE -> {
                    ClinicProfileScreen(
                        currentProfile = clinicProfile,
                        sessionState = sessionState,
                        syncState = syncState,
                        onOpenAuthSyncDialog = { showAuthSyncDialog = true },
                        onSyncRequested = { viewModel.syncDataNow() },
                        onSignOutRequested = { showLogoutDialog = true },
                        onOpenAppUpdateDialog = { showAppUpdateDialog = true },
                        onSaveProfile = { updated ->
                            viewModel.saveClinicProfile(updated)
                        }
                    )
                }

                AppScreen.PURCHASES -> {
                    PurchasesScreen(
                        purchases = purchases,
                        onOpenAddPurchaseDialog = {
                            preSelectedPurchaseVaccineId = null
                            preSelectedPurchaseVendorId = null
                            showPurchaseDialog = true
                        },
                        onOpenScanBillDialog = {
                            showBillScanDialog = true
                        }
                    )
                }

                AppScreen.VENDORS -> {
                    VendorsScreen(
                        vendors = vendors,
                        onOpenAddVendorDialog = {
                            editingVendor = null
                            showVendorFormDialog = true
                        },
                        onOpenEditVendorDialog = { vendor ->
                            editingVendor = vendor
                            showVendorFormDialog = true
                        },
                        onDeleteVendor = { vendor ->
                            viewModel.deleteVendor(vendor)
                        },
                        onNewPurchaseFromVendor = { vendor ->
                            preSelectedPurchaseVendorId = vendor.id
                            showPurchaseDialog = true
                        }
                    )
                }
            }
        }
    }

    // --- MODAL DIALOGS ---

    // 1. Purchase (Restock) Dialog
    if (showPurchaseDialog) {
        AddPurchaseDialog(
            vaccines = vaccines,
            vendors = vendors,
            preSelectedVaccineId = preSelectedPurchaseVaccineId,
            preSelectedVendorId = preSelectedPurchaseVendorId,
            onDismiss = { showPurchaseDialog = false },
            onOpenAddVendor = {
                editingVendor = null
                showVendorFormDialog = true
            },
            onOpenScanBill = {
                showPurchaseDialog = false
                showBillScanDialog = true
            },
            onConfirmPurchase = { vaccineId, vendorId, quantity, unitPurchasePrice, batchNumber, expiryDate, invoiceNumber, notes ->
                showPurchaseDialog = false
                viewModel.recordPurchase(
                    vaccineId = vaccineId,
                    vendorId = vendorId,
                    quantity = quantity,
                    unitPurchasePrice = unitPurchasePrice,
                    batchNumber = batchNumber,
                    expiryDate = expiryDate,
                    invoiceNumber = invoiceNumber,
                    notes = notes
                )
            },
            onConfirmMultiPurchase = { vendorId, items, invoiceNumber, notes ->
                showPurchaseDialog = false
                viewModel.recordMultiPurchase(
                    vendorId = vendorId,
                    items = items,
                    invoiceNumber = invoiceNumber,
                    notes = notes
                )
            }
        )
    }

    // 1b. Scan Bill / Upload PDF Stock Update Dialog
    if (showBillScanDialog) {
        BillScanStockDialog(
            vaccines = vaccines,
            vendors = vendors,
            preSelectedVaccineId = preSelectedPurchaseVaccineId,
            onDismiss = { showBillScanDialog = false },
            onApplyScannedBill = { vendorId, newVendorName, newVendorPhone, invoiceNumber, notes, items ->
                showBillScanDialog = false
                viewModel.applyScannedBillStockUpdate(
                    vendorId = vendorId,
                    newVendorName = newVendorName,
                    newVendorPhone = newVendorPhone,
                    invoiceNumber = invoiceNumber,
                    notes = notes,
                    items = items
                )
            }
        )
    }

    // 2. Sale (Dispense) Dialog
    if (showSaleDialog) {
        RecordSaleDialog(
            vaccines = vaccines,
            preSelectedVaccineId = preSelectedSaleVaccineId,
            onDismiss = { showSaleDialog = false },
            onConfirmSale = { vaccineId, quantity, unitSellingPrice, customerOrPatient, batchNumber, notes ->
                showSaleDialog = false
                viewModel.recordSale(
                    vaccineId = vaccineId,
                    quantity = quantity,
                    unitSellingPrice = unitSellingPrice,
                    customerOrPatient = customerOrPatient,
                    batchNumber = batchNumber,
                    notes = notes
                )
            },
            onConfirmMultiSale = { items, customerOrPatient, notes ->
                showSaleDialog = false
                viewModel.recordMultiSale(
                    items = items,
                    customerOrPatient = customerOrPatient,
                    notes = notes
                )
            }
        )
    }

    // 3. Stock Adjustment Dialog
    if (showAdjustDialog) {
        AdjustStockDialog(
            vaccines = vaccines,
            preSelectedVaccineId = preSelectedAdjustVaccineId,
            onDismiss = { showAdjustDialog = false },
            onConfirmAdjustment = { vaccineId, isAddition, quantity, reason ->
                showAdjustDialog = false
                viewModel.adjustStock(
                    vaccineId = vaccineId,
                    isAddition = isAddition,
                    quantity = quantity,
                    reason = reason
                )
            }
        )
    }

    // 4. Vaccine Form Dialog
    if (showVaccineFormDialog) {
        VaccineFormDialog(
            initialVaccine = editingVaccine,
            onDismiss = { showVaccineFormDialog = false },
            onSave = { vaccine ->
                showVaccineFormDialog = false
                viewModel.saveVaccine(vaccine)
            },
            onDelete = {
                val toDelete = editingVaccine
                showVaccineFormDialog = false
                vaccineToDelete = toDelete
            }
        )
    }

    // 5. Vendor Form Dialog
    if (showVendorFormDialog) {
        VendorFormDialog(
            initialVendor = editingVendor,
            onDismiss = { showVendorFormDialog = false },
            onSave = { vendor ->
                showVendorFormDialog = false
                viewModel.saveVendor(vendor)
            }
        )
    }

    // 6. Delete Vaccine Confirmation Dialog
    vaccineToDelete?.let { vac ->
        AlertDialog(
            onDismissRequest = { vaccineToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Vaccine?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to delete '${vac.name}' from your inventory catalog?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (vac.currentStock > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Warning: This vaccine currently has ${vac.currentStock} ${vac.unit} on hand.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "The vaccine item will be removed from your catalog. Previous sales & restock history will remain in the clinic ledger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = vac
                        vaccineToDelete = null
                        viewModel.deleteVaccine(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_vaccine_dialog_btn")
                ) {
                    Text("Delete Vaccine")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { vaccineToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAuthSyncDialog) {
        AuthSyncDialog(
            sessionState = sessionState,
            syncState = syncState,
            authManager = viewModel.authManager,
            vaccines = vaccines,
            patients = patients,
            transactions = transactions,
            vendors = vendors,
            clinicProfile = clinicProfile,
            cloudSnapshot = cloudSnapshot,
            isLoadingCloudPreview = isLoadingCloudPreview,
            initialOpenTransferTab = authSyncOpenTransferTab,
            initialTransferDirection = authSyncTransferDirection,
            onFetchCloudPreview = { viewModel.fetchCloudPreview() },
            onUploadSelectedToCloud = { request -> viewModel.uploadSelectedToCloud(request) },
            onDownloadSelectedFromCloud = { request -> viewModel.downloadSelectedFromCloud(request) },
            onSyncRequested = { viewModel.syncDataNow() },
            onSyncWithConfig = { config -> viewModel.syncDataWithConfig(config) },
            onSignOutRequested = { viewModel.signOutUser() },
            onPurgeStaleCloudData = { viewModel.purgeStaleCloudData() },
            onDeleteOutOfStockVaccines = { viewModel.deleteOutOfStockVaccines() },
            onEraseAllCloudData = { viewModel.eraseAllCloudData() },
            onEraseAllData = { viewModel.eraseAllData() },
            onDismiss = {
                authSyncOpenTransferTab = false
                showAuthSyncDialog = false
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Log Out of Clinic Account", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to log out of ${sessionState.displayName.ifBlank { clinicProfile.doctorName }}?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (sessionState.displayEmailOrPhone.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Account: ${sessionState.displayEmailOrPhone}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your local vaccine inventory, sales ledger, and patient data will remain preserved safely on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOutUser()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_logout")
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutDialog = false },
                    modifier = Modifier.testTag("btn_cancel_logout")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAppUpdateDialog) {
        AppUpdateDialog(
            vaccinesCount = vaccines.size,
            patientsCount = patients.size,
            transactionsCount = transactions.size,
            isCloudAuthenticated = sessionState.isAuthenticated,
            onRunPreUpdateBackupAndSync = {
                viewModel.runPreUpdateSafetyBackupAndSync()
            },
            onDismiss = { showAppUpdateDialog = false }
        )
    }
}
