package com.example.ui.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.database.AppDatabase
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.StockTransaction
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.data.util.LocalBackupManager
import com.example.data.util.SyncTimestampTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ClinicExportFormat(
    val extension: String,
    val mimeType: String,
    val labelEn: String,
    val labelHi: String
) {
    JSON_BACKUP(
        extension = "json",
        mimeType = "application/json",
        labelEn = "Full Backup (.JSON)",
        labelHi = "पूरा बैकअप (.JSON)"
    ),
    CSV_SPREADSHEET(
        extension = "csv",
        mimeType = "text/csv",
        labelEn = "Excel / Sheets (.CSV)",
        labelHi = "एक्सेल / शीट रिपोर्ट (.CSV)"
    )
}

data class ImportBackupResult(
    val vaccinesCount: Int,
    val patientsCount: Int,
    val transactionsCount: Int,
    val vendorsCount: Int,
    val profileRestored: Boolean
) {
    val totalCount: Int
        get() = vaccinesCount + patientsCount + transactionsCount + vendorsCount + if (profileRestored) 1 else 0
}

/**
 * Handles exporting all clinic data (Vaccines, Patients, Transactions, Vendors, Clinic Profile)
 * into restorable JSON backups or Excel/Google Sheets CSV reports, and sharing/saving them
 * directly to Google Drive, WhatsApp, or other apps with zero storage permissions.
 */
object DataExportManager {

