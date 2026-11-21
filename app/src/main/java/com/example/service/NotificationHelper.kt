package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.PatientRegistration

object NotificationHelper {

    const val CHANNEL_ID = "vaccine_appointments_channel"
    const val CHANNEL_NAME = "Vaccination Appointments & Reminders"
    const val CHANNEL_DESCRIPTION = "Alerts and reminders for scheduled child vaccination appointments"

    const val EXTRA_TARGET_SCREEN = "extra_target_screen"
    const val TARGET_PATIENTS_SCREEN = "PATIENTS"

    /**
     * Initializes the high-priority notification channel for Android 8.0+ (API 26+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Checks if notification permission is granted
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Sends a notification for a specific patient's upcoming vaccination
     */
    fun sendPatientAppointmentNotification(context: Context, patient: PatientRegistration) {
        createNotificationChannel(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, TARGET_PATIENTS_SCREEN)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            patient.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Vaccination Due: ${patient.patientName}"
        val content = "${patient.patientName} (${patient.dobOrAge}) is due for ${patient.nextPlannedVaccine} today! Contact parent: ${patient.mobileNumber}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cute_clinic_logo)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "${patient.patientName} (${patient.dobOrAge}) is scheduled for vaccination:\n" +
                                "• Vaccine: ${patient.nextPlannedVaccine}\n" +
                                "• Due Schedule: ${patient.nextDueDate}\n" +
                                "• Parent: ${patient.parentName} (${patient.mobileNumber})\n\n" +
                                "Tap to open Patient Records and send WhatsApp reminder."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(patient.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission missing
        }
    }

    /**
     * Sends a consolidated daily summary notification when multiple patients are due
     */
    fun sendDailySummaryNotification(context: Context, dueTodayCount: Int, patientNames: List<String>) {
        createNotificationChannel(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, TARGET_PATIENTS_SCREEN)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "$dueTodayCount Patient Appointments Due Today!"
        val namesSummary = patientNames.take(4).joinToString(", ") + if (patientNames.size > 4) " and ${patientNames.size - 4} more" else ""
        val content = "Upcoming vaccinations for: $namesSummary"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cute_clinic_logo)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "You have $dueTodayCount vaccination appointment(s) due today at the clinic:\n" +
                                patientNames.joinToString("\n") { "• $it" } +
                                "\n\nTap to open clinic records and manage patient visits."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9999, notification)
        } catch (_: SecurityException) {
            // Permission missing
        }
    }

    /**
     * Instantly sends a test notification to verify audio, visual and banner delivery
     */
    fun sendTestNotification(context: Context) {
        createNotificationChannel(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_SCREEN, TARGET_PATIENTS_SCREEN)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cute_clinic_logo)
            .setContentTitle("Appointment Reminder Active!")
            .setContentText("Local vaccination alerts are configured and active for patient appointments.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Local vaccination alerts are active! The system checks patient records and uses AlarmManager to notify you on scheduled appointment dates.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1001, notification)
        } catch (_: SecurityException) {
            // Permission missing
        }
    }
}
