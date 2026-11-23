package com.example.data.util

import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.StockTransaction
import com.example.data.model.TransactionType
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * Safe Firestore <-> Room entity mappers with explicit document ID fallback, Number coercion,
 * legacy field compatibility, and server-timestamp metadata extraction.
 */
object FirestoreEntityMapper {

    private fun DocumentSnapshot.longField(vararg keys: String, default: Long = 0L): Long {
        for (key in keys) {
            val raw = get(key)
            if (raw is Number) return raw.toLong()
            if (raw is String) raw.toLongOrNull()?.let { return it }
        }
        return default
    }

    private fun DocumentSnapshot.intField(vararg keys: String, default: Int = 0): Int {
        for (key in keys) {
            val raw = get(key)
            if (raw is Number) return raw.toInt()
            if (raw is String) raw.toIntOrNull()?.let { return it }
        }
        return default
    }

    private fun DocumentSnapshot.doubleField(vararg keys: String, default: Double = 0.0): Double {
        for (key in keys) {
            val raw = get(key)
            if (raw is Number) return raw.toDouble()
            if (raw is String) raw.toDoubleOrNull()?.let { return it }
        }
        return default
    }

    private fun DocumentSnapshot.stringField(vararg keys: String, default: String = ""): String {
        for (key in keys) {
            val raw = get(key)
            if (raw is String) return raw
        }
        return default
    }

    private fun DocumentSnapshot.resolvedId(): Long {
        val bodyId = longField("id", default = 0L)
        if (bodyId > 0L) return bodyId
        return id.toLongOrNull()?.takeIf { it > 0L } ?: 0L
    }

