package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
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
import kotlinx.coroutines.delay
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

    private const val MAX_SCAN_DIMENSION = 2048

    // Comprehensive Indian & global pediatric/adult vaccine brand-to-generic synonyms
    private val brandSynonyms: Map<String, List<String>> = mapOf(
        "bcg" to listOf("bcg", "tubervac", "bacillus calmette"),
        "opv" to listOf("opv", "oral polio", "biopolio", "polio drops", "bopv"),
        "ipv" to listOf("ipv", "poliovac", "imovax polio", "inactivated polio", "shanipv", "poliorix"),
        "pentavalent" to listOf("pentavalent", "pentavac", "pentaxim", "easyfive", "comvac5", "shan5", "dtp-hb-hib", "dtap", "infanrix", "quadrovax", "easyfour"),
        "hexavalent" to listOf("hexavalent", "hexaxim", "infanrix hexa", "easysix", "hexa", "dtap-ipv-hb-hib"),
        "pcv" to listOf("pcv", "pcv13", "pcv 13", "pcv10", "pcv20", "prevenar", "prevenar 13", "pneumosil", "synflorix", "pneumococcal", "pneumovax", "apexxnar"),
        "rotavirus" to listOf("rotavirus", "rotavac", "rotarix", "rotateq", "rotasiil", "rotasure", "rota"),
        "mmr" to listOf("mmr", "priorix", "tresivac", "zyvac mmr", "m-r-vac", "measles", "mumps", "rubella"),
        "varicella" to listOf("varicella", "varilrix", "variped", "nexipox", "biovac-v", "biovac v", "chickenpox", "okavax", "varivax"),
        "hepatitis a" to listOf("hepatitis a", "hep a", "hep-a", "havrix", "biovac-a", "biovac a", "avaxim", "twinrix", "havpur"),
        "hepatitis b" to listOf("hepatitis b", "hep b", "hep-b", "engerix", "shanvac", "genevac", "revac-b", "bevac", "heplisav"),
        "typhoid" to listOf("typhoid", "typbar", "typbar tcv", "zyvac tcv", "biovac typhoid", "typhim", "tcv", "pedatyph", "enteroshield"),
        "influenza" to listOf("influenza", "fluquadri", "vaxigrip", "vaxigrip tetra", "influvac", "influvac tetra", "fluarix", "flu", "nasovac", "quadrivalent"),
        "tdap" to listOf("tdap", "boostrix", "adacel", "td", "tetanus", "bett", "tt", "dpt", "tripacel", "tetraxim"),
        "hpv" to listOf("hpv", "gardasil", "gardasil 9", "cervavac", "cervarix"),
        "meningococcal" to listOf("meningococcal", "menactra", "menveo", "nimenrix", "menquadfi", "bexsero"),
        "rabies" to listOf("rabies", "rabipur", "abhayrab", "verorab", "indarab", "vaxirab", "chirorab"),
        "japanese encephalitis" to listOf("japanese encephalitis", "jenvac", "jeev"),
        "hib" to listOf("hib", "acthib", "hiberix", "sii hibpro", "haemophilus"),
        "cholera" to listOf("cholera", "shanchol", "euvichol", "dukoral"),
        "yellow fever" to listOf("yellow fever", "stamaril"),
        "herpes zoster" to listOf("shingrix", "zoster", "shingles"),
        "rsv" to listOf("rsv", "abrysvo", "arexvy", "beyfortus")
    )

    private val billExtractionSchema = Schema.obj(
        properties = mapOf(
            "vendorName" to Schema.string(description = "Supplier, distributor, stockist, or pharmacy name on the bill/invoice, or empty string"),
            "vendorPhone" to Schema.string(description = "Supplier phone or mobile number if visible, or empty string"),
            "invoiceNumber" to Schema.string(description = "Invoice number, bill number, memo number, or PO number on the bill, or empty string"),
            "invoiceDate" to Schema.string(description = "Invoice date on the bill (e.g. DD/MM/YYYY), or empty string"),
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "extractedName" to Schema.string(description = "Exact name of the vaccine, immunization, or biological product on this bill row"),
                        "catalogIndex" to Schema.integer(description = "1-based index number [1..N] of the matching vaccine from the provided Clinic Inventory Catalog list, or 0 if it is a new vaccine not in the catalog"),
                        "category" to Schema.string(description = "Category: Routine Pediatric, Adult & Booster, Seasonal, Travel & Pediatric, or Emergency"),
                        "code" to Schema.string(description = "Short uppercase code for the vaccine (e.g. BCG, OPV, IPV, PCV13, MMR, ROTAVAC, FLU, HEXAXIM)"),
                        "quantity" to Schema.integer(description = "Billed quantity of doses or vials purchased on this line item (integer >= 1)"),
                        "freeQuantity" to Schema.integer(description = "Free/bonus doses or vials printed in Free/Sch column on this row, or 0 if none"),
                        "unitPurchasePrice" to Schema.double(description = "Net purchase cost / rate per single dose or vial in INR (excluding or including tax as billed per unit)"),
                        "sellingPriceOrMrp" to Schema.double(description = "MRP or selling price per single dose/vial in INR if printed on the bill, or 0.0"),
                        "batchNumber" to Schema.string(description = "Batch number or Lot number for this line item if printed on the bill, or empty string"),
                        "expiryDate" to Schema.string(description = "Expiry date for this line item (e.g. MM/YYYY or MMM-YY) if printed on the bill, or empty string")
                    ),
                    optionalProperties = listOf(
                        "catalogIndex",
                        "category",
                        "code",
                        "freeQuantity",
                        "unitPurchasePrice",
                        "sellingPriceOrMrp",
                        "batchNumber",
                        "expiryDate"
                    )
                ),
                description = "Every vaccine or immunization line item found on the invoice"
            )
        ),
        optionalProperties = listOf("vendorName", "vendorPhone", "invoiceNumber", "invoiceDate")
    )

    private val primaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.1f
                responseMimeType = "application/json"
                responseSchema = billExtractionSchema
            }
        )
    }

    private val fallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.1f
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
     * Downscales a Bitmap so its longest side is at most [maxDimension] (default 2048px)
     * keeping small printed batch numbers, quantities, and vaccine names razor-sharp for OCR
     * while staying well under Gemini's 20MB inline image limit.
     */
    fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int = MAX_SCAN_DIMENSION): Bitmap {
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
     * Rotates/flips a decoded Bitmap upright according to its JPEG EXIF orientation tag.
     * Fixes sideways/upside-down Android camera photos that previously caused intermittent OCR failures!
     */
    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM rotating bitmap, using original orientation", e)
            bitmap
        }
    }

    /**
     * Loads a Bitmap directly from a local camera File path, retrying briefly if the camera app
     * is still flushing bytes, and correcting EXIF orientation so the invoice is always upright.
     */
    suspend fun loadBillBitmapFromFile(filePath: String): Result<List<Bitmap>> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            var attempts = 0
            while ((!file.exists() || file.length() == 0L) && attempts < 4) {
                delay(180L)
                attempts++
            }
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Captured photo file was empty or not saved."))
            }

            val exifOrientation = try {
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val maxDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (maxDim > 0 && maxDim / sampleSize > MAX_SCAN_DIMENSION * 2) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                ?: return@withContext Result.failure(IllegalArgumentException("Could not decode captured photo."))

            val upright = applyExifOrientation(decoded, exifOrientation)
            val scaled = scaleBitmapDown(upright, MAX_SCAN_DIMENSION)
            Result.success(listOf(scaled))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load camera bill file", e)
            Result.failure(e)
        }
    }

    /**
     * Loads either an image Uri (with EXIF rotation correction) or a PDF Uri (up to 4 high-res pages)
     * into crisp Bitmaps ready for preview and Gemini Vision extraction.
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
                                val scale = (MAX_SCAN_DIMENSION.toFloat() / max(page.width, 1)).coerceIn(1.5f, 3.0f)
                                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                canvas.drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmaps.add(scaleBitmapDown(bmp, MAX_SCAN_DIMENSION))
                            }
                        }
                    }
                }
                Result.success(bitmaps to true)
            } else {
                val exifOrientation = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        ExifInterface(stream).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    } ?: ExifInterface.ORIENTATION_NORMAL
                } catch (_: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }

                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                val maxDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
                var sampleSize = 1
                while (maxDim > 0 && maxDim / sampleSize > MAX_SCAN_DIMENSION * 2) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                } ?: return@withContext Result.failure(IllegalArgumentException("Could not decode the selected image."))

                val upright = applyExifOrientation(decoded, exifOrientation)
                Result.success(listOf(scaleBitmapDown(upright, MAX_SCAN_DIMENSION)) to false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bill from URI", e)
            Result.failure(e)
        }
    }

    /**
     * Analyzes bill image(s) with Firebase AI Logic (`gemini-3.8-flash` primary + automatic retry/fallback
     * to `gemini-3.5-flash-lite` on transient failure) using strict JSON `responseSchema`, deterministic
     * temperature, EXIF-upright high-res images, and 1-based catalog index matching.
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
            "Clinic catalog is currently empty (set catalogIndex = 0 for all items)."
        } else {
            existingVaccines.mapIndexed { idx, vac ->
                "[${idx + 1}] Name=\"${vac.name}\" | Code=\"${vac.code}\" | Category=\"${vac.category}\" | Cost=${vac.purchasePrice} | Sell=${vac.sellingPrice}"
            }.joinToString("\n")
        }

        val vendorSummary = if (existingVendors.isEmpty()) {
            "No existing vendors."
        } else {
            existingVendors.joinToString(", ") { "\"${it.name}\"" }
        }

        val prompt = """
            You are an expert medical & pediatric vaccine pharmacy invoice OCR assistant.
            Carefully read every row of the attached vaccine supplier invoice / distributor bill image(s), even if slightly skewed, handwritten, or printed on a GST tax invoice table.

            Clinic's existing Vaccine Inventory Catalog (use 1-based index for `catalogIndex`):
            $catalogSummary

            Clinic's existing Vendors:
            $vendorSummary

            Extraction Rules:
            1. Extract `vendorName` (supplier/distributor/pharmacy), `vendorPhone`, `invoiceNumber` (Bill/Inv No), and `invoiceDate`.
            2. Extract EVERY vaccine, toxoid, immunoglobulin, or immunization row from the table. Do NOT skip rows and do NOT include tax totals, grand totals, CGST/SGST rows, or freight charges as vaccine items.
            3. For each vaccine row:
               - Set `extractedName` to the vaccine product name shown on the bill.
               - Match it to the closest existing vaccine in the numbered Clinic Inventory Catalog above and set `catalogIndex` to its `[1..N]` index number (for example: Hexaxim/EasySix/Infanrix Hexa -> Hexavalent, Pentavac/Pentaxim/Easyfive -> Pentavalent, Prevenar/Pneumosil -> PCV, Rotavac/Rotarix/Rotasiil -> Rotavirus, Priorix/Tresivac -> MMR, Vaxigrip/FluQuadri/Influvac -> Influenza, Typbar/Zyvac TCV -> Typhoid, Havrix/Avaxim/Biovac-A -> Hepatitis A, Engerix/Shanvac/Genevac -> Hepatitis B, Boostrix/Adacel -> Tdap, Varilrix/Nexipox/Variped -> Varicella, Gardasil/Cervavac -> HPV, Rabipur/Abhayrab -> Rabies, Menactra/Menveo -> Meningococcal).
               - If the vaccine does NOT match any catalog item, set `catalogIndex` to 0 so it is added as a new vaccine.
               - Extract billed `quantity` (numeric doses/vials/syringes >= 1). If a "Free" or "Sch" column has free bonus doses (e.g. `10+2` or `Qty 10, Free 2`), put `quantity = 10` and `freeQuantity = 2`.
               - Extract `unitPurchasePrice` (Rate / Purchase price per single unit in INR; if only total line amount is shown, divide by quantity).
               - Extract `sellingPriceOrMrp` (MRP per unit in INR if printed, otherwise 0.0).
               - Extract `batchNumber` (Batch / Lot No) and `expiryDate` (Exp e.g. 08/2027 or AUG-27).
        """.trimIndent()

        val preparedBitmaps = bitmaps.take(4).map { scaleBitmapDown(it, MAX_SCAN_DIMENSION) }
        val modelsToTry = listOf(
            "gemini-3.8-flash" to primaryModel,
            "gemini-3.5-flash-lite" to fallbackModel
        )

        var lastError: Throwable? = null

        for ((attemptIdx, pair) in modelsToTry.withIndex()) {
            val (modelLabel, activeModel) = pair
            try {
                if (attemptIdx > 0) {
                    delay(700L)
                }
                val response = withTimeout(60_000L) {
                    activeModel.generateContent(
                        content {
                            preparedBitmaps.forEach { bmp -> image(bmp) }
                            text(prompt)
                        }
                    )
                }

                val rawText = response.text?.trim().orEmpty()
                if (rawText.isBlank()) {
                    lastError = IllegalStateException("$modelLabel returned an empty response.")
                    continue
                }

                val parsedState = parseStructuredBillJson(
                    rawJsonText = rawText,
                    existingVaccines = existingVaccines,
                    existingVendors = existingVendors
                )

                if (parsedState is BillScanUiState.Success) {
                    return parsedState
                }

                // If primary model returned valid JSON with 0 items, give fallback model one chance before asking manual review
                if (attemptIdx == 0 && parsedState is BillScanUiState.ManualReviewNeeded) {
                    Log.w(TAG, "$modelLabel found 0 items, retrying once with fallback model")
                    lastError = null
                    continue
                }
                return parsedState
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Bill AI analysis timed out on $modelLabel", e)
                lastError = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Bill AI analysis failed on $modelLabel: ${e.message}", e)
                lastError = e
            }
        }

        // Transparently report real error instead of silently injecting fake stock items
        val userErrorMessage = when (lastError) {
            is TimeoutCancellationException ->
                "AI bill scan timed out. Please check your internet connection and tap 'Retry Scan'."
            else -> {
                val detail = lastError?.localizedMessage?.takeIf { it.isNotBlank() }
                if (detail != null) {
                    "Could not read bill automatically ($detail). Tap 'Retry Scan' or select vaccines below."
                } else {
                    "Could not read bill automatically. Check internet / photo clarity and tap 'Retry Scan', or add vaccines below."
                }
            }
        }
        return BillScanUiState.Error(userErrorMessage)
    }

    /**
     * Parses Gemini's structured JSON response into [BillScanUiState].
     * Kept internal for direct unit testing.
     */
    internal fun parseStructuredBillJson(
        rawJsonText: String,
        existingVaccines: List<Vaccine>,
        existingVendors: List<Vendor>
    ): BillScanUiState {
        // Safely trim outer whitespace (structured output guarantees raw JSON, defensive strip only if needed)
        val trimmed = rawJsonText.trim()
        val startBrace = trimmed.indexOf('{')
        val endBrace = trimmed.lastIndexOf('}')
        if (startBrace < 0 || endBrace <= startBrace) {
            return BillScanUiState.Error("AI returned an unexpected format. Please tap 'Retry Scan'.")
        }
        val cleanJson = trimmed.substring(startBrace, endBrace + 1)

        val root = JSONObject(cleanJson)
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

                // Skip accidental summary rows
                val lowerName = extractedName.lowercase(Locale.US)
                if (lowerName == "total" || lowerName == "grand total" || lowerName == "cgst" || lowerName == "sgst") {
                    continue
                }

                // Resolve 1-based catalogIndex first, then fallback to fuzzy/synonym matching
                val rawCatalogIdx = parseFlexibleInt(obj.opt("catalogIndex"), 0)
                val indexMatchedVac = if (rawCatalogIdx in 1..existingVaccines.size) {
                    existingVaccines[rawCatalogIdx - 1]
                } else {
                    null
                }
                val extractedCode = obj.optString("code", "").trim()
                val matchedVac = indexMatchedVac
                    ?: fuzzyMatchVaccine(extractedName, extractedCode, existingVaccines)

                val billedQty = parseFlexibleInt(obj.opt("quantity"), 1).coerceAtLeast(1)
                val freeQty = parseFlexibleInt(obj.opt("freeQuantity"), 0).coerceAtLeast(0)
                val totalQty = (billedQty + freeQty).coerceAtLeast(1)

                val unitCost = parseFlexibleDouble(obj.opt("unitPurchasePrice"), matchedVac?.purchasePrice ?: 0.0)
                    .let { if (it.isNaN() || it <= 0.0) (matchedVac?.purchasePrice ?: 0.0) else it }

                val rawMrp = parseFlexibleDouble(obj.opt("sellingPriceOrMrp"), 0.0)
                    .let { if (it.isNaN() || it < 0.0) 0.0 else it }

                val defaultSellPrice = when {
                    rawMrp > 0.0 && rawMrp >= unitCost -> rawMrp
                    matchedVac != null && matchedVac.sellingPrice > 0.0 -> matchedVac.sellingPrice
                    unitCost > 0.0 -> (unitCost * 1.25).roundToInt().toDouble()
                    else -> 0.0
                }

                val category = obj.optString("category", matchedVac?.category ?: "Routine Pediatric")
                    .ifBlank { matchedVac?.category ?: "Routine Pediatric" }
                val code = extractedCode.ifBlank { matchedVac?.code.orEmpty() }
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
                        quantityText = totalQty.toString(),
                        unitPurchasePriceText = formatPriceInput(unitCost),
                        sellingPriceText = formatPriceInput(defaultSellPrice),
                        batchNumber = obj.optString("batchNumber", "").trim(),
                        expiryDate = obj.optString("expiryDate", "").trim()
                    )
                )
            }
        }

        if (parsedItems.isEmpty()) {
            return BillScanUiState.ManualReviewNeeded(
                result = ScannedBillResult(
                    vendorName = vendorName,
                    vendorPhone = vendorPhone,
                    matchedVendorId = matchedVendor?.id,
                    invoiceNumber = invoiceNumber,
                    invoiceDate = invoiceDate,
                    items = emptyList(),
                    summaryNote = "No printed vaccine rows detected"
                ),
                noticeMessage = "No clear vaccine rows were found on this image. Retake a sharper, well-lit photo of the bill table or tap a vaccine chip below to add manually."
            )
        }

        return BillScanUiState.Success(
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
    }

    private fun parseFlexibleInt(raw: Any?, default: Int): Int {
        return when (raw) {
            is Number -> raw.toDouble().roundToInt()
            is String -> {
                val clean = raw.trim().replace(",", "")
                clean.toDoubleOrNull()?.roundToInt()
                    ?: clean.filter { it.isDigit() }.toIntOrNull()
                    ?: default
            }
            else -> default
        }
    }

    private fun parseFlexibleDouble(raw: Any?, default: Double): Double {
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> {
                val clean = raw.trim().replace("₹", "").replace("Rs.", "", ignoreCase = true)
                    .replace("Rs", "", ignoreCase = true).replace(",", "").trim()
                clean.toDoubleOrNull() ?: default
            }
            else -> default
        }
    }

    private fun stripPackagingNoise(text: String): String {
        return text.lowercase(Locale.US)
            .replace(Regex("\\b(0\\.5\\s*ml|1\\s*ml|2\\s*ml|pfs|vial|vials|inj|injection|vaccine|vac|susp|suspension|drops|dose|doses|syringe|ip|usp|bp)\\b"), " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun fuzzyMatchVaccine(
        extractedName: String,
        extractedCode: String,
        existingVaccines: List<Vaccine>
    ): Vaccine? {
        if (existingVaccines.isEmpty()) return null
        val cleanName = extractedName.lowercase(Locale.US).trim()
        val cleanCode = extractedCode.lowercase(Locale.US).trim()
        val strippedName = stripPackagingNoise(extractedName)

        // 1. Exact or substring match on name, stripped name, or code
        existingVaccines.firstOrNull { vac ->
            val vName = vac.name.lowercase(Locale.US).trim()
            val vCode = vac.code.lowercase(Locale.US).trim()
            val vStripped = stripPackagingNoise(vac.name)
            vName == cleanName ||
                (cleanCode.isNotBlank() && vCode == cleanCode) ||
                (strippedName.length >= 3 && vStripped.isNotBlank() && (vStripped == strippedName || vStripped.contains(strippedName) || strippedName.contains(vStripped))) ||
                (cleanName.length >= 3 && (vName.contains(cleanName) || cleanName.contains(vName)))
        }?.let { return it }

        // 2. Brand-to-generic synonym dictionary match (e.g., Hexaxim -> Hexavalent, Prevenar -> PCV, Priorix -> MMR)
        for ((canonicalKey, synonyms) in brandSynonyms) {
            val matchesSynonym = synonyms.any { syn ->
                cleanName.contains(syn) || strippedName.contains(syn) || (cleanCode.isNotBlank() && cleanCode == syn)
            }
            if (matchesSynonym) {
                existingVaccines.firstOrNull { vac ->
                    val vName = vac.name.lowercase(Locale.US)
                    val vCode = vac.code.lowercase(Locale.US)
                    val vStripped = stripPackagingNoise(vac.name)
                    vName.contains(canonicalKey) ||
                        vCode.contains(canonicalKey) ||
                        vStripped.contains(canonicalKey) ||
                        synonyms.any { syn -> vName.contains(syn) || vCode == syn || vStripped.contains(syn) }
                }?.let { return it }
            }
        }

        // 3. Token overlap score for multi-word pediatric names (e.g. "Hepatitis B Pediatric" vs "Hep B")
        val extractedTokens = strippedName.split(" ").filter { it.length >= 3 }.toSet()
        if (extractedTokens.isNotEmpty()) {
            val best = existingVaccines.maxByOrNull { vac ->
                val vTokens = stripPackagingNoise(vac.name).split(" ").filter { it.length >= 3 }.toSet()
                extractedTokens.intersect(vTokens).size
            }
            if (best != null) {
                val bestTokens = stripPackagingNoise(best.name).split(" ").filter { it.length >= 3 }.toSet()
                if (extractedTokens.intersect(bestTokens).isNotEmpty()) {
                    return best
                }
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
