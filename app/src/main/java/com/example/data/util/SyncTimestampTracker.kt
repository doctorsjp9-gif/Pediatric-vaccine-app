package com.example.data.util

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Tracks per-record local modification timestamps, authoritative server-sync timestamps,
 * per-record dirty flags, and a stable device identifier in SharedPreferences.
 *
 * Tracking `isDirty` alongside `syncedServerMillis` makes multi-device Last-Write-Wins
 * synchronization immune to phone clock skew:
 * - Local edits set `isDirty = true` with local wall clock timestamp.
 * - Confirmed pushes/pulls from Firestore clear `isDirty = false` and store the server timestamp.
 * - Clean local records ALWAYS accept newer/differing confirmed Cloud state without pushing stale counts back.
 */
object SyncTimestampTracker {

    private const val PREFS_NAME = "clinic_sync_timestamps_v1"
    private const val KEY_DEVICE_ID = "sync_device_id"
    private const val KEY_PROFILE = "clinic_profile"
    private const val KEY_PROFILE_SERVER = "clinic_profile_server"
    private const val KEY_PROFILE_DIRTY = "clinic_profile_dirty"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDeviceId(context: Context): String {
        val sp = prefs(context)
        val existing = sp.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "dev_" + UUID.randomUUID().toString().replace("-", "").take(16)
        sp.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    private fun markModified(context: Context, prefix: String, id: Long, timestampMillis: Long): Long {
        if (id <= 0L) return timestampMillis
        prefs(context).edit()
            .putLong("${prefix}_$id", timestampMillis)
            .putBoolean("${prefix}_dirty_$id", true)
            .apply()
        return timestampMillis
    }

    private fun setSyncedClean(context: Context, prefix: String, id: Long, serverTimestampMillis: Long) {
        if (id <= 0L) return
        prefs(context).edit()
            .putLong("${prefix}_$id", serverTimestampMillis)
            .putLong("${prefix}_server_$id", serverTimestampMillis)
            .putBoolean("${prefix}_dirty_$id", false)
            .apply()
    }

    private fun getMeta(context: Context, prefix: String, id: Long): LocalSyncMeta {
        if (id <= 0L) return LocalSyncMeta()
        val sp = prefs(context)
        return LocalSyncMeta(
            syncedServerMillis = sp.getLong("${prefix}_server_$id", 0L),
            localModifiedMillis = sp.getLong("${prefix}_$id", 0L),
            isDirty = sp.getBoolean("${prefix}_dirty_$id", false)
        )
    }

    private fun removeRecord(context: Context, prefix: String, id: Long) {
        prefs(context).edit()
            .remove("${prefix}_$id")
            .remove("${prefix}_server_$id")
            .remove("${prefix}_dirty_$id")
            .apply()
    }

    // --- Vaccines ---
    fun markVaccineModified(context: Context, id: Long, timestampMillis: Long = System.currentTimeMillis()): Long =
        markModified(context, "vaccine", id, timestampMillis)

    fun getVaccineModifiedAt(context: Context, id: Long): Long =
        if (id <= 0L) 0L else prefs(context).getLong("vaccine_$id", 0L)

    fun getVaccineSyncMeta(context: Context, id: Long): LocalSyncMeta =
        getMeta(context, "vaccine", id)

    fun setVaccineSyncedAt(context: Context, id: Long, timestampMillis: Long) =
        setSyncedClean(context, "vaccine", id, timestampMillis)

    fun removeVaccineTimestamp(context: Context, id: Long) =
        removeRecord(context, "vaccine", id)

    // --- Patients ---
    fun markPatientModified(context: Context, id: Long, timestampMillis: Long = System.currentTimeMillis()): Long =
        markModified(context, "patient", id, timestampMillis)

    fun getPatientModifiedAt(context: Context, id: Long): Long =
        if (id <= 0L) 0L else prefs(context).getLong("patient_$id", 0L)

    fun getPatientSyncMeta(context: Context, id: Long): LocalSyncMeta =
        getMeta(context, "patient", id)

    fun setPatientSyncedAt(context: Context, id: Long, timestampMillis: Long) =
        setSyncedClean(context, "patient", id, timestampMillis)

    fun removePatientTimestamp(context: Context, id: Long) =
        removeRecord(context, "patient", id)

    // --- Vendors ---
    fun markVendorModified(context: Context, id: Long, timestampMillis: Long = System.currentTimeMillis()): Long =
        markModified(context, "vendor", id, timestampMillis)

    fun getVendorModifiedAt(context: Context, id: Long): Long =
        if (id <= 0L) 0L else prefs(context).getLong("vendor_$id", 0L)

    fun getVendorSyncMeta(context: Context, id: Long): LocalSyncMeta =
        getMeta(context, "vendor", id)

    fun setVendorSyncedAt(context: Context, id: Long, timestampMillis: Long) =
        setSyncedClean(context, "vendor", id, timestampMillis)

    fun removeVendorTimestamp(context: Context, id: Long) =
        removeRecord(context, "vendor", id)

    // --- Transactions ---
    fun markTransactionModified(context: Context, id: Long, timestampMillis: Long = System.currentTimeMillis()): Long =
        markModified(context, "tx", id, timestampMillis)

    fun getTransactionModifiedAt(context: Context, id: Long): Long =
        if (id <= 0L) 0L else prefs(context).getLong("tx_$id", 0L)

    fun getTransactionSyncMeta(context: Context, id: Long): LocalSyncMeta =
        getMeta(context, "tx", id)

    fun setTransactionSyncedAt(context: Context, id: Long, timestampMillis: Long) =
        setSyncedClean(context, "tx", id, timestampMillis)

    fun removeTransactionTimestamp(context: Context, id: Long) =
        removeRecord(context, "tx", id)

    // --- Clinic Profile ---
    fun markProfileModified(context: Context, timestampMillis: Long = System.currentTimeMillis()): Long {
        prefs(context).edit()
            .putLong(KEY_PROFILE, timestampMillis)
            .putBoolean(KEY_PROFILE_DIRTY, true)
            .apply()
        return timestampMillis
    }

    fun getProfileModifiedAt(context: Context): Long =
        prefs(context).getLong(KEY_PROFILE, 0L)

    fun getProfileSyncMeta(context: Context): LocalSyncMeta {
        val sp = prefs(context)
        return LocalSyncMeta(
            syncedServerMillis = sp.getLong(KEY_PROFILE_SERVER, 0L),
            localModifiedMillis = sp.getLong(KEY_PROFILE, 0L),
            isDirty = sp.getBoolean(KEY_PROFILE_DIRTY, false)
        )
    }

    fun setProfileSyncedAt(context: Context, timestampMillis: Long) {
        prefs(context).edit()
            .putLong(KEY_PROFILE, timestampMillis)
            .putLong(KEY_PROFILE_SERVER, timestampMillis)
            .putBoolean(KEY_PROFILE_DIRTY, false)
            .apply()
    }

    fun clearAllRecordTimestamps(context: Context) {
        val sp = prefs(context)
        val deviceId = getDeviceId(context)
        sp.edit().clear().putString(KEY_DEVICE_ID, deviceId).apply()
    }
}
