package com.example.ui.util

import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Data model for a single vaccination dose taken by a patient.
 */
data class PatientVaccineEntry(
    val id: String = UUID.randomUUID().toString(),
    val vaccineName: String,
    val doseName: String = "Dose 1",
    val dateAdministeredMillis: Long = System.currentTimeMillis(),
    val batchNumber: String = "",
    val notes: String = "",
    val isHistorical: Boolean = false // e.g. taken at birth or previous hospital before clinic registration
)

object PatientVaccineRecordHelper {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    /**
     * Serializes a list of vaccine entries into a JSON string for persistent storage in Room and Firestore.
     */
    fun serializeEntries(entries: List<PatientVaccineEntry>): String {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("vaccineName", entry.vaccineName)
            obj.put("doseName", entry.doseName)
            obj.put("dateAdministeredMillis", entry.dateAdministeredMillis)
            obj.put("batchNumber", entry.batchNumber)
            obj.put("notes", entry.notes)
            obj.put("isHistorical", entry.isHistorical)
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Parses a JSON string of vaccine entries.
     */
    fun parseEntries(jsonStr: String): List<PatientVaccineEntry> {
        if (jsonStr.isBlank()) return emptyList()
        val result = mutableListOf<PatientVaccineEntry>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    PatientVaccineEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        vaccineName = obj.optString("vaccineName", ""),
                        doseName = obj.optString("doseName", "Dose 1"),
                        dateAdministeredMillis = obj.optLong("dateAdministeredMillis", System.currentTimeMillis()),
                        batchNumber = obj.optString("batchNumber", ""),
                        notes = obj.optString("notes", ""),
                        isHistorical = obj.optBoolean("isHistorical", false)
                    )
                )
            }
        } catch (_: Exception) {
            // In case of non-JSON format, return empty
        }
        return result
    }

    /**
     * Retrieves ALL previous vaccines taken by this patient across all visits, past registrations,
     * clinical history entries, and current dose.
     */
    fun getAllTakenVaccines(patient: PatientRegistration): List<PatientVaccineEntry> {
        val list = mutableListOf<PatientVaccineEntry>()

        // 1. Parse structured previous history
        if (patient.previousVaccinesHistory.isNotBlank()) {
            list.addAll(parseEntries(patient.previousVaccinesHistory))
        }

        // 2. Add vaccineGivenToday if present and not already represented in structured history
        val currentGiven = patient.vaccineGivenToday.trim()
        if (currentGiven.isNotBlank()) {
            val currentNames = if (currentGiven.contains(",")) {
                currentGiven.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                listOf(currentGiven)
            }

            for (vName in currentNames) {
                val expectedId = "current_${patient.id}_${vName.hashCode()}"
                val alreadyPresent = list.any {
                    it.id == expectedId ||
                            (it.vaccineName.equals(vName, ignoreCase = true) &&
                                    (it.doseName.equals(patient.vaccineGivenDose.ifBlank { "Dose 1" }, ignoreCase = true) ||
                                            abs(it.dateAdministeredMillis - patient.dateGivenMillis) < 86400000L))
                }
                if (!alreadyPresent) {
                    list.add(
                        PatientVaccineEntry(
                            id = expectedId,
                            vaccineName = vName,
                            doseName = patient.vaccineGivenDose.ifBlank { "Dose 1" },
                            dateAdministeredMillis = patient.dateGivenMillis,
                            batchNumber = patient.vaccineGivenBatch,
                            notes = "Administered at clinic",
                            isHistorical = false
                        )
                    )
                }
            }
        }

        // 3. Fallback: Parse bracketed entries in patient.notes e.g. [12 May 2026: MMR (Dose 1) Batch: X1 - notes]
        if (patient.notes.isNotBlank()) {
            val regex = Regex("""\[([^:]+):\s*([^(\]]+)(?:\(([^)]+)\))?(?:\s*Batch:\s*([^-\]]+))?(?:\s*-\s*([^\]]+))?\]""")
            val matches = regex.findAll(patient.notes)
            for (match in matches) {
                val dateStr = match.groupValues.getOrNull(1)?.trim() ?: ""
                val vNamesRaw = match.groupValues.getOrNull(2)?.trim() ?: ""
                val dose = match.groupValues.getOrNull(3)?.trim() ?: "Dose"
                val batch = match.groupValues.getOrNull(4)?.trim() ?: ""
                val remark = match.groupValues.getOrNull(5)?.trim() ?: ""

                if (vNamesRaw.isNotBlank()) {
                    val subNames = if (vNamesRaw.contains(",")) {
                        vNamesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    } else {
                        listOf(vNamesRaw)
                    }

                    var parsedMillis = System.currentTimeMillis()
                    try {
                        val parsed = dateFormat.parse(dateStr)
                        if (parsed != null) parsedMillis = parsed.time
                    } catch (_: Exception) {}

                    for (subName in subNames) {
                        val noteId = "note_${subName.hashCode()}_$parsedMillis"
                        val alreadyExists = list.any {
                            it.id == noteId ||
                                    (it.vaccineName.equals(subName, ignoreCase = true) &&
                                            (it.doseName.equals(dose, ignoreCase = true) || abs(it.dateAdministeredMillis - parsedMillis) < 86400000L))
                        }
                        if (!alreadyExists) {
                            list.add(
                                PatientVaccineEntry(
                                    id = noteId,
                                    vaccineName = subName,
                                    doseName = dose.ifBlank { "Subsequent Dose" },
                                    dateAdministeredMillis = parsedMillis,
                                    batchNumber = batch,
                                    notes = remark,
                                    isHistorical = true
                                )
                            )
                        }
                    }
                }
            }
        }

        // Sort chronologically ascending (oldest to newest)
        return list.sortedBy { it.dateAdministeredMillis }
    }

    /**
     * Appends a new administered vaccine or historical dose to the patient's record.
     * If an entry with the same vaccine name and dose already exists, updates its date/batch/notes.
     */
    fun appendVaccineToPatientHistory(
        patient: PatientRegistration,
        newVaccineNames: String,
        dose: String,
        batch: String,
        dateMillis: Long,
        notes: String = "",
        isHistorical: Boolean = false
    ): PatientRegistration {
        val currentEntries = getAllTakenVaccines(patient).toMutableList()

        val names = if (newVaccineNames.contains(",")) {
            newVaccineNames.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else if (newVaccineNames.isNotBlank()) {
            listOf(newVaccineNames.trim())
        } else {
            emptyList()
        }

        for (name in names) {
            val existingIdx = currentEntries.indexOfFirst {
                it.vaccineName.equals(name, ignoreCase = true) &&
                        it.doseName.equals(dose.ifBlank { "Dose 1" }, ignoreCase = true)
            }
            if (existingIdx >= 0) {
                val existing = currentEntries[existingIdx]
                currentEntries[existingIdx] = existing.copy(
                    dateAdministeredMillis = dateMillis,
                    batchNumber = if (batch.isNotBlank()) batch else existing.batchNumber,
                    notes = if (notes.isNotBlank()) notes else existing.notes
                )
            } else {
                currentEntries.add(
                    PatientVaccineEntry(
                        vaccineName = name,
                        doseName = dose.ifBlank { "Dose 1" },
                        dateAdministeredMillis = dateMillis,
                        batchNumber = batch,
                        notes = notes,
                        isHistorical = isHistorical
                    )
                )
            }
        }

        val serialized = serializeEntries(currentEntries)
        return patient.copy(previousVaccinesHistory = serialized)
    }

    /**
     * Updates the administered date (and optionally dose, batch, or notes) of a previously given vaccine entry.
     */
    fun updateVaccineDateInPatientHistory(
        patient: PatientRegistration,
        entryId: String,
        newDateMillis: Long,
        newDoseName: String? = null,
        newBatchNumber: String? = null,
        newNotes: String? = null
    ): PatientRegistration {
        val currentEntries = getAllTakenVaccines(patient).toMutableList()
        val idx = currentEntries.indexOfFirst { it.id == entryId }
        var updatedDateGivenMillis = patient.dateGivenMillis

        if (idx >= 0) {
            val target = currentEntries[idx]
            val updatedEntry = target.copy(
                dateAdministeredMillis = newDateMillis,
                doseName = newDoseName?.ifBlank { target.doseName } ?: target.doseName,
                batchNumber = newBatchNumber ?: target.batchNumber,
                notes = newNotes ?: target.notes
            )
            currentEntries[idx] = updatedEntry

            // Also sync patient.dateGivenMillis if this entry matches the primary vaccineGivenToday
            val currentGivenList = patient.vaccineGivenToday.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (entryId.startsWith("current_") ||
                (currentGivenList.size == 1 && currentGivenList.first().equals(target.vaccineName, ignoreCase = true))
            ) {
                updatedDateGivenMillis = newDateMillis
            }
        }

        val serialized = serializeEntries(currentEntries)
        return patient.copy(
            dateGivenMillis = updatedDateGivenMillis,
            previousVaccinesHistory = serialized
        )
    }

    /**
     * Removes a specific historical entry from patient's record.
     */
    fun removeVaccineFromPatientHistory(
        patient: PatientRegistration,
        entryId: String
    ): PatientRegistration {
        val currentEntries = getAllTakenVaccines(patient).toMutableList()
        currentEntries.removeAll { it.id == entryId }
        val serialized = serializeEntries(currentEntries)
        return patient.copy(previousVaccinesHistory = serialized)
    }

    /**
     * Formats the comprehensive vaccine history into an official WhatsApp message certificate (supports Hindi & English).
     */
    fun formatAllVaccinesForWhatsApp(
        patient: PatientRegistration,
        clinicProfile: ClinicProfile
    ): String {
        val clinicName = clinicProfile.clinicName.ifBlank { "Dr Priyank Vaccine Clinic" }
        val allVaccines = getAllTakenVaccines(patient)
        val useHindi = AppLanguageManager.currentAppLanguage == AppLanguage.HINDI ||
            AppLanguageManager.currentWhatsAppLanguage != ReminderLanguage.ENGLISH

        if (useHindi) {
            val header = "*$clinicName* 🩺\n" +
                    "*आधिकारिक बाल टीकाकरण रिकॉर्ड (OFFICIAL VACCINATION RECORD)*\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "👶 *बच्चे का नाम:* ${patient.patientName} (${patient.gender})\n" +
                    "🎂 *आयु / जन्म तिथि:* ${patient.dobOrAge}\n" +
                    "👨‍👩‍👦 *अभिभावक:* ${patient.parentName}\n" +
                    "📞 *मोबाइल:* ${patient.mobileNumber}\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━\n"

            val historyHeader = "📋 *दिए गए सभी टीके (कुल ${allVaccines.size} खुराक):*\n"
            val historyContent = if (allVaccines.isNotEmpty()) {
                allVaccines.mapIndexed { index, entry ->
                    val dateStr = dateFormat.format(Date(entry.dateAdministeredMillis))
                    val batchStr = if (entry.batchNumber.isNotBlank()) " | बैच: ${entry.batchNumber}" else ""
                    " ${index + 1}. ✅ *${entry.vaccineName}* (${entry.doseName})\n" +
                            "     📅 टीकाकरण तिथि: $dateStr$batchStr"
                }.joinToString("\n\n")
            } else {
                "   • अभी तक कोई टीका दर्ज नहीं है।"
            }

            val nextSection = if (patient.nextPlannedVaccine.isNotBlank()) {
                "\n\n━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                        "🗓️ *अगला निर्धारित टीका (UPCOMING VACCINE):*\n" +
                        "   • *${patient.nextPlannedVaccine}*\n" +
                        "   • 📅 निर्धारित तिथि (Due Date): ${patient.nextDueDate}\n" +
                        "   • स्थिति: ${patient.reminderStatus}"
            } else ""

            val footer = "\n━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "📍 *क्लिनिक का पता:* ${clinicProfile.address}\n" +
                    "📞 *हेल्पलाइन:* ${clinicProfile.phone}\n" +
                    "👨‍⚕️ *शिशु रोग विशेषज्ञ:* ${clinicProfile.doctorName} (Reg: ${clinicProfile.registrationNumber})\n" +
                    "_आपके बच्चे के उत्तम स्वास्थ्य की कामना!_ 🌟"

            return "$header$historyHeader$historyContent$nextSection$footer"
        }

        val header = "*$clinicName* 🩺\n" +
                "*OFFICIAL CHILD VACCINATION RECORD & HISTORY*\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👶 *Patient / Child:* ${patient.patientName} (${patient.gender})\n" +
                "🎂 *Age / DOB:* ${patient.dobOrAge}\n" +
                "👨‍👩‍👦 *Parent:* ${patient.parentName}\n" +
                "📞 *Mobile:* ${patient.mobileNumber}\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━\n"

        val historyHeader = "📋 *ALL VACCINES ADMINISTERED (${allVaccines.size} Doses Total):*\n"
        val historyContent = if (allVaccines.isNotEmpty()) {
            allVaccines.mapIndexed { index, entry ->
                val dateStr = dateFormat.format(Date(entry.dateAdministeredMillis))
                val batchStr = if (entry.batchNumber.isNotBlank()) " | Batch: ${entry.batchNumber}" else ""
                " ${index + 1}. ✅ *${entry.vaccineName}* (${entry.doseName})\n" +
                        "     📅 Administered: $dateStr$batchStr"
            }.joinToString("\n\n")
        } else {
            "   • No previous vaccines recorded yet."
        }

        val nextSection = if (patient.nextPlannedVaccine.isNotBlank()) {
            "\n\n━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "🗓️ *UPCOMING SCHEDULED VACCINES:*\n" +
                    "   • *${patient.nextPlannedVaccine}*\n" +
                    "   • 📅 Scheduled Due: ${patient.nextDueDate}\n" +
                    "   • Status: ${patient.reminderStatus}"
        } else ""

        val footer = "\n━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📍 *Clinic Address:* ${clinicProfile.address}\n" +
                "📞 *Helpline:* ${clinicProfile.phone}\n" +
                "👨‍⚕️ *Pediatrician:* ${clinicProfile.doctorName} (Reg: ${clinicProfile.registrationNumber})\n" +
                "_${clinicProfile.billFooterNote}_ 🌟"

        return "$header$historyHeader$historyContent$nextSection$footer"
    }
}
