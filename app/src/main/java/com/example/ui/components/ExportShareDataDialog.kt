package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.database.AppDatabase
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.StockTransaction
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.ui.util.AppLanguage
import com.example.ui.util.AppLanguageManager
import com.example.ui.util.ClinicExportFormat
import com.example.ui.util.DataExportManager
import com.example.ui.util.tr
import kotlinx.coroutines.launch

@Composable
fun ExportShareDataDialog(
    vaccines: List<Vaccine>,
    patients: List<PatientRegistration>,
    transactions: List<StockTransaction>,
    vendors: List<Vendor>,
    clinicProfile: ClinicProfile,
    onImportBackupUri: (Uri, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFormat by remember { mutableStateOf(ClinicExportFormat.JSON_BACKUP) }
    var isExporting by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    fun msg(en: String, hi: String): String =
        if (AppLanguageManager.appLanguageFlow.value == AppLanguage.HINDI) hi else en

    // SAF Save Directly to Google Drive / Downloads (zero storage permissions!)
    val saveToDriveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                val db = AppDatabase.getInstance(context)
                val res = DataExportManager.writeExportToUri(context, uri, db, ClinicExportFormat.JSON_BACKUP)
                isExporting = false
                if (res.isSuccess) {
                    val text = msg("Backup saved to Google Drive / Storage!", "बैकअप Google Drive / स्टोरेज में सेव हो गया!")
                    statusBanner = text
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                } else {
                    val err = res.exceptionOrNull()?.localizedMessage ?: "Export failed"
                    statusBanner = err
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val saveToDriveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                val db = AppDatabase.getInstance(context)
                val res = DataExportManager.writeExportToUri(context, uri, db, ClinicExportFormat.CSV_SPREADSHEET)
                isExporting = false
                if (res.isSuccess) {
                    val text = msg("CSV report saved to Google Drive / Storage!", "CSV रिपोर्ट Google Drive / स्टोरेज में सेव हो गई!")
                    statusBanner = text
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                } else {
                    val err = res.exceptionOrNull()?.localizedMessage ?: "Export failed"
                    statusBanner = err
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // SAF Restore / Import JSON Backup from Google Drive or Phone Storage
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportBackupUri(uri, false)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .systemBarsPadding()
            .imePadding()
            .padding(vertical = 8.dp)
            .testTag("export_share_data_dialog"),
        title = {
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
                            .size(38.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF006874), Color(0xFF0D7A3E))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export and Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = tr("Export & Share All Data", "सभी डेटा एक्सपोर्ट और शेयर करें"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tr(
                                "Google Drive backup & WhatsApp sharing",
                                "Google Drive बैकअप और WhatsApp शेयरिंग"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("export_dialog_close_btn")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Strip of All Included Clinic Data
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = tr("Included Clinic Data:", "शामिल किया गया क्लिनिक डेटा:"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ExportCountBadge(
                                label = tr("Vaccines", "वैक्सीन"),
                                value = "${vaccines.size} (${vaccines.sumOf { it.currentStock }})"
                            )
                            ExportCountBadge(
                                label = tr("Patients", "मरीज़"),
                                value = "${patients.size}"
                            )
                            ExportCountBadge(
                                label = tr("Bills/Logs", "बिल/लेजर"),
                                value = "${transactions.size}"
                            )
                            ExportCountBadge(
                                label = tr("Vendors", "वेंडर"),
                                value = "${vendors.size}"
                            )
                        }
                    }
                }

                // Format Picker
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = tr("1. Choose Export File Format", "1. एक्सपोर्ट फ़ाइल फ़ॉर्मेट चुनें"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ClinicExportFormat.entries.forEach { fmt ->
                            val isSelected = selectedFormat == fmt
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFormat = fmt },
                                label = {
                                    Text(
                                        text = tr(fmt.labelEn, fmt.labelHi),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Check else if (fmt == ClinicExportFormat.JSON_BACKUP) Icons.Default.Description else Icons.Default.TableChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE0F7FA),
                                    selectedLabelColor = Color(0xFF004D40),
                                    selectedLeadingIconColor = Color(0xFF006874)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_format_${fmt.extension}")
                            )
                        }
                    }
                    Text(
                        text = if (selectedFormat == ClinicExportFormat.JSON_BACKUP) {
                            tr(
                                "Complete structured backup (.json) — can be restored anytime onto this or another phone.",
                                "पूरा बैकअप (.json) — इसे कभी भी इसी या दूसरे फ़ोन में वापस रिस्टोर किया जा सकता है।"
                            )
                        } else {
                            tr(
                                "Spreadsheet report (.csv) — opens directly in Google Sheets, Microsoft Excel, or WhatsApp preview.",
                                "स्प्रेडशीट रिपोर्ट (.csv) — सीधे Google Sheets या Excel में खोलकर देख सकते हैं।"
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (statusBanner != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE0F2FE),
                        border = BorderStroke(1.dp, Color(0xFF7DD3FC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusBanner!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0369A1),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 2. Google Drive Actions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = tr("2. Export to Google Drive", "2. Google Drive पर सेव / अपलोड करें"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isExporting = true
                                try {
                                    val db = AppDatabase.getInstance(context)
                                    val file = DataExportManager.createExportFile(context, db, selectedFormat)
                                    val opened = DataExportManager.shareFileToGoogleDrive(context, file, selectedFormat)
                                    statusBanner = if (opened) {
                                        msg("Opened Google Drive upload for ${file.name}", "${file.name} के लिए Google Drive खुल गया है")
                                    } else {
                                        msg("Could not open Google Drive", "Google Drive नहीं खुल सका")
                                    }
                                } catch (e: Exception) {
                                    statusBanner = e.localizedMessage ?: "Export error"
                                } finally {
                                    isExporting = false
                                }
                            }
                        },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A73E8),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_export_drive_upload")
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.AddToDrive, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("Upload Directly to Google Drive", "सीधे Google Drive पर अपलोड करें"),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val name = DataExportManager.suggestedFileName(selectedFormat)
                            if (selectedFormat == ClinicExportFormat.JSON_BACKUP) {
                                saveToDriveJsonLauncher.launch(name)
                            } else {
                                saveToDriveCsvLauncher.launch(name)
                            }
                        },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF1A73E8).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1A73E8)),
                        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_export_save_saf")
                    ) {
                        Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("Save to Drive Folder / Phone Storage", "Drive फ़ोल्डर या फ़ोन में सेव करें"),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 3. WhatsApp & Other Apps Sharing
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = tr("3. Share via WhatsApp", "3. WhatsApp पर शेयर करें"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isExporting = true
                                try {
                                    val db = AppDatabase.getInstance(context)
                                    val file = DataExportManager.createExportFile(context, db, selectedFormat)
                                    val caption = msg(
                                        "${clinicProfile.clinicName} — Complete Clinic Data Backup (${file.name})",
                                        "${clinicProfile.clinicName} — पूरा क्लिनिक डेटा बैकअप (${file.name})"
                                    )
                                    val ok = DataExportManager.shareFileToWhatsApp(context, file, selectedFormat, caption)
                                    statusBanner = if (ok) {
                                        msg("Shared ${file.name} to WhatsApp", "${file.name} WhatsApp पर शेयर किया गया")
                                    } else {
                                        msg("Could not open WhatsApp", "WhatsApp नहीं खुल सका")
                                    }
                                } catch (e: Exception) {
                                    statusBanner = e.localizedMessage ?: "Export error"
                                } finally {
                                    isExporting = false
                                }
                            }
                        },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF16A34A),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_export_whatsapp_file")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr(
                                "Share ${selectedFormat.extension.uppercase()} File on WhatsApp",
                                "WhatsApp पर ${selectedFormat.extension.uppercase()} फ़ाइल भेजें"
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val text = DataExportManager.buildWhatsAppSummaryText(
                                    vaccines = vaccines,
                                    patients = patients,
                                    transactions = transactions,
                                    vendors = vendors,
                                    clinicProfile = clinicProfile
                                )
                                DataExportManager.shareTextToWhatsApp(context, text)
                            },
                            enabled = !isExporting,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF15803D)),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_export_whatsapp_summary")
                        ) {
                            Text(
                                text = tr("WhatsApp Stock Text", "WhatsApp स्टॉक संदेश"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    try {
                                        val db = AppDatabase.getInstance(context)
                                        val file = DataExportManager.createExportFile(context, db, selectedFormat)
                                        DataExportManager.shareFileViaOtherApps(context, file, selectedFormat)
                                    } catch (e: Exception) {
                                        statusBanner = e.localizedMessage ?: "Share failed"
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            enabled = !isExporting,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_export_other_apps")
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tr("Other Apps", "अन्य ऐप्स"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 4. Restore / Import from Google Drive or WhatsApp Backup JSON
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = tr("Restore Backup from Drive or WhatsApp", "Drive या WhatsApp बैकअप वापस रिस्टोर करें"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr(
                                "Pick a .json clinic backup file saved in Google Drive or received on WhatsApp to restore all vaccine stock counts & patients.",
                                "Google Drive में सेव या WhatsApp पर प्राप्त .json बैकअप फ़ाइल चुनकर सभी वैक्सीन स्टॉक और मरीज़ डेटा रिस्टोर करें।"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF006874)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF006874)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_import_backup_file")
                        ) {
                            Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr("Import / Restore .JSON Backup", ".JSON बैकअप इम्पोर्ट / रिस्टोर करें"),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("export_dialog_done_btn")
            ) {
                Text(tr("Close", "बंद करें"))
            }
        }
    )
}

@Composable
private fun ExportCountBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF14532D)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF166534)
        )
    }
}
