package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.ui.util.AppLanguage
import com.example.ui.util.AppLanguageManager
import com.example.ui.util.ReminderLanguage
import com.example.ui.util.WhatsAppHelper
import com.example.ui.util.tr

/**
 * Compact, high-visibility Language & WhatsApp Reminder Language Switcher Card.
 * Allows 1-tap switching between English and Hindi (हिंदी) for both the App UI and WhatsApp Vaccine Reminders.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageAndWhatsAppBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLanguage by AppLanguageManager.appLanguageFlow.collectAsState()
    val whatsappLanguage by AppLanguageManager.whatsappLanguageFlow.collectAsState()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("language_whatsapp_banner")
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Left: App Language Toggle (EN | हिंदी)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = Color(0xFF006874),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = tr("App:", "ऐप:"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isEn = appLanguage == AppLanguage.ENGLISH
                        val isHi = appLanguage == AppLanguage.HINDI

                        Surface(
                            shape = RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp),
                            color = if (isEn) Color(0xFF006874) else Color.Transparent,
                            modifier = Modifier
                                .clickable {
                                    AppLanguageManager.setAppLanguage(context, AppLanguage.ENGLISH)
                                }
                                .testTag("btn_lang_en")
                        ) {
                            Text(
                                text = "EN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isEn) Color.White else Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp),
                            color = if (isHi) Color(0xFF006874) else Color.Transparent,
                            modifier = Modifier
                                .clickable {
                                    AppLanguageManager.setAppLanguage(context, AppLanguage.HINDI)
                                    AppLanguageManager.setWhatsAppReminderLanguage(context, ReminderLanguage.HINDI)
                                }
                                .testTag("btn_lang_hi")
                        ) {
                            Text(
                                text = "हिंदी",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isHi) Color.White else Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Right: WhatsApp Reminder Language Pills (हिंदी | EN | Both)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "WhatsApp:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF15803D)
                )

                ReminderLanguage.entries.forEach { langOption ->
                    val selected = whatsappLanguage == langOption
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (selected) Color(0xFF15803D) else Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, if (selected) Color(0xFF15803D) else Color(0xFFBBF7D0)),
                        modifier = Modifier
                            .clickable {
                                AppLanguageManager.setWhatsAppReminderLanguage(context, langOption)
                            }
                            .testTag("chip_whatsapp_lang_${langOption.code.lowercase()}")
                    ) {
                        Text(
                            text = langOption.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Dialog for previewing, editing, and sending a Vaccine Reminder on WhatsApp
 * with 1-tap switching between Hindi (हिंदी), English, and Bilingual (हिंदी + English).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhatsAppReminderDialog(
    patient: PatientRegistration,
    clinicProfile: ClinicProfile,
    initialLanguage: ReminderLanguage = AppLanguageManager.currentWhatsAppLanguage,
    onDismiss: () -> Unit,
    onSendConfirmed: (String, ReminderLanguage) -> Unit
) {
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var messageText by remember(patient, clinicProfile, selectedLanguage) {
        mutableStateOf(
            WhatsAppHelper.buildVaccineReminderMessage(
                patient = patient,
                clinic = clinicProfile,
                language = selectedLanguage
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .systemBarsPadding()
            .imePadding()
            .padding(vertical = 8.dp)
            .testTag("dialog_whatsapp_vaccine_reminder"),
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
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = tr("WhatsApp Vaccine Reminder", "WhatsApp टीकाकरण रिमाइंडर"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${patient.patientName} • ${patient.mobileNumber}",
                            style = MaterialTheme.typography.labelSmall,
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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Language Selector Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr(
                                    "Select Reminder Language (रिमाइंडर की भाषा चुनें):",
                                    "रिमाइंडर की भाषा चुनें (Select Language):"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14532D)
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReminderLanguage.entries.forEach { lang ->
                                val isSelected = selectedLanguage == lang
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedLanguage = lang
                                        AppLanguageManager.setWhatsAppReminderLanguage(context, lang)
                                        messageText = WhatsAppHelper.buildVaccineReminderMessage(
                                            patient = patient,
                                            clinic = clinicProfile,
                                            language = lang
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = lang.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF15803D),
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("dialog_whatsapp_lang_${lang.code.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // Editable Message Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Message Preview (Editable):", "संदेश प्रीव्यू (आप बदल सकते हैं):"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            messageText = WhatsAppHelper.buildVaccineReminderMessage(
                                patient = patient,
                                clinic = clinicProfile,
                                language = selectedLanguage
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(tr("Reset Template", "रीसेट करें"), style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 280.dp)
                        .testTag("input_whatsapp_reminder_message"),
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSendConfirmed(messageText, selectedLanguage)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DA851)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_send_whatsapp_reminder")
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (selectedLanguage) {
                        ReminderLanguage.HINDI -> "Send in हिंदी (WhatsApp)"
                        ReminderLanguage.BILINGUAL -> "Send हिंदी + EN (WhatsApp)"
                        ReminderLanguage.ENGLISH -> "Send in English (WhatsApp)"
                    },
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(tr("Cancel", "रद्द करें"))
            }
        }
    )
}