    private const val TAG = "DataExportManager"
    private const val GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs"
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    fun suggestedFileName(format: ClinicExportFormat): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        return when (format) {
            ClinicExportFormat.JSON_BACKUP -> "DrPriyankClinic_Backup_$stamp.json"
            ClinicExportFormat.CSV_SPREADSHEET -> "DrPriyankClinic_Report_$stamp.csv"
        }
    }

    suspend fun buildFullBackupJsonString(database: AppDatabase): String = withContext(Dispatchers.IO) {
        val vaccines = database.vaccineDao().getAllVaccinesList()
        val vendors = database.vendorDao().getAllVendorsList()
        val transactions = database.transactionDao().getAllTransactionsList()
        val patients = database.patientRegistrationDao().getAllPatientsList()
        val profile = database.clinicProfileDao().getProfileSync()

        val root = JSONObject().apply {
            put("schemaVersion", 2)
            put("appName", "Dr Priyank Vaccine Clinic")
            put("backupTimestamp", System.currentTimeMillis())
            put(
                "exportedAtReadable",
                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            )
        }

        val vaccinesArr = JSONArray()
        for (v in vaccines) {
            vaccinesArr.put(JSONObject().apply {
                put("id", v.id)
                put("name", v.name)
                put("category", v.category)
                put("code", v.code)
                put("currentStock", v.currentStock)
                put("purchasePrice", v.purchasePrice)
                put("sellingPrice", v.sellingPrice)
                put("minStockAlert", v.minStockAlert)
                put("storageTemp", v.storageTemp)
                put("unit", v.unit)
                put("notes", v.notes)
            })
        }
        root.put("vaccines", vaccinesArr)

        val vendorsArr = JSONArray()
        for (v in vendors) {
            vendorsArr.put(JSONObject().apply {
                put("id", v.id)
                put("name", v.name)
                put("contactPerson", v.contactPerson)
                put("phone", v.phone)
                put("email", v.email)
                put("address", v.address)
                put("notes", v.notes)
                put("createdAt", v.createdAt)
            })
        }
        root.put("vendors", vendorsArr)

        val txArr = JSONArray()
        for (t in transactions) {
            txArr.put(JSONObject().apply {
                put("id", t.id)
                put("type", t.type)
                put("vaccineId", t.vaccineId)
                put("vaccineName", t.vaccineName)
                if (t.vendorId != null) put("vendorId", t.vendorId)
                if (t.vendorName != null) put("vendorName", t.vendorName)
                put("quantity", t.quantity)
                put("unitPrice", t.unitPrice)
                put("costPricePerUnit", t.costPricePerUnit)
                put("totalAmount", t.totalAmount)
                put("profit", t.profit)
                put("batchNumber", t.batchNumber)
                put("expiryDate", t.expiryDate)
                put("referenceOrCustomer", t.referenceOrCustomer)
                put("timestamp", t.timestamp)
                put("notes", t.notes)
            })
        }
        root.put("transactions", txArr)

        val patientsArr = JSONArray()
        for (p in patients) {
            patientsArr.put(JSONObject().apply {
                put("id", p.id)
                put("patientName", p.patientName)
                put("dobOrAge", p.dobOrAge)
                put("gender", p.gender)
                put("parentName", p.parentName)
                put("mobileNumber", p.mobileNumber)
                put("vaccineGivenToday", p.vaccineGivenToday)
                put("vaccineGivenBatch", p.vaccineGivenBatch)
                put("vaccineGivenDose", p.vaccineGivenDose)
                put("dateGivenMillis", p.dateGivenMillis)
                put("nextPlannedVaccine", p.nextPlannedVaccine)
                put("nextDueDate", p.nextDueDate)
                put("nextDueDateMillis", p.nextDueDateMillis)
                put("reminderStatus", p.reminderStatus)
                put("lastReminderMillis", p.lastReminderMillis)
                put("notes", p.notes)
                put("previousVaccinesHistory", p.previousVaccinesHistory)
            })
        }
        root.put("patients", patientsArr)

        if (profile != null) {
            root.put("clinicProfile", JSONObject().apply {
                put("id", profile.id)
                put("clinicName", profile.clinicName)
                put("doctorName", profile.doctorName)
                put("phone", profile.phone)
                put("email", profile.email)
                put("address", profile.address)
                put("registrationNumber", profile.registrationNumber)
                put("logoType", profile.logoType)
                put("themeColor", profile.themeColor)
                put("billFooterNote", profile.billFooterNote)
                put("customWhatsappMessage", profile.customWhatsappMessage)
                put("photoUri", profile.photoUri)
            })
        }

        root.toString(2)
    }

    suspend fun buildClinicCsvReportString(database: AppDatabase): String = withContext(Dispatchers.IO) {
        val vaccines = database.vaccineDao().getAllVaccinesList()
        val patients = database.patientRegistrationDao().getAllPatientsList()
        val transactions = database.transactionDao().getAllTransactionsList()
        val vendors = database.vendorDao().getAllVendorsList()
        val profile = database.clinicProfileDao().getProfileSync() ?: ClinicProfile()
        val dateFmt = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())

        fun esc(value: Any?): String {
            val s = value?.toString() ?: ""
            return if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                "\"${s.replace("\"", "\"\"")}\""
            } else {
                s
            }
        }

        val sb = StringBuilder()
        // UTF-8 BOM so Excel and Google Sheets render Rupee symbols & Hindi cleanly
        sb.append('\uFEFF')

        // Header
        sb.appendLine("CLINIC DATA EXPORT REPORT")
        sb.appendLine("Clinic Name,${esc(profile.clinicName)}")
        sb.appendLine("Doctor,${esc(profile.doctorName)}")
        sb.appendLine("Phone,${esc(profile.phone)}")
        sb.appendLine("Address,${esc(profile.address)}")
        sb.appendLine("Exported On,${esc(dateFmt.format(Date()))}")
        sb.appendLine("Summary,Vaccines: ${vaccines.size} | Total Doses: ${vaccines.sumOf { it.currentStock }} | Patients: ${patients.size} | Transactions: ${transactions.size} | Vendors: ${vendors.size}")
        sb.appendLine()

        // Section 1: Vaccine Stock Inventory
        sb.appendLine("=== 1. VACCINE STOCK INVENTORY ===")
        sb.appendLine("ID,Vaccine Name,Code,Category,Current Stock,Unit,Min Alert,Purchase Price (INR),Selling Price (INR),Stock Cost Value (INR),Retail Value (INR),Storage Temp,Notes")
        for (v in vaccines) {
            val costVal = String.format(Locale.US, "%.2f", v.currentStock * v.purchasePrice)
            val retailVal = String.format(Locale.US, "%.2f", v.currentStock * v.sellingPrice)
            sb.appendLine(
                listOf(
                    v.id, v.name, v.code, v.category, v.currentStock, v.unit,
                    v.minStockAlert, v.purchasePrice, v.sellingPrice,
                    costVal, retailVal, v.storageTemp, v.notes
                ).joinToString(",") { esc(it) }
            )
        }
        sb.appendLine()

        // Section 2: Patient Immunization Records
        sb.appendLine("=== 2. PATIENT VACCINATION RECORDS ===")
        sb.appendLine("ID,Patient Name,DOB / Age,Gender,Parent Name,Mobile,Vaccine Given,Dose,Batch,Date Given,Next Planned Vaccine,Next Due Date,Reminder Status,Notes")
        for (p in patients) {
            sb.appendLine(
                listOf(
                    p.id, p.patientName, p.dobOrAge, p.gender, p.parentName, p.mobileNumber,
                    p.vaccineGivenToday, p.vaccineGivenDose, p.vaccineGivenBatch,
                    dateFmt.format(Date(p.dateGivenMillis)),
                    p.nextPlannedVaccine, p.nextDueDate, p.reminderStatus, p.notes
                ).joinToString(",") { esc(it) }
            )
        }
        sb.appendLine()

        // Section 3: Sales, Purchases & Stock Adjustments Ledger
        sb.appendLine("=== 3. STOCK TRANSACTIONS LEDGER (SALES / PURCHASES / ADJUSTMENTS) ===")
        sb.appendLine("ID,Type,Date & Time,Vaccine Name,Quantity,Unit Price (INR),Total Amount (INR),Profit (INR),Vendor / Patient Ref,Batch,Expiry Date,Notes")
        for (t in transactions) {
            sb.appendLine(
                listOf(
                    t.id, t.type, dateFmt.format(Date(t.timestamp)),
                    t.vaccineName, t.quantity, t.unitPrice, t.totalAmount, t.profit,
                    t.vendorName ?: t.referenceOrCustomer, t.batchNumber, t.expiryDate, t.notes
                ).joinToString(",") { esc(it) }
            )
        }
        sb.appendLine()

        // Section 4: Vaccine Vendors / Suppliers
        sb.appendLine("=== 4. VACCINE VENDORS & SUPPLIERS ===")
        sb.appendLine("ID,Vendor Name,Contact Person,Phone,Email,Address,Notes")
        for (ven in vendors) {
            sb.appendLine(
                listOf(
                    ven.id, ven.name, ven.contactPerson, ven.phone, ven.email, ven.address, ven.notes
                ).joinToString(",") { esc(it) }
            )
        }

        sb.toString()
    }

    fun buildWhatsAppSummaryText(
        vaccines: List<Vaccine>,
        patients: List<PatientRegistration>,
        transactions: List<StockTransaction>,
        vendors: List<Vendor>,
        clinicProfile: ClinicProfile
    ): String {
        val isHindi = AppLanguageManager.appLanguageFlow.value == AppLanguage.HINDI
        val nowStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val totalUnits = vaccines.sumOf { it.currentStock }
        val stockValue = vaccines.sumOf { it.currentStock * it.purchasePrice }
        val lowCount = vaccines.count { it.currentStock in 1..it.minStockAlert }
        val outCount = vaccines.count { it.currentStock <= 0 }

        val sb = StringBuilder()
        if (isHindi) {
            sb.appendLine("🏥 *${clinicProfile.clinicName.ifBlank { "Dr Priyank Vaccine Clinic" }}*")
            sb.appendLine("👨‍⚕️ ${clinicProfile.doctorName}")
            sb.appendLine("📅 डेटा सारांश: $nowStr")
            sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
            sb.appendLine("💉 कुल वैक्सीन प्रकार: *${vaccines.size}* (कुल खुराक: *$totalUnits*)")
            sb.appendLine("💰 कुल स्टॉक मूल्य: *₹${String.format(Locale.US, "%.0f", stockValue)}*")
            sb.appendLine("⚠️ कम स्टॉक: *$lowCount* | स्टॉक खत्म: *$outCount*")
            sb.appendLine("👶 पंजीकृत मरीज़: *${patients.size}*")
            sb.appendLine("🧾 कुल बिल/लेन-देन: *${transactions.size}* | वेंडर: *${vendors.size}*")
            sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
            sb.appendLine("📋 *वर्तमान वैक्सीन स्टॉक सूची:*")
            vaccines.sortedBy { it.name.lowercase() }.forEach { v ->
                val flag = when {
                    v.currentStock <= 0 -> "🔴"
                    v.currentStock <= v.minStockAlert -> "🟡"
                    else -> "🟢"
                }
                sb.appendLine("$flag ${v.name}: *${v.currentStock} ${v.unit}*")
            }
        } else {
            sb.appendLine("🏥 *${clinicProfile.clinicName.ifBlank { "Dr Priyank Vaccine Clinic" }}*")
            sb.appendLine("👨‍⚕️ ${clinicProfile.doctorName}")
            sb.appendLine("📅 Clinic Data Summary: $nowStr")
            sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
            sb.appendLine("💉 Total Vaccines: *${vaccines.size}* (Total Stock: *$totalUnits doses*)")
            sb.appendLine("💰 Inventory Value: *₹${String.format(Locale.US, "%.0f", stockValue)}*")
            sb.appendLine("⚠️ Low Stock: *$lowCount* | Out of Stock: *$outCount*")
            sb.appendLine("👶 Registered Patients: *${patients.size}*")
            sb.appendLine("🧾 Transactions: *${transactions.size}* | Vendors: *${vendors.size}*")
            sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
            sb.appendLine("📋 *Current Vaccine Stock Counts:*")
            vaccines.sortedBy { it.name.lowercase() }.forEach { v ->
                val flag = when {
                    v.currentStock <= 0 -> "🔴"
                    v.currentStock <= v.minStockAlert -> "🟡"
                    else -> "🟢"
                }
                sb.appendLine("$flag ${v.name}: *${v.currentStock} ${v.unit}*")
            }
        }
        return sb.toString().trim()
    }

    suspend fun createExportFile(
        context: Context,
        database: AppDatabase,
        format: ClinicExportFormat
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
        }
        // Clean older cached exports (keep folder tidy)
        exportDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach {
            try { it.delete() } catch (_: Exception) {}
        }

        val outFile = File(exportDir, suggestedFileName(format))
        val content = when (format) {
            ClinicExportFormat.JSON_BACKUP -> buildFullBackupJsonString(database)
            ClinicExportFormat.CSV_SPREADSHEET -> buildClinicCsvReportString(database)
        }
        outFile.writeText(content, Charsets.UTF_8)
        outFile
    }

    suspend fun writeExportToUri(
        context: Context,
        targetUri: Uri,
        database: AppDatabase,
        format: ClinicExportFormat
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = when (format) {
                ClinicExportFormat.JSON_BACKUP -> buildFullBackupJsonString(database)
                ClinicExportFormat.CSV_SPREADSHEET -> buildClinicCsvReportString(database)
            }
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext Result.failure(IllegalStateException("Unable to open destination file"))
            Result.success("Exported ${format.labelEn} successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing export to URI", e)
            Result.failure(e)
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildFileShareIntent(
        context: Context,
        file: File,
        mimeType: String,
        subject: String,
        extraText: String? = null
    ): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (!extraText.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, extraText)
            }
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Shares exported clinic file directly to Google Drive app if installed,
     * otherwise opens the Android system Share Sheet (where Google Drive or cloud apps can be chosen).
     */
    fun shareFileToGoogleDrive(
        context: Context,
        file: File,
        format: ClinicExportFormat
    ): Boolean {
        val subject = "Dr Priyank Clinic Backup (${file.name})"
        val intent = buildFileShareIntent(context, file, format.mimeType, subject)

        return try {
            if (isPackageInstalled(context, GOOGLE_DRIVE_PACKAGE)) {
                intent.setPackage(GOOGLE_DRIVE_PACKAGE)
                context.startActivity(intent)
                true
            } else {
                val chooser = Intent.createChooser(
                    buildFileShareIntent(context, file, format.mimeType, subject),
                    "Upload / Save to Google Drive"
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(chooser)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct Drive intent failed, falling back to chooser", e)
            try {
                val fallback = Intent.createChooser(
                    buildFileShareIntent(context, file, format.mimeType, subject),
                    "Upload / Save to Google Drive"
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Shares exported clinic file (.json or .csv) directly via WhatsApp (or WhatsApp Business),
     * falling back to Android Share Sheet if WhatsApp is not installed.
     */
    fun shareFileToWhatsApp(
        context: Context,
        file: File,
        format: ClinicExportFormat,
        captionText: String
    ): Boolean {
        val subject = "Dr Priyank Clinic Data (${file.name})"
        val targetPkg = when {
            isPackageInstalled(context, WHATSAPP_PACKAGE) -> WHATSAPP_PACKAGE
            isPackageInstalled(context, WHATSAPP_BUSINESS_PACKAGE) -> WHATSAPP_BUSINESS_PACKAGE
            else -> null
        }

        return try {
            val intent = buildFileShareIntent(context, file, format.mimeType, subject, captionText)
            if (targetPkg != null) {
                intent.setPackage(targetPkg)
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, "Share Clinic Data via WhatsApp")
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(chooser)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Direct WhatsApp file share failed, falling back to chooser", e)
            try {
                val fallback = Intent.createChooser(
                    buildFileShareIntent(context, file, format.mimeType, subject, captionText),
                    "Share Clinic Data"
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun shareTextToWhatsApp(context: Context, summaryText: String): Boolean {
        val targetPkg = when {
            isPackageInstalled(context, WHATSAPP_PACKAGE) -> WHATSAPP_PACKAGE
            isPackageInstalled(context, WHATSAPP_BUSINESS_PACKAGE) -> WHATSAPP_BUSINESS_PACKAGE
            else -> null
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summaryText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (targetPkg != null) setPackage(targetPkg)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, summaryText)
                }
                context.startActivity(
                    Intent.createChooser(fallback, "Share Clinic Summary")
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun shareFileViaOtherApps(context: Context, file: File, format: ClinicExportFormat): Boolean {
        return try {
            val intent = buildFileShareIntent(
                context = context,
                file = file,
                mimeType = format.mimeType,
                subject = "Dr Priyank Clinic Backup (${file.name})"
            )
            val chooser = Intent.createChooser(intent, "Share Clinic Backup File")
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "System share failed", e)
            false
        }
    }

    /**
     * Imports / restores a previously exported .json clinic backup file (e.g., from Google Drive or WhatsApp)
     * into Room and marks all restored items with a fresh timestamp so Cloud Sync updates other devices too.
     */
    suspend fun importBackupFromJsonUri(
        context: Context,
        uri: Uri,
        database: AppDatabase,
        replaceExisting: Boolean = false
    ): Result<ImportBackupResult> = withContext(Dispatchers.IO) {
        try {
            val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: return@withContext Result.failure(IllegalArgumentException("Could not read selected backup file"))

            val root = JSONObject(jsonText)
            val now = System.currentTimeMillis()

            val vaccines = mutableListOf<Vaccine>()
            root.optJSONArray("vaccines")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name", "").trim()
                    if (name.isBlank()) continue
                    vaccines.add(
                        Vaccine(
                            id = o.optLong("id", 0L),
                            name = name,
                            category = o.optString("category", "Routine"),
                            code = o.optString("code", ""),
                            currentStock = o.optInt("currentStock", 0).coerceAtLeast(0),
                            purchasePrice = o.optDouble("purchasePrice", 0.0),
                            sellingPrice = o.optDouble("sellingPrice", 0.0),
                            minStockAlert = o.optInt("minStockAlert", 10),
                            storageTemp = o.optString("storageTemp", "2°C to 8°C"),
                            unit = o.optString("unit", "doses"),
                            notes = o.optString("notes", "")
                        )
                    )
                }
            }

            val vendors = mutableListOf<Vendor>()
            root.optJSONArray("vendors")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name", "").trim()
                    if (name.isBlank()) continue
                    vendors.add(
                        Vendor(
                            id = o.optLong("id", 0L),
                            name = name,
                            contactPerson = o.optString("contactPerson", ""),
                            phone = o.optString("phone", ""),
                            email = o.optString("email", ""),
                            address = o.optString("address", ""),
                            notes = o.optString("notes", ""),
                            createdAt = o.optLong("createdAt", now)
                        )
                    )
                }
            }

            val transactions = mutableListOf<StockTransaction>()
            root.optJSONArray("transactions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    transactions.add(
                        StockTransaction(
                            id = o.optLong("id", 0L),
                            type = o.optString("type", "SALE"),
                            vaccineId = o.optLong("vaccineId", 0L),
                            vaccineName = o.optString("vaccineName", ""),
                            vendorId = if (o.has("vendorId") && !o.isNull("vendorId")) o.optLong("vendorId") else null,
                            vendorName = if (o.has("vendorName") && !o.isNull("vendorName")) o.optString("vendorName") else null,
                            quantity = o.optInt("quantity", 1).coerceAtLeast(1),
                            unitPrice = o.optDouble("unitPrice", 0.0),
                            costPricePerUnit = o.optDouble("costPricePerUnit", 0.0),
                            totalAmount = o.optDouble("totalAmount", 0.0).coerceAtLeast(0.0),
                            profit = o.optDouble("profit", 0.0),
                            batchNumber = o.optString("batchNumber", ""),
                            expiryDate = o.optString("expiryDate", ""),
                            referenceOrCustomer = o.optString("referenceOrCustomer", ""),
                            timestamp = o.optLong("timestamp", now),
                            notes = o.optString("notes", "")
                        )
                    )
                }
            }

            val patients = mutableListOf<PatientRegistration>()
            root.optJSONArray("patients")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pName = o.optString("patientName", "").trim()
                    if (pName.isBlank()) continue
                    patients.add(
                        PatientRegistration(
                            id = o.optLong("id", 0L),
                            patientName = pName,
                            dobOrAge = o.optString("dobOrAge", ""),
                            gender = o.optString("gender", "Male"),
                            parentName = o.optString("parentName", ""),
                            mobileNumber = o.optString("mobileNumber", ""),
                            vaccineGivenToday = o.optString("vaccineGivenToday", ""),
                            vaccineGivenBatch = o.optString("vaccineGivenBatch", ""),
                            vaccineGivenDose = o.optString("vaccineGivenDose", "Dose 1"),
                            dateGivenMillis = o.optLong("dateGivenMillis", now),
                            nextPlannedVaccine = o.optString("nextPlannedVaccine", ""),
                            nextDueDate = o.optString("nextDueDate", ""),
                            nextDueDateMillis = o.optLong("nextDueDateMillis", now),
                            reminderStatus = o.optString("reminderStatus", "PENDING"),
                            lastReminderMillis = o.optLong("lastReminderMillis", 0L),
                            notes = o.optString("notes", ""),
                            previousVaccinesHistory = o.optString("previousVaccinesHistory", "")
                        )
                    )
                }
            }

            val profile = root.optJSONObject("clinicProfile")?.let { o ->
                ClinicProfile(
                    id = o.optInt("id", 1),
                    clinicName = o.optString("clinicName", "Dr Priyank Vaccine Clinic"),
                    doctorName = o.optString("doctorName", "Dr. Priyank, MD (Pediatrics)"),
                    phone = o.optString("phone", "+91 98765 43210"),
                    email = o.optString("email", "drpriyank.clinic@gmail.com"),
                    address = o.optString("address", "102, Shivalik Medical Center, Ring Road, Ahmedabad"),
                    registrationNumber = o.optString("registrationNumber", "GMC-REG-29182"),
                    logoType = o.optString("logoType", "CUTE_TEDDY"),
                    themeColor = o.optString("themeColor", "TEAL"),
                    billFooterNote = o.optString("billFooterNote", ""),
                    customWhatsappMessage = o.optString("customWhatsappMessage", ""),
                    photoUri = o.optString("photoUri", "")
                )
            }

            if (vaccines.isEmpty() && patients.isEmpty() && transactions.isEmpty() && vendors.isEmpty() && profile == null) {
                return@withContext Result.failure(IllegalArgumentException("No clinic records found in this JSON file"))
            }

            if (replaceExisting) {
                database.vaccineDao().deleteAll()
                database.patientRegistrationDao().deleteAll()
                database.transactionDao().deleteAll()
                database.vendorDao().deleteAll()
            }

            if (vaccines.isNotEmpty()) {
                val ids = database.vaccineDao().insertAll(vaccines)
                ids.forEach { id -> SyncTimestampTracker.markVaccineModified(context, id, now) }
            }
            if (vendors.isNotEmpty()) {
                val ids = database.vendorDao().insertAll(vendors)
                ids.forEach { id -> SyncTimestampTracker.markVendorModified(context, id, now) }
            }
            if (transactions.isNotEmpty()) {
                database.transactionDao().insertAll(transactions)
                transactions.forEach { tx -> if (tx.id > 0L) SyncTimestampTracker.markTransactionModified(context, tx.id, now) }
            }
            if (patients.isNotEmpty()) {
                database.patientRegistrationDao().insertAll(patients)
                patients.forEach { p -> if (p.id > 0L) SyncTimestampTracker.markPatientModified(context, p.id, now) }
            }
            if (profile != null) {
                database.clinicProfileDao().insertOrUpdate(profile)
                SyncTimestampTracker.markProfileModified(context, now)
            }

            LocalBackupManager.markUserIntentionallyClearedData(context, false)
            LocalBackupManager.saveAutoBackup(context, database)

            Result.success(
                ImportBackupResult(
                    vaccinesCount = vaccines.size,
                    patientsCount = patients.size,
                    transactionsCount = transactions.size,
                    vendorsCount = vendors.size,
                    profileRestored = profile != null
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing JSON backup", e)
            Result.failure(e)
        }
    }
}
