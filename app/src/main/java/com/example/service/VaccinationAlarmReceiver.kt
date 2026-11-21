package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class VaccinationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PATIENT_VACCINATION_ALARM = "com.example.ACTION_PATIENT_VACCINATION_ALARM"
        const val ACTION_CHECK_VACCINATION_APPOINTMENTS = "com.example.ACTION_CHECK_VACCINATION_APPOINTMENTS"
        const val EXTRA_PATIENT_ID = "extra_patient_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        when (action) {
            ACTION_PATIENT_VACCINATION_ALARM -> {
                val patientId = intent.getLongExtra(EXTRA_PATIENT_ID, -1L)
                if (patientId != -1L) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getInstance(context)
                            val patient = db.patientRegistrationDao().getPatientById(patientId)
                            if (patient != null) {
                                NotificationHelper.sendPatientAppointmentNotification(context, patient)
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            ACTION_CHECK_VACCINATION_APPOINTMENTS -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        checkAndNotifyUpcomingAppointments(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun checkAndNotifyUpcomingAppointments(context: Context) {
        val db = AppDatabase.getInstance(context)
        val allPatients = db.patientRegistrationDao().getAllPatientsList()

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfDay = startOfDay + (24L * 60 * 60 * 1000)

        // Patients scheduled for today or currently overdue and pending reminder
        val dueTodayPatients = allPatients.filter { patient ->
            patient.reminderStatus != "SENT" && patient.nextDueDateMillis in startOfDay..endOfDay
        }

        if (dueTodayPatients.isNotEmpty()) {
            if (dueTodayPatients.size == 1) {
                NotificationHelper.sendPatientAppointmentNotification(context, dueTodayPatients.first())
            } else {
                val names = dueTodayPatients.map { "${it.patientName} (${it.nextPlannedVaccine})" }
                NotificationHelper.sendDailySummaryNotification(context, dueTodayPatients.size, names)
            }
        }

        // Reschedule daily check for tomorrow at 9:00 AM
        AppointmentAlarmScheduler.scheduleDailyAppointmentCheckAlarm(context)
    }
}