    fun extractMeta(doc: DocumentSnapshot): RemoteSyncMeta {
        val serverTimestampMillis = try {
            doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.toDate()?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
        val clientModifiedMillis = doc.longField("lastModifiedMillis", default = 0L)
        val deviceId = doc.stringField("deviceId", default = "")
        val syncKey = doc.stringField("syncKey", default = "")
        return RemoteSyncMeta(
            serverTimestampMillis = serverTimestampMillis,
            clientModifiedMillis = clientModifiedMillis,
            deviceId = deviceId,
            syncKey = syncKey
        )
    }

    fun toVaccineSafe(doc: DocumentSnapshot): Vaccine? {
        if (!doc.exists()) return null
        val resolvedId = doc.resolvedId()
        if (resolvedId <= 0L) return null
        val name = doc.stringField("name").trim()
        if (name.isBlank()) return null

        return Vaccine(
            id = resolvedId,
            name = name,
            category = doc.stringField("category", default = "Routine"),
            code = doc.stringField("code", default = ""),
            currentStock = doc.intField("currentStock", "dosesRemaining", default = 0).coerceAtLeast(0),
            purchasePrice = doc.doubleField("purchasePrice", default = 0.0),
            sellingPrice = doc.doubleField("sellingPrice", default = 0.0),
            minStockAlert = doc.intField("minStockAlert", "minStockThreshold", default = 10).coerceAtLeast(0),
            storageTemp = doc.stringField("storageTemp", default = "2°C to 8°C"),
            unit = doc.stringField("unit", default = "doses"),
            notes = doc.stringField("notes", default = "")
        )
    }

    fun toVendorSafe(doc: DocumentSnapshot): Vendor? {
        if (!doc.exists()) return null
        val resolvedId = doc.resolvedId()
        if (resolvedId <= 0L) return null
        val name = doc.stringField("name").trim()
        if (name.isBlank()) return null

        return Vendor(
            id = resolvedId,
            name = name,
            contactPerson = doc.stringField("contactPerson", default = ""),
            phone = doc.stringField("phone", "phoneNumber", default = ""),
            email = doc.stringField("email", default = ""),
            address = doc.stringField("address", default = ""),
            notes = doc.stringField("notes", default = ""),
            createdAt = doc.longField("createdAt", default = System.currentTimeMillis())
        )
    }

    fun toTransactionSafe(doc: DocumentSnapshot): StockTransaction? {
        if (!doc.exists()) return null
        val resolvedId = doc.resolvedId()
        if (resolvedId <= 0L) return null
        val rawType = doc.stringField("type", default = TransactionType.PURCHASE)
        val safeType = when (rawType) {
            TransactionType.PURCHASE,
            TransactionType.SALE,
            TransactionType.ADJUSTMENT_ADD,
            TransactionType.ADJUSTMENT_REDUCE -> rawType
            else -> TransactionType.PURCHASE
        }
        val vendorIdRaw = doc.get("vendorId")
        val vendorId = (vendorIdRaw as? Number)?.toLong()
        val vendorName = (doc.get("vendorName") as? String)?.takeIf { it.isNotBlank() }

        return StockTransaction(
            id = resolvedId,
            type = safeType,
            vaccineId = doc.longField("vaccineId", default = 0L),
            vaccineName = doc.stringField("vaccineName", default = ""),
            vendorId = vendorId,
            vendorName = vendorName,
            quantity = doc.intField("quantity", default = 1).coerceAtLeast(1),
            unitPrice = doc.doubleField("unitPrice", default = 0.0),
            costPricePerUnit = doc.doubleField("costPricePerUnit", default = 0.0),
            totalAmount = doc.doubleField("totalAmount", default = 0.0).coerceAtLeast(0.0),
            profit = doc.doubleField("profit", default = 0.0),
            batchNumber = doc.stringField("batchNumber", default = ""),
            expiryDate = doc.stringField("expiryDate", default = ""),
            referenceOrCustomer = doc.stringField("referenceOrCustomer", "patientName", "invoiceNumber", default = ""),
            timestamp = doc.longField("timestamp", "transactionDate", default = System.currentTimeMillis()),
            notes = doc.stringField("notes", default = "")
        )
    }

    fun toPatientSafe(doc: DocumentSnapshot): PatientRegistration? {
        if (!doc.exists()) return null
        val resolvedId = doc.resolvedId()
        if (resolvedId <= 0L) return null
        val patientName = doc.stringField("patientName").trim()
        if (patientName.isBlank()) return null

        return PatientRegistration(
            id = resolvedId,
            patientName = patientName,
            dobOrAge = doc.stringField("dobOrAge", "patientAgeOrDob", default = ""),
            gender = doc.stringField("gender", default = "Male"),
            parentName = doc.stringField("parentName", default = ""),
            mobileNumber = doc.stringField("mobileNumber", "parentContactNumber", default = "").take(50),
            vaccineGivenToday = doc.stringField("vaccineGivenToday", default = ""),
            vaccineGivenBatch = doc.stringField("vaccineGivenBatch", "vaccineBatchNumber", default = ""),
            vaccineGivenDose = doc.stringField("vaccineGivenDose", "vaccineDoseGiven", default = "Dose 1"),
            dateGivenMillis = doc.longField("dateGivenMillis", "registrationDate", default = System.currentTimeMillis()),
            nextPlannedVaccine = doc.stringField("nextPlannedVaccine", default = ""),
            nextDueDate = doc.stringField("nextDueDate", default = ""),
            nextDueDateMillis = doc.longField("nextDueDateMillis", default = 0L),
            reminderStatus = doc.stringField("reminderStatus", default = "PENDING"),
            lastReminderMillis = doc.longField("lastReminderMillis", "reminderSentDate", default = 0L),
            notes = doc.stringField("notes", "doctorRemarks", default = ""),
            previousVaccinesHistory = doc.stringField("previousVaccinesHistory", default = "")
        )
    }

    fun toClinicProfileSafe(doc: DocumentSnapshot): ClinicProfile? {
        if (!doc.exists()) return null
        val clinicName = doc.stringField("clinicName", default = "Dr Priyank Vaccine Clinic").ifBlank {
            "Dr Priyank Vaccine Clinic"
        }
        val doctorName = doc.stringField("doctorName", default = "Dr. Priyank, MD (Pediatrics)").ifBlank {
            "Dr. Priyank, MD (Pediatrics)"
        }
        return ClinicProfile(
            id = doc.intField("id", default = 1).takeIf { it > 0 } ?: 1,
            clinicName = clinicName,
            doctorName = doctorName,
            phone = doc.stringField("phone", "mobileNumber", default = "+91 98765 43210"),
            email = doc.stringField("email", default = "contact@drpriyankclinic.com"),
            address = doc.stringField("address", default = "102, Shivalik Medical Center, Ring Road, Ahmedabad"),
            registrationNumber = doc.stringField("registrationNumber", default = "GMC-REG-29182"),
            logoType = doc.stringField("logoType", default = "CUTE_TEDDY"),
            themeColor = doc.stringField("themeColor", default = "TEAL"),
            billFooterNote = doc.stringField("billFooterNote", default = ""),
            customWhatsappMessage = doc.stringField("customWhatsappMessage", default = ""),
            photoUri = doc.stringField("photoUri", "customLogoUri", default = "")
        )
    }

    // --- Entity -> Firestore Map Serializers (100% aligned with firestore.rules) ---

    fun Vaccine.toFirestoreMap(modifiedAtMillis: Long, deviceId: String): Map<String, Any> = buildMap {
        put("id", id)
        put("name", name.ifBlank { "Vaccine" }.take(200))
        put("category", category)
        put("code", code)
        put("syncKey", SyncIdAndConflictResolver.canonicalVaccineKey(code, name))
        put("currentStock", currentStock.coerceAtLeast(0))
        put("purchasePrice", purchasePrice)
        put("sellingPrice", sellingPrice)
        put("minStockAlert", minStockAlert.coerceAtLeast(0))
        put("storageTemp", storageTemp)
        put("unit", unit)
        put("notes", notes)
        put("lastModifiedMillis", modifiedAtMillis)
        put("deviceId", deviceId)
        put("updatedAt", FieldValue.serverTimestamp())
    }

    fun Vendor.toFirestoreMap(modifiedAtMillis: Long, deviceId: String): Map<String, Any> = buildMap {
        put("id", id)
        put("name", name.ifBlank { "Vendor" }.take(200))
        put("contactPerson", contactPerson)
        put("phone", phone)
        put("email", email)
        put("address", address)
        put("notes", notes)
        put("createdAt", createdAt)
        put("lastModifiedMillis", modifiedAtMillis)
        put("deviceId", deviceId)
        put("updatedAt", FieldValue.serverTimestamp())
    }

    fun StockTransaction.toFirestoreMap(modifiedAtMillis: Long, deviceId: String): Map<String, Any> = buildMap {
        put("id", id)
        put("type", type)
        put("vaccineId", vaccineId)
        put("vaccineName", vaccineName)
        if (vendorId != null) put("vendorId", vendorId)
        if (!vendorName.isNullOrBlank()) put("vendorName", vendorName)
        put("quantity", quantity.coerceAtLeast(1))
        put("unitPrice", unitPrice)
        put("costPricePerUnit", costPricePerUnit)
        put("totalAmount", totalAmount.coerceAtLeast(0.0))
        put("profit", profit)
        put("batchNumber", batchNumber)
        put("expiryDate", expiryDate)
        put("referenceOrCustomer", referenceOrCustomer)
        put("timestamp", timestamp)
        put("notes", notes)
        put("lastModifiedMillis", modifiedAtMillis)
        put("deviceId", deviceId)
        put("updatedAt", FieldValue.serverTimestamp())
    }

    fun PatientRegistration.toFirestoreMap(modifiedAtMillis: Long, deviceId: String): Map<String, Any> = buildMap {
        put("id", id)
        put("patientName", patientName.ifBlank { "Patient" }.take(200))
        put("dobOrAge", dobOrAge)
        put("gender", gender)
        put("parentName", parentName)
        put("mobileNumber", mobileNumber.take(50))
        put("vaccineGivenToday", vaccineGivenToday)
        put("vaccineGivenBatch", vaccineGivenBatch)
        put("vaccineGivenDose", vaccineGivenDose)
        put("dateGivenMillis", dateGivenMillis)
        put("nextPlannedVaccine", nextPlannedVaccine)
        put("nextDueDate", nextDueDate)
        put("nextDueDateMillis", nextDueDateMillis)
        put("reminderStatus", reminderStatus)
        put("lastReminderMillis", lastReminderMillis)
        put("notes", notes)
        put("previousVaccinesHistory", previousVaccinesHistory)
        put("lastModifiedMillis", modifiedAtMillis)
        put("deviceId", deviceId)
        put("updatedAt", FieldValue.serverTimestamp())
    }

    fun ClinicProfile.toFirestoreMap(modifiedAtMillis: Long, deviceId: String): Map<String, Any> = buildMap {
        put("id", id)
        put("clinicName", clinicName.ifBlank { "Dr Priyank Vaccine Clinic" }.take(200))
        put("doctorName", doctorName.ifBlank { "Dr. Priyank, MD (Pediatrics)" }.take(200))
        put("phone", phone.take(50))
        put("email", email)
        put("address", address)
        put("registrationNumber", registrationNumber)
        put("logoType", logoType)
        put("themeColor", themeColor)
        put("billFooterNote", billFooterNote)
        put("customWhatsappMessage", customWhatsappMessage)
        put("photoUri", photoUri)
        put("lastModifiedMillis", modifiedAtMillis)
        put("deviceId", deviceId)
        put("updatedAt", FieldValue.serverTimestamp())
    }
}
