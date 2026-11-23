package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.data.repository.ScannedBillStockItem
import com.example.util.BillScanUiState
import com.example.util.BillScannerHelper
import com.example.util.ScannedBillLineItem
import com.example.util.ScannedBillResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScanStockDialog(
    vaccines: List<Vaccine>,
    vendors: List<Vendor>,
    preSelectedVaccineId: Long? = null,
    onDismiss: () -> Unit,
    onApplyScannedBill: (
        vendorId: Long?,
        newVendorName: String,
        newVendorPhone: String,
        invoiceNumber: String,
        notes: String,
        items: List<ScannedBillStockItem>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestVaccines by rememberUpdatedState(vaccines)
    val latestVendors by rememberUpdatedState(vendors)

    var scanState by remember { mutableStateOf<BillScanUiState>(BillScanUiState.Idle) }
    var billPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedSourceLabel by rememberSaveable { mutableStateOf("") }
    var pendingCameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    // Bill header state
    var selectedVendorId by rememberSaveable { mutableStateOf<Long?>(vendors.firstOrNull()?.id) }
    var vendorDropdownExpanded by remember { mutableStateOf(false) }
    var newVendorName by rememberSaveable { mutableStateOf("") }
    var newVendorPhone by rememberSaveable { mutableStateOf("") }
    var invoiceNumber by rememberSaveable { mutableStateOf("") }
    var invoiceDate by rememberSaveable { mutableStateOf("") }
    var showSupplierSection by rememberSaveable { mutableStateOf(false) }

    // Editable scanned line items
    val lineItems = remember { mutableStateListOf<ScannedBillLineItem>() }

    // Inline vaccine selector state
    var inlineSelectorItemIndex by remember { mutableStateOf<Int?>(null) }
    var inlineSelectorSearchQuery by remember { mutableStateOf("") }

    fun createLineItemForVaccine(targetVac: Vaccine?, defaultQty: Int = 10): ScannedBillLineItem {
        return ScannedBillLineItem(
            id = "row_${System.currentTimeMillis()}_${(100..999).random()}",
            included = true,
            extractedName = targetVac?.name ?: "Pediatric Vaccine",
            matchedVaccineId = targetVac?.id,
            category = targetVac?.category ?: "Routine Pediatric",
            code = targetVac?.code ?: "VAC",
            quantityText = defaultQty.toString(),
            unitPurchasePriceText = BillScannerHelper.formatPriceInput(targetVac?.purchasePrice ?: 0.0),
            sellingPriceText = BillScannerHelper.formatPriceInput(targetVac?.sellingPrice ?: 0.0),
            batchNumber = "",
            expiryDate = ""
        )
    }

    LaunchedEffect(preSelectedVaccineId, vaccines.size) {
        if (lineItems.isEmpty() && preSelectedVaccineId != null) {
            val target = vaccines.find { it.id == preSelectedVaccineId }
            if (target != null) {
                lineItems.add(createLineItemForVaccine(target, defaultQty = 10))
            }
        }
    }

    fun applyScanResultToForm(result: ScannedBillResult) {
        if (result.invoiceNumber.isNotBlank()) {
            invoiceNumber = result.invoiceNumber
        } else if (invoiceNumber.isBlank()) {
            invoiceNumber = "BILL-${System.currentTimeMillis().toString().takeLast(4)}"
        }
        if (result.invoiceDate.isNotBlank()) {
            invoiceDate = result.invoiceDate
        }
        if (result.matchedVendorId != null) {
            selectedVendorId = result.matchedVendorId
            newVendorName = ""
            newVendorPhone = ""
        } else if (result.vendorName.isNotBlank()) {
            val matchedVendor = latestVendors.firstOrNull { v ->
                v.name.equals(result.vendorName, ignoreCase = true) ||
                    v.name.lowercase().contains(result.vendorName.lowercase()) ||
                    result.vendorName.lowercase().contains(v.name.lowercase())
            }
            if (matchedVendor != null) {
                selectedVendorId = matchedVendor.id
                newVendorName = ""
                newVendorPhone = ""
            } else {
                selectedVendorId = null
                newVendorName = result.vendorName
                newVendorPhone = result.vendorPhone
            }
        }
        if (result.items.isNotEmpty()) {
            lineItems.clear()
            lineItems.addAll(result.items)
        }
    }

    fun triggerAiScan(bitmaps: List<Bitmap>, sourceName: String) {
        if (bitmaps.isEmpty()) return
        billPages = bitmaps
        selectedSourceLabel = sourceName
        scanState = BillScanUiState.Analyzing("Reading bill & matching vaccines from $sourceName...")
        scope.launch {
            val state = BillScannerHelper.analyzeBillWithGemini(
                bitmaps = bitmaps,
                existingVaccines = latestVaccines,
                existingVendors = latestVendors,
                preSelectedVaccineId = preSelectedVaccineId,
                sourceFileName = sourceName
            )
            scanState = state
            when (state) {
                is BillScanUiState.Success -> {
                    applyScanResultToForm(state.result)
                }
                is BillScanUiState.ManualReviewNeeded -> {
                    applyScanResultToForm(state.result)
                }
                else -> {}
            }
        }
    }

    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            triggerAiScan(listOf(BillScannerHelper.scaleBitmapDown(bitmap, 2048)), "Camera Bill Photo")
        } else {
            Toast.makeText(context, "Camera closed without capturing a photo.", Toast.LENGTH_SHORT).show()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val savedPath = pendingCameraFilePath
        if (success && !savedPath.isNullOrBlank()) {
            scope.launch {
                scanState = BillScanUiState.Analyzing("Loading captured bill photo...")
                val loadResult = BillScannerHelper.loadBillBitmapFromFile(savedPath)
                loadResult.onSuccess { bitmaps ->
                    triggerAiScan(bitmaps, "Camera Bill Photo")
                }.onFailure { err ->
                    scanState = BillScanUiState.Error(
                        "Could not read captured photo (${err.localizedMessage ?: "file error"}). Please retake the bill photo or choose from Gallery."
                    )
                }
            }
        } else if (!success) {
            Toast.makeText(context, "Camera capture cancelled. Tap Camera again or pick a bill photo.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraCapture() {
        try {
            val file = BillScannerHelper.createCameraBillFile(context)
            pendingCameraFilePath = file.absolutePath
            val uri = BillScannerHelper.getUriForCameraFile(context, file)
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            try {
                takePicturePreviewLauncher.launch(null)
            } catch (ex: Exception) {
                Toast.makeText(context, "Unable to open camera: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraCapture()
        } else {
            Toast.makeText(context, "Camera permission is needed to take a picture of the bill.", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val displayName = BillScannerHelper.getDisplayName(context, uri)
            scope.launch {
                scanState = BillScanUiState.Analyzing("Loading bill image ($displayName)...")
                val loadResult = BillScannerHelper.loadBillBitmapsFromUri(context, uri)
                loadResult.onSuccess { (bitmaps, _) ->
                    triggerAiScan(bitmaps, displayName)
                }.onFailure { err ->
                    scanState = BillScanUiState.Error(
                        "Unable to open selected image (${err.localizedMessage ?: "invalid image"}). Try another photo or PDF."
                    )
                }
            }
        }
    }

    val pdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val displayName = BillScannerHelper.getDisplayName(context, uri)
            scope.launch {
                scanState = BillScanUiState.Analyzing("Rendering uploaded bill ($displayName)...")
                val loadResult = BillScannerHelper.loadBillBitmapsFromUri(context, uri)
                loadResult.onSuccess { (bitmaps, isPdf) ->
                    val label = if (isPdf) "PDF: $displayName" else displayName
                    triggerAiScan(bitmaps, label)
                }.onFailure { err ->
                    scanState = BillScanUiState.Error(
                        "Unable to open PDF/document (${err.localizedMessage ?: "unreadable file"}). Ensure the PDF is not password-protected."
                    )
                }
            }
        }
    }

    fun quickAddOrIncrementVaccine(vaccine: Vaccine) {
        val existingIdx = lineItems.indexOfFirst {
            it.matchedVaccineId == vaccine.id || it.extractedName.equals(vaccine.name, ignoreCase = true)
        }
        if (existingIdx >= 0) {
            val current = lineItems[existingIdx]
            val currentQty = current.parsedQty()
            val newQty = currentQty + 10
            val currentCost = current.parsedUnitCost()
            val currentSell = current.parsedSellingPrice()
            lineItems[existingIdx] = current.copy(
                matchedVaccineId = vaccine.id,
                extractedName = vaccine.name,
                category = vaccine.category,
                code = vaccine.code,
                quantityText = newQty.toString(),
                unitPurchasePriceText = BillScannerHelper.formatPriceInput(
                    if (currentCost > 0.0) currentCost else vaccine.purchasePrice
                ),
                sellingPriceText = BillScannerHelper.formatPriceInput(
                    if (currentSell > 0.0) currentSell else vaccine.sellingPrice
                ),
                included = true
            )
            Toast.makeText(context, "${vaccine.name}: +10 doses (Total +$newQty)", Toast.LENGTH_SHORT).show()
        } else {
            lineItems.add(
                0,
                createLineItemForVaccine(vaccine, defaultQty = 10)
            )
            Toast.makeText(context, "Added ${vaccine.name} (+10 doses) to bill list", Toast.LENGTH_SHORT).show()
        }
    }

    fun submitStockUpdate(itemsToSubmit: List<ScannedBillLineItem>) {
        val validItems = itemsToSubmit.filter { it.extractedName.isNotBlank() && it.parsedQty() > 0 }
        if (validItems.isEmpty()) {
            Toast.makeText(context, "Please set a quantity greater than 0 for at least one vaccine", Toast.LENGTH_SHORT).show()
            return
        }
        val mappedItems = validItems.map { row ->
            val matchedVac = vaccines.find { it.id == row.matchedVaccineId }
            val unitCost = row.parsedUnitCost()
            val sellPrice = row.parsedSellingPrice()
            ScannedBillStockItem(
                matchedVaccineId = row.matchedVaccineId,
                vaccineName = matchedVac?.name ?: row.extractedName.trim(),
                category = matchedVac?.category ?: row.category.ifBlank { "Routine Pediatric" },
                code = matchedVac?.code ?: row.code.ifBlank {
                    row.extractedName.take(6).uppercase().filter { it.isLetterOrDigit() }
                },
                quantity = row.parsedQty().coerceAtLeast(1),
                unitPurchasePrice = if (unitCost > 0.0) unitCost else (matchedVac?.purchasePrice ?: 0.0),
                sellingPrice = if (sellPrice > 0.0) sellPrice else (matchedVac?.sellingPrice ?: 0.0),
                batchNumber = row.batchNumber.trim(),
                expiryDate = row.expiryDate.trim()
            )
        }
        val noteSummary = buildString {
            append("Stock updated via ")
            append(selectedSourceLabel.ifBlank { "Bill Scanner" })
            if (invoiceDate.isNotBlank()) append(" (Bill Date: $invoiceDate)")
        }
        onApplyScannedBill(
            selectedVendorId,
            newVendorName.trim(),
            newVendorPhone.trim(),
            invoiceNumber.trim(),
            noteSummary,
            mappedItems
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("bill_scan_stock_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFF8FAFC),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF006874),
                                    Color(0xFF0277BD),
                                    Color(0xFF5E35B1)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DocumentScanner,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Scan Bill / Upload PDF",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Take photo or upload PDF bill to update stock",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("btn_close_bill_scan_dialog")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Main Content Body
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Source Picker Buttons
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (billPages.isEmpty()) "1. Capture or Upload Vaccine Bill" else "1. Bill Attached ($selectedSourceLabel)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0369A1),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (billPages.isNotEmpty() && scanState !is BillScanUiState.Analyzing) {
                                        TextButton(
                                            onClick = {
                                                triggerAiScan(billPages, selectedSourceLabel)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("btn_rescan_bill")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Re-scan",
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Re-Scan", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Take Pic of Bill (Camera)
                                    Button(
                                        onClick = {
                                            val hasPerm = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) {
                                                launchCameraCapture()
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0277BD),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_take_bill_photo")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddAPhoto,
                                            contentDescription = "Camera",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Camera",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }

                                    // Gallery Image
                                    Button(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF006874),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_pick_bill_image")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = "Gallery",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Photo",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }

                                    // Upload PDF Bill
                                    Button(
                                        onClick = {
                                            pdfDocumentLauncher.launch(
                                                arrayOf("application/pdf", "image/*")
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF5E35B1),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_upload_bill_pdf")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = "Upload PDF",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "PDF Bill",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Compact Preview Strip + AI Status
                                billPages.firstOrNull()?.let { bmp ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF0F9FF))
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Scanned Bill Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFE2E8F0))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            when (val st = scanState) {
                                                is BillScanUiState.Analyzing -> {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = Color(0xFF0277BD)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = st.statusText,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0277BD)
                                                        )
                                                    }
                                                }
                                                is BillScanUiState.Success -> {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoAwesome,
                                                            contentDescription = null,
                                                            tint = Color(0xFF0D7A3E),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "AI Extracted ${lineItems.size} Vaccine Item(s)",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0D7A3E)
                                                        )
                                                    }
                                                    Text(
                                                        text = "Verify quantities below & tap 'Update Stock'",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color(0xFF334155)
                                                    )
                                                }
                                                is BillScanUiState.ManualReviewNeeded -> {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.Info,
                                                            contentDescription = null,
                                                            tint = Color(0xFF0277BD),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Bill Loaded — Select Vaccines Below",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0277BD)
                                                        )
                                                    }
                                                    Text(
                                                        text = st.noticeMessage,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color(0xFF334155),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                is BillScanUiState.Error -> {
                                                    Text(
                                                        text = st.message,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                    if (billPages.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        OutlinedButton(
                                                            onClick = { triggerAiScan(billPages, selectedSourceLabel) },
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            modifier = Modifier.testTag("btn_retry_bill_ai_scan")
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Refresh,
                                                                contentDescription = "Retry AI Scan",
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Retry AI Scan",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                                // Show error banner even if image decoding failed before billPages was populated
                                if (billPages.isEmpty() && scanState is BillScanUiState.Error) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFFEF2F2),
                                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = (scanState as BillScanUiState.Error).message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFB91C1C),
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Quick-Tap Clinic Vaccine Selector Bar
                    if (vaccines.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Vaccines,
                                            contentDescription = null,
                                            tint = Color(0xFF006874),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Quick-Tap Clinic Vaccines (+10 Doses per tap):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF006874)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        vaccines.forEach { vac ->
                                            val isSelectedInBill = lineItems.any { it.matchedVaccineId == vac.id && it.included }
                                            FilterChip(
                                                selected = isSelectedInBill,
                                                onClick = { quickAddOrIncrementVaccine(vac) },
                                                label = {
                                                    Text(
                                                        text = "${vac.name} (${vac.currentStock})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (isSelectedInBill) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = if (isSelectedInBill) Icons.Default.CheckCircle else Icons.Default.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFFE0F7FA),
                                                    selectedLabelColor = Color(0xFF006874),
                                                    selectedLeadingIconColor = Color(0xFF006874)
                                                ),
                                                modifier = Modifier.testTag("chip_quick_add_vac_${vac.id}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Vaccines to Restock Section Header + Add Row Button
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = Color(0xFF0D7A3E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "2. Vaccines to Restock (${lineItems.count { it.included }})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0D7A3E)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val defaultVac = vaccines.firstOrNull { v ->
                                        lineItems.none { it.matchedVaccineId == v.id }
                                    } ?: vaccines.firstOrNull()
                                    lineItems.add(createLineItemForVaccine(defaultVac, defaultQty = 10))
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_add_scanned_item_row")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Row",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Vaccine", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Empty prompt card if no rows yet
                    if (lineItems.isEmpty() && scanState !is BillScanUiState.Analyzing) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = Color(0xFF0277BD),
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Take a Photo of Bill, Upload PDF, or Tap a Vaccine Chip Above",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "You can scan a vendor invoice or tap any clinic vaccine chip above to immediately add doses to your stock.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF475569)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            val defaultVac = vaccines.find { it.id == preSelectedVaccineId }
                                                ?: vaccines.minByOrNull { it.currentStock }
                                                ?: vaccines.firstOrNull()
                                            lineItems.add(createLineItemForVaccine(defaultVac, defaultQty = 10))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("btn_add_first_vaccine_row")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Select Vaccine to Update Stock", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 4. Scanned / Selected Vaccine Line Item Cards
                    itemsIndexed(lineItems) { index, item ->
                        val matchedVac = vaccines.find { it.id == item.matchedVaccineId }
                        val isInlineSelectorOpen = inlineSelectorItemIndex == index

                        ScannedBillLineItemCard(
                            index = index,
                            item = item,
                            matchedVaccine = matchedVac,
                            allVaccines = vaccines,
                            isInlineSelectorOpen = isInlineSelectorOpen,
                            inlineSelectorSearchQuery = inlineSelectorSearchQuery,
                            onToggleInlineSelector = {
                                if (inlineSelectorItemIndex == index) {
                                    inlineSelectorItemIndex = null
                                } else {
                                    inlineSelectorSearchQuery = ""
                                    inlineSelectorItemIndex = index
                                }
                            },
                            onInlineSearchQueryChange = { inlineSelectorSearchQuery = it },
                            onSelectMatchedVaccine = { chosenVac ->
                                if (chosenVac != null) {
                                    val curCost = item.parsedUnitCost()
                                    val curSell = item.parsedSellingPrice()
                                    lineItems[index] = item.copy(
                                        matchedVaccineId = chosenVac.id,
                                        extractedName = chosenVac.name,
                                        category = chosenVac.category,
                                        code = chosenVac.code,
                                        unitPurchasePriceText = BillScannerHelper.formatPriceInput(
                                            if (curCost > 0.0) curCost else chosenVac.purchasePrice
                                        ),
                                        sellingPriceText = BillScannerHelper.formatPriceInput(
                                            if (curSell > 0.0) curSell else chosenVac.sellingPrice
                                        )
                                    )
                                } else {
                                    lineItems[index] = item.copy(matchedVaccineId = null)
                                }
                                inlineSelectorItemIndex = null
                            },
                            onUpdateItem = { updated ->
                                lineItems[index] = updated
                            },
                            onRemoveItem = {
                                if (inlineSelectorItemIndex == index) {
                                    inlineSelectorItemIndex = null
                                }
                                lineItems.removeAt(index)
                            },
                            onQuickApplySingleItem = {
                                submitStockUpdate(listOf(item.copy(included = true)))
                            }
                        )
                    }

                    // 5. Collapsible Supplier & Invoice Details Section
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showSupplierSection = !showSupplierSection },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Business,
                                            contentDescription = null,
                                            tint = Color(0xFF006874),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "3. Bill Supplier & Invoice Info (Optional)",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF006874)
                                            )
                                            val vendorSummary = vendors.find { it.id == selectedVendorId }?.name
                                                ?: newVendorName.ifBlank { "Default Supplier" }
                                            Text(
                                                text = "$vendorSummary • Invoice: ${invoiceNumber.ifBlank { "Auto" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (showSupplierSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand Supplier Info",
                                        tint = Color(0xFF475569)
                                    )
                                }

                                AnimatedVisibility(visible = showSupplierSection) {
                                    Column(modifier = Modifier.padding(top = 10.dp)) {
                                        ExposedDropdownMenuBox(
                                            expanded = vendorDropdownExpanded,
                                            onExpandedChange = { vendorDropdownExpanded = !vendorDropdownExpanded }
                                        ) {
                                            val selectedVendorObj = vendors.find { it.id == selectedVendorId }
                                            val displayVendorText = when {
                                                selectedVendorObj != null -> "${selectedVendorObj.name} (${selectedVendorObj.phone})"
                                                newVendorName.isNotBlank() -> "New Supplier: $newVendorName"
                                                else -> "Select Existing Vendor or Add New"
                                            }
                                            OutlinedTextField(
                                                value = displayVendorText,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Matched Clinic Vendor") },
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = vendorDropdownExpanded)
                                                },
                                                modifier = Modifier
                                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                    .fillMaxWidth()
                                                    .testTag("bill_scan_vendor_dropdown"),
                                                singleLine = true
                                            )
                                            ExposedDropdownMenu(
                                                expanded = vendorDropdownExpanded,
                                                onDismissRequest = { vendorDropdownExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "+ Create / Use New Supplier from Bill",
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedVendorId = null
                                                        vendorDropdownExpanded = false
                                                    }
                                                )
                                                vendors.forEach { vendor ->
                                                    DropdownMenuItem(
                                                        text = { Text("${vendor.name} • ${vendor.phone}") },
                                                        onClick = {
                                                            selectedVendorId = vendor.id
                                                            vendorDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        if (selectedVendorId == null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = newVendorName,
                                                    onValueChange = { newVendorName = it },
                                                    label = { Text("Supplier Name") },
                                                    singleLine = true,
                                                    modifier = Modifier
                                                        .weight(1.2f)
                                                        .testTag("input_scanned_vendor_name")
                                                )
                                                OutlinedTextField(
                                                    value = newVendorPhone,
                                                    onValueChange = { newVendorPhone = it },
                                                    label = { Text("Supplier Phone") },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                                    modifier = Modifier
                                                        .weight(0.8f)
                                                        .testTag("input_scanned_vendor_phone")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = invoiceNumber,
                                                onValueChange = { invoiceNumber = it },
                                                label = { Text("Bill / Invoice No.") },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Description,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("input_scanned_invoice_no")
                                            )
                                            OutlinedTextField(
                                                value = invoiceDate,
                                                onValueChange = { invoiceDate = it },
                                                label = { Text("Bill Date") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("input_scanned_invoice_date")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Bar
                HorizontalDivider(color = Color(0xFFE2E8F0))
                val includedItems = lineItems.filter { it.included && it.extractedName.isNotBlank() && it.parsedQty() > 0 }
                val totalDosesToAdd: Int = includedItems.sumOf { it.parsedQty() }
                val totalBillAmount: Double = includedItems.sumOf { row ->
                    val matchedVac = vaccines.find { v -> v.id == row.matchedVaccineId }
                    val unitCost = if (row.parsedUnitCost() > 0.0) row.parsedUnitCost() else (matchedVac?.purchasePrice ?: 0.0)
                    row.parsedQty() * unitCost
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${includedItems.size} Vaccines • +$totalDosesToAdd Doses",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF006874)
                            )
                            Text(
                                text = "Total Cost: ₹${String.format("%.2f", totalBillAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D7A3E)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("btn_cancel_bill_scan")
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = { submitStockUpdate(includedItems) },
                                enabled = includedItems.isNotEmpty() && totalDosesToAdd > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0D7A3E),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("btn_apply_scanned_bill_stock")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Update Stock (+$totalDosesToAdd)",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ScannedBillLineItem.parsedQty(): Int =
    quantityText.trim().toIntOrNull() ?: 0

private fun ScannedBillLineItem.parsedUnitCost(): Double =
    unitPurchasePriceText.trim().toDoubleOrNull() ?: 0.0

private fun ScannedBillLineItem.parsedSellingPrice(): Double =
    sellingPriceText.trim().toDoubleOrNull() ?: 0.0

@Composable
private fun ScannedBillLineItemCard(
    index: Int,
    item: ScannedBillLineItem,
    matchedVaccine: Vaccine?,
    allVaccines: List<Vaccine>,
    isInlineSelectorOpen: Boolean,
    inlineSelectorSearchQuery: String,
    onToggleInlineSelector: () -> Unit,
    onInlineSearchQueryChange: (String) -> Unit,
    onSelectMatchedVaccine: (Vaccine?) -> Unit,
    onUpdateItem: (ScannedBillLineItem) -> Unit,
    onRemoveItem: () -> Unit,
    onQuickApplySingleItem: () -> Unit
) {
    val currentQty = item.parsedQty()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.included) Color.White else Color(0xFFF1F5F9)
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = when {
                !item.included -> Color(0xFFCBD5E1)
                matchedVaccine != null -> Color(0xFF86EFAC)
                else -> Color(0xFFBAE6FD)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scanned_bill_item_card_$index")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Checkbox + Vaccine Title + Remove Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = item.included,
                        onCheckedChange = { checked ->
                            onUpdateItem(item.copy(included = checked))
                        },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0D7A3E)),
                        modifier = Modifier.testTag("checkbox_scanned_item_$index")
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.extractedName.ifBlank { "Vaccine Item #${index + 1}" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (matchedVaccine != null) {
                                "Current Stock: ${matchedVaccine.currentStock} → After Update: ${matchedVaccine.currentStock + currentQty} doses"
                            } else {
                                "New Vaccine Entry (+${currentQty} doses)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (matchedVaccine != null) Color(0xFF15803D) else Color(0xFF0369A1)
                        )
                    }
                }

                IconButton(
                    onClick = onRemoveItem,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove Item",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Full-width Matched Vaccine Selector Bar (Never cramped!)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (matchedVaccine != null) Color(0xFFDCFCE7) else Color(0xFFE0F2FE),
                border = BorderStroke(
                    1.dp,
                    if (matchedVaccine != null) Color(0xFF4ADE80) else Color(0xFF38BDF8)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleInlineSelector() }
                    .testTag("badge_match_vaccine_$index")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (matchedVaccine != null) Icons.Default.Vaccines else Icons.Default.NewReleases,
                            contentDescription = null,
                            tint = if (matchedVaccine != null) Color(0xFF15803D) else Color(0xFF0369A1),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (matchedVaccine != null) {
                                "Matched Catalog: ${matchedVaccine.name} (${matchedVaccine.code})"
                            } else {
                                "Create as New Vaccine (Tap to Pick Existing)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (matchedVaccine != null) Color(0xFF15803D) else Color(0xFF0369A1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isInlineSelectorOpen) "Close" else "Change",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (matchedVaccine != null) Color(0xFF15803D) else Color(0xFF0369A1)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Change Vaccine Match",
                            tint = if (matchedVaccine != null) Color(0xFF15803D) else Color(0xFF0369A1),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Inline Expandable Vaccine Picker
            AnimatedVisibility(visible = isInlineSelectorOpen) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0F9FF),
                    border = BorderStroke(1.dp, Color(0xFF7DD3FC)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Select Clinic Vaccine to Restock:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = inlineSelectorSearchQuery,
                            onValueChange = onInlineSearchQueryChange,
                            placeholder = { Text("Search clinic vaccines...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val filteredCatalog = remember(allVaccines, inlineSelectorSearchQuery) {
                            if (inlineSelectorSearchQuery.isBlank()) allVaccines
                            else allVaccines.filter {
                                it.name.contains(inlineSelectorSearchQuery, ignoreCase = true) ||
                                    it.code.contains(inlineSelectorSearchQuery, ignoreCase = true) ||
                                    it.category.contains(inlineSelectorSearchQuery, ignoreCase = true)
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (matchedVaccine == null) Color(0xFFE0F2FE) else Color.White,
                                border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectMatchedVaccine(null) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NewReleases,
                                        contentDescription = null,
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "+ Add '${item.extractedName.ifBlank { "New Vaccine" }}' as New Catalog Entry",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                            }

                            filteredCatalog.take(10).forEach { vac ->
                                val isCurrent = matchedVaccine?.id == vac.id
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCurrent) Color(0xFFDCFCE7) else Color.White,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCurrent) Color(0xFF4ADE80) else Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectMatchedVaccine(vac) }
                                        .testTag("inline_select_vac_${vac.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = vac.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = "${vac.code} • Stock: ${vac.currentStock} doses • Cost: ₹${vac.purchasePrice.toInt()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF475569)
                                            )
                                        }
                                        if (isCurrent) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = Color(0xFF15803D),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Vaccine Name & Batch Number (2-column layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.extractedName,
                    onValueChange = { newName ->
                        onUpdateItem(item.copy(extractedName = newName))
                    },
                    label = { Text("Vaccine Name") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.25f)
                        .testTag("input_scanned_item_name_$index")
                )
                OutlinedTextField(
                    value = item.batchNumber,
                    onValueChange = { newBatch ->
                        onUpdateItem(item.copy(batchNumber = newBatch))
                    },
                    label = { Text("Batch No.") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.75f)
                        .testTag("input_scanned_item_batch_$index")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 4: Quick Quantity Stepper Row (-5, -1, +1, +5, +10)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF0FDF4))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Quick Qty:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF15803D)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-5, -1, 1, 5, 10).forEach { delta ->
                        val label = if (delta > 0) "+$delta" else "$delta"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (delta > 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            border = BorderStroke(
                                1.dp,
                                if (delta > 0) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                            ),
                            modifier = Modifier
                                .clickable {
                                    val updatedQty = (currentQty + delta).coerceAtLeast(1)
                                    onUpdateItem(item.copy(quantityText = updatedQty.toString()))
                                }
                                .testTag("btn_qty_step_${index}_$label")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (delta > 0) Color(0xFF15803D) else Color(0xFFB91C1C),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 5 (2-Column Grid Part 1): Quantity (Doses) & Purchase Cost (₹)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.quantityText,
                    onValueChange = { v ->
                        onUpdateItem(item.copy(quantityText = v.filter { it.isDigit() }))
                    },
                    label = { Text("Qty (Doses)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_scanned_item_qty_$index")
                )

                OutlinedTextField(
                    value = item.unitPurchasePriceText,
                    onValueChange = { v ->
                        onUpdateItem(item.copy(unitPurchasePriceText = v))
                    },
                    label = { Text("Purchase Cost (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_scanned_item_cost_$index")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 6 (2-Column Grid Part 2): Selling Price / MRP (₹) & Expiry Date (MM/YY)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.sellingPriceText,
                    onValueChange = { v ->
                        onUpdateItem(item.copy(sellingPriceText = v))
                    },
                    label = { Text("Selling MRP (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_scanned_item_mrp_$index")
                )

                OutlinedTextField(
                    value = item.expiryDate,
                    onValueChange = { exp ->
                        onUpdateItem(item.copy(expiryDate = exp))
                    },
                    label = { Text("Expiry (MM/YY)") },
                    placeholder = { Text("12/2027") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_scanned_item_expiry_$index")
                )
            }

            // Direct Single-Card Update Action
            if (currentQty > 0 && item.extractedName.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentStockNum = matchedVaccine?.currentStock ?: 0
                    Text(
                        text = if (matchedVaccine != null) {
                            "New Stock: $currentStockNum + $currentQty = ${currentStockNum + currentQty} doses"
                        } else {
                            "New Vaccine: +$currentQty doses"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D7A3E),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onQuickApplySingleItem,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D7A3E),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .testTag("btn_quick_apply_item_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Apply +$currentQty Now",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
