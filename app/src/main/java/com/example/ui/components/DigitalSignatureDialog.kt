package com.example.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ClinicProfile
import com.example.ui.theme.ProfitGreen
import com.example.ui.util.SignatureManager

enum class SignatureMode {
    DRAW,
    CERTIFIED_STAMP,
    SAVED_SIGNATURE
}

@Composable
fun DigitalSignatureDialog(
    clinic: ClinicProfile,
    onDismiss: () -> Unit,
    onSignatureConfirmed: (bitmap: Bitmap, signatureType: String, signerName: String, signatureId: String) -> Unit
) {
    val context = LocalContext.current

    val hasSaved = remember { SignatureManager.hasSavedSignature(context) }
    var selectedMode by remember {
        mutableStateOf(if (hasSaved) SignatureMode.SAVED_SIGNATURE else SignatureMode.DRAW)
    }

    // Touch strokes for DRAW mode
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }
    var saveAsDefault by remember { mutableStateOf(true) }
    var selectedInkColor by remember { mutableStateOf("#0A2540") } // Navy Blue by default

    // Saved signature bitmap (if loaded)
    var savedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        if (hasSaved) {
            savedBitmap = SignatureManager.getSavedSignature(context)
        }
    }

    // Pre-generated stamp bitmap for Certified Stamp mode
    val stampSignatureId = remember { SignatureManager.generateSignatureId() }
    val certifiedStampBitmap = remember(clinic) {
        SignatureManager.createCertifiedSealBitmap(
            doctorName = clinic.doctorName,
            registrationNumber = clinic.registrationNumber,
            clinicName = clinic.clinicName,
            signatureId = stampSignatureId
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .padding(vertical = 12.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Digitally Sign Bill", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = clinic.doctorName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Segmented Tabs for Signature Option
                TabRow(
                    selectedTabIndex = when (selectedMode) {
                        SignatureMode.DRAW -> 0
                        SignatureMode.CERTIFIED_STAMP -> 1
                        SignatureMode.SAVED_SIGNATURE -> 2
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedMode == SignatureMode.DRAW,
                        onClick = { selectedMode = SignatureMode.DRAW },
                        text = { Text("Draw", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("tab_draw_signature")
                    )
                    Tab(
                        selected = selectedMode == SignatureMode.CERTIFIED_STAMP,
                        onClick = { selectedMode = SignatureMode.CERTIFIED_STAMP },
                        text = { Text("Official Seal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("tab_stamp_signature")
                    )
                    if (hasSaved) {
                        Tab(
                            selected = selectedMode == SignatureMode.SAVED_SIGNATURE,
                            onClick = { selectedMode = SignatureMode.SAVED_SIGNATURE },
                            text = { Text("Saved", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("tab_saved_signature")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (selectedMode) {
                    SignatureMode.DRAW -> {
                        // Drawing Controls Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sign in the box below using finger or stylus:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row {
                                // Ink Color Chips
                                FilterChip(
                                    selected = selectedInkColor == "#0A2540",
                                    onClick = { selectedInkColor = "#0A2540" },
                                    label = { Text("Navy", fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                FilterChip(
                                    selected = selectedInkColor == "#111827",
                                    onClick = { selectedInkColor = "#111827" },
                                    label = { Text("Black", fontSize = 10.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Interactive Signature Drawing Canvas Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(10.dp))
                                .onSizeChanged { canvasSize = it }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentStroke = listOf(offset)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentStroke = currentStroke + change.position
                                        },
                                        onDragEnd = {
                                            if (currentStroke.isNotEmpty()) {
                                                completedStrokes.add(currentStroke)
                                                currentStroke = emptyList()
                                            }
                                        },
                                        onDragCancel = {
                                            currentStroke = emptyList()
                                        }
                                    )
                                }
                                .testTag("signature_canvas")
                        ) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                // Baseline guide
                                drawLine(
                                    color = Color(0xFFD1D5DB),
                                    start = Offset(24f, size.height - 36f),
                                    end = Offset(size.width - 24f, size.height - 36f),
                                    strokeWidth = 1.5f
                                )

                                val parsedInkColor = Color(android.graphics.Color.parseColor(selectedInkColor))

                                // Render completed strokes
                                for (stroke in completedStrokes) {
                                    if (stroke.size == 1) {
                                        drawCircle(parsedInkColor, radius = 3.5f, center = stroke.first())
                                    } else {
                                        for (i in 0 until stroke.size - 1) {
                                            drawLine(
                                                color = parsedInkColor,
                                                start = stroke[i],
                                                end = stroke[i + 1],
                                                strokeWidth = 5.5f,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }

                                // Render stroke currently being drawn
                                if (currentStroke.size > 1) {
                                    for (i in 0 until currentStroke.size - 1) {
                                        drawLine(
                                            color = parsedInkColor,
                                            start = currentStroke[i],
                                            end = currentStroke[i + 1],
                                            strokeWidth = 5.5f,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }

                            // Watermark / Placeholder when empty
                            if (completedStrokes.isEmpty() && currentStroke.isEmpty()) {
                                Text(
                                    text = "✍️ Sign Here",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            // Signatory indicator bottom label
                            Text(
                                text = "Authorized Medical Signatory",
                                color = Color(0xFF9CA3AF),
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 24.dp, bottom = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Canvas Tools Row: Clear, Undo, Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        if (completedStrokes.isNotEmpty()) {
                                            completedStrokes.removeAt(completedStrokes.lastIndex)
                                        }
                                    },
                                    enabled = completedStrokes.isNotEmpty(),
                                    modifier = Modifier.testTag("btn_undo_stroke")
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Undo", fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        completedStrokes.clear()
                                        currentStroke = emptyList()
                                    },
                                    enabled = completedStrokes.isNotEmpty(),
                                    modifier = Modifier.testTag("btn_clear_canvas")
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }

                            Text(
                                text = "${completedStrokes.size} strokes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        // Save as default checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = saveAsDefault,
                                onCheckedChange = { saveAsDefault = it },
                                modifier = Modifier.testTag("checkbox_save_signature_default")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Remember signature for future bills",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    SignatureMode.CERTIFIED_STAMP -> {
                        // Stamp Description
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF0FDF4),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Official Clinic e-Seal",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF14532D)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Generates a certified digital authenticity seal containing Doctor's registration, date/timestamp, and a cryptographically verifiable transaction ID.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF166534)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Visual Preview of the Stamp
                                Image(
                                    bitmap = certifiedStampBitmap.asImageBitmap(),
                                    contentDescription = "Certified Medical Seal Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    SignatureMode.SAVED_SIGNATURE -> {
                        // Stored Signature
                        if (savedBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Saved Signature on Device",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        TextButton(
                                            onClick = {
                                                SignatureManager.clearSavedSignature(context)
                                                savedBitmap = null
                                                selectedMode = SignatureMode.DRAW
                                            }
                                        ) {
                                            Text("Delete Saved", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = savedBitmap!!.asImageBitmap(),
                                            contentDescription = "Stored Doctor Signature",
                                            modifier = Modifier.fillMaxWidth().height(100.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Click 'Apply Signature' to instantly sign this bill using this signature.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No saved signature found. Switch to 'Draw' to create one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedMode) {
                        SignatureMode.DRAW -> {
                            if (completedStrokes.isEmpty()) {
                                Toast.makeText(context, "Please sign on the canvas first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val bitmap = SignatureManager.renderStrokesToBitmap(
                                strokes = completedStrokes,
                                viewWidth = canvasSize.width.coerceAtLeast(300),
                                viewHeight = canvasSize.height.coerceAtLeast(150),
                                inkColorHex = selectedInkColor
                            )
                            if (saveAsDefault) {
                                SignatureManager.saveSignature(context, bitmap)
                            }
                            val signatureId = SignatureManager.generateSignatureId()
                            onSignatureConfirmed(bitmap, "DRAWN", clinic.doctorName, signatureId)
                            onDismiss()
                        }
                        SignatureMode.CERTIFIED_STAMP -> {
                            onSignatureConfirmed(certifiedStampBitmap, "CERTIFIED_SEAL", clinic.doctorName, stampSignatureId)
                            onDismiss()
                        }
                        SignatureMode.SAVED_SIGNATURE -> {
                            val bmp = savedBitmap
                            if (bmp != null) {
                                val signatureId = SignatureManager.generateSignatureId()
                                onSignatureConfirmed(bmp, "SAVED", clinic.doctorName, signatureId)
                                onDismiss()
                            } else {
                                Toast.makeText(context, "No saved signature available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("btn_apply_signature"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Apply Signature to Bill")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.testTag("btn_cancel_signature")) {
                Text("Cancel")
            }
        }
    )
}
