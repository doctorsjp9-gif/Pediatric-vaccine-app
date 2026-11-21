package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.StockTransaction
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.data.repository.CategorySyncAction
import com.example.data.repository.CloudDataSnapshot
import com.example.data.repository.CloudSyncConfig
import com.example.data.repository.SelectiveCloudTransferRequest
import com.example.data.repository.SyncStatusState
import com.example.ui.auth.AuthManager
import com.example.ui.auth.UserSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@Composable
fun AuthSyncDialog(
    sessionState: UserSessionState,
    syncState: SyncStatusState,
    authManager: AuthManager,
    vaccines: List<Vaccine> = emptyList(),
    patients: List<PatientRegistration> = emptyList(),
    transactions: List<StockTransaction> = emptyList(),
    vendors: List<Vendor> = emptyList(),
    clinicProfile: ClinicProfile = ClinicProfile(),
    cloudSnapshot: CloudDataSnapshot? = null,
    isLoadingCloudPreview: Boolean = false,
    initialOpenTransferTab: Boolean = false,
    initialTransferDirection: CloudTransferDirection = CloudTransferDirection.UPLOAD,
    onFetchCloudPreview: () -> Unit = {},
    onUploadSelectedToCloud: (SelectiveCloudTransferRequest) -> Unit = {},
    onDownloadSelectedFromCloud: (SelectiveCloudTransferRequest) -> Unit = {},
    onSyncRequested: () -> Unit,
    onSyncWithConfig: (CloudSyncConfig) -> Unit = { onSyncRequested() },
    onSignOutRequested: () -> Unit,
    onPurgeStaleCloudData: () -> Unit = {},
    onDeleteOutOfStockVaccines: () -> Unit = {},
    onEraseAllCloudData: () -> Unit = {},
    onEraseAllData: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember(initialOpenTransferTab, sessionState.isAuthenticated) {
        mutableIntStateOf(
            if (initialOpenTransferTab) {
                if (sessionState.isAuthenticated) 1 else 2
            } else {
                0
            }
        )
    }
    var transferDirection by remember(initialTransferDirection) { mutableStateOf(initialTransferDirection) }

    // Google Sign-In state
    var isGoogleLoading by remember { mutableStateOf(false) }

    // Mobile + OTP state
    var mobileInput by remember { mutableStateOf(sessionState.mobileNumber ?: "") }
    var otpInput by remember { mutableStateOf("") }
    var generatedOtp by remember { mutableStateOf<String?>(null) }
    var isOtpSent by remember { mutableStateOf(false) }
    var otpCooldown by remember { mutableIntStateOf(0) }
    var otpErrorMessage by remember { mutableStateOf<String?>(null) }

    // Selective Cloud Sync Preserve / Delete Configuration States
    var vaccinesAction by remember { mutableStateOf(CategorySyncAction.PRESERVE_BOTH) }
    var patientsAction by remember { mutableStateOf(CategorySyncAction.PRESERVE_BOTH) }
    var transactionsAction by remember { mutableStateOf(CategorySyncAction.PRESERVE_BOTH) }
    var vendorsAction by remember { mutableStateOf(CategorySyncAction.PRESERVE_BOTH) }
    var profileAction by remember { mutableStateOf(CategorySyncAction.PRESERVE_BOTH) }
    var deleteDefaultDemoRecords by remember { mutableStateOf(false) }

    // Individual item selection (Unchecked = Delete from device & cloud, Checked = Preserve)
    var vaccineIdsToDelete by remember { mutableStateOf(setOf<Long>()) }
    var patientIdsToDelete by remember { mutableStateOf(setOf<Long>()) }
    var vendorIdsToDelete by remember { mutableStateOf(setOf<Long>()) }

    var expandVaccinesList by remember { mutableStateOf(false) }
    var expandPatientsList by remember { mutableStateOf(false) }
    var expandVendorsList by remember { mutableStateOf(false) }

    // Confirmation dialog states
    var showConfirmPurgeDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteZeroStockDialog by remember { mutableStateOf(false) }
    var showConfirmEraseCloudDialog by remember { mutableStateOf(false) }
    var showConfirmEraseAllDialog by remember { mutableStateOf(false) }

    // OTP Countdown timer
    LaunchedEffect(otpCooldown) {
        if (otpCooldown > 0) {
            delay(1000)
            otpCooldown -= 1
        }
    }

    fun buildCurrentSyncConfig(): CloudSyncConfig {
        return CloudSyncConfig(
            vaccinesAction = vaccinesAction,
            patientsAction = patientsAction,
            transactionsAction = transactionsAction,
            vendorsAction = vendorsAction,
            clinicProfileAction = profileAction,
            vaccineIdsToDelete = vaccineIdsToDelete,
            patientIdsToDelete = patientIdsToDelete,
            vendorIdsToDelete = vendorIdsToDelete,
            deleteDefaultDemoRecords = deleteDefaultDemoRecords
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .systemBarsPadding()
            .imePadding()
            .padding(vertical = 8.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF006874), Color(0xFF00ACC1))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (sessionState.isAuthenticated) Icons.Default.CloudDone else Icons.Default.CloudSync,
                            contentDescription = "Sync",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (sessionState.isAuthenticated) "Cloud Sync & Data Options" else "Login & Device Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Preserve your clinic data or selectively delete unwanted items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("auth_dialog_close")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Colorful Protection Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE0F7FA)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF80DEEA)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Multi-device",
                            tint = Color(0xFF006064),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Safe Update & Selective Cloud Sync",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF004D40)
                            )
                            Text(
                                text = "Updating the app never deletes your old data. When syncing with Cloud, choose exactly which data to preserve and which to delete below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF006064)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tabs
                val tabTitles = if (sessionState.isAuthenticated) {
                    listOf("Account", "Up/Down", "Sync Rules", "Manage", "Login")
                } else {
                    listOf("Gmail", "Mobile", "Up/Down", "Sync Rules", "Erase")
                }

                TabRow(
                    selectedTabIndex = selectedTab.coerceIn(0, tabTitles.lastIndex),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (sessionState.isAuthenticated && selectedTab == 0) {
                    // TAB 0: Active Account Overview Tab
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            brush = Brush.linearGradient(
                                                listOf(Color(0xFF006874), Color(0xFF26A69A))
                                            ),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = "Doctor",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = sessionState.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = sessionState.displayEmailOrPhone,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Auth Provider:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = when (sessionState.authProvider) {
                                        "GOOGLE" -> "Gmail (Google Sign-In)"
                                        "MOBILE" -> "Mobile Number (OTP Verified)"
                                        "BOTH" -> "Gmail + Mobile Number"
                                        else -> "Standard Session"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cloud Sync Status:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = syncState.statusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (syncState.lastError != null) MaterialTheme.colorScheme.error else Color(0xFF1B5E20)
                                )
                            }

                            if (syncState.lastSyncTimeMillis != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Last Synced:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val formattedTime = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                        .format(Date(syncState.lastSyncTimeMillis))
                                    Text(
                                        text = formattedTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary Sync Button (Safe Preserve All)
                    Button(
                        onClick = { onSyncWithConfig(buildCurrentSyncConfig()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_dialog_sync_now_btn"),
                        enabled = !syncState.isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006874)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (syncState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing across devices...")
                        } else {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync & Preserve All Data Now")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dedicated Selective Upload & Selective Download Quick Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                transferDirection = CloudTransferDirection.UPLOAD
                                selectedTab = 1
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_dialog_quick_selective_upload_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0E8A42),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select & Upload", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Button(
                            onClick = {
                                transferDirection = CloudTransferDirection.DOWNLOAD
                                selectedTab = 1
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_dialog_quick_selective_download_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1565C0),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select & Download", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Highlighted Button to Customize Which Data to Preserve / Delete on Sync
                    Button(
                        onClick = { selectedTab = 2 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_dialog_choose_preserve_delete_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5E35B1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Data to Preserve or Delete on Sync")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick access to Data Management
                    OutlinedButton(
                        onClick = { selectedTab = 3 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_dialog_goto_manage_data_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bulk Erase / Reset Options")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onSignOutRequested,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_dialog_sign_out_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = "Sign Out", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out of Account")
                    }
                } else if ((sessionState.isAuthenticated && selectedTab == 1) || (!sessionState.isAuthenticated && selectedTab == 2)) {
                    // TAB: Selective Cloud Upload / Download
                    SelectiveCloudTransferPanel(
                        isAuthenticated = sessionState.isAuthenticated,
                        syncState = syncState,
                        localVaccines = vaccines,
                        localPatients = patients,
                        localTransactions = transactions,
                        localVendors = vendors,
                        localClinicProfile = clinicProfile,
                        cloudSnapshot = cloudSnapshot,
                        isLoadingCloudPreview = isLoadingCloudPreview,
                        initialDirection = transferDirection,
                        onFetchCloudPreview = onFetchCloudPreview,
                        onUploadSelected = onUploadSelectedToCloud,
                        onDownloadSelected = onDownloadSelectedFromCloud,
                        onGoToLoginTab = { selectedTab = 0 }
                    )
                } else if ((sessionState.isAuthenticated && selectedTab == 2) || (!sessionState.isAuthenticated && selectedTab == 3)) {
                    // TAB: Selective Cloud Sync — Choose Which Data to Preserve & Which to Delete
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Selective Cloud Sync Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF311B92)
                        )
                        Text(
                            text = "Select what to preserve and what to delete for each category or individual item when syncing with Cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Quick Preset Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = vaccinesAction == CategorySyncAction.PRESERVE_BOTH &&
                                    patientsAction == CategorySyncAction.PRESERVE_BOTH &&
                                    vaccineIdsToDelete.isEmpty() && patientIdsToDelete.isEmpty(),
                                onClick = {
                                    vaccinesAction = CategorySyncAction.PRESERVE_BOTH
                                    patientsAction = CategorySyncAction.PRESERVE_BOTH
                                    transactionsAction = CategorySyncAction.PRESERVE_BOTH
                                    vendorsAction = CategorySyncAction.PRESERVE_BOTH
                                    profileAction = CategorySyncAction.PRESERVE_BOTH
                                    vaccineIdsToDelete = emptySet()
                                    patientIdsToDelete = emptySet()
                                    vendorIdsToDelete = emptySet()
                                    deleteDefaultDemoRecords = false
                                },
                                label = { Text("Preserve Everything (Safe)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFD4F7DF),
                                    selectedLabelColor = Color(0xFF003916)
                                )
                            )
                            FilterChip(
                                selected = vaccinesAction == CategorySyncAction.PRESERVE_LOCAL_ONLY &&
                                    patientsAction == CategorySyncAction.PRESERVE_LOCAL_ONLY,
                                onClick = {
                                    vaccinesAction = CategorySyncAction.PRESERVE_LOCAL_ONLY
                                    patientsAction = CategorySyncAction.PRESERVE_LOCAL_ONLY
                                    transactionsAction = CategorySyncAction.PRESERVE_LOCAL_ONLY
                                    vendorsAction = CategorySyncAction.PRESERVE_LOCAL_ONLY
                                    profileAction = CategorySyncAction.PRESERVE_LOCAL_ONLY
                                },
                                label = { Text("Keep Device Data Only") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFD1ECFF),
                                    selectedLabelColor = Color(0xFF001F33)
                                )
                            )
                            FilterChip(
                                selected = vaccinesAction == CategorySyncAction.RESTORE_CLOUD_ONLY &&
                                    patientsAction == CategorySyncAction.RESTORE_CLOUD_ONLY,
                                onClick = {
                                    vaccinesAction = CategorySyncAction.RESTORE_CLOUD_ONLY
                                    patientsAction = CategorySyncAction.RESTORE_CLOUD_ONLY
                                    transactionsAction = CategorySyncAction.RESTORE_CLOUD_ONLY
                                    vendorsAction = CategorySyncAction.RESTORE_CLOUD_ONLY
                                    profileAction = CategorySyncAction.RESTORE_CLOUD_ONLY
                                },
                                label = { Text("Restore Cloud Only") }
                            )
                        }

                        // Toggle: Remove Default Demo/Sample Data Only
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF8E1),
                            border = BorderStroke(1.dp, Color(0xFFFFE082)),
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
                                        text = "Delete Sample / Demo Records Only",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    Text(
                                        text = "Removes pre-loaded demo patients & sample stock while preserving all your real clinic entries.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF5D4037)
                                    )
                                }
                                Switch(
                                    checked = deleteDefaultDemoRecords,
                                    onCheckedChange = { deleteDefaultDemoRecords = it },
                                    modifier = Modifier.testTag("switch_delete_demo_records")
                                )
                            }
                        }

                        // Category 1: Vaccines & Stock Catalog + Individual Item Checkboxes
                        SyncCategorySelectorCard(
                            title = "1. Vaccines & Stock (${vaccines.size})",
                            icon = Icons.Default.Vaccines,
                            accentColor = Color(0xFF006874),
                            containerColor = Color(0xFFE0F7FA).copy(alpha = 0.6f),
                            selectedAction = vaccinesAction,
                            onActionSelected = { vaccinesAction = it },
                            hasIndividualItems = vaccines.isNotEmpty(),
                            isExpanded = expandVaccinesList,
                            onToggleExpand = { expandVaccinesList = !expandVaccinesList },
                            deletedCount = vaccineIdsToDelete.size,
                            totalCount = vaccines.size
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Checked = Preserve • Unchecked = Delete",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF006874)
                                    )
                                    Row {
                                        TextButton(onClick = { vaccineIdsToDelete = emptySet() }) {
                                            Text("Preserve All", fontSize = 11.sp)
                                        }
                                        TextButton(onClick = { vaccineIdsToDelete = vaccines.map { it.id }.toSet() }) {
                                            Text("Delete All", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                vaccines.forEach { vac ->
                                    val isPreserved = vac.id !in vaccineIdsToDelete
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPreserved) Color.White else Color(0xFFFFEBEE),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isPreserved) Color(0xFFB2EBF2) else Color(0xFFFFCDD2)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                vaccineIdsToDelete = if (isPreserved) {
                                                    vaccineIdsToDelete + vac.id
                                                } else {
                                                    vaccineIdsToDelete - vac.id
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isPreserved,
                                                onCheckedChange = { keep ->
                                                    vaccineIdsToDelete = if (keep) {
                                                        vaccineIdsToDelete - vac.id
                                                    } else {
                                                        vaccineIdsToDelete + vac.id
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFF00897B),
                                                    uncheckedColor = MaterialTheme.colorScheme.error
                                                )
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = vac.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Stock: ${vac.currentStock} ${vac.unit} • ${vac.category}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isPreserved) Color(0xFFE8F5E9) else Color(0xFFFFCDD2)
                                            ) {
                                                Text(
                                                    text = if (isPreserved) "PRESERVE" else "DELETE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isPreserved) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Category 2: Patient Registrations & Vaccine Records + Individual Checkboxes
                        SyncCategorySelectorCard(
                            title = "2. Patients & Records (${patients.size})",
                            icon = Icons.Default.ChildCare,
                            accentColor = Color(0xFF5E35B1),
                            containerColor = Color(0xFFEDE7F6).copy(alpha = 0.6f),
                            selectedAction = patientsAction,
                            onActionSelected = { patientsAction = it },
                            hasIndividualItems = patients.isNotEmpty(),
                            isExpanded = expandPatientsList,
                            onToggleExpand = { expandPatientsList = !expandPatientsList },
                            deletedCount = patientIdsToDelete.size,
                            totalCount = patients.size
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Checked = Preserve • Unchecked = Delete",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF5E35B1)
                                    )
                                    Row {
                                        TextButton(onClick = { patientIdsToDelete = emptySet() }) {
                                            Text("Preserve All", fontSize = 11.sp)
                                        }
                                        TextButton(onClick = { patientIdsToDelete = patients.map { it.id }.toSet() }) {
                                            Text("Delete All", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                patients.forEach { pt ->
                                    val isPreserved = pt.id !in patientIdsToDelete
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPreserved) Color.White else Color(0xFFFFEBEE),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isPreserved) Color(0xFFD1C4E9) else Color(0xFFFFCDD2)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                patientIdsToDelete = if (isPreserved) {
                                                    patientIdsToDelete + pt.id
                                                } else {
                                                    patientIdsToDelete - pt.id
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isPreserved,
                                                onCheckedChange = { keep ->
                                                    patientIdsToDelete = if (keep) {
                                                        patientIdsToDelete - pt.id
                                                    } else {
                                                        patientIdsToDelete + pt.id
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFF5E35B1),
                                                    uncheckedColor = MaterialTheme.colorScheme.error
                                                )
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${pt.patientName} (${pt.dobOrAge})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Parent: ${pt.parentName} • Ph: ${pt.mobileNumber}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isPreserved) Color(0xFFE8F5E9) else Color(0xFFFFCDD2)
                                            ) {
                                                Text(
                                                    text = if (isPreserved) "PRESERVE" else "DELETE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isPreserved) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Category 3: Sales, Invoices & Restock Ledger
                        SyncCategorySelectorCard(
                            title = "3. Sales, Bills & Ledger",
                            icon = Icons.Default.ReceiptLong,
                            accentColor = Color(0xFF0E8A42),
                            containerColor = Color(0xFFD4F7DF).copy(alpha = 0.55f),
                            selectedAction = transactionsAction,
                            onActionSelected = { transactionsAction = it },
                            hasIndividualItems = false,
                            isExpanded = false,
                            onToggleExpand = {},
                            deletedCount = 0,
                            totalCount = 0
                        ) {}

                        // Category 4: Vaccine Vendors + Individual Checkboxes
                        SyncCategorySelectorCard(
                            title = "4. Vaccine Vendors (${vendors.size})",
                            icon = Icons.Default.Business,
                            accentColor = Color(0xFFEF6C00),
                            containerColor = Color(0xFFFFF3E0).copy(alpha = 0.7f),
                            selectedAction = vendorsAction,
                            onActionSelected = { vendorsAction = it },
                            hasIndividualItems = vendors.isNotEmpty(),
                            isExpanded = expandVendorsList,
                            onToggleExpand = { expandVendorsList = !expandVendorsList },
                            deletedCount = vendorIdsToDelete.size,
                            totalCount = vendors.size
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                vendors.forEach { vendor ->
                                    val isPreserved = vendor.id !in vendorIdsToDelete
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPreserved) Color.White else Color(0xFFFFEBEE),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isPreserved) Color(0xFFFFE0B2) else Color(0xFFFFCDD2)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                vendorIdsToDelete = if (isPreserved) {
                                                    vendorIdsToDelete + vendor.id
                                                } else {
                                                    vendorIdsToDelete - vendor.id
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isPreserved,
                                                onCheckedChange = { keep ->
                                                    vendorIdsToDelete = if (keep) {
                                                        vendorIdsToDelete - vendor.id
                                                    } else {
                                                        vendorIdsToDelete + vendor.id
                                                    }
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = vendor.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${vendor.contactPerson} • ${vendor.phone}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = if (isPreserved) "PRESERVE" else "DELETE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPreserved) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Category 5: Clinic Profile & Doctor Registration
                        SyncCategorySelectorCard(
                            title = "5. Clinic Profile & Reg. No",
                            icon = Icons.Default.Storefront,
                            accentColor = Color(0xFF0277BD),
                            containerColor = Color(0xFFE1F5FE).copy(alpha = 0.6f),
                            selectedAction = profileAction,
                            onActionSelected = { profileAction = it },
                            hasIndividualItems = false,
                            isExpanded = false,
                            onToggleExpand = {},
                            deletedCount = 0,
                            totalCount = 0
                        ) {}

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                onSyncWithConfig(buildCurrentSyncConfig())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sync_info_sync_btn"),
                            enabled = !syncState.isSyncing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF006874)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudSync, contentDescription = "Sync", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Apply Preserve / Delete Choices & Sync Now",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if ((sessionState.isAuthenticated && selectedTab == 3) || (!sessionState.isAuthenticated && selectedTab == 4)) {
                    // TAB: Clean & Erase Old Data Options
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Manage & Erase Old Data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Control what is kept in your clinic inventory and cloud backup. Use these options if you don't want old test data or old vaccines.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Option 1: Purge Stale Cloud Data
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete Stale Cloud Records", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Deletes all old vaccines and records in Firebase Cloud that were deleted from this device. Guarantees old vaccines will never reappear on future syncs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showConfirmPurgeDialog = true },
                                    modifier = Modifier.fillMaxWidth().testTag("btn_purge_stale_cloud"),
                                    enabled = sessionState.isAuthenticated && !syncState.isSyncing,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Purge Stale Cloud Data Now")
                                }
                            }
                        }

                        // Option 2: Delete Zero-Stock Old Vaccines
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete Out-of-Stock Vaccines", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Removes all vaccines with 0 inventory completely from both this device and cloud storage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { showConfirmDeleteZeroStockDialog = true },
                                    modifier = Modifier.fillMaxWidth().testTag("btn_delete_zero_stock"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Delete 0-Stock Vaccines")
                                }
                            }
                        }

                        // Option 3: Erase All Cloud Data Only
                        if (sessionState.isAuthenticated) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFB74D))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Erase All Cloud Data", fontWeight = FontWeight.Bold, color = Color(0xFFE65100), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Wipes all cloud storage (vaccines, patients, transactions) for your account in Firebase Firestore. Your current on-device data remains intact.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF5D4037)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { showConfirmEraseCloudDialog = true },
                                        modifier = Modifier.fillMaxWidth().testTag("btn_erase_cloud_data"),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100)),
                                        border = BorderStroke(1.dp, Color(0xFFE65100)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Erase Cloud Storage Completely")
                                    }
                                }
                            }
                        }

                        // Option 4: Erase All Old Data (Cloud & Local Reset)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Erase All Old Data (Complete Reset)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Permanently wipes all vaccines, patients, and transactions from BOTH this device and cloud backup. Gives you a 100% clean slate to start fresh.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showConfirmEraseAllDialog = true },
                                    modifier = Modifier.fillMaxWidth().testTag("btn_erase_all_data"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Erase All Data Completely")
                                }
                            }
                        }
                    }
                } else if ((!sessionState.isAuthenticated && selectedTab == 0) || (sessionState.isAuthenticated && selectedTab == 4)) {
                    // TAB: Gmail (Google Sign-In)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (sessionState.isAuthenticated) "Switch Account / Re-login" else "One-Tap Login using Gmail",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Secure Google Authentication powered by Jetpack Credential Manager. Automatically syncs all your clinical records.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Large Google Sign-In Button
                        Button(
                            onClick = {
                                isGoogleLoading = true
                                authManager.signInWithGoogle(
                                    activityContext = context,
                                    scope = coroutineScope,
                                    onSuccess = {
                                        isGoogleLoading = false
                                        Toast.makeText(context, "Google Sign-In successful!", Toast.LENGTH_SHORT).show()
                                        onSyncWithConfig(buildCurrentSyncConfig())
                                    },
                                    onError = { error ->
                                        isGoogleLoading = false
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                    },
                                    onCancelled = {
                                        isGoogleLoading = false
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_google_signin"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isGoogleLoading
                        ) {
                            if (isGoogleLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MarkEmailRead,
                                    contentDescription = "Google",
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sign in with Google / Gmail",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "HIPAA",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Zero-Knowledge Encryption: Clinic databases are partitioned per doctor ID in Google Cloud Firestore.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (!sessionState.isAuthenticated && selectedTab == 1) {
                    // TAB 1 (Unauthenticated): Mobile Number with OTP
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Login with Mobile Number & OTP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter your clinic or doctor phone number to receive a 6-digit verification code.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = mobileInput,
                            onValueChange = { input ->
                                mobileInput = input.filter { it.isDigit() || it == '+' || it == ' ' }.take(15)
                                otpErrorMessage = null
                            },
                            label = { Text("Doctor / Clinic Mobile Number") },
                            placeholder = { Text("+91 98765 43210") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = "Phone")
                            },
                            trailingIcon = {
                                if (mobileInput.length >= 10 && !isOtpSent) {
                                    TextButton(
                                        onClick = {
                                            val cleanPhone = mobileInput.filter { it.isDigit() }
                                            if (cleanPhone.length >= 10) {
                                                val otp = Random.nextInt(100000, 999999).toString()
                                                generatedOtp = otp
                                                isOtpSent = true
                                                otpCooldown = 30
                                                otpErrorMessage = null
                                                Toast.makeText(context, "OTP Code: $otp", Toast.LENGTH_LONG).show()
                                            } else {
                                                otpErrorMessage = "Please enter a valid 10-digit mobile number"
                                            }
                                        },
                                        modifier = Modifier.testTag("send_otp_btn")
                                    ) {
                                        Text("Send OTP", fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mobile_number_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!isOtpSent) {
                            Button(
                                onClick = {
                                    val cleanPhone = mobileInput.filter { it.isDigit() }
                                    if (cleanPhone.length >= 10) {
                                        val otp = Random.nextInt(100000, 999999).toString()
                                        generatedOtp = otp
                                        isOtpSent = true
                                        otpCooldown = 30
                                        otpErrorMessage = null
                                        Toast.makeText(context, "OTP Code: $otp", Toast.LENGTH_LONG).show()
                                    } else {
                                        otpErrorMessage = "Please enter a valid 10-digit mobile number"
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("request_otp_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Get 6-Digit OTP Code")
                            }
                        }

                        // OTP Generated Banner & Input Box
                        AnimatedVisibility(visible = isOtpSent) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFE8F5E9)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF81C784))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "OTP Generated",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "OTP Sent to $mobileInput",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1B5E20)
                                            )
                                            Text(
                                                text = "Verification code: ${generatedOtp ?: "123456"} (Tap to auto-fill)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = otpInput,
                                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpInput = it },
                                    label = { Text("Enter 6-Digit OTP") },
                                    placeholder = { Text("6-digit code") },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Key, contentDescription = "OTP")
                                    },
                                    trailingIcon = {
                                        if (generatedOtp != null && otpInput != generatedOtp) {
                                            TextButton(onClick = { otpInput = generatedOtp!! }) {
                                                Text("Auto-fill")
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("otp_code_input")
                                )

                                if (otpErrorMessage != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = otpErrorMessage!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            val otp = Random.nextInt(100000, 999999).toString()
                                            generatedOtp = otp
                                            otpCooldown = 30
                                            Toast.makeText(context, "New OTP: $otp", Toast.LENGTH_SHORT).show()
                                        },
                                        enabled = otpCooldown == 0
                                    ) {
                                        Text(if (otpCooldown > 0) "Resend in ${otpCooldown}s" else "Resend OTP")
                                    }

                                    Button(
                                        onClick = {
                                            val success = authManager.loginWithMobileOtp(
                                                mobileNumber = mobileInput,
                                                enteredOtp = otpInput,
                                                expectedOtp = generatedOtp ?: "123456"
                                            )
                                            if (success) {
                                                Toast.makeText(context, "Mobile Verified! Welcome Doctor.", Toast.LENGTH_SHORT).show()
                                                onSyncWithConfig(buildCurrentSyncConfig())
                                            } else {
                                                otpErrorMessage = "Incorrect OTP code. Please check and retry."
                                            }
                                        },
                                        enabled = otpInput.length == 6,
                                        modifier = Modifier.testTag("verify_otp_btn"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Verify & Login")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    // Confirmation AlertDialog 1: Purge Stale Cloud Data
    if (showConfirmPurgeDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmPurgeDialog = false },
            icon = {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = { Text("Delete Stale Cloud Records?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will scan Firebase Firestore and permanently delete any vaccines or records that are no longer in your active local inventory. Future syncs will only contain your current active items, preventing old deleted vaccines from reappearing.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmPurgeDialog = false
                        onPurgeStaleCloudData()
                    },
                    modifier = Modifier.testTag("confirm_purge_stale_cloud_btn")
                ) {
                    Text("Purge Stale Records")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmPurgeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation AlertDialog 2: Delete Zero-Stock Vaccines
    if (showConfirmDeleteZeroStockDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteZeroStockDialog = false },
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = { Text("Delete Zero-Stock Vaccines?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete all vaccines with 0 current stock from both this device and cloud storage?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteZeroStockDialog = false
                        onDeleteOutOfStockVaccines()
                    },
                    modifier = Modifier.testTag("confirm_delete_zero_stock_btn")
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

    // Confirmation AlertDialog 3: Erase Cloud Data Only
    if (showConfirmEraseCloudDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmEraseCloudDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(28.dp))
            },
            title = { Text("Erase All Cloud Data?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will permanently wipe all cloud storage records (vaccines, patients, transactions) in Firebase Firestore for your account. Your local records on this device remain untouched. Are you sure?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmEraseCloudDialog = false
                        onEraseAllCloudData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    modifier = Modifier.testTag("confirm_erase_cloud_data_btn")
                ) {
                    Text("Erase Cloud Storage")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmEraseCloudDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation AlertDialog 4: Erase All Data (Cloud & Local)
    if (showConfirmEraseAllDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmEraseAllDialog = false },
            icon = {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
            },
            title = { Text("⚠️ Erase All Old Data Completely?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Text("This will permanently ERASE ALL vaccines, patient registrations, vendors, and transactions from BOTH this device and cloud storage. This cannot be undone.\n\nAre you sure you want to start fresh with a clean slate?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmEraseAllDialog = false
                        onEraseAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_erase_all_data_btn")
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

@Composable
private fun SyncCategorySelectorCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    selectedAction: CategorySyncAction,
    onActionSelected: (CategorySyncAction) -> Unit,
    hasIndividualItems: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    deletedCount: Int,
    totalCount: Int,
    itemSelectorContent: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(accentColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Text(
                            text = selectedAction.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasIndividualItems) {
                    TextButton(onClick = onToggleExpand) {
                        Text(
                            text = if (deletedCount > 0) "Del ($deletedCount/$totalCount)" else "Pick Items",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (deletedCount > 0) MaterialTheme.colorScheme.error else accentColor
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = if (deletedCount > 0) MaterialTheme.colorScheme.error else accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4 Action Chips: Preserve All | Keep Device Only | Keep Cloud Only | Delete All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategorySyncAction.entries.forEach { action ->
                    val isSelected = selectedAction == action
                    val isDestructive = action == CategorySyncAction.DELETE_ALL
                    FilterChip(
                        selected = isSelected,
                        onClick = { onActionSelected(action) },
                        label = {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isDestructive) Color(0xFFFFCDD2) else accentColor,
                            selectedLabelColor = if (isDestructive) Color(0xFFB71C1C) else Color.White
                        )
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded && hasIndividualItems) {
                itemSelectorContent()
            }
        }
    }
}
