package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.StockTransaction
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.data.repository.CloudDataSnapshot
import com.example.data.repository.SelectiveCloudTransferRequest
import com.example.data.repository.SyncStatusState
import com.example.ui.util.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CloudTransferDirection {
    UPLOAD,
    DOWNLOAD
}

@Composable
fun SelectiveCloudTransferPanel(
    isAuthenticated: Boolean,
    syncState: SyncStatusState,
    localVaccines: List<Vaccine>,
    localPatients: List<PatientRegistration>,
    localTransactions: List<StockTransaction>,
    localVendors: List<Vendor>,
    localClinicProfile: ClinicProfile,
    cloudSnapshot: CloudDataSnapshot?,
    isLoadingCloudPreview: Boolean,
    initialDirection: CloudTransferDirection = CloudTransferDirection.UPLOAD,
    onFetchCloudPreview: () -> Unit,
    onUploadSelected: (SelectiveCloudTransferRequest) -> Unit,
    onDownloadSelected: (SelectiveCloudTransferRequest) -> Unit,
    onGoToLoginTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var direction by remember(initialDirection) { mutableStateOf(initialDirection) }

    // Upload selection states (default: all local items selected)
    var upIncludeVaccines by remember { mutableStateOf(true) }
    var upIncludePatients by remember { mutableStateOf(true) }
    var upIncludeTransactions by remember { mutableStateOf(true) }
    var upIncludeVendors by remember { mutableStateOf(true) }
    var upIncludeProfile by remember { mutableStateOf(true) }
    var upReplaceOnCloud by remember { mutableStateOf(false) }

    var upSelectedVaccineIds by remember(localVaccines) { mutableStateOf(localVaccines.map { it.id }.toSet()) }
    var upSelectedPatientIds by remember(localPatients) { mutableStateOf(localPatients.map { it.id }.toSet()) }
    var upSelectedTxIds by remember(localTransactions) { mutableStateOf(localTransactions.map { it.id }.toSet()) }
    var upSelectedVendorIds by remember(localVendors) { mutableStateOf(localVendors.map { it.id }.toSet()) }

    // Download selection states (initialized when cloudSnapshot arrives)
    var downIncludeVaccines by remember { mutableStateOf(true) }
    var downIncludePatients by remember { mutableStateOf(true) }
    var downIncludeTransactions by remember { mutableStateOf(true) }
    var downIncludeVendors by remember { mutableStateOf(true) }
    var downIncludeProfile by remember { mutableStateOf(true) }
    var downReplaceOnDevice by remember { mutableStateOf(false) }

    var downSelectedVaccineIds by remember(cloudSnapshot) {
        mutableStateOf(cloudSnapshot?.vaccines?.map { it.id }?.toSet() ?: emptySet())
    }
    var downSelectedPatientIds by remember(cloudSnapshot) {
        mutableStateOf(cloudSnapshot?.patients?.map { it.id }?.toSet() ?: emptySet())
    }
    var downSelectedTxIds by remember(cloudSnapshot) {
        mutableStateOf(cloudSnapshot?.transactions?.map { it.id }?.toSet() ?: emptySet())
    }
    var downSelectedVendorIds by remember(cloudSnapshot) {
        mutableStateOf(cloudSnapshot?.vendors?.map { it.id }?.toSet() ?: emptySet())
    }

    // Expandable sections
    var expandVaccines by remember { mutableStateOf(false) }
    var expandPatients by remember { mutableStateOf(false) }
    var expandTransactions by remember { mutableStateOf(false) }
    var expandVendors by remember { mutableStateOf(false) }

    // Automatically fetch live Cloud data preview when user switches to Download mode
    LaunchedEffect(direction, isAuthenticated) {
        if (direction == CloudTransferDirection.DOWNLOAD && isAuthenticated && cloudSnapshot == null && !isLoadingCloudPreview) {
            onFetchCloudPreview()
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("selective_cloud_transfer_panel"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Direction Selector Header (Upload vs Download)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransferDirectionButton(
                title = tr("Upload to Cloud", "क्लाउड पर अपलोड"),
                subtitle = tr("Device → Cloud", "फ़ोन → क्लाउड"),
                icon = Icons.Default.CloudUpload,
                selected = direction == CloudTransferDirection.UPLOAD,
                accentColor = Color(0xFF006874),
                selectedBg = Color(0xFFE0F7FA),
                onClick = { direction = CloudTransferDirection.UPLOAD },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_mode_selective_upload")
            )
            TransferDirectionButton(
                title = tr("Download Data", "क्लाउड से डाउनलोड"),
                subtitle = tr("Cloud → Device", "क्लाउड → फ़ोन"),
                icon = Icons.Default.CloudDownload,
                selected = direction == CloudTransferDirection.DOWNLOAD,
                accentColor = Color(0xFF1565C0),
                selectedBg = Color(0xFFE3F2FD),
                onClick = { direction = CloudTransferDirection.DOWNLOAD },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_mode_selective_download")
            )
        }

        if (!isAuthenticated) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                border = BorderStroke(1.dp, Color(0xFFFFE082)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("Sign In Required for Cloud Transfer", "क्लाउड अपलोड/डाउनलोड के लिए लॉगिन ज़रूरी है"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                    Text(
                        text = tr(
                            "Please sign in with Google/Gmail or Mobile OTP first to upload local clinic records or download cloud backups.",
                            "अपना डेटा क्लाउड पर अपलोड या डाउनलोड करने के लिए पहले Google या मोबाइल OTP से लॉगिन करें।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5D4037)
                    )
                    Button(
                        onClick = onGoToLoginTab,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006874)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_transfer_goto_login")
                    ) {
                        Text(tr("Go to Login", "लॉगिन स्क्रीन पर जाएं"), fontWeight = FontWeight.Bold)
                    }
                }
            }
            return
        }

        if (direction == CloudTransferDirection.UPLOAD) {
            // ================= UPLOAD MODE (DEVICE -> CLOUD) =================
            val selectedVaccinesCount = if (upIncludeVaccines) upSelectedVaccineIds.size else 0
            val selectedPatientsCount = if (upIncludePatients) upSelectedPatientIds.size else 0
            val selectedTxCount = if (upIncludeTransactions) upSelectedTxIds.size else 0
            val selectedVendorsCount = if (upIncludeVendors) upSelectedVendorIds.size else 0
            val selectedProfileCount = if (upIncludeProfile) 1 else 0
            val totalSelectedToUpload = selectedVaccinesCount + selectedPatientsCount + selectedTxCount + selectedVendorsCount + selectedProfileCount

            // Quick Select All / Clear All bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("Select Device Data to Upload:", "अपलोड करने के लिए डेटा चुनें:"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF004F58)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            upIncludeVaccines = true
                            upIncludePatients = true
                            upIncludeTransactions = true
                            upIncludeVendors = true
                            upIncludeProfile = true
                            upSelectedVaccineIds = localVaccines.map { it.id }.toSet()
                            upSelectedPatientIds = localPatients.map { it.id }.toSet()
                            upSelectedTxIds = localTransactions.map { it.id }.toSet()
                            upSelectedVendorIds = localVendors.map { it.id }.toSet()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(tr("Select All", "सभी चुनें"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            upIncludeVaccines = false
                            upIncludePatients = false
                            upIncludeTransactions = false
                            upIncludeVendors = false
                            upIncludeProfile = false
                            upSelectedVaccineIds = emptySet()
                            upSelectedPatientIds = emptySet()
                            upSelectedTxIds = emptySet()
                            upSelectedVendorIds = emptySet()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(tr("Clear", "हटाएं"), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 1. Vaccines
            SelectiveTransferCategoryCard(
                title = tr("Vaccines & Stock", "वैक्सीन और स्टॉक"),
                icon = Icons.Default.Vaccines,
                accentColor = Color(0xFF006874),
                selectedCount = selectedVaccinesCount,
                totalCount = localVaccines.size,
                isChecked = upIncludeVaccines,
                onCheckedChange = { checked ->
                    upIncludeVaccines = checked
                    if (checked && upSelectedVaccineIds.isEmpty()) {
                        upSelectedVaccineIds = localVaccines.map { it.id }.toSet()
                    }
                },
                isExpanded = expandVaccines,
                onToggleExpand = { expandVaccines = !expandVaccines },
                hasItems = localVaccines.isNotEmpty()
            ) {
                ItemSelectionControlsRow(
                    onSelectAll = {
                        upIncludeVaccines = true
                        upSelectedVaccineIds = localVaccines.map { it.id }.toSet()
                    },
                    onClearAll = { upSelectedVaccineIds = emptySet() }
                )
                localVaccines.forEach { vac ->
                    val checked = upIncludeVaccines && vac.id in upSelectedVaccineIds
                    SelectableItemRow(
                        title = vac.name,
                        subtitle = "${tr("Stock", "स्टॉक")}: ${vac.currentStock} ${vac.unit} • ₹${vac.sellingPrice.toInt()}",
                        badgeText = vac.category,
                        badgeColor = Color(0xFFE0F7FA),
                        badgeTextColor = Color(0xFF006064),
                        checked = checked,
                        accentColor = Color(0xFF006874),
                        onToggle = {
                            upIncludeVaccines = true
                            upSelectedVaccineIds = if (checked) upSelectedVaccineIds - vac.id else upSelectedVaccineIds + vac.id
                            if (upSelectedVaccineIds.isEmpty()) upIncludeVaccines = false
                        }
                    )
                }
            }

            // 2. Patients
            SelectiveTransferCategoryCard(
                title = tr("Patients & Records", "मरीज़ और टीका रिकॉर्ड"),
                icon = Icons.Default.ChildCare,
                accentColor = Color(0xFF5E35B1),
                selectedCount = selectedPatientsCount,
                totalCount = localPatients.size,
                isChecked = upIncludePatients,
                onCheckedChange = { checked ->
                    upIncludePatients = checked
                    if (checked && upSelectedPatientIds.isEmpty()) {
                        upSelectedPatientIds = localPatients.map { it.id }.toSet()
                    }
                },
                isExpanded = expandPatients,
                onToggleExpand = { expandPatients = !expandPatients },
                hasItems = localPatients.isNotEmpty()
            ) {
                ItemSelectionControlsRow(
                    onSelectAll = {
                        upIncludePatients = true
                        upSelectedPatientIds = localPatients.map { it.id }.toSet()
                    },
                    onClearAll = { upSelectedPatientIds = emptySet() }
                )
                localPatients.forEach { pt ->
                    val checked = upIncludePatients && pt.id in upSelectedPatientIds
                    SelectableItemRow(
                        title = "${pt.patientName} (${pt.dobOrAge})",
                        subtitle = "${tr("Parent", "अभिभावक")}: ${pt.parentName.ifBlank { "—" }} • ${pt.mobileNumber}",
                        badgeText = pt.nextPlannedVaccine.ifBlank { tr("Record", "रिकॉर्ड") }.take(12),
                        badgeColor = Color(0xFFEDE7F6),
                        badgeTextColor = Color(0xFF4527A0),
                        checked = checked,
                        accentColor = Color(0xFF5E35B1),
                        onToggle = {
                            upIncludePatients = true
                            upSelectedPatientIds = if (checked) upSelectedPatientIds - pt.id else upSelectedPatientIds + pt.id
                            if (upSelectedPatientIds.isEmpty()) upIncludePatients = false
                        }
                    )
                }
            }

            // 3. Sales, Bills & Stock Ledger
            SelectiveTransferCategoryCard(
                title = tr("Sales, Bills & Ledger", "बिक्री, बिल और लेजर"),
                icon = Icons.Default.ReceiptLong,
                accentColor = Color(0xFF0E8A42),
                selectedCount = selectedTxCount,
                totalCount = localTransactions.size,
                isChecked = upIncludeTransactions,
                onCheckedChange = { checked ->
                    upIncludeTransactions = checked
                    if (checked && upSelectedTxIds.isEmpty()) {
                        upSelectedTxIds = localTransactions.map { it.id }.toSet()
                    }
                },
                isExpanded = expandTransactions,
                onToggleExpand = { expandTransactions = !expandTransactions },
                hasItems = localTransactions.isNotEmpty()
            ) {
                ItemSelectionControlsRow(
                    onSelectAll = {
                        upIncludeTransactions = true
                        upSelectedTxIds = localTransactions.map { it.id }.toSet()
                    },
                    onClearAll = { upSelectedTxIds = emptySet() }
                )
                localTransactions.take(40).forEach { tx ->
                    val checked = upIncludeTransactions && tx.id in upSelectedTxIds
                    val dateStr = dateFormatter.format(Date(tx.timestamp))
                    SelectableItemRow(
                        title = "${tx.vaccineName} (${tx.quantity} qty)",
                        subtitle = "${tx.referenceOrCustomer.ifBlank { tx.type }} • $dateStr",
                        badgeText = "₹${tx.totalAmount.toInt()}",
                        badgeColor = Color(0xFFE8F5E9),
                        badgeTextColor = Color(0xFF1B5E20),
                        checked = checked,
                        accentColor = Color(0xFF0E8A42),
                        onToggle = {
                            upIncludeTransactions = true
                            upSelectedTxIds = if (checked) upSelectedTxIds - tx.id else upSelectedTxIds + tx.id
                            if (upSelectedTxIds.isEmpty()) upIncludeTransactions = false
                        }
                    )
                }
                if (localTransactions.size > 40) {
                    Text(
                        text = tr("Showing latest 40 of ${localTransactions.size} transactions (Select All includes all).", "कुल ${localTransactions.size} में से नवीनतम 40 दिख रहे हैं।"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 4. Vendors
            SelectiveTransferCategoryCard(
                title = tr("Vaccine Vendors", "वैक्सीन सप्लायर (Vendors)"),
                icon = Icons.Default.Business,
                accentColor = Color(0xFFEF6C00),
                selectedCount = selectedVendorsCount,
                totalCount = localVendors.size,
                isChecked = upIncludeVendors,
                onCheckedChange = { checked ->
                    upIncludeVendors = checked
                    if (checked && upSelectedVendorIds.isEmpty()) {
                        upSelectedVendorIds = localVendors.map { it.id }.toSet()
                    }
                },
                isExpanded = expandVendors,
                onToggleExpand = { expandVendors = !expandVendors },
                hasItems = localVendors.isNotEmpty()
            ) {
                ItemSelectionControlsRow(
                    onSelectAll = {
                        upIncludeVendors = true
                        upSelectedVendorIds = localVendors.map { it.id }.toSet()
                    },
                    onClearAll = { upSelectedVendorIds = emptySet() }
                )
                localVendors.forEach { vendor ->
                    val checked = upIncludeVendors && vendor.id in upSelectedVendorIds
                    SelectableItemRow(
                        title = vendor.name,
                        subtitle = "${vendor.contactPerson.ifBlank { "Supplier" }} • ${vendor.phone}",
                        badgeText = "Vendor",
                        badgeColor = Color(0xFFFFF3E0),
                        badgeTextColor = Color(0xFFE65100),
                        checked = checked,
                        accentColor = Color(0xFFEF6C00),
                        onToggle = {
                            upIncludeVendors = true
                            upSelectedVendorIds = if (checked) upSelectedVendorIds - vendor.id else upSelectedVendorIds + vendor.id
                            if (upSelectedVendorIds.isEmpty()) upIncludeVendors = false
                        }
                    )
                }
            }

            // 5. Clinic Profile
            ClinicProfileSelectionRow(
                title = tr("Clinic Profile & Reg. No.", "क्लिनिक प्रोफ़ाइल और रजि. नंबर"),
                subtitle = "${localClinicProfile.clinicName} • ${localClinicProfile.doctorName}",
                checked = upIncludeProfile,
                accentColor = Color(0xFF0277BD),
                onCheckedChange = { upIncludeProfile = it }
            )

            // Safe Mode Toggle
            ReplaceBehaviorToggleCard(
                title = tr("Overwrite unselected cloud items in chosen categories", "चुनी गई श्रेणियों का पुराना क्लाउड डेटा बदलें (Overwrite)"),
                subtitle = if (upReplaceOnCloud) {
                    tr("Cloud items not selected above will be removed for checked categories.", "जो आइटम चुने नहीं गए हैं, वे क्लाउड से हट जाएंगे।")
                } else {
                    tr("Safe Merge (Recommended): Only uploads checked items; keeps other cloud data untouched.", "सुरक्षित मर्ज (अनुशंसित): केवल चुने हुए आइटम अपलोड होंगे; बाकी क्लाउड डेटा सुरक्षित रहेगा।")
                },
                checked = upReplaceOnCloud,
                onCheckedChange = { upReplaceOnCloud = it }
            )

            // Upload Action Button
            Button(
                onClick = {
                    onUploadSelected(
                        SelectiveCloudTransferRequest(
                            includeVaccines = upIncludeVaccines && upSelectedVaccineIds.isNotEmpty(),
                            selectedVaccineIds = if (upSelectedVaccineIds.size == localVaccines.size) null else upSelectedVaccineIds,
                            includePatients = upIncludePatients && upSelectedPatientIds.isNotEmpty(),
                            selectedPatientIds = if (upSelectedPatientIds.size == localPatients.size) null else upSelectedPatientIds,
                            includeTransactions = upIncludeTransactions && upSelectedTxIds.isNotEmpty(),
                            selectedTransactionIds = if (upSelectedTxIds.size == localTransactions.size) null else upSelectedTxIds,
                            includeVendors = upIncludeVendors && upSelectedVendorIds.isNotEmpty(),
                            selectedVendorIds = if (upSelectedVendorIds.size == localVendors.size) null else upSelectedVendorIds,
                            includeClinicProfile = upIncludeProfile,
                            replaceExistingOnTarget = upReplaceOnCloud
                        )
                    )
                },
                enabled = !syncState.isSyncing && totalSelectedToUpload > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006874)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_selective_upload_cloud")
            ) {
                if (syncState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tr("Uploading to Cloud...", "क्लाउड पर अपलोड हो रहा है..."), fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("Upload Selected to Cloud ($totalSelectedToUpload)", "चुना हुआ डेटा अपलोड करें ($totalSelectedToUpload)"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // ================= DOWNLOAD MODE (CLOUD -> DEVICE) =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Select Cloud Data to Download:", "डाउनलोड करने के लिए क्लाउड डेटा चुनें:"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D47A1)
                    )
                    if (cloudSnapshot != null) {
                        Text(
                            text = tr(
                                "Found ${cloudSnapshot.totalRecords} records in your Cloud backup",
                                "आपके क्लाउड बैकअप में ${cloudSnapshot.totalRecords} रिकॉर्ड मिले"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onFetchCloudPreview,
                    enabled = !isLoadingCloudPreview && !syncState.isSyncing,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_refresh_cloud_preview")
                ) {
                    if (isLoadingCloudPreview) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Refresh", "रिफ्रेश"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isLoadingCloudPreview && cloudSnapshot == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF1565C0))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = tr("Loading your Cloud records preview...", "क्लाउड डेटा लोड हो रहा है..."),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0D47A1)
                        )
                    }
                }
            } else if (cloudSnapshot == null || cloudSnapshot.totalRecords == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = tr("No Cloud Backup Records Found Yet", "अभी क्लाउड में कोई बैकअप डेटा नहीं मिला"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = tr(
                                "Switch to 'Upload to Cloud' to back up your device data first, or tap Refresh to check again.",
                                "पहले 'क्लाउड पर अपलोड' टैब से अपना डेटा अपलोड करें, या दोबारा जांचने के लिए रिफ्रेश दबाएं।"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            } else {
                val localVacIds = remember(localVaccines) { localVaccines.map { it.id }.toSet() }
                val localPatIds = remember(localPatients) { localPatients.map { it.id }.toSet() }
                val localTxIds = remember(localTransactions) { localTransactions.map { it.id }.toSet() }
                val localVenIds = remember(localVendors) { localVendors.map { it.id }.toSet() }

                val downVacCount = if (downIncludeVaccines) downSelectedVaccineIds.size else 0
                val downPatCount = if (downIncludePatients) downSelectedPatientIds.size else 0
                val downTxCount = if (downIncludeTransactions) downSelectedTxIds.size else 0
                val downVenCount = if (downIncludeVendors) downSelectedVendorIds.size else 0
                val downProfCount = if (downIncludeProfile && cloudSnapshot.clinicProfile != null) 1 else 0
                val totalSelectedToDownload = downVacCount + downPatCount + downTxCount + downVenCount + downProfCount

                // Quick Select All / Clear All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            downIncludeVaccines = cloudSnapshot.vaccines.isNotEmpty()
                            downIncludePatients = cloudSnapshot.patients.isNotEmpty()
                            downIncludeTransactions = cloudSnapshot.transactions.isNotEmpty()
                            downIncludeVendors = cloudSnapshot.vendors.isNotEmpty()
                            downIncludeProfile = cloudSnapshot.clinicProfile != null
                            downSelectedVaccineIds = cloudSnapshot.vaccines.map { it.id }.toSet()
                            downSelectedPatientIds = cloudSnapshot.patients.map { it.id }.toSet()
                            downSelectedTxIds = cloudSnapshot.transactions.map { it.id }.toSet()
                            downSelectedVendorIds = cloudSnapshot.vendors.map { it.id }.toSet()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(tr("Select All", "सभी चुनें"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = {
                            downIncludeVaccines = false
                            downIncludePatients = false
                            downIncludeTransactions = false
                            downIncludeVendors = false
                            downIncludeProfile = false
                            downSelectedVaccineIds = emptySet()
                            downSelectedPatientIds = emptySet()
                            downSelectedTxIds = emptySet()
                            downSelectedVendorIds = emptySet()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(tr("Clear", "हटाएं"), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }

                // 1. Cloud Vaccines
                SelectiveTransferCategoryCard(
                    title = tr("Cloud Vaccines", "क्लाउड वैक्सीन"),
                    icon = Icons.Default.Vaccines,
                    accentColor = Color(0xFF006874),
                    selectedCount = downVacCount,
                    totalCount = cloudSnapshot.vaccines.size,
                    isChecked = downIncludeVaccines && cloudSnapshot.vaccines.isNotEmpty(),
                    onCheckedChange = { checked ->
                        downIncludeVaccines = checked
                        if (checked && downSelectedVaccineIds.isEmpty()) {
                            downSelectedVaccineIds = cloudSnapshot.vaccines.map { it.id }.toSet()
                        }
                    },
                    isExpanded = expandVaccines,
                    onToggleExpand = { expandVaccines = !expandVaccines },
                    hasItems = cloudSnapshot.vaccines.isNotEmpty()
                ) {
                    ItemSelectionControlsRow(
                        onSelectAll = {
                            downIncludeVaccines = true
                            downSelectedVaccineIds = cloudSnapshot.vaccines.map { it.id }.toSet()
                        },
                        onClearAll = { downSelectedVaccineIds = emptySet() }
                    )
                    cloudSnapshot.vaccines.forEach { vac ->
                        val checked = downIncludeVaccines && vac.id in downSelectedVaccineIds
                        val isNewOnDevice = vac.id !in localVacIds
                        SelectableItemRow(
                            title = vac.name,
                            subtitle = "${tr("Stock", "स्टॉक")}: ${vac.currentStock} ${vac.unit} • ₹${vac.sellingPrice.toInt()}",
                            badgeText = if (isNewOnDevice) tr("NEW", "नया") else tr("On Device", "फ़ोन में है"),
                            badgeColor = if (isNewOnDevice) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                            badgeTextColor = if (isNewOnDevice) Color(0xFF15803D) else Color(0xFF475569),
                            checked = checked,
                            accentColor = Color(0xFF006874),
                            onToggle = {
                                downIncludeVaccines = true
                                downSelectedVaccineIds = if (checked) downSelectedVaccineIds - vac.id else downSelectedVaccineIds + vac.id
                                if (downSelectedVaccineIds.isEmpty()) downIncludeVaccines = false
                            }
                        )
                    }
                }

                // 2. Cloud Patients
                SelectiveTransferCategoryCard(
                    title = tr("Cloud Patients", "क्लाउड मरीज़ रिकॉर्ड"),
                    icon = Icons.Default.ChildCare,
                    accentColor = Color(0xFF5E35B1),
                    selectedCount = downPatCount,
                    totalCount = cloudSnapshot.patients.size,
                    isChecked = downIncludePatients && cloudSnapshot.patients.isNotEmpty(),
                    onCheckedChange = { checked ->
                        downIncludePatients = checked
                        if (checked && downSelectedPatientIds.isEmpty()) {
                            downSelectedPatientIds = cloudSnapshot.patients.map { it.id }.toSet()
                        }
                    },
                    isExpanded = expandPatients,
                    onToggleExpand = { expandPatients = !expandPatients },
                    hasItems = cloudSnapshot.patients.isNotEmpty()
                ) {
                    ItemSelectionControlsRow(
                        onSelectAll = {
                            downIncludePatients = true
                            downSelectedPatientIds = cloudSnapshot.patients.map { it.id }.toSet()
                        },
                        onClearAll = { downSelectedPatientIds = emptySet() }
                    )
                    cloudSnapshot.patients.forEach { pt ->
                        val checked = downIncludePatients && pt.id in downSelectedPatientIds
                        val isNewOnDevice = pt.id !in localPatIds
                        SelectableItemRow(
                            title = "${pt.patientName} (${pt.dobOrAge})",
                            subtitle = "${tr("Parent", "अभिभावक")}: ${pt.parentName.ifBlank { "—" }} • ${pt.mobileNumber}",
                            badgeText = if (isNewOnDevice) tr("NEW", "नया") else tr("On Device", "फ़ोन में है"),
                            badgeColor = if (isNewOnDevice) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                            badgeTextColor = if (isNewOnDevice) Color(0xFF15803D) else Color(0xFF475569),
                            checked = checked,
                            accentColor = Color(0xFF5E35B1),
                            onToggle = {
                                downIncludePatients = true
                                downSelectedPatientIds = if (checked) downSelectedPatientIds - pt.id else downSelectedPatientIds + pt.id
                                if (downSelectedPatientIds.isEmpty()) downIncludePatients = false
                            }
                        )
                    }
                }

                // 3. Cloud Transactions
                SelectiveTransferCategoryCard(
                    title = tr("Cloud Sales & Ledger", "क्लाउड बिक्री व बिल लेजर"),
                    icon = Icons.Default.ReceiptLong,
                    accentColor = Color(0xFF0E8A42),
                    selectedCount = downTxCount,
                    totalCount = cloudSnapshot.transactions.size,
                    isChecked = downIncludeTransactions && cloudSnapshot.transactions.isNotEmpty(),
                    onCheckedChange = { checked ->
                        downIncludeTransactions = checked
                        if (checked && downSelectedTxIds.isEmpty()) {
                            downSelectedTxIds = cloudSnapshot.transactions.map { it.id }.toSet()
                        }
                    },
                    isExpanded = expandTransactions,
                    onToggleExpand = { expandTransactions = !expandTransactions },
                    hasItems = cloudSnapshot.transactions.isNotEmpty()
                ) {
                    ItemSelectionControlsRow(
                        onSelectAll = {
                            downIncludeTransactions = true
                            downSelectedTxIds = cloudSnapshot.transactions.map { it.id }.toSet()
                        },
                        onClearAll = { downSelectedTxIds = emptySet() }
                    )
                    cloudSnapshot.transactions.take(40).forEach { tx ->
                        val checked = downIncludeTransactions && tx.id in downSelectedTxIds
                        val isNewOnDevice = tx.id !in localTxIds
                        val dateStr = dateFormatter.format(Date(tx.timestamp))
                        SelectableItemRow(
                            title = "${tx.vaccineName} (${tx.quantity} qty)",
                            subtitle = "${tx.referenceOrCustomer.ifBlank { tx.type }} • $dateStr • ₹${tx.totalAmount.toInt()}",
                            badgeText = if (isNewOnDevice) tr("NEW", "नया") else tr("On Device", "फ़ोन में है"),
                            badgeColor = if (isNewOnDevice) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                            badgeTextColor = if (isNewOnDevice) Color(0xFF15803D) else Color(0xFF475569),
                            checked = checked,
                            accentColor = Color(0xFF0E8A42),
                            onToggle = {
                                downIncludeTransactions = true
                                downSelectedTxIds = if (checked) downSelectedTxIds - tx.id else downSelectedTxIds + tx.id
                                if (downSelectedTxIds.isEmpty()) downIncludeTransactions = false
                            }
                        )
                    }
                }

                // 4. Cloud Vendors
                SelectiveTransferCategoryCard(
                    title = tr("Cloud Vendors", "क्लाउड वेंडर्स"),
                    icon = Icons.Default.Business,
                    accentColor = Color(0xFFEF6C00),
                    selectedCount = downVenCount,
                    totalCount = cloudSnapshot.vendors.size,
                    isChecked = downIncludeVendors && cloudSnapshot.vendors.isNotEmpty(),
                    onCheckedChange = { checked ->
                        downIncludeVendors = checked
                        if (checked && downSelectedVendorIds.isEmpty()) {
                            downSelectedVendorIds = cloudSnapshot.vendors.map { it.id }.toSet()
                        }
                    },
                    isExpanded = expandVendors,
                    onToggleExpand = { expandVendors = !expandVendors },
                    hasItems = cloudSnapshot.vendors.isNotEmpty()
                ) {
                    ItemSelectionControlsRow(
                        onSelectAll = {
                            downIncludeVendors = true
                            downSelectedVendorIds = cloudSnapshot.vendors.map { it.id }.toSet()
                        },
                        onClearAll = { downSelectedVendorIds = emptySet() }
                    )
                    cloudSnapshot.vendors.forEach { vendor ->
                        val checked = downIncludeVendors && vendor.id in downSelectedVendorIds
                        val isNewOnDevice = vendor.id !in localVenIds
                        SelectableItemRow(
                            title = vendor.name,
                            subtitle = "${vendor.contactPerson.ifBlank { "Supplier" }} • ${vendor.phone}",
                            badgeText = if (isNewOnDevice) tr("NEW", "नया") else tr("On Device", "फ़ोन में है"),
                            badgeColor = if (isNewOnDevice) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                            badgeTextColor = if (isNewOnDevice) Color(0xFF15803D) else Color(0xFF475569),
                            checked = checked,
                            accentColor = Color(0xFFEF6C00),
                            onToggle = {
                                downIncludeVendors = true
                                downSelectedVendorIds = if (checked) downSelectedVendorIds - vendor.id else downSelectedVendorIds + vendor.id
                                if (downSelectedVendorIds.isEmpty()) downIncludeVendors = false
                            }
                        )
                    }
                }

                // 5. Cloud Clinic Profile
                if (cloudSnapshot.clinicProfile != null) {
                    ClinicProfileSelectionRow(
                        title = tr("Cloud Clinic Profile", "क्लाउड क्लिनिक प्रोफ़ाइल"),
                        subtitle = "${cloudSnapshot.clinicProfile.clinicName} • ${cloudSnapshot.clinicProfile.doctorName}",
                        checked = downIncludeProfile,
                        accentColor = Color(0xFF0277BD),
                        onCheckedChange = { downIncludeProfile = it }
                    )
                }

                // Safe Merge vs Replace Local Toggle
                ReplaceBehaviorToggleCard(
                    title = tr("Replace existing device records in checked categories", "फ़ोन का पुराना डेटा बदलें (Replace Local)"),
                    subtitle = if (downReplaceOnDevice) {
                        tr("Existing device items in checked categories will be replaced with your selected cloud items.", "चुनी गई श्रेणियों में फ़ोन का पुराना डेटा हटकर केवल क्लाउड डेटा आएगा।")
                    } else {
                        tr("Safe Merge (Recommended): Adds/updates checked cloud items without deleting your other local records.", "सुरक्षित मर्ज (अनुशंसित): केवल चुने हुए क्लाउड आइटम जुड़ेंगे; फ़ोन का बाकी डेटा सुरक्षित रहेगा।")
                    },
                    checked = downReplaceOnDevice,
                    onCheckedChange = { downReplaceOnDevice = it }
                )

                // Download Action Button
                Button(
                    onClick = {
                        onDownloadSelected(
                            SelectiveCloudTransferRequest(
                                includeVaccines = downIncludeVaccines && downSelectedVaccineIds.isNotEmpty(),
                                selectedVaccineIds = if (downSelectedVaccineIds.size == cloudSnapshot.vaccines.size) null else downSelectedVaccineIds,
                                includePatients = downIncludePatients && downSelectedPatientIds.isNotEmpty(),
                                selectedPatientIds = if (downSelectedPatientIds.size == cloudSnapshot.patients.size) null else downSelectedPatientIds,
                                includeTransactions = downIncludeTransactions && downSelectedTxIds.isNotEmpty(),
                                selectedTransactionIds = if (downSelectedTxIds.size == cloudSnapshot.transactions.size) null else downSelectedTxIds,
                                includeVendors = downIncludeVendors && downSelectedVendorIds.isNotEmpty(),
                                selectedVendorIds = if (downSelectedVendorIds.size == cloudSnapshot.vendors.size) null else downSelectedVendorIds,
                                includeClinicProfile = downIncludeProfile && cloudSnapshot.clinicProfile != null,
                                replaceExistingOnTarget = downReplaceOnDevice
                            )
                        )
                    },
                    enabled = !syncState.isSyncing && totalSelectedToDownload > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_selective_download_cloud")
                ) {
                    if (syncState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr("Downloading from Cloud...", "क्लाउड से डाउनलोड हो रहा है..."), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("Download Selected ($totalSelectedToDownload)", "चुना हुआ डेटा डाउनलोड करें ($totalSelectedToDownload)"),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferDirectionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    selectedBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) selectedBg else Color(0xFFF8FAFC),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accentColor else Color(0xFFCBD5E1)),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(if (selected) accentColor else Color(0xFF94A3B8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) accentColor else Color(0xFF334155),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SelectiveTransferCategoryCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    selectedCount: Int,
    totalCount: Int,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    hasItems: Boolean,
    itemsContent: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) accentColor.copy(alpha = 0.08f) else Color(0xFFF8FAFC)
        ),
        border = BorderStroke(1.dp, if (isChecked) accentColor.copy(alpha = 0.45f) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    enabled = totalCount > 0,
                    colors = CheckboxDefaults.colors(checkedColor = accentColor)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$selectedCount / $totalCount ${tr("selected", "चुने गए")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedCount > 0) accentColor else Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (hasItems) {
                    TextButton(
                        onClick = onToggleExpand,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isExpanded) tr("Hide", "छुपाएं") else tr("Pick", "चुनें"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded && hasItems) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsContent()
                }
            }
        }
    }
}

@Composable
private fun ItemSelectionControlsRow(
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onSelectAll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(tr("All", "सभी"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onClearAll, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(tr("None", "कोई नहीं"), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SelectableItemRow(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    badgeTextColor: Color,
    checked: Boolean,
    accentColor: Color,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (checked) accentColor.copy(alpha = 0.45f) else Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = accentColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = badgeColor) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeTextColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ClinicProfileSelectionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) accentColor.copy(alpha = 0.08f) else Color(0xFFF8FAFC)
        ),
        border = BorderStroke(1.dp, if (checked) accentColor.copy(alpha = 0.45f) else Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = accentColor)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Storefront, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReplaceBehaviorToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (checked) Color(0xFFFFF3E0) else Color(0xFFF0FDF4),
        border = BorderStroke(1.dp, if (checked) Color(0xFFFFB74D) else Color(0xFF86EFAC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (checked) Color(0xFFE65100) else Color(0xFF14532D)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (checked) Color(0xFF5D4037) else Color(0xFF166534)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag("switch_replace_existing_target")
            )
        }
    }
}
