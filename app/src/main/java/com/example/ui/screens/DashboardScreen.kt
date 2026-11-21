package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ClinicProfile
import com.example.data.model.StockTransaction
import com.example.data.model.Vaccine
import com.example.data.repository.SyncStatusState
import com.example.ui.auth.UserSessionState
import com.example.ui.components.ClinicLogoAvatar
import com.example.ui.components.DashboardProfitRow
import com.example.ui.components.InventoryValuationCard
import com.example.ui.components.LanguageAndWhatsAppBanner
import com.example.ui.components.LowStockAlertBanner
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.TransactionItemCard
import com.example.ui.util.AppUpdateManager
import com.example.ui.util.tr
import com.example.ui.viewmodel.DashboardSummary

@Composable
fun DashboardScreen(
    summary: DashboardSummary,
    recentTransactions: List<StockTransaction>,
    lowStockVaccines: List<Vaccine>,
    clinicProfile: ClinicProfile = ClinicProfile(),
    sessionState: UserSessionState? = null,
    syncState: SyncStatusState? = null,
    onOpenAuthSyncDialog: () -> Unit = {},
    onOpenSelectiveUpload: () -> Unit = onOpenAuthSyncDialog,
    onOpenSelectiveDownload: () -> Unit = onOpenAuthSyncDialog,
    onSyncRequested: () -> Unit = {},
    onSignOutRequested: () -> Unit = {},
    onOpenPurchaseDialog: () -> Unit,
    onOpenScanBillDialog: () -> Unit = {},
    onOpenSaleDialog: () -> Unit,
    onOpenAdjustDialog: () -> Unit,
    onOpenAddVaccineDialog: () -> Unit,
    onNavigateToInventory: () -> Unit,
    onNavigateToPurchases: () -> Unit,
    onNavigateToSales: () -> Unit,
    onNavigateToPatients: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onOpenAppUpdateDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val updateState by AppUpdateManager.updateState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Sleek Clinical Hero Header Banner at the Top with Official Dr. Priyank Logo
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToProfile() }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF004F58),
                                    Color(0xFF006874),
                                    Color(0xFF00838F)
                                )
                            )
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ClinicLogoAvatar(
                            profile = clinicProfile,
                            size = 64.dp,
                            border = BorderStroke(2.dp, Color(0xFF80DEEA))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = clinicProfile.clinicName.ifBlank { "Dr Priyank Vaccine Clinic" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = clinicProfile.doctorName.ifBlank { "डॉ प्रियंक पटेल (MBBS, DCH, DNB)" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE0F7FA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "Reg. No. : ${clinicProfile.registrationNumber.ifBlank { "PED-REG-2025" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFE0F7FA),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1b. Hindi / English App & WhatsApp Reminder Language Switcher Banner
        item {
            LanguageAndWhatsAppBanner()
        }

        // 2. Clean Multi-Device Cloud Sync & Preserve/Delete Options Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAuthSyncDialog() }
                    .testTag("dashboard_sync_banner")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (sessionState?.isAuthenticated == true) Color(0xFFE8F8EE) else Color(0xFFE0F7FA),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (syncState?.isSyncing == true) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color(0xFF006874),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (sessionState?.isAuthenticated == true) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                            contentDescription = "Cloud Sync",
                                            tint = if (sessionState?.isAuthenticated == true) Color(0xFF0D7A3E) else Color(0xFF006874),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (sessionState?.isAuthenticated == true)
                                        "Synced with ${sessionState.displayName}"
                                    else
                                        "Cloud Sync & Data Backup",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (sessionState?.isAuthenticated == true)
                                        syncState?.statusMessage ?: "Old data preserved • Tap for options"
                                    else
                                        "Sign in to sync & preserve/delete data",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (sessionState?.isAuthenticated == true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = onSyncRequested,
                                    enabled = syncState?.isSyncing != true,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0D7A3E),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("dashboard_sync_now_btn")
                                ) {
                                    Icon(imageVector = Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                OutlinedButton(
                                    onClick = onSignOutRequested,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("dashboard_logout_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Log Out",
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = onOpenAuthSyncDialog,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF006874),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag("dashboard_login_btn")
                            ) {
                                Text(tr("Sign In", "लॉगिन"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Selective Cloud Upload & Download 1-Tap Quick Strip
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSelectiveUpload,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, Color(0xFF006874).copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF006874)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp)
                                .testTag("dashboard_selective_upload_btn")
                        ) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr("Selective Upload", "डेटा अपलोड"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenSelectiveDownload,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, Color(0xFF1565C0).copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1565C0)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp)
                                .testTag("dashboard_selective_download_btn")
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr("Selective Download", "डेटा डाउनलोड"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 2b. Direct Application Update & Version Status Strip
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (updateState.isUpdateAvailable) Color(0xFFF0FDF4) else Color.White
                ),
                border = BorderStroke(
                    1.dp,
                    if (updateState.isUpdateAvailable) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAppUpdateDialog() }
                    .testTag("dashboard_app_update_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (updateState.isUpdateAvailable) Color(0xFFDCFCE7) else Color(0xFFE0F2FE),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Update App",
                                    tint = if (updateState.isUpdateAvailable) Color(0xFF15803D) else Color(0xFF0284C7),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (updateState.isUpdateAvailable) {
                                    tr(
                                        "App Update Available (v${updateState.latestAvailableVersion})",
                                        "नया ऐप अपडेट उपलब्ध (v${updateState.latestAvailableVersion})"
                                    )
                                } else {
                                    tr(
                                        "App Version v${updateState.currentModuleVersion} (Up to Date)",
                                        "ऐप वर्ज़न v${updateState.currentModuleVersion} (अपडेटेड)"
                                    )
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tr(
                                    "Update directly from app • Old data 100% preserved",
                                    "सीधे ऐप से अपडेट करें • पुराना डेटा 100% सुरक्षित रहेगा"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onOpenAppUpdateDialog,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (updateState.isUpdateAvailable) Color(0xFF0D7A3E) else Color(0xFF006874),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("dashboard_update_app_btn")
                    ) {
                        Text(
                            text = if (updateState.isUpdateAvailable) tr("Update Now", "अपडेट करें") else tr("Check Update", "अपडेट जांचें"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Low stock warning banner if any items are running low
        if (summary.lowStockCount > 0 || summary.outOfStockCount > 0) {
            item {
                LowStockAlertBanner(
                    lowStockCount = summary.lowStockCount,
                    outOfStockCount = summary.outOfStockCount,
                    onViewLowStockClick = onNavigateToInventory
                )
            }
        }

        // Section: Quick Stock Actions & Patient Shortcuts
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr("Quick Stock Actions", "त्वरित स्टॉक और बिल एक्शन"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE0F7FA)
                        ) {
                            Text(
                                text = tr("Single or Multi-Vaccine", "एक या एक से अधिक टीके"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF006874),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Purchase from Vendor Button
                        ElevatedButton(
                            onClick = onOpenPurchaseDialog,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color(0xFF0277BD),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_action_restock")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tr("Purchase", "खरीदें"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = tr("Restock", "स्टॉक जोड़ें"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1
                                )
                            }
                        }

                        // Record Sale (Dispense) Button
                        ElevatedButton(
                            onClick = onOpenSaleDialog,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color(0xFF0D7A3E),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_action_sale")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tr("Dispense", "टीका दें"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = tr("Record Sale", "बिक्री दर्ज करें"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1
                                )
                            }
                        }

                        // Quick Adjust Button
                        ElevatedButton(
                            onClick = onOpenAdjustDialog,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color(0xFF5E35B1),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_action_adjust")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tr("Adjust", "समायोजन"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = tr("Audit", "स्टॉक जांच"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scan Bill Photo or Upload PDF Bar
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE0F7FA),
                        border = BorderStroke(1.dp, Color(0xFF00838F).copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenScanBillDialog() }
                            .testTag("dashboard_scan_bill_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF006874), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DocumentScanner,
                                        contentDescription = "Scan Bill",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr("Scan Bill Photo or Upload PDF", "बिल फोटो स्कैन करें या PDF अपलोड करें"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF004D40),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tr("Take a pic or upload PDF to update stock", "बिल की फोटो या PDF से वैक्सीन स्टॉक अपडेट करें"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF00695C),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onOpenScanBillDialog,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF006874),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.heightIn(min = 36.dp)
                            ) {
                                Text(
                                    text = tr("Scan / PDF", "स्कैन / PDF"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Clean, Minimal 2-Chip Shortcut Row for Patient Hub & Clinic Profile
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToPatients,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .testTag("shortcut_patients")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChildCare,
                                contentDescription = null,
                                tint = Color(0xFF006874),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr("Patient Hub & Alerts", "मरीज़ और हिंदी अलर्ट"),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onNavigateToProfile,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .testTag("shortcut_profile")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = Color(0xFF006874),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr("Clinic Settings", "क्लिनिक प्रोफ़ाइल"),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Section: Profit Analytics (Daily, Weekly, Monthly)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Profit Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))
                DashboardProfitRow(summary = summary)
            }
        }

        // Section: Inventory Valuation & Stock Health
        item {
            InventoryValuationCard(summary = summary)
        }

        // Section: Recent Activity Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Stock Movements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )

                TextButton(onClick = onNavigateToSales) {
                    Text("View All", style = MaterialTheme.typography.labelMedium)
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        if (recentTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No stock transactions yet. Record a restock or sale to see activity.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        } else {
            items(recentTransactions.take(5), key = { it.id }) { tx ->
                TransactionItemCard(transaction = tx)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
