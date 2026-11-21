package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patient_registrations")
data class PatientRegistration(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientName: String = "",
    val dobOrAge: String = "",
    val gender: String = "Male",
    val parentName: String = "",
    val mobileNumber: String = "",
    val vaccineGivenToday: String = "",
    val vaccineGivenBatch: String = "",
    val vaccineGivenDose: String = "Dose 1",
    val dateGivenMillis: Long = System.currentTimeMillis(),
    val nextPlannedVaccine: String = "",
    val nextDueDate: String = "",
    val nextDueDateMillis: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000),
    val reminderStatus: String = "PENDING", // PENDING, SENT
    val lastReminderMillis: Long = 0L,
    val notes: String = "",
    val previousVaccinesHistory: String = ""
)
