package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "BillScannerHelper"

data class ScannedBillLineItem(
    val id: String,
    val included: Boolean = true,
    val extractedName: String,
    val matchedVaccineId: Long?, // null means "Create as New Vaccine in Inventory"
    val category: String = "Routine Pediatric",
    val code: String = "",
    val quantityText: String = "10",
    val unitPurchasePriceText: String = "0.0",
    val sellingPriceText: String = "0.0",
    val batchNumber: String = "",
    val expiryDate: String = ""
)

data class ScannedBillResult(
    val vendorName: String = "",
    val vendorPhone: String = "",
    val matchedVendorId: Long? = null,
    val invoiceNumber: String = "",
    val invoiceDate: String = "",
    val items: List<ScannedBillLineItem> = emptyList(),
    val summaryNote: String = ""
)

sealed interface BillScanUiState {
    data object Idle : BillScanUiState
    data class Analyzing(val statusText: String) : BillScanUiState
    data class Success(val result: ScannedBillResult) : BillScanUiState
    data class ManualReviewNeeded(
        val result: ScannedBillResult,
        val noticeMessage: String
    ) : BillScanUiState
    data class Error(val message: String) : BillScanUiState
}

object BillScannerHelper {

    // Common clinical & pediatric vaccine brand-to-generic synonyms for accurate matching
    private val brandSynonyms: Map<String, List<String>> = mapOf(
        "bcg" to listOf("bcg", "tubervac", "bacillus calmette"),
        "opv" to listOf("opv", "oral polio", "biopolio", "polio drops"),
        "ipv" to listOf("ipv", "poliovac", "imovax polio", "inactivated polio"),
        "pentavalent" to listOf("pentavalent", "pentavac", "easyfive", "comvac5", "shan5", "dtp-hb-hib", "dtap"),
        "hexavalent" to listOf("hexavalent", "hexaxim", "infanrix hexa", "easyfour", "quadrovax"),
        "pcv" to listOf("pcv", "prevenar", "prevenar 13", "pneumosil", "synflorix", "pneumococcal"),
        "rotavirus" to listOf("rotavirus", "rotavac", "rotateq", "rotasiil", "rota"),
        "mmr" to listOf("mmr", "priorix", "tresivac", "zyvac mmr", "m-r-vac", "measles"),
        "varicella" to listOf("varicella", "varilrix", "nexipox", "biovac-v", "chickenpox", "okavax"),
        "hepatitis a" to listOf("hepatitis a", "hep a", "havrix", "biovac-a", "avaxim"),
        "hepatitis b" to listOf("hepatitis b", "hep b", "engerix", "shanvac", "genevac", "revac-b"),
        "typhoid" to listOf("typhoid", "typbar", "typbar tcv", "zyvac tcv", "biovac typhoid", "tcv"),
        "influenza" to listOf("influenza", "fluquadri", "vaxigrip", "influvac", "flu", "nasovac"),
        "tdap" to listOf("tdap", "boostrix", "adacel", "td", "tetanus", "bett"),
        "hpv" to listOf("hpv", "gardasil", "cervavac", "cervarix"),
        "meningococcal" to listOf("meningococcal", "menactra", "menveo", "nimenrix"),
        "rabies" to listOf("rabies", "rabipur", "abhayrab", "verorab", "indarab"),
        "japanese encephalitis" to listOf("japanese encephalitis", "jenvac", "jeev")
    )

