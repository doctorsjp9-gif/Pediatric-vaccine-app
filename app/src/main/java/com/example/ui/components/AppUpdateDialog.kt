package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.util.AppLanguageManager
import com.example.ui.util.AppUpdateManager
import com.example.ui.util.UpdateSourceType
import com.example.ui.util.tr
import kotlinx.coroutines.launch

@Composable
fun AppUpdateDialog(
    vaccinesCount: Int,
    patientsCount: Int,
    transactionsCount: Int,
    isCloudAuthenticated: Boolean,
    onRunPreUpdateBackupAndSync: suspend () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateState by AppUpdateManager.updateState.collectAsStateWithLifecycle()

    var showSourceConfig by remember { mutableStateOf(false) }
    var selectedSourceType by remember(updateState.sourceType) { mutableStateOf(updateState.sourceType) }
    var apkUrlInput by remember(updateState.apkDownloadUrl) { mutableStateOf(updateState.apkDownloadUrl) }
    var githubRepoInput by remember(updateState.githubRepo) { mutableStateOf(updateState.githubRepo) }
    var manifestUrlInput by remember(updateState.manifestJsonUrl) { mutableStateOf(updateState.manifestJsonUrl) }
    var versionNameInput by remember(updateState.latestAvailableVersion) {
        mutableStateOf(
            if (updateState.latestAvailableVersion == updateState.installedPackageVersion) {
                ""
            } else {
                updateState.latestAvailableVersion
            }
        )
    }
    var versionCodeInput by remember(updateState.latestBuildNumber) {
        mutableStateOf((updateState.installedVersionCode + 1).toString())
    }
    var releaseNotesInput by remember(updateState.releaseNotesText) { mutableStateOf(updateState.releaseNotesText) }

    LaunchedEffect(Unit) {
        AppUpdateManager.init(context)
        if (!AppUpdateManager.updateState.value.hasDownloadableLink &&
            AppUpdateManager.updateState.value.githubRepo.isBlank() &&
            AppUpdateManager.updateState.value.manifestJsonUrl.isBlank()
        ) {
            showSourceConfig = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("app_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.5.dp, Color(0xFF006874)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.dr_priyank_logo_full),
                                contentDescription = "Clinic Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = tr("App Update & New APK", "ऐप अपडेट और नया APK"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = tr(
                                    "Installed: v${updateState.installedPackageVersion} (Build #${updateState.installedVersionCode})",
                                    "मौजूदा ऐप: v${updateState.installedPackageVersion} (बिल्ड #${updateState.installedVersionCode})"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_update_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF475569)
                        )
                    }
                }

                // 2. Real Version Status & Download Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (updateState.isUpdateAvailable) Color(0xFFF0FDF4) else Color(0xFFF0F9FF)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (updateState.isUpdateAvailable) Color(0xFF86EFAC) else Color(0xFFBAE6FD)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (updateState.isUpdateAvailable) Color(0xFF16A34A) else Color(0xFF0284C7),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (updateState.isUpdateAvailable) Icons.Default.NewReleases else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (updateState.isUpdateAvailable) {
                                            tr(
                                                "New APK Ready: v${updateState.latestAvailableVersion}",
                                                "नया APK उपलब्ध: v${updateState.latestAvailableVersion}"
                                            )
                                        } else if (updateState.hasDownloadableLink) {
                                            tr(
                                                "APK Link Ready (v${updateState.latestAvailableVersion})",
                                                "APK लिंक तैयार है (v${updateState.latestAvailableVersion})"
                                            )
                                        } else {
                                            tr(
                                                "Current Build: v${updateState.installedPackageVersion}",
                                                "मौजूदा वर्ज़न: v${updateState.installedPackageVersion}"
                                            )
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (updateState.isUpdateAvailable) Color(0xFF14532D) else Color(0xFF0C4A6E)
                                    )
                                    val subInfo = buildString {
                                        if (updateState.apkFileName.isNotBlank()) {
                                            append(updateState.apkFileName)
                                            if (updateState.apkFileSizeMb.isNotBlank()) append(" (${updateState.apkFileSizeMb})")
                                        } else if (updateState.releaseDate.isNotBlank()) {
                                            append(tr("Updated: ", "दिनांक: "))
                                            append(updateState.releaseDate)
                                        } else {
                                            append(
                                                tr(
                                                    "Last Checked: ${AppUpdateManager.formatTimestamp(updateState.lastCheckedTimestamp)}",
                                                    "अंतिम जांच: ${AppUpdateManager.formatTimestamp(updateState.lastCheckedTimestamp)}"
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = subInfo,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val found = AppUpdateManager.checkForUpdates(context)
                                        val state = AppUpdateManager.updateState.value
                                        val toastMsg = state.updateStatusMessage.ifBlank {
                                            if (found) {
                                                AppLanguageManager.text("New APK found!", "नया APK मिल गया!")
                                            } else {
                                                AppLanguageManager.text("Check finished", "जांच पूरी हुई")
                                            }
                                        }
                                        Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !updateState.isChecking,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_check_for_updates")
                            ) {
                                if (updateState.isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tr("Check", "जांचें"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (updateState.updateStatusMessage.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (updateState.lastError != null) Color(0xFFFFEBEE) else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = if (updateState.lastError != null) Icons.Default.Warning else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (updateState.lastError != null) Color(0xFFB71C1C) else Color(0xFF006874),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = updateState.updateStatusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (updateState.lastError != null) Color(0xFFB71C1C) else Color(0xFF0F172A)
                                    )
                                }
                            }
                        }

                        if (updateState.releaseNotesText.isNotBlank()) {
                            Text(
                                text = "${tr("Release Notes:", "नए अपडेट में क्या है:")}\n${updateState.releaseNotesText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF334155)
                            )
                        }

                        // Live Download Progress Bar (when verifying link or downloading directly)
                        if (updateState.isVerifyingLink || updateState.isDownloading) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFF99F6E4)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("download_progress_box")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (updateState.isVerifyingLink) {
                                                tr("Verifying APK link...", "APK लिंक की जांच हो रही है...")
                                            } else {
                                                tr(
                                                    "Downloading APK... ${updateState.downloadProgressPercent}%",
                                                    "नया APK डाउनलोड हो रहा है... ${updateState.downloadProgressPercent}%"
                                                )
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F766E)
                                        )
                                        if (updateState.downloadedSizeLabel.isNotBlank()) {
                                            Text(
                                                text = updateState.downloadedSizeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                    }
                                    if (updateState.isVerifyingLink || updateState.downloadProgressPercent <= 0) {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = Color(0xFF006874)
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            progress = { (updateState.downloadProgressPercent / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = Color(0xFF0D7A3E)
                                        )
                                    }
                                    if (updateState.downloadedFileName.isNotBlank()) {
                                        Text(
                                            text = tr(
                                                "File: ${updateState.downloadedFileName}",
                                                "फाइल: ${updateState.downloadedFileName}"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }

                        // Completed Download Banner -> 1-tap Open Downloads to Install the exact new file
                        if (updateState.downloadCompleted && updateState.downloadedFileName.isNotBlank()) {
                            Button(
                                onClick = { AppUpdateManager.openDownloadsFolder(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_open_downloads_to_install")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr(
                                        "Open Downloads to Install New APK",
                                        "इंस्टॉल करने के लिए Downloads खोलें"
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // REAL Direct Download New APK Button + Browser Fallback
                        if (updateState.hasDownloadableLink) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (updateState.autoBackupBeforeUpdate) {
                                            onRunPreUpdateBackupAndSync()
                                            AppUpdateManager.recordBackupTimestamp(context)
                                        }
                                        AppUpdateManager.startDirectApkDownload(context)
                                    }
                                },
                                enabled = !updateState.isDownloading && !updateState.isVerifyingLink,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (updateState.isUpdateAvailable) Color(0xFF0D7A3E) else Color(0xFF006874)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_download_new_apk")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (updateState.isDownloading) {
                                        tr(
                                            "Downloading... (${updateState.downloadProgressPercent}%)",
                                            "डाउनलोड हो रहा है... (${updateState.downloadProgressPercent}%)"
                                        )
                                    } else if (updateState.isUpdateAvailable) {
                                        tr(
                                            "Download New APK (v${updateState.latestAvailableVersion})",
                                            "नया APK डाउनलोड करें (v${updateState.latestAvailableVersion})"
                                        )
                                    } else {
                                        tr(
                                            "Download Configured APK Now",
                                            "सेव किया गया नया APK डाउनलोड करें"
                                        )
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        if (updateState.autoBackupBeforeUpdate) {
                                            onRunPreUpdateBackupAndSync()
                                            AppUpdateManager.recordBackupTimestamp(context)
                                        }
                                        AppUpdateManager.openRealApkDownload(context)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("btn_open_apk_in_browser")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tr("Open Download Link in Browser", "डाउनलोड लिंक ब्राउज़र में खोलें"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Button(
                                onClick = { showSourceConfig = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("btn_setup_apk_link")
                            ) {
                                Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr("Add Your New Generated APK Link", "अपना नया जनरेट किया APK लिंक जोड़ें"),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 3. Configure Real New APK Source (Direct APK / Google Drive / GitHub Release / JSON)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSourceConfig = !showSourceConfig },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color(0xFF006874),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = tr("New Software / APK Source Setup", "नए सॉफ्टवेयर (APK) का लिंक सेट करें"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = tr(
                                            "Paste Google Drive / GitHub / Direct APK link to download new builds",
                                            "नया जनरेट किया APK डाउनलोड करने के लिए Drive / GitHub / Direct लिंक डालें"
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                            TextButton(onClick = { showSourceConfig = !showSourceConfig }) {
                                Text(
                                    text = if (showSourceConfig) tr("Hide", "छुपाएं") else tr("Edit", "बदलें"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        AnimatedVisibility(visible = showSourceConfig) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Source Type Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedSourceType == UpdateSourceType.DIRECT_APK_OR_CLOUD,
                                        onClick = { selectedSourceType = UpdateSourceType.DIRECT_APK_OR_CLOUD },
                                        label = { Text(tr("APK / Drive", "APK / Drive"), style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF006874),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                    FilterChip(
                                        selected = selectedSourceType == UpdateSourceType.GITHUB_RELEASES,
                                        onClick = { selectedSourceType = UpdateSourceType.GITHUB_RELEASES },
                                        label = { Text("GitHub", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF006874),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                    FilterChip(
                                        selected = selectedSourceType == UpdateSourceType.MANIFEST_JSON,
                                        onClick = { selectedSourceType = UpdateSourceType.MANIFEST_JSON },
                                        label = { Text("JSON URL", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF006874),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }

                                when (selectedSourceType) {
                                    UpdateSourceType.DIRECT_APK_OR_CLOUD -> {
                                        Text(
                                            text = tr(
                                                "How to update to your newly generated APK:\n1. Generate APK from AI Studio & upload it to Google Drive (Anyone with link) or your server.\n2. Paste that link below & tap Save. Google Drive links auto-convert to direct download!\n3. If signed in, the link syncs to Cloud so all your phones get the new APK.",
                                                "नया सॉफ्टवेयर डाउनलोड करने का तरीका:\n1. AI Studio से नया APK जनरेट करें और उसे Google Drive (Anyone with link) या सर्वर पर अपलोड करें।\n2. उसका लिंक नीचे पेस्ट करके 'सेव करें' दबाएं (Google Drive लिंक अपने आप डायरेक्ट डाउनलोड में बदल जाएगा)।\n3. अब 'नया APK डाउनलोड करें' दबाते ही नया सॉफ्टवेयर डाउनलोड होगा।"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF334155)
                                        )
                                        OutlinedTextField(
                                            value = apkUrlInput,
                                            onValueChange = { apkUrlInput = it },
                                            label = { Text(tr("New APK Link (Drive / Direct .apk URL)", "नए APK का लिंक (Drive / Direct .apk URL)")) },
                                            placeholder = { Text("https://drive.google.com/file/d/.../view") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("input_direct_apk_url")
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = versionNameInput,
                                                onValueChange = { versionNameInput = it },
                                                label = { Text(tr("New Version", "नया वर्ज़न")) },
                                                placeholder = { Text("2.7.0") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("input_new_version_name")
                                            )
                                            OutlinedTextField(
                                                value = versionCodeInput,
                                                onValueChange = { versionCodeInput = it.filter { ch -> ch.isDigit() } },
                                                label = { Text(tr("Build #", "बिल्ड #")) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(0.7f)
                                                    .testTag("input_new_version_code")
                                            )
                                        }
                                        OutlinedTextField(
                                            value = releaseNotesInput,
                                            onValueChange = { releaseNotesInput = it },
                                            label = { Text(tr("What's New (Optional)", "नए अपडेट का विवरण (वैकल्पिक)")) },
                                            maxLines = 2,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    UpdateSourceType.GITHUB_RELEASES -> {
                                        Text(
                                            text = tr(
                                                "Enter your GitHub repository (e.g. drpriyank/vaccine-clinic). The app will automatically fetch the latest GitHub Release and download the attached .apk file.",
                                                "अपना GitHub रिपॉजिटरी लिखें (उदा. drpriyank/vaccine-clinic)। ऐप अपने आप नवीनतम GitHub Release से नया .apk खोजकर डाउनलोड करेगा।"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF334155)
                                        )
                                        OutlinedTextField(
                                            value = githubRepoInput,
                                            onValueChange = { githubRepoInput = it },
                                            label = { Text(tr("GitHub Repo (owner/repo or URL)", "GitHub रिपॉजिटरी (owner/repo)")) },
                                            placeholder = { Text("username/my-clinic-app") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("input_github_repo")
                                        )
                                    }

                                    UpdateSourceType.MANIFEST_JSON -> {
                                        Text(
                                            text = tr(
                                                "Host a version.json file containing: {\"versionCode\": 3, \"versionName\": \"2.7.0\", \"apkUrl\": \"https://.../app.apk\"}",
                                                "अपने सर्वर पर version.json का लिंक दें जिसमें versionCode, versionName और apkUrl हो।"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF334155)
                                        )
                                        OutlinedTextField(
                                            value = manifestUrlInput,
                                            onValueChange = { manifestUrlInput = it },
                                            label = { Text("version.json URL") },
                                            placeholder = { Text("https://yourdomain.com/version.json") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("input_manifest_json_url")
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val parsedCode = versionCodeInput.toIntOrNull()
                                                ?: (updateState.installedVersionCode.toInt() + 1)
                                            val result = AppUpdateManager.saveAndPublishReleaseSource(
                                                context = context,
                                                sourceType = selectedSourceType,
                                                apkDownloadUrl = apkUrlInput,
                                                newVersionName = versionNameInput.ifBlank { updateState.installedPackageVersion },
                                                newVersionCode = parsedCode,
                                                githubRepo = githubRepoInput,
                                                manifestJsonUrl = manifestUrlInput,
                                                releaseNotes = releaseNotesInput
                                            )
                                            if (result.isSuccess) {
                                                showSourceConfig = false
                                                Toast.makeText(
                                                    context,
                                                    result.getOrNull() ?: AppLanguageManager.text("Saved!", "सेव हो गया!"),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },
                                    enabled = !updateState.isPublishingRelease && !updateState.isChecking,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006874)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_save_update_source")
                                ) {
                                    if (updateState.isPublishingRelease) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (isCloudAuthenticated) {
                                            tr("Save & Sync Release Source", "अपडेट लिंक सेव व सिंक करें")
                                        } else {
                                            tr("Save & Check Now", "सेव करें और जांचें")
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (selectedSourceType == UpdateSourceType.DIRECT_APK_OR_CLOUD && apkUrlInput.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val parsedCode = versionCodeInput.toIntOrNull()
                                                    ?: (updateState.installedVersionCode.toInt() + 1)
                                                val result = AppUpdateManager.saveAndPublishReleaseSource(
                                                    context = context,
                                                    sourceType = selectedSourceType,
                                                    apkDownloadUrl = apkUrlInput,
                                                    newVersionName = versionNameInput.ifBlank { updateState.installedPackageVersion },
                                                    newVersionCode = parsedCode,
                                                    githubRepo = githubRepoInput,
                                                    manifestJsonUrl = manifestUrlInput,
                                                    releaseNotes = releaseNotesInput
                                                )
                                                if (result.isSuccess) {
                                                    showSourceConfig = false
                                                    if (updateState.autoBackupBeforeUpdate) {
                                                        onRunPreUpdateBackupAndSync()
                                                        AppUpdateManager.recordBackupTimestamp(context)
                                                    }
                                                    AppUpdateManager.startDirectApkDownload(context, apkUrlInput)
                                                }
                                            }
                                        },
                                        enabled = !updateState.isPublishingRelease && !updateState.isDownloading && !updateState.isVerifyingLink,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D7A3E)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_save_and_download_now")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = tr("Save & Download APK Now", "सेव करें और अभी APK डाउनलोड करें"),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Zero-Data-Loss Backup Protection Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr(
                                    "Old Data Protection Before Installing New APK",
                                    "नया APK इंस्टॉल करने से पहले पुराना डेटा सुरक्षा"
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14532D)
                            )
                        }

                        Text(
                            text = tr(
                                "Protected Records: $vaccinesCount Vaccines • $patientsCount Patients • $transactionsCount Bills",
                                "सुरक्षित डेटा: $vaccinesCount टीके • $patientsCount मरीज़ • $transactionsCount बिल"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF166534)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr(
                                    "Last Backup: ${AppUpdateManager.formatTimestamp(updateState.lastBackupTimestamp)}",
                                    "अंतिम बैकअप: ${AppUpdateManager.formatTimestamp(updateState.lastBackupTimestamp)}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        onRunPreUpdateBackupAndSync()
                                        AppUpdateManager.recordBackupTimestamp(context)
                                        Toast.makeText(
                                            context,
                                            AppLanguageManager.text(
                                                "Safety backup saved! Your data will remain safe when installing the new APK.",
                                                "सुरक्षा बैकअप सेव हो गया! नया APK इंस्टॉल करने पर पुराना डेटा सुरक्षित रहेगा।"
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_pre_update_backup")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tr("Backup Now", "बैकअप लें"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 5. Google Play Store Channel (if distributed via Play Store)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            if (updateState.autoBackupBeforeUpdate) {
                                onRunPreUpdateBackupAndSync()
                                AppUpdateManager.recordBackupTimestamp(context)
                            }
                            AppUpdateManager.openGooglePlayStoreUpdate(context)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_update_via_play_store")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("Check on Google Play Store", "Google Play Store पर अपडेट जांचें"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // 6. Auto-Backup Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Auto-Backup Old Data Before Download", "डाउनलोड से पहले पुराना डेटा ऑटो-बैकअप करें"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = tr("Saves local + cloud backup before opening new APK link", "नया APK खोलने से पहले सभी मरीज़ और स्टॉक सेव रखता है"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                    Switch(
                        checked = updateState.autoBackupBeforeUpdate,
                        onCheckedChange = { AppUpdateManager.setAutoBackupBeforeUpdate(context, it) },
                        modifier = Modifier.testTag("switch_auto_backup_before_update")
                    )
                }
            }
        }
    }
}
