package com.example.data.util

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.StockTransaction
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Ensures user clinic data (Vaccines, Patients, Transactions, Vendors, Clinic Profile)
 * is automatically backed up to persistent internal storage (filesDir) and never lost
 * across application updates or database schema upgrades.
 */
object LocalBackupManager {

    private const val BACKUP_FILE_NAME = "clinic_persistent_backup_v1.json"
    private const val PREFS_NAME = "clinic_data_protection_prefs"
    private const val KEY_INITIAL_SEED_DONE = "initial_seed_completed_v1"
    private const val KEY_USER_CLEARED_DATA = "user_intentionally_cleared_data"

    fun isInitialSeedDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_INITIAL_SEED_DONE, false)
    }

    fun markInitialSeedDone(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_INITIAL_SEED_DONE, true).apply()
    }

    fun markUserIntentionallyClearedData(context: Context, cleared: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_USER_CLEARED_DATA, cleared)
            .putBoolean(KEY_INITIAL_SEED_DONE, true)
            .apply()
    }

    fun didUserIntentionallyClearData(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USER_CLEARED_DATA, false)
    }

    fun hasBackupFile(context: Context): Boolean {
        val file = File(context.filesDir, BACKUP_FILE_NAME)
        return file.exists() && file.length() > 10
    }

    suspend fun saveAutoBackup(context: Context, database: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            val vaccines = database.vaccineDao().getAllVaccinesList()
            val vendors = database.vendorDao().getAllVendorsList()
            val transactions = database.transactionDao().getAllTransactionsList()
            val patients = database.patientRegistrationDao().getAllPatientsList()
            val profile = database.clinicProfileDao().getProfileSync()

            // Do not overwrite an existing non-empty backup with an accidental empty state unless user explicitly cleared data
            if (vaccines.isEmpty() && patients.isEmpty() && transactions.isEmpty() && vendors.isEmpty()) {
                if (!didUserIntentionallyClearData(context) && hasBackupFile(context)) {
                    return@withContext
                }
            }

            val root = JSONObject()
            root.put("backupTimestamp", System.currentTimeMillis())

            // Vaccines
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

            // Vendors
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

            // Transactions
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

            // Patients
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

            // Clinic Profile
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

            val file = File(context.filesDir, BACKUP_FILE_NAME)
            file.writeText(root.toString())
            markInitialSeedDone(context)
        } catch (e: Exception) {
            Log.w("LocalBackupManager", "Failed to save auto backup", e)
        }
    }

    /**
     * Restores data from the persistent JSON snapshot if the Room database was reset during an update.
     * Returns true if any records were restored.
     */
    suspend fun restoreFromAutoBackupIfNeeded(context: Context, database: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        try {
            if (didUserIntentionallyClearData(context)) {
                return@withContext false
            }
            val file = File(context.filesDir, BACKUP_FILE_NAME)
            if (!file.exists() || file.length() <= 10) {
                return@withContext false
            }

            val existingVaccines = database.vaccineDao().getAllVaccinesList()
            val existingPatients = database.patientRegistrationDao().getAllPatientsList()
            val existingTx = database.transactionDao().getAllTransactionsList()
            if (existingVaccines.isNotEmpty() || existingPatients.isNotEmpty() || existingTx.isNotEmpty()) {
                // Database already has live data, no need to restore over it
                return@withContext true
            }

            val root = JSONObject(file.readText())
            var restoredAny = false

            // Restore Vaccines
            root.optJSONArray("vaccines")?.let { arr ->
                val list = mutableListOf<Vaccine>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Vaccine(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", ""),
                            category = o.optString("category", "Routine"),
                            code = o.optString("code", ""),
                            currentStock = o.optInt("currentStock", 0),
                            purchasePrice = o.optDouble("purchasePrice", 0.0),
                            sellingPrice = o.optDouble("sellingPrice", 0.0),
                            minStockAlert = o.optInt("minStockAlert", 10),
                            storageTemp = o.optString("storageTemp", "2°C to 8°C"),
                            unit = o.optString("unit", "doses"),
                            notes = o.optString("notes", "")
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    database.vaccineDao().insertAll(list)
                    restoredAny = true
                }
            }

            // Restore Vendors
            root.optJSONArray("vendors")?.let { arr ->
                val list = mutableListOf<Vendor>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Vendor(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", ""),
                            contactPerson = o.optString("contactPerson", ""),
                            phone = o.optString("phone", ""),
                            email = o.optString("email", ""),
                            address = o.optString("address", ""),
                            notes = o.optString("notes", ""),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    database.vendorDao().insertAll(list)
                    restoredAny = true
                }
            }

            // Restore Transactions
            root.optJSONArray("transactions")?.let { arr ->
                val list = mutableListOf<StockTransaction>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        StockTransaction(
                            id = o.optLong("id", 0L),
                            type = o.optString("type", "SALE"),
                            vaccineId = o.optLong("vaccineId", 0L),
                            vaccineName = o.optString("vaccineName", ""),
                            vendorId = if (o.has("vendorId")) o.optLong("vendorId") else null,
                            vendorName = if (o.has("vendorName")) o.optString("vendorName") else null,
                            quantity = o.optInt("quantity", 1),
                            unitPrice = o.optDouble("unitPrice", 0.0),
                            costPricePerUnit = o.optDouble("costPricePerUnit", 0.0),
                            totalAmount = o.optDouble("totalAmount", 0.0),
                            profit = o.optDouble("profit", 0.0),
                            batchNumber = o.optString("batchNumber", ""),
                            expiryDate = o.optString("expiryDate", ""),
                            referenceOrCustomer = o.optString("referenceOrCustomer", ""),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            notes = o.optString("notes", "")
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    database.transactionDao().insertAll(list)
                    restoredAny = true
                }
            }

            // Restore Patients
            root.optJSONArray("patients")?.let { arr ->
                val list = mutableListOf<PatientRegistration>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        PatientRegistration(
                            id = o.optLong("id", 0L),
                            patientName = o.optString("patientName", ""),
                            dobOrAge = o.optString("dobOrAge", ""),
                            gender = o.optString("gender", "Male"),
                            parentName = o.optString("parentName", ""),
                            mobileNumber = o.optString("mobileNumber", ""),
                            vaccineGivenToday = o.optString("vaccineGivenToday", ""),
                            vaccineGivenBatch = o.optString("vaccineGivenBatch", ""),
                            vaccineGivenDose = o.optString("vaccineGivenDose", "Dose 1"),
                            dateGivenMillis = o.optLong("dateGivenMillis", System.currentTimeMillis()),
                            nextPlannedVaccine = o.optString("nextPlannedVaccine", ""),
                            nextDueDate = o.optString("nextDueDate", ""),
                            nextDueDateMillis = o.optLong("nextDueDateMillis", System.currentTimeMillis()),
                            reminderStatus = o.optString("reminderStatus", "PENDING"),
                            lastReminderMillis = o.optLong("lastReminderMillis", 0L),
                            notes = o.optString("notes", ""),
                            previousVaccinesHistory = o.optString("previousVaccinesHistory", "")
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    database.patientRegistrationDao().insertAll(list)
                    restoredAny = true
                }
            }

            // Restore Clinic Profile
            root.optJSONObject("clinicProfile")?.let { o ->
                val profile = ClinicProfile(
                    id = o.optInt("id", 1),
                    clinicName = o.optString("clinicName", "Dr Priyank Vaccine Clinic"),
                    doctorName = o.optString("doctorName", "Dr. Priyank, MD (Pediatrics)"),
                    phone = o.optString("phone", "+91 98765 43210"),
                    email = o.optString("email", "drpriyank.clinic@gmail.com"),
                    address = o.optString("address", "102, Shivalik Medical Center, Ring Road, Ahmedabad"),
                    registrationNumber = o.optString("registrationNumber", "GMC-REG-29182"),
                    logoType = o.optString("logoType", "CUTE_TEDDY"),
                    themeColor = o.optString("themeColor", "TEAL"),
                    billFooterNote = o.optString("billFooterNote", "Wishing your little one good health, bright smiles & strong immunity! 🌟"),
                    customWhatsappMessage = o.optString("customWhatsappMessage", ""),
                    photoUri = o.optString("photoUri", "")
                )
                database.clinicProfileDao().insertOrUpdate(profile)
                restoredAny = true
            }

            Log.i("LocalBackupManager", "Restored user data from persistent backup: $restoredAny")
            restoredAny
        } catch (e: Exception) {
            Log.e("LocalBackupManager", "Error restoring from auto backup", e)
            false
        }
    }
}