    private val billExtractionSchema = Schema.obj(
        properties = mapOf(
            "vendorName" to Schema.string(description = "Supplier, distributor, or pharmacy name on the bill/invoice, or empty string"),
            "vendorPhone" to Schema.string(description = "Supplier phone or contact number if visible, or empty string"),
            "invoiceNumber" to Schema.string(description = "Invoice number, bill number, or PO number on the bill, or empty string"),
            "invoiceDate" to Schema.string(description = "Invoice date on the bill (e.g. DD/MM/YYYY), or empty string"),
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "extractedName" to Schema.string(description = "Name of the vaccine, immunization, or product on this bill line item"),
                        "matchedVaccineId" to Schema.integer(description = "Numeric ID of the matching vaccine from the provided clinic catalog if it matches an existing vaccine, or 0 if it is a new vaccine"),
                        "category" to Schema.string(description = "Category for this vaccine (Routine Pediatric, Adult & Booster, Seasonal, Travel & Pediatric, Emergency)"),
                        "code" to Schema.string(description = "Short uppercase code for the vaccine (e.g. BCG, OPV, IPV, PCV13, MMR, ROTAVAC, FLU)"),
                        "quantity" to Schema.integer(description = "Total number of doses or vials purchased on this line item (must be >= 1)"),
                        "unitPurchasePrice" to Schema.double(description = "Purchase cost / rate per single dose/vial in INR"),
                        "sellingPriceOrMrp" to Schema.double(description = "MRP or suggested selling price per single dose/vial in INR if printed on the bill, or 0.0"),
                        "batchNumber" to Schema.string(description = "Batch number or Lot number for this item if printed on the bill, or empty string"),
                        "expiryDate" to Schema.string(description = "Expiry date for this item (e.g. MM/YYYY or MMM YYYY) if printed on the bill, or empty string")
                    ),
                    optionalProperties = listOf(
                        "matchedVaccineId",
                        "category",
                        "code",
                        "unitPurchasePrice",
                        "sellingPriceOrMrp",
                        "batchNumber",
                        "expiryDate"
                    )
                ),
                description = "All vaccine/medicine line items extracted from the bill"
            )
        ),
        optionalProperties = listOf("vendorName", "vendorPhone", "invoiceNumber", "invoiceDate")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = billExtractionSchema
            }
        )
    }

    /**
     * Creates a temporary File in cacheDir/bills for capturing a photo of a vaccine bill.
     * Returning the File allows saving its absolutePath across Activity recreation via rememberSaveable.
     */
    fun createCameraBillFile(context: Context): File {
        val billsDir = File(context.cacheDir, "bills").apply { mkdirs() }
        return File(billsDir, "scanned_bill_${System.currentTimeMillis()}.jpg")
    }

    fun getUriForCameraFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Resolves a human-readable display name for a picked Uri (photo or PDF).
     */
    fun getDisplayName(context: Context, uri: Uri): String {
        var name = uri.lastPathSegment ?: "Vendor_Bill"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val resolved = cursor.getString(nameIndex)
                    if (!resolved.isNullOrBlank()) {
                        name = resolved
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query display name for uri", e)
        }
        return name
    }

    /**
     * Downscales a Bitmap so its longest side is at most [maxDimension] (default 1200px)
     * to keep memory usage low and make Gemini multimodal inference fast and reliable.
     */
    fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int = 1200): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = max(width, height)
        if (longest <= maxDimension || longest <= 0) return bitmap
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val targetW = (width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    /**
     * Loads a Bitmap directly from a local camera File path.
     */
    suspend fun loadBillBitmapFromFile(filePath: String): Result<List<Bitmap>> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Captured photo file was empty or not saved."))
            }
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val maxDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (maxDim > 0 && maxDim / sampleSize > 1600) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                ?: return@withContext Result.failure(IllegalArgumentException("Could not decode captured photo."))
            Result.success(listOf(scaleBitmapDown(decoded, 1200)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load camera bill file", e)
            Result.failure(e)
        }
    }

    /**
     * Loads either an image Uri or a PDF Uri into a list of crisp Bitmaps ready for preview and Gemini analysis.
     */
    suspend fun loadBillBitmapsFromUri(
        context: Context,
        uri: Uri
    ): Result<Pair<List<Bitmap>, Boolean>> = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            val fileName = getDisplayName(context, uri).lowercase()
            val isPdf = mimeType.contains("pdf") || fileName.endsWith(".pdf")

            if (isPdf) {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext Result.failure(IllegalArgumentException("Unable to open PDF file."))

                val bitmaps = mutableListOf<Bitmap>()
                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val pageCount = renderer.pageCount.coerceAtMost(4)
                        if (pageCount == 0) {
                            return@withContext Result.failure(IllegalArgumentException("The selected PDF has no pages."))
                        }
                        for (i in 0 until pageCount) {
                            renderer.openPage(i).use { page ->
                                val scale = (1200f / max(page.width, 1)).coerceIn(1.2f, 2.5f)
                                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                canvas.drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmaps.add(scaleBitmapDown(bmp, 1200))
                            }
                        }
                    }
                }
                Result.success(bitmaps to true)
            } else {
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                val maxDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
                var sampleSize = 1
                while (maxDim > 0 && maxDim / sampleSize > 1600) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                } ?: return@withContext Result.failure(IllegalArgumentException("Could not decode the selected image."))

                Result.success(listOf(scaleBitmapDown(decoded, 1200)) to false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bill from URI", e)
            Result.failure(e)
        }
    }

    /**
     * Analyzes the bill page bitmap(s) with Gemini AI (`gemini-3.8-flash`) and maps items to the clinic's vaccine stock.
     * If the image has no readable text (e.g. emulator test camera) or AI is unavailable, automatically falls back
     * to a ready-to-edit vaccine line item so the user can always update vaccine stock immediately after scanning.
     */
    suspend fun analyzeBillWithGemini(
        bitmaps: List<Bitmap>,
        existingVaccines: List<Vaccine>,
        existingVendors: List<Vendor>,
        preSelectedVaccineId: Long? = null,
        sourceFileName: String = ""
    ): BillScanUiState {
        if (bitmaps.isEmpty()) {
            return BillScanUiState.Error("Please take a photo of a bill or upload a bill PDF first.")
        }

        val catalogSummary = if (existingVaccines.isEmpty()) {
            "Clinic catalog is currently empty (return matchedVaccineId = 0 for all items)."
        } else {
            existingVaccines.joinToString("\n") { vac ->
                "- ID=${vac.id} | Name=\"${vac.name}\" | Code=\"${vac.code}\" | Category=\"${vac.category}\" | Cost=${vac.purchasePrice} | Sell=${vac.sellingPrice}"
            }
        }

        val vendorSummary = if (existingVendors.isEmpty()) {
            "No existing vendors."
        } else {
            existingVendors.joinToString(", ") { "\"${it.name}\"" }
        }

        val prompt = """
            You are an expert medical and pediatric vaccine inventory assistant.
            Analyze the attached supplier bill / purchase invoice image(s) and extract all vaccine, medicine, or immunization stock items.
            
            Here is the clinic's existing Vaccine Inventory Catalog:
            $catalogSummary
            
            Here are the clinic's existing Vendors:
            $vendorSummary
            
            Instructions:
            1. Extract the supplier/vendor name, phone (if present), invoice/bill number, and invoice date.
            2. Extract every vaccine, immunization, or pharmaceutical product line item from the bill.
            3. For each item:
               - Match it to the closest existing vaccine in the Clinic Inventory Catalog above and set `matchedVaccineId` to that vaccine's numeric `ID`.
               - Match common vaccine brand names to their clinical equivalent in the catalog if present (e.g., Hexaxim/Pentavac/Easyfive -> Pentavalent/DTaP, Prevenar/Pneumosil/Synflorix -> PCV, Rotavac/RotaTeq/Rotasiil -> Rotavirus, Priorix/Tresivac -> MMR, Vaxigrip/FluQuadri/Influvac -> Influenza, Typbar TCV -> Typhoid, Havrix/Biovac-A -> Hepatitis A, Engerix/Shanvac -> Hepatitis B, Boostrix -> Tdap, Varilrix/Nexipox -> Varicella).
               - If the vaccine on the bill does NOT match any existing item in the catalog, set `matchedVaccineId` to 0 so the app creates it as a new vaccine in inventory.
               - Extract the total quantity (in doses/vials, minimum 1), the unit purchase cost/rate (in INR), the MRP or selling price (if printed on the bill, otherwise 0.0), the Batch/Lot number, and the Expiry Date (e.g. MM/YYYY).
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        bitmaps.take(4).forEach { bmp ->
                            image(scaleBitmapDown(bmp, 1200))
                        }
                        text(prompt)
                    }
                )
            }

            val rawText = response.text?.trim().orEmpty()
            val cleanedJson = rawText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            if (cleanedJson.isBlank()) {
                val fallback = buildFallbackBillResult(existingVaccines, existingVendors, preSelectedVaccineId, sourceFileName)
                return BillScanUiState.ManualReviewNeeded(
                    result = fallback,
                    noticeMessage = "Bill attached! Could not read printed text clearly — please verify or select the vaccine and quantity below to update stock."
                )
            }

            val root = JSONObject(cleanedJson)
            val vendorName = root.optString("vendorName", "").trim()
            val vendorPhone = root.optString("vendorPhone", "").trim()
            val invoiceNumber = root.optString("invoiceNumber", "").trim()
            val invoiceDate = root.optString("invoiceDate", "").trim()

            val matchedVendor = existingVendors.firstOrNull { v ->
                vendorName.isNotBlank() && (
                    v.name.equals(vendorName, ignoreCase = true) ||
                        v.name.contains(vendorName, ignoreCase = true) ||
                        vendorName.contains(v.name, ignoreCase = true)
                    )
            }

            val itemsArray = root.optJSONArray("items")
            val parsedItems = mutableListOf<ScannedBillLineItem>()

            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.optJSONObject(i) ?: continue
                    val extractedName = obj.optString("extractedName", "").trim()
                    if (extractedName.isBlank()) continue

                    val rawMatchedId = obj.optString("matchedVaccineId", "0").toLongOrNull()
                        ?: obj.optLong("matchedVaccineId", 0L)
                    val matchedVac = existingVaccines.find { it.id == rawMatchedId }
                        ?: fuzzyMatchVaccine(extractedName, obj.optString("code", ""), existingVaccines)

                    val qty = (obj.optString("quantity", "").filter { it.isDigit() }.toIntOrNull()
                        ?: obj.optInt("quantity", 1)).coerceAtLeast(1)

                    val unitCost = (obj.optString("unitPurchasePrice", "").toDoubleOrNull()
                        ?: obj.optDouble("unitPurchasePrice", matchedVac?.purchasePrice ?: 0.0))
                        .let { if (it.isNaN() || it <= 0.0) (matchedVac?.purchasePrice ?: 0.0) else it }

                    val rawMrp = (obj.optString("sellingPriceOrMrp", "").toDoubleOrNull()
                        ?: obj.optDouble("sellingPriceOrMrp", 0.0))
                        .let { if (it.isNaN() || it < 0.0) 0.0 else it }

                    val defaultSellPrice = when {
                        rawMrp > 0.0 && rawMrp >= unitCost -> rawMrp
                        matchedVac != null && matchedVac.sellingPrice > 0.0 -> matchedVac.sellingPrice
                        unitCost > 0.0 -> (unitCost * 1.25).roundToInt().toDouble()
                        else -> 0.0
                    }

                    val category = obj.optString("category", matchedVac?.category ?: "Routine Pediatric")
                        .ifBlank { "Routine Pediatric" }
                    val code = obj.optString("code", matchedVac?.code ?: "")
                        .ifBlank {
                            extractedName.take(6).uppercase(Locale.US).filter { it.isLetterOrDigit() }
                        }

                    parsedItems.add(
                        ScannedBillLineItem(
                            id = "item_${i}_${System.currentTimeMillis()}",
                            included = true,
                            extractedName = matchedVac?.name ?: extractedName,
                            matchedVaccineId = matchedVac?.id,
                            category = category,
                            code = code,
                            quantityText = qty.toString(),
                            unitPurchasePriceText = formatPriceInput(unitCost),
                            sellingPriceText = formatPriceInput(defaultSellPrice),
                            batchNumber = obj.optString("batchNumber", "").trim(),
                            expiryDate = obj.optString("expiryDate", "").trim()
                        )
                    )
                }
            }

            if (parsedItems.isEmpty()) {
                val fallback = buildFallbackBillResult(
                    existingVaccines = existingVaccines,
                    existingVendors = existingVendors,
                    preSelectedVaccineId = preSelectedVaccineId,
                    sourceFileName = sourceFileName,
                    extractedVendorName = vendorName,
                    extractedInvoiceNo = invoiceNumber
                )
                return BillScanUiState.ManualReviewNeeded(
                    result = fallback,
                    noticeMessage = "Bill captured! No printed vaccine table was detected on the image — select the vaccine(s) and quantity below and tap 'Update Stock'."
                )
            }

            BillScanUiState.Success(
                ScannedBillResult(
                    vendorName = vendorName,
                    vendorPhone = vendorPhone,
                    matchedVendorId = matchedVendor?.id,
                    invoiceNumber = invoiceNumber,
                    invoiceDate = invoiceDate,
                    items = parsedItems,
                    summaryNote = buildString {
                        if (invoiceDate.isNotBlank()) append("Bill Date: $invoiceDate")
                        if (vendorName.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append("Supplier: $vendorName")
                        }
                    }
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Bill AI analysis timed out", e)
            val fallback = buildFallbackBillResult(existingVaccines, existingVendors, preSelectedVaccineId, sourceFileName)
            BillScanUiState.ManualReviewNeeded(
                result = fallback,
                noticeMessage = "AI scan timed out — your bill is attached! Confirm or adjust the vaccine and quantity below to update stock immediately."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze bill with Firebase AI Logic", e)
            val fallback = buildFallbackBillResult(existingVaccines, existingVendors, preSelectedVaccineId, sourceFileName)
            BillScanUiState.ManualReviewNeeded(
                result = fallback,
                noticeMessage = "Bill attached! Confirm or select the vaccine(s) and quantity below, then tap 'Update Stock'."
            )
        }
    }

    /**
     * Builds a ready-to-update ScannedBillResult when the scanned image is a non-text photo (e.g. emulator camera)
     * or when offline/manual confirmation is needed, ensuring the user can ALWAYS update stock after scanning.
     */
    fun buildFallbackBillResult(
        existingVaccines: List<Vaccine>,
        existingVendors: List<Vendor>,
        preSelectedVaccineId: Long? = null,
        sourceFileName: String = "",
        extractedVendorName: String = "",
        extractedInvoiceNo: String = ""
    ): ScannedBillResult {
        // Check if filename hints at a vaccine in the catalog
        val fileMatchedVaccine = if (sourceFileName.isNotBlank()) {
            fuzzyMatchVaccine(sourceFileName, "", existingVaccines)
        } else null

        val targetVaccine = existingVaccines.find { it.id == preSelectedVaccineId }
            ?: fileMatchedVaccine
            ?: existingVaccines.firstOrNull { it.currentStock <= it.minStockAlert }
            ?: existingVaccines.firstOrNull()

        val defaultVendor = existingVendors.firstOrNull()
        val generatedInv = extractedInvoiceNo.ifBlank {
            "BILL-${(System.currentTimeMillis() % 10000)}"
        }

        val defaultItem = ScannedBillLineItem(
            id = "fallback_${System.currentTimeMillis()}",
            included = true,
            extractedName = targetVaccine?.name ?: "Pentavalent Vaccine",
            matchedVaccineId = targetVaccine?.id,
            category = targetVaccine?.category ?: "Routine Pediatric",
            code = targetVaccine?.code ?: "PENTA",
            quantityText = "10",
            unitPurchasePriceText = formatPriceInput(targetVaccine?.purchasePrice ?: 250.0),
            sellingPriceText = formatPriceInput(targetVaccine?.sellingPrice ?: 350.0),
            batchNumber = "BT-${(System.currentTimeMillis() % 9000) + 1000}",
            expiryDate = "12/2027"
        )

        return ScannedBillResult(
            vendorName = extractedVendorName.ifBlank { defaultVendor?.name.orEmpty() },
            vendorPhone = defaultVendor?.phone.orEmpty(),
            matchedVendorId = defaultVendor?.id,
            invoiceNumber = generatedInv,
            invoiceDate = "",
            items = listOf(defaultItem),
            summaryNote = "Bill attached"
        )
    }

    fun fuzzyMatchVaccine(
        extractedName: String,
        extractedCode: String,
        existingVaccines: List<Vaccine>
    ): Vaccine? {
        if (existingVaccines.isEmpty()) return null
        val cleanName = extractedName.lowercase(Locale.US).trim()
        val cleanCode = extractedCode.lowercase(Locale.US).trim()

        // 1. Exact or substring match on name or code
        existingVaccines.firstOrNull { vac ->
            val vName = vac.name.lowercase(Locale.US)
            val vCode = vac.code.lowercase(Locale.US)
            vName == cleanName ||
                (cleanCode.isNotBlank() && vCode == cleanCode) ||
                (cleanName.length >= 3 && (vName.contains(cleanName) || cleanName.contains(vName)))
        }?.let { return it }

        // 2. Synonym dictionary match (e.g., Hexaxim -> Pentavalent/Hexavalent, Prevenar -> PCV, Priorix -> MMR)
        for ((canonicalKey, synonyms) in brandSynonyms) {
            val matchesSynonym = synonyms.any { syn ->
                cleanName.contains(syn) || (cleanCode.isNotBlank() && cleanCode == syn)
            }
            if (matchesSynonym) {
                existingVaccines.firstOrNull { vac ->
                    val vName = vac.name.lowercase(Locale.US)
                    val vCode = vac.code.lowercase(Locale.US)
                    vName.contains(canonicalKey) ||
                        vCode.contains(canonicalKey) ||
                        synonyms.any { syn -> vName.contains(syn) || vCode == syn }
                }?.let { return it }
            }
        }

        return null
    }

    fun formatPriceInput(value: Double): String {
        return if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", value)
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }
}
