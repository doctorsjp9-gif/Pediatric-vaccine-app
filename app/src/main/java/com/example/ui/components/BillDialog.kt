package com.example.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ClinicProfile
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.util.BillData
import com.example.ui.util.PdfBillGenerator
import com.example.ui.util.SignatureManager
import com.example.ui.util.WhatsAppHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BillDialog(
    billData: BillData,
    clinic: ClinicProfile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var currentBillData by remember(billData) { mutableStateOf(billData) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var previewBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    val hasSavedSig = remember { SignatureManager.hasSavedSignature(context) }

    val formattedDate = remember(currentBillData.timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(currentBillData.timestamp))
    }

    val formattedSigDate = remember(currentBillData.signatureDate) {
        currentBillData.signatureDate?.let {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(it))
        } ?: ""
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
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Immunization Bill", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Bill Card Container
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        // Clean Colorful Aligned Medical Header Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFF005B66), Color(0xFF00838F))
                                    )
                                )
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ClinicLogoAvatar(
                                    profile = clinic,
                                    size = 50.dp,
                                    border = BorderStroke(2.dp, Color.White)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = clinic.clinicName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = clinic.doctorName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE0F7FA)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Strictly Aligned Registration Number Row
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF00444D),
                                        border = BorderStroke(1.dp, Color(0xFF4DD0E1))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Reg. No.",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF80DEEA),
                                                modifier = Modifier.width(52.dp)
                                            )
                                            Text(
                                                text = ": ",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF80DEEA)
                                            )
                                            Text(
                                                text = clinic.registrationNumber.ifBlank { "N/A" },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFF59D)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${clinic.address} • Ph: ${clinic.phone}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFB2EBF2)
                                    )
                                }
                            }
                        }

                        // Gold-Teal Accent Strip
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFFFFB300), Color(0xFF26C6DA))
                                    )
                                )
                        )

                        Column(modifier = Modifier.padding(14.dp)) {
                            // Aligned Bill Metadata & Patient Details Grid
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF4FBFB),
                                border = BorderStroke(1.dp, Color(0xFFB2DFDB)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AlignedBillRow("Bill / Inv No.", currentBillData.invoiceNumber, highlight = true)
                                    AlignedBillRow("Bill Date", formattedDate)
                                    AlignedBillRow(
                                        "Patient / Child",
                                        "${currentBillData.patientName} ${if (currentBillData.ageOrDob.isNotBlank()) "(${currentBillData.ageOrDob})" else ""}",
                                        highlight = true
                                    )
                                    if (currentBillData.parentName.isNotBlank() || currentBillData.mobileNumber.isNotBlank()) {
                                        AlignedBillRow(
                                            "Parent / Ph",
                                            "${currentBillData.parentName} ${if (currentBillData.mobileNumber.isNotBlank()) "• ${currentBillData.mobileNumber}" else ""}"
                                        )
                                    }
                                    AlignedBillRow("Doctor Reg. No.", clinic.registrationNumber.ifBlank { "N/A" }, highlight = true)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Administered Vaccines", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))

                            // Line items table
                            currentBillData.items.forEachIndexed { index, item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${index + 1}. ${item.description}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (item.batchNumber.isNotBlank()) {
                                                Text(
                                                    text = "Batch: ${item.batchNumber}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Qty: ${item.quantity} × ₹${String.format("%.2f", item.unitPrice)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = "₹${String.format("%.2f", item.totalAmount)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Total Row
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ProfitGreenContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Total Paid (Rupees)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF003916)
                                    )
                                    Text(
                                        text = "₹${String.format("%.2f", currentBillData.grandTotal)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ProfitGreen
                                    )
                                }
                            }

                            // Next Vaccine Callout if present
                            if (currentBillData.nextVaccine.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFF8E1),
                                    border = BorderStroke(1.dp, Color(0xFFFFE082)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "🗓️ Next Planned Vaccine",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                        Text(
                                            text = "${currentBillData.nextVaccine} (Due: ${currentBillData.nextDueDate})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF37474F)
                                        )
                                    }
                                }
                            }

                            // Authorized Signatory & Digital Signature Section
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(10.dp))

                            if (currentBillData.isDigitallySigned) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Verified,
                                                    contentDescription = null,
                                                    tint = Color(0xFF15803D),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Digitally Signed & Certified",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF14532D)
                                                )
                                            }

                                            Row {
                                                TextButton(
                                                    onClick = { showSignatureDialog = true },
                                                    modifier = Modifier.testTag("btn_change_signature")
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Change", fontSize = 11.sp)
                                                }
                                                TextButton(
                                                    onClick = {
                                                        currentBillData = currentBillData.copy(
                                                            isDigitallySigned = false,
                                                            signatureBitmap = null,
                                                            signatureId = "",
                                                            signatureDate = null
                                                        )
                                                        Toast.makeText(context, "Signature removed", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.testTag("btn_remove_signature")
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Remove", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }

                                        val sigBmp = currentBillData.signatureBitmap
                                        if (sigBmp != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(64.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.White)
                                                    .border(BorderStroke(1.dp, Color(0xFFDCFCE7)), RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    bitmap = sigBmp.asImageBitmap(),
                                                    contentDescription = "Digital Signature Preview",
                                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Signer: ${currentBillData.signerName.ifBlank { clinic.doctorName }} • Reg. No. : ${clinic.registrationNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = "Verified: $formattedSigDate • ID: ${currentBillData.signatureId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF059669)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Draw,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Doctor's Digital Signature",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = "Bill is currently unsigned. Sign with finger, stylus, or clinic e-seal.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (hasSavedSig) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Button(
                                                    onClick = {
                                                        val bmp = SignatureManager.getSavedSignature(context)
                                                        if (bmp != null) {
                                                            val sigId = SignatureManager.generateSignatureId()
                                                            currentBillData = currentBillData.copy(
                                                                isDigitallySigned = true,
                                                                signatureBitmap = bmp,
                                                                signatureType = "SAVED",
                                                                signerName = clinic.doctorName,
                                                                signatureId = sigId,
                                                                signatureDate = System.currentTimeMillis()
                                                            )
                                                            Toast.makeText(context, "Signed with doctor's saved signature!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            showSignatureDialog = true
                                                        }
                                                    },
                                                    modifier = Modifier.testTag("btn_quick_sign_saved")
                                                ) {
                                                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Quick Sign", fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { showSignatureDialog = true },
                                                    modifier = Modifier.testTag("btn_digitally_sign_bill")
                                                ) {
                                                    Icon(Icons.Default.Draw, contentDescription = "Draw New Signature")
                                                }
                                            }
                                        } else {
                                            FilledTonalButton(
                                                onClick = { showSignatureDialog = true },
                                                modifier = Modifier.testTag("btn_digitally_sign_bill")
                                            ) {
                                                Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Sign Bill", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions Card
                Text("Generate PDF & Share Bill", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                // Digitally sign bill quick action if not yet signed
                if (!currentBillData.isDigitallySigned) {
                    OutlinedButton(
                        onClick = { showSignatureDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_action_sign_bill")
                    ) {
                        Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Digital Signature to Bill")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Preview & Download Clean PDF Bill Button
                Button(
                    onClick = {
                        isGeneratingPdf = true
                        val result = PdfBillGenerator.generateBillPdf(context, currentBillData, clinic)
                        isGeneratingPdf = false
                        result.onSuccess { pdfFile ->
                            previewPdfFile = pdfFile
                            previewBitmaps = PdfBillGenerator.renderPdfPagesToBitmaps(pdfFile)
                        }.onFailure { ex ->
                            Toast.makeText(context, "Failed to generate PDF: ${ex.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_preview_pdf_bill"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006874))
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Preview / Save Clean PDF Bill",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 1. Share as PDF by WhatsApp Button (PRIMARY)
                Button(
                    onClick = {
                        isGeneratingPdf = true
                        val result = PdfBillGenerator.generateBillPdf(context, currentBillData, clinic)
                        isGeneratingPdf = false
                        result.onSuccess { pdfFile ->
                            WhatsAppHelper.shareBillPdf(
                                context = context,
                                pdfFile = pdfFile,
                                patientPhone = currentBillData.mobileNumber,
                                billData = currentBillData,
                                clinic = clinic
                            )
                        }.onFailure { ex ->
                            Toast.makeText(context, "Failed to generate PDF: ${ex.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_share_pdf_whatsapp"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentBillData.isDigitallySigned) "Share Signed PDF by WhatsApp" else "Share as PDF by WhatsApp",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Share Text Bill by WhatsApp
                OutlinedButton(
                    onClick = {
                        val message = WhatsAppHelper.buildTextBillMessage(currentBillData, clinic)
                        WhatsAppHelper.sendWhatsAppText(context, currentBillData.mobileNumber, message)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_share_text_whatsapp")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Text Receipt on WhatsApp")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("btn_bill_done")) {
                Text("Done")
            }
        }
    )

    // In-App Rendered PDF Bill Preview Dialog
    val activePdf = previewPdfFile
    if (activePdf != null) {
        AlertDialog(
            onDismissRequest = { previewPdfFile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(12.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFF006874)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generated PDF Bill Preview", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { previewPdfFile = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
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
                    previewBitmaps.forEachIndexed { idx, bmp ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFB0BEC5)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "PDF Bill Page ${idx + 1}",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val res = PdfBillGenerator.savePdfToDownloads(
                                    context,
                                    activePdf,
                                    "Vaccine_Bill_${currentBillData.invoiceNumber}.pdf"
                                )
                                res.onSuccess { path ->
                                    Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                                }.onFailure { e ->
                                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006874))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                PdfBillGenerator.printPdfFile(
                                    context,
                                    activePdf,
                                    "Vaccine Bill ${currentBillData.invoiceNumber}"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print PDF")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewPdfFile = null }) {
                    Text("Close Preview")
                }
            }
        )
    }

    if (showSignatureDialog) {
        DigitalSignatureDialog(
            clinic = clinic,
            onDismiss = { showSignatureDialog = false },
            onSignatureConfirmed = { bitmap, sigType, signerName, signatureId ->
                currentBillData = currentBillData.copy(
                    isDigitallySigned = true,
                    signatureBitmap = bitmap,
                    signatureType = sigType,
                    signerName = signerName,
                    signatureId = signatureId,
                    signatureDate = System.currentTimeMillis()
                )
                Toast.makeText(context, "Bill digitally signed successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun AlignedBillRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF455A64),
            modifier = Modifier.width(105.dp)
        )
        Text(
            text = ": ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF455A64)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) Color(0xFF005B66) else Color(0xFF191C1D),
            modifier = Modifier.weight(1f)
        )
    }
}
