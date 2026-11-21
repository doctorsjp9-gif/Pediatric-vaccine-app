package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PatientRegistration
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientRegistrationDao {
    @Query("SELECT * FROM patient_registrations ORDER BY nextDueDateMillis ASC")
    fun getAllPatients(): Flow<List<PatientRegistration>>

    @Query("SELECT * FROM patient_registrations ORDER BY nextDueDateMillis ASC")
    suspend fun getAllPatientsList(): List<PatientRegistration>

    @Query("SELECT * FROM patient_registrations WHERE id = :id")
    suspend fun getPatientById(id: Long): PatientRegistration?

    @Query("SELECT * FROM patient_registrations WHERE nextDueDateMillis <= :targetMillis ORDER BY nextDueDateMillis ASC")
    fun getUpcomingDuePatients(targetMillis: Long): Flow<List<PatientRegistration>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(patient: PatientRegistration): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(patients: List<PatientRegistration>)

    @Update
    suspend fun update(patient: PatientRegistration)

    @Delete
    suspend fun delete(patient: PatientRegistration)

    @Query("UPDATE patient_registrations SET reminderStatus = 'SENT', lastReminderMillis = :sentMillis WHERE id = :patientId")
    suspend fun markReminderSent(patientId: Long, sentMillis: Long)

    @Query("UPDATE patient_registrations SET nextDueDate = :nextDueDate, nextDueDateMillis = :nextDueDateMillis, reminderStatus = 'PENDING' WHERE id = :patientId")
    suspend fun updateReminderDate(patientId: Long, nextDueDate: String, nextDueDateMillis: Long)

    @Query("UPDATE patient_registrations SET dobOrAge = :dobOrAge WHERE id = :patientId")
    suspend fun updatePatientAge(patientId: Long, dobOrAge: String)

    @Query("DELETE FROM patient_registrations")
    suspend fun deleteAll()

    @Query("DELETE FROM patient_registrations WHERE reminderStatus = 'SENT'")
    suspend fun deleteRemindedPatients()

    @Query("DELETE FROM patient_registrations WHERE nextDueDateMillis < :beforeMillis")
    suspend fun deleteOverduePatientsOlderThan(beforeMillis: Long)

    @Query("SELECT * FROM patient_registrations WHERE reminderStatus = 'SENT'")
    suspend fun getRemindedPatients(): List<PatientRegistration>

    @Query("SELECT * FROM patient_registrations WHERE nextDueDateMillis < :beforeMillis")
    suspend fun getOverduePatientsOlderThan(beforeMillis: Long): List<PatientRegistration>
}
