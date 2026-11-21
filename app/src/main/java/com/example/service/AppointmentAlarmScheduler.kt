package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.database.AppDatabase
import com.example.data.model.PatientRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object AppointmentAlarmScheduler {

    private const val DAILY_CHECK_REQUEST_CODE = 9988

    /**
     * Schedules a local AlarmManager notification for a specific patient's vaccination appointment date at 9:00 AM.
     */
    fun schedulePatientAppointmentAlarm(context: Context, patient: PatientRegistration) {
        if (patient.id <= 0L || patient.nextDueDateMillis <= 0L || patient.reminderStatus == "SENT") {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Set appointment alert for 9:00 AM on the scheduled due date
        val triggerCalendar = Calendar.getInstance().apply {
            timeInMillis = patient.nextDueDateMillis
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = System.currentTimeMillis()
        var triggerTime = triggerCalendar.timeInMillis

        // If 9:00 AM on the due date is in the past, but the due date is today, trigger 5 minutes from now
        if (triggerTime <= now) {
            val endOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.timeInMillis

            if (patient.nextDueDateMillis in (now - 24 * 60 * 60 * 1000)..endOfToday) {
                triggerTime = now + (30 * 1000) // Trigger in 30 seconds if due today
            } else {
                // Completely in the past; don't schedule old alarms
                return
            }
        }

        val intent = Intent(context, VaccinationAlarmReceiver::class.java).apply {
            action = VaccinationAlarmReceiver.ACTION_PATIENT_VACCINATION_ALARM
            putExtra(VaccinationAlarmReceiver.EXTRA_PATIENT_ID, patient.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            patient.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (_: Exception) {
                // Ignore inexact fallback failure
            }
        }
    }

    /**
     * Checks if exact alarms can be scheduled on Android 12+ (API 31+) & Android 14 (API 34)
     */
    fun canScheduleExact(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    /**
     * Cancels an existing scheduled appointment alarm for a patient.
     */
    fun cancelPatientAppointmentAlarm(context: Context, patientId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, VaccinationAlarmReceiver::class.java).apply {
            action = VaccinationAlarmReceiver.ACTION_PATIENT_VACCINATION_ALARM
            putExtra(VaccinationAlarmReceiver.EXTRA_PATIENT_ID, patientId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            patientId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Scans patient records and reschedules alarms for all upcoming vaccination appointments.
     */
    fun rescheduleAllPatientAlarms(context: Context, patients: List<PatientRegistration>) {
        val now = System.currentTimeMillis()
        val upcoming = patients.filter { it.nextDueDateMillis >= (now - 24 * 60 * 60 * 1000) && it.reminderStatus != "SENT" }

        for (patient in upcoming) {
            schedulePatientAppointmentAlarm(context, patient)
        }

        scheduleDailyAppointmentCheckAlarm(context)
    }

    /**
     * Schedules a daily recurring AlarmManager check at 9:00 AM every morning.
     */
    fun scheduleDailyAppointmentCheckAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, VaccinationAlarmReceiver::class.java).apply {
            action = VaccinationAlarmReceiver.ACTION_CHECK_VACCINATION_APPOINTMENTS
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_CHECK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (_: Exception) {
            // Fallback
        }
    }

    /**
     * Immediately checks all patient records in the background and posts notifications for due appointments.
     */
    fun runImmediateAppointmentCheck(context: Context, onComplete: ((dueCount: Int) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val allPatients = db.patientRegistrationDao().getAllPatientsList()

            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val endOfDay = startOfDay + (24L * 60 * 60 * 1000)

            val duePatients = allPatients.filter {
                it.reminderStatus != "SENT" && it.nextDueDateMillis in startOfDay..endOfDay
            }

            if (duePatients.isNotEmpty()) {
                if (duePatients.size == 1) {
                    NotificationHelper.sendPatientAppointmentNotification(context, duePatients.first())
                } else {
                    val names = duePatients.map { "${it.patientName} (${it.nextPlannedVaccine})" }
                    NotificationHelper.sendDailySummaryNotification(context, duePatients.size, names)
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                onComplete?.invoke(duePatients.size)
            }
        }
    }
}
