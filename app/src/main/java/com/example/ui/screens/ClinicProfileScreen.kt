package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Verified
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.util.AppUpdateManager
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ClinicProfile
import com.example.data.repository.SyncStatusState
import com.example.ui.auth.UserSessionState
import com.example.ui.components.ClinicLogoAvatar
import com.example.ui.components.DigitalSignatureDialog
import com.example.ui.components.LanguageAndWhatsAppBanner
import com.example.ui.util.ImageStorageHelper
import com.example.ui.util.SignatureManager
import com.example.ui.util.WhatsAppHelper
import com.example.ui.util.tr

data class LogoOption(val id: String, val name: String, val icon: ImageVector)
data class ThemeOption(val id: String, val name: String, val primaryColor: Color, val containerColor: Color)

@Composable
fun ClinicProfileScreen(
    currentProfile: ClinicProfile,
    onSaveProfile: (ClinicProfile) -> Unit,
    sessionState: UserSessionState? = null,
    syncState: SyncStatusState? = null,
    onOpenAuthSyncDialog: () -> Unit = {},
    onSyncRequested: () -> Unit = {},
    onSignOutRequested: () -> Unit = {},
    onOpenAppUpdateDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateState by AppUpdateManager.updateState.collectAsStateWithLifecycle()

    var clinicName by remember(currentProfile) { mutableStateOf(currentProfile.clinicName) }
    var doctorName by remember(currentProfile) { mutableStateOf(currentProfile.doctorName) }
    var phone by remember(currentProfile) { mutableStateOf(currentProfile.phone) }
    var email by remember(currentProfile) { mutableStateOf(currentProfile.email) }
    var address by remember(currentProfile) { mutableStateOf(currentProfile.address) }
    var registrationNumber by remember(currentProfile) { mutableStateOf(currentProfile.registrationNumber) }
    var logoType by remember(currentProfile) { mutableStateOf(currentProfile.logoType) }
    var photoUri by remember(currentProfile) { mutableStateOf(currentProfile.photoUri) }
    var themeColor by remember(currentProfile) { mutableStateOf(currentProfile.themeColor) }
    var billFooterNote by remember(currentProfile) { mutableStateOf(currentProfile.billFooterNote) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = ImageStorageHelper.saveImageToInternalStorage(context, uri, "clinic_profile_photo")
            if (savedPath != null) {
                photoUri = savedPath
                Toast.makeText(context, "Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to save selected photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showSignatureDialog by remember { mutableStateOf(false) }
    var savedSignatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        if (SignatureManager.hasSavedSignature(context)) {
            savedSignatureBitmap = SignatureManager.getSavedSignature(context)
        }
    }

    val logoOptions = listOf(
        LogoOption("CUTE_TEDDY", "Dr. Priyank Logo", Icons.Default.Healing),
        LogoOption("SHIELD", "Safety Shield", Icons.Default.Security),
        LogoOption("SYRINGE", "Syringe", Icons.Default.Vaccines),
        LogoOption("STETHOSCOPE", "Doctor", Icons.Default.MedicalServices),
        LogoOption("HEART", "Heart Care", Icons.Default.Favorite),
        LogoOption("HOSPITAL", "Clinic Center", Icons.Default.LocalHospital)
    )

    val themeOptions = listOf(
        ThemeOption("TEAL", "Teal Clinical", Color(0xFF006874), Color(0xFF97F0FF)),
        ThemeOption("BLUE", "Pediatric Blue", Color(0xFF0061A4), Color(0xFFD1E4FF)),
        ThemeOption("EMERALD", "Emerald Health", Color(0xFF006C4C), Color(0xFF89F8C7)),
        ThemeOption("CORAL", "Warm Coral", Color(0xFFA04020), Color(0xFFFFDBCF)),
        ThemeOption("PURPLE", "Lavender Bloom", Color(0xFF6B4EA2), Color(0xFFECDCFF)),
        ThemeOption("BERRY", "Pediatric Berry", Color(0xFFC2185B), Color(0xFFFFD8E4)),
        ThemeOption("GOLDEN", "Amber Sunshine", Color(0xFFC66900), Color(0xFFFFE0B2))
    )

    val livePreviewProfile = currentProfile.copy(
        clinicName = clinicName,
        doctorName = doctorName,
        phone = phone,
        email = email,
        address = address,
        registrationNumber = registrationNumber,
        logoType = logoType,
        photoUri = photoUri,
        themeColor = themeColor,
        billFooterNote = billFooterNote
    )

    Scaffold(
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("clinic_profile_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Language & WhatsApp Reminder Language Settings Banner
            item {
                LanguageAndWhatsAppBanner()
            }

            // Live Header Preview Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Header & Bill Preview",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "OFFICIAL BRANDING",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live Header mockup
                        val livePreviewProfile = currentProfile.copy(
                            clinicName = clinicName,
                            doctorName = doctorName,
                            phone = phone,
                            email = email,
                            address = address,
                            registrationNumber = registrationNumber,
                            logoType = logoType,
                            photoUri = photoUri,
                            themeColor = themeColor,
                            billFooterNote = billFooterNote
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ClinicLogoAvatar(
                                profile = livePreviewProfile,
                                size = 56.dp,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = clinicName.ifBlank { "Dr Priyank Vaccine Clinic" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${doctorName.ifBlank { "Dr. Priyank, MD" }} • Reg: ${registrationNumber.ifBlank { "GMC-REG-29182" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = "${address.ifBlank { "Clinic Address" }} • 📞 ${phone.ifBlank { "+91 98765 43210" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Bill Motto: \"$billFooterNote\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Clinic Logo & Profile Photo Section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Logo & Profile Photo",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Add photo as profile image or select official logo emblem",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Photo Avatar & Upload Actions
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    ClinicLogoAvatar(
                                        profile = livePreviewProfile,
                                        size = 72.dp,
                                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PhotoCamera,
                                                contentDescription = "Camera",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (photoUri.isNotBlank()) "Custom Photo Active" else "Default Emblem Selected",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (photoUri.isNotBlank())
                                            "Shown on app top bar, doctor profile, bills & PDF receipts"
                                        else
                                            "Upload doctor's photo, clinic board picture, or logo image",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("btn_pick_profile_photo")
                                        ) {
                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (photoUri.isNotBlank()) "Change Photo" else "Add Photo")
                                        }

                                        if (photoUri.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    photoUri = ""
                                                    Toast.makeText(context, "Switched back to logo emblem", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.testTag("btn_remove_custom_photo")
                                            ) {
                                                Text("Use Logo", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Or Choose a Clinic Emblem / Icon:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(logoOptions) { option ->
                                val isSelected = logoType == option.id && photoUri.isBlank()
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .clickable {
                                            logoType = option.id
                                            if (photoUri.isNotBlank()) {
                                                photoUri = "" // Switch to chosen emblem
                                            }
                                        }
                                        .testTag("logo_option_${option.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (option.id == "CUTE_TEDDY") {
                                            Image(
                                                painter = painterResource(id = R.drawable.dr_priyank_logo_full),
                                                contentDescription = option.name,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = option.icon,
                                                contentDescription = option.name,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = option.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Clinic Theme Picker
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "App & Bill Color Theme",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select a clinic theme accent that reflects your practice personality.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(themeOptions) { option ->
                                val isSelected = themeColor == option.id
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) option.containerColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = if (isSelected) BorderStroke(2.dp, option.primaryColor) else null,
                                    modifier = Modifier
                                        .clickable { themeColor = option.id }
                                        .testTag("theme_option_${option.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(option.primaryColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = option.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = option.primaryColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Clinic Contact & Identity Form
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Clinic Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = clinicName,
                            onValueChange = { clinicName = it },
                            label = { Text("Clinic Name *") },
                            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("profile_input_clinic_name")
                        )

                        OutlinedTextField(
                            value = doctorName,
                            onValueChange = { doctorName = it },
                            label = { Text("Doctor / In-Charge Name & Degree *") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("profile_input_doctor_name")
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Mobile / Helpline *") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("profile_input_phone")
                            )

                            OutlinedTextField(
                                value = registrationNumber,
                                onValueChange = { registrationNumber = it },
                                label = { Text("Registration No.") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("profile_input_reg_num")
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("profile_input_email")
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Clinic Address (Prints on Bills & Reminders) *") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("profile_input_address")
                        )

                        OutlinedTextField(
                            value = billFooterNote,
                            onValueChange = { billFooterNote = it },
                            label = { Text("Bill Footer Note / Good Health Greeting") },
                            placeholder = { Text("Wishing your little one good health!") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth().testTag("profile_input_footer_note")
                        )
                    }
                }
            }

            // Multi-Device Cloud Sync & Account
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (sessionState?.isAuthenticated == true) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cross-Device Data Sync",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (sessionState?.isAuthenticated == true) {
                                Surface(
                                    color = Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Online Sync Active",
                                        color = Color(0xFF2E7D32),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (sessionState?.isAuthenticated == true)
                                "Logged in as ${sessionState.displayName} (${sessionState.displayEmailOrPhone}). Syncs inventory, patients, and bills to all clinic phones and tablets."
                            else
                                "Login with your Gmail account or Mobile number with OTP to automatically sync stock and patient data between different devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenAuthSyncDialog,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_manage_sync_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (sessionState?.isAuthenticated == true) Icons.Default.Devices else Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (sessionState?.isAuthenticated == true) "Manage Sync" else "Login / Setup")
                            }

                            if (sessionState?.isAuthenticated == true) {
                                OutlinedButton(
                                    onClick = onSyncRequested,
                                    enabled = syncState?.isSyncing != true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_sync_now_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (syncState?.isSyncing == true) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sync Now")
                                    }
                                }
                            }
                        }

                        if (sessionState?.isAuthenticated == true) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onSignOutRequested,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_logout_btn"),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Log Out",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (sessionState.displayEmailOrPhone.isNotBlank()) "Log Out (${sessionState.displayEmailOrPhone})" else "Log Out",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Direct Application Update & Version Control Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_app_update_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (updateState.isUpdateAvailable) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdate,
                                            contentDescription = null,
                                            tint = if (updateState.isUpdateAvailable) Color(0xFF15803D) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = tr("Direct Application Update", "सीधे ऐप अपडेट करें (App Update)"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (updateState.isUpdateAvailable) {
                                            tr(
                                                "Update v${updateState.latestAvailableVersion} Ready • Current: v${updateState.currentModuleVersion}",
                                                "नया अपडेट v${updateState.latestAvailableVersion} उपलब्ध • मौजूदा: v${updateState.currentModuleVersion}"
                                            )
                                        } else {
                                            tr(
                                                "Installed Version: v${updateState.currentModuleVersion} (Up to Date)",
                                                "मौजूदा वर्ज़न: v${updateState.currentModuleVersion} (अपडेटेड)"
                                            )
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (updateState.isUpdateAvailable) Color(0xFF15803D) else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = tr(
                                "Check for and install the latest app features, IAP vaccine schedules, or full package updates directly from the application without losing any old clinic data.",
                                "बिना कोई पुराना डेटा खोए सीधे एप्लिकेशन से नया अपडेट, IAP वैक्सीन शेड्यूल और नए फीचर्स इंस्टॉल करें।"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onOpenAppUpdateDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (updateState.isUpdateAvailable) Color(0xFF0D7A3E) else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_update_app_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (updateState.isUpdateAvailable) {
                                    tr("Update Application Now (v${updateState.latestAvailableVersion})", "अभी ऐप अपडेट करें (v${updateState.latestAvailableVersion})")
                                } else {
                                    tr("Open App Update Center", "ऐप अपडेट सेंटर खोलें")
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Doctor Digital Signature Settings Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Draw,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Doctor's Digital Signature",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (savedSignatureBitmap != null) "Verified Signature Stored" else "No default signature saved",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (savedSignatureBitmap != null) Color(0xFF15803D) else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Save your digital signature once to automatically sign patient immunization receipts, bills, and PDF documents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (savedSignatureBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(BorderStroke(1.dp, Color(0xFF86EFAC)), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = savedSignatureBitmap!!.asImageBitmap(),
                                    contentDescription = "Stored Signature Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showSignatureDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_configure_signature"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (savedSignatureBitmap != null) "Change Signature" else "Add Digital Signature")
                            }

                            if (savedSignatureBitmap != null) {
                                OutlinedButton(
                                    onClick = {
                                        SignatureManager.clearSavedSignature(context)
                                        savedSignatureBitmap = null
                                        Toast.makeText(context, "Stored signature removed", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("btn_clear_saved_signature")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Save & Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (clinicName.isBlank() || phone.isBlank() || address.isBlank()) {
                                Toast.makeText(context, "Please enter Clinic Name, Phone, and Address", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val updated = currentProfile.copy(
                                clinicName = clinicName.trim(),
                                doctorName = doctorName.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                registrationNumber = registrationNumber.trim(),
                                logoType = logoType,
                                themeColor = themeColor,
                                billFooterNote = billFooterNote.trim(),
                                photoUri = photoUri
                            )

                            onSaveProfile(updated)
                            Toast.makeText(context, "Clinic Profile updated successfully!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_save_clinic_profile")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Clinic Profile", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val testMessage = """
*${clinicName.ifBlank { "Dr Priyank Vaccine Clinic" }}* 🩺
*टीकाकरण (वैक्सीन) रिमाइंडर — टेस्ट संदेश*

नमस्ते अभिभावक जी 🙏,
यह आपके क्लिनिक से हिंदी WhatsApp वैक्सीन रिमाइंडर का टेस्ट संदेश है:

📍 *क्लिनिक का पता:* ${address.ifBlank { "Clinic Address" }}
📞 *संपर्क / WhatsApp:* ${phone.ifBlank { "+91 98765 43210" }}
👨‍⚕️ *डॉक्टर:* ${doctorName.ifBlank { "Dr. Priyank" }}

_आपके बच्चे के उत्तम स्वास्थ्य और उज्ज्वल भविष्य की कामना!_ 🌟
                            """.trimIndent()
                            WhatsAppHelper.sendWhatsAppText(context, phone, testMessage)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_test_whatsapp")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr("Send Test Hindi WhatsApp Reminder", "हिंदी WhatsApp रिमाइंडर टेस्ट भेजें"))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showSignatureDialog) {
        val dummyProfile = currentProfile.copy(
            clinicName = clinicName,
            doctorName = doctorName,
            registrationNumber = registrationNumber
        )
        DigitalSignatureDialog(
            clinic = dummyProfile,
            onDismiss = { showSignatureDialog = false },
            onSignatureConfirmed = { bitmap, _, _, _ ->
                SignatureManager.saveSignature(context, bitmap)
                savedSignatureBitmap = bitmap
                Toast.makeText(context, "Doctor digital signature saved successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
