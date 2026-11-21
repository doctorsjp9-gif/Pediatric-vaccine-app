package com.example.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Helper to convert local epoch millis to UTC midnight millis for DatePickerState.
 */
fun localMillisToUtcMidnight(localMillis: Long): Long {
    val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localCal.get(Calendar.YEAR),
            localCal.get(Calendar.MONTH),
            localCal.get(Calendar.DAY_OF_MONTH),
            0, 0, 0
        )
    }
    return utcCal.timeInMillis
}

/**
 * Helper to convert DatePickerState's selected UTC midnight millis back to local epoch millis at 9:00 AM.
 */
fun utcMidnightToLocalMillis(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMillis
    }
    val localCal = Calendar.getInstance().apply {
        set(
            utcCal.get(Calendar.YEAR),
            utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH),
            9, 0, 0
        )
        set(Calendar.MILLISECOND, 0)
    }
    return localCal.timeInMillis
}

/**
 * Formats a timestamp into a standard human readable date (e.g. "15 Nov 2026").
 */
fun formatTimestampToDate(millis: Long, pattern: String = "dd MMM yyyy"): String {
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(Date(millis))
}

/**
 * Calculates human-readable age given Date of Birth millis.
 */
fun calculateAgeFromDob(dobMillis: Long): String {
    val dobCal = Calendar.getInstance().apply { timeInMillis = dobMillis }
    val nowCal = Calendar.getInstance()

    if (dobCal.after(nowCal)) {
        return "Newborn (At Birth)"
    }

    var years = nowCal.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
    var months = nowCal.get(Calendar.MONTH) - dobCal.get(Calendar.MONTH)
    var days = nowCal.get(Calendar.DAY_OF_MONTH) - dobCal.get(Calendar.DAY_OF_MONTH)

    if (days < 0) {
        months--
        val prevMonthCal = (nowCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        days += prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    if (months < 0) {
        years--
        months += 12
    }

    val totalMonths = years * 12 + months

    return when {
        years == 0 && totalMonths == 0 && days <= 0 -> "Newborn (At Birth)"
        years == 0 && totalMonths == 0 -> "$days Days"
        years == 0 -> {
            if (days > 0) "$totalMonths Months ($days d)" else "$totalMonths Months"
        }
        years in 1..2 -> {
            if (months > 0) "$totalMonths Months ($years y $months m)" else "$years Year"
        }
        else -> {
            if (months > 0) "$years Yrs $months Mos" else "$years Years"
        }
    }
}

/**
 * Reusable Material 3 Date Picker Dialog modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerModal(
    title: String,
    initialDateMillis: Long? = null,
    minDateMillis: Long? = null,
    maxDateMillis: Long? = null,
    onDateSelected: (selectedDateMillis: Long, formattedDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    val utcInitialMillis = remember(initialDateMillis) {
        initialDateMillis?.let { localMillisToUtcMidnight(it) } ?: System.currentTimeMillis()
    }

    val selectableDates = remember(minDateMillis, maxDateMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                if (minDateMillis != null && utcTimeMillis < localMillisToUtcMidnight(minDateMillis)) {
                    return false
                }
                if (maxDateMillis != null && utcTimeMillis > localMillisToUtcMidnight(maxDateMillis)) {
                    return false
                }
                return true
            }
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = utcInitialMillis,
        selectableDates = selectableDates
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val localMillis = utcMidnightToLocalMillis(utcMillis)
                        val formatted = formatTimestampToDate(localMillis)
                        onDateSelected(localMillis, formatted)
                    }
                    onDismiss()
                }
            ) {
                Text("Confirm", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 12.dp)
                )
            }
        )
    }
}
