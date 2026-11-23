package com.example.data.util

import com.example.data.model.Vaccine
import kotlin.random.Random

/**
 * Metadata extracted from a remote Firestore document snapshot.
 * [serverTimestampMillis] is stamped by Firestore's authoritative server clock (`FieldValue.serverTimestamp()`),
 * making cross-device ordering immune to phone clock skew.
 */
data class RemoteSyncMeta(
    val serverTimestampMillis: Long = 0L,
    val clientModifiedMillis: Long = 0L,
    val deviceId: String = "",
    val syncKey: String = ""
) {
    val effectiveMillis: Long
        get() = if (serverTimestampMillis > 0L) serverTimestampMillis else clientModifiedMillis
}

/**
 * Metadata tracked locally per record in SharedPreferences.
 * [isDirty] distinguishes unpushed local edits from clean records previously pulled/synced from Cloud.
 */
data class LocalSyncMeta(
    val syncedServerMillis: Long = 0L,
    val localModifiedMillis: Long = 0L,
    val isDirty: Boolean = false
)

enum class ConflictWinner {
    REMOTE,
    LOCAL,
    EQUAL
}

data class RemoteVaccineEntry(
    val vaccine: Vaccine,
    val meta: RemoteSyncMeta
)

/**
 * Deterministic reconciliation plan produced for the Vaccines collection.
 * Handles exact ID updates, cross-device canonical matching (same vaccine under different IDs),
 * and ID collision recovery (two different vaccines sharing the same legacy auto-increment ID).
 */
data class VaccineMergePlan(
    val toUpsertLocally: List<Pair<Vaccine, Long>>,
    val toDeleteLocalDuplicateIds: List<Long>,
    val transactionVaccineIdRemappings: Map<Long, Long>,
    val toPushToCloud: List<Pair<Vaccine, Long>>,
    val alignedCleanTimestamps: Map<Long, Long>
)

object SyncIdAndConflictResolver {

    private var lastGeneratedId: Long = 0L

    /**
     * Generates a positive, collision-resistant Long ID for newly created local records.
     * Prevents Device A and Device B from independently assigning the same sequential Room
     * auto-increment ID (e.g. 7, 8, 9) to completely different vaccines, patients, vendors, or transactions.
     * Always > 1_000_000L so it never collides with pre-seeded clinic catalog IDs (1..6).
     */
    @Synchronized
    fun generateSyncSafeId(nowMillis: Long = System.currentTimeMillis()): Long {
        val candidateBase = nowMillis.coerceAtLeast(1_700_000_000_000L) * 1000L + Random.nextInt(0, 1000)
        val nextId = if (candidateBase <= lastGeneratedId) lastGeneratedId + 1L else candidateBase
        lastGeneratedId = nextId
        return nextId
    }

    /**
     * Canonical normalized identity key for a vaccine across devices.
     * Uses normalized vaccine code when available, falling back to normalized name.
     */
    fun canonicalVaccineKey(code: String, name: String): String {
        val cleanCode = normalizeToken(code)
        if (cleanCode.isNotBlank()) return "code:$cleanCode"
        val cleanName = normalizeToken(name)
        return if (cleanName.isNotBlank()) "name:$cleanName" else ""
    }

    fun canonicalVaccineNameKey(name: String): String = normalizeToken(name)

    fun canonicalVaccineCodeKey(code: String): String = normalizeToken(code)

    private fun normalizeToken(raw: String): String =
        raw.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * Returns true when two Vaccine records represent the same logical vaccine product
     * (matching non-blank code OR matching normalized name).
     */
    fun isSameLogicalVaccine(a: Vaccine, b: Vaccine): Boolean {
        val codeA = canonicalVaccineCodeKey(a.code)
        val codeB = canonicalVaccineCodeKey(b.code)
        if (codeA.isNotBlank() && codeB.isNotBlank() && codeA == codeB) {
            return true
        }
        val nameA = canonicalVaccineNameKey(a.name)
        val nameB = canonicalVaccineNameKey(b.name)
        return nameA.isNotBlank() && nameB.isNotBlank() && nameA == nameB
    }

    /**
     * Clock-skew-proof conflict resolution between a confirmed remote Firestore document and a local Room record.
     *
     * Key invariant:
     * - If the local record is CLEAN (`!localMeta.isDirty`), this device has NO unpushed local changes for it.
     *   Any differing server-confirmed remote state (especially from another device) unconditionally updates local,
     *   eliminating clock-skew rejections where Device B's fast clock previously blocked Device A's newer stock count!
     * - If the local record IS DIRTY (`localMeta.isDirty`), both devices edited concurrently/offline:
     *   - If the remote doc was written by this same device, keep LOCAL so we don't roll back a newer local edit.
     *   - Otherwise, prefer whichever timestamp is strictly newer (using Firestore's server timestamp on remote).
     */
    fun resolveRecordConflict(
        remoteMeta: RemoteSyncMeta,
        localMeta: LocalSyncMeta,
        myDeviceId: String,
        contentDiffers: Boolean
    ): ConflictWinner {
        if (!contentDiffers) return ConflictWinner.EQUAL

        if (!localMeta.isDirty) {
            // Local has no unpushed changes -> never overwrite cloud with stale clean local data!
            if (remoteMeta.deviceId != myDeviceId) {
                return ConflictWinner.REMOTE
            }
            if (remoteMeta.effectiveMillis >= localMeta.syncedServerMillis || localMeta.syncedServerMillis == 0L) {
                return ConflictWinner.REMOTE
            }
            return ConflictWinner.REMOTE
        }

        // Local HAS unpushed dirty edits on this device
        if (remoteMeta.deviceId.isNotBlank() && remoteMeta.deviceId == myDeviceId) {
            // Remote snapshot is just an older copy written by this same device
            return ConflictWinner.LOCAL
        }

        val remoteTs = remoteMeta.effectiveMillis
        val localTs = maxOf(localMeta.localModifiedMillis, localMeta.syncedServerMillis)

        return when {
            remoteTs > localTs -> ConflictWinner.REMOTE
            else -> ConflictWinner.LOCAL
        }
    }

    /**
     * Full two-way vaccine reconciliation plan (used by `syncWithConfig` in PRESERVE_BOTH mode).
     * Guarantees:
     * 1. Stock count updates on Device A always propagate to clean Device B regardless of phone clock skew.
     * 2. Same vaccine created under different Room IDs on Device A vs Device B converges to the cloud document's ID
     *    with the winning stock count, deletes the local duplicate, and remaps local StockTransactions.
     * 3. Two *different* vaccines colliding on the same legacy auto-increment ID (e.g. ID 7 on both devices) are
     *    both preserved by assigning the local colliding vaccine a fresh sync-safe ID and pushing it to Cloud.
     */
    fun planVaccineMerge(
        localVaccines: List<Vaccine>,
        remoteEntries: List<RemoteVaccineEntry>,
        localMetaProvider: (Long) -> LocalSyncMeta,
        myDeviceId: String,
        nowMillis: Long,
        idGenerator: () -> Long = { generateSyncSafeId() }
    ): VaccineMergePlan {
        val toUpsertLocally = mutableListOf<Pair<Vaccine, Long>>()
        val toDeleteLocalDuplicateIds = mutableListOf<Long>()
        val remappings = linkedMapOf<Long, Long>()
        val toPushToCloud = mutableListOf<Pair<Vaccine, Long>>()
        val alignedCleanTimestamps = linkedMapOf<Long, Long>()

        val localById = localVaccines.associateBy { it.id }.toMutableMap()
        val matchedLocalIds = mutableSetOf<Long>()
        val matchedRemoteIds = mutableSetOf<Long>()

        // Pass 1: Exact ID matches where the records represent the same logical vaccine
        for (entry in remoteEntries) {
            val remote = entry.vaccine
            val local = localById[remote.id] ?: continue
            if (!isSameLogicalVaccine(local, remote)) continue

            matchedLocalIds.add(local.id)
            matchedRemoteIds.add(remote.id)

            val localMeta = localMetaProvider(local.id)
            val contentDiffers = local != remote
            val winner = resolveRecordConflict(entry.meta, localMeta, myDeviceId, contentDiffers)
            val effectiveRemoteTs = entry.meta.effectiveMillis.takeIf { it > 0L } ?: nowMillis

            when (winner) {
                ConflictWinner.EQUAL -> {
                    alignedCleanTimestamps[remote.id] = maxOf(effectiveRemoteTs, localMeta.syncedServerMillis)
                }
                ConflictWinner.REMOTE -> {
                    toUpsertLocally.add(remote to effectiveRemoteTs)
                }
                ConflictWinner.LOCAL -> {
                    val stamp = maxOf(localMeta.localModifiedMillis, effectiveRemoteTs + 1L, nowMillis)
                    toPushToCloud.add(local to stamp)
                }
            }
        }

        // Pass 2: Canonical key matches across different IDs (e.g., same vaccine added on 2 devices with ID 7 vs ID 8)
        val unmatchedLocals = localVaccines.filter { it.id !in matchedLocalIds }.toMutableList()
        for (entry in remoteEntries) {
            val remote = entry.vaccine
            if (remote.id in matchedRemoteIds) continue

            val canonicalLocal = unmatchedLocals.firstOrNull { isSameLogicalVaccine(it, remote) }
            if (canonicalLocal != null) {
                matchedLocalIds.add(canonicalLocal.id)
                matchedRemoteIds.add(remote.id)
                unmatchedLocals.remove(canonicalLocal)

                val localMeta = localMetaProvider(canonicalLocal.id)
                val normalizedLocalWithRemoteId = canonicalLocal.copy(id = remote.id)
                val contentDiffers = normalizedLocalWithRemoteId != remote
                val winner = resolveRecordConflict(entry.meta, localMeta, myDeviceId, contentDiffers)
                val effectiveRemoteTs = entry.meta.effectiveMillis.takeIf { it > 0L } ?: nowMillis

                // Converge local row to cloud's canonical document ID
                if (canonicalLocal.id != remote.id) {
                    toDeleteLocalDuplicateIds.add(canonicalLocal.id)
                    remappings[canonicalLocal.id] = remote.id
                }

                when (winner) {
                    ConflictWinner.EQUAL, ConflictWinner.REMOTE -> {
                        toUpsertLocally.add(remote to effectiveRemoteTs)
                    }
                    ConflictWinner.LOCAL -> {
                        val stamp = maxOf(localMeta.localModifiedMillis, effectiveRemoteTs + 1L, nowMillis)
                        toUpsertLocally.add(normalizedLocalWithRemoteId to stamp)
                        toPushToCloud.add(normalizedLocalWithRemoteId to stamp)
                    }
                }
            }
        }

        // Pass 3: Remaining remote vaccines (brand new to this device, OR colliding on ID with a different local vaccine!)
        val remoteIdsSet = remoteEntries.map { it.vaccine.id }.toSet()
        for (entry in remoteEntries) {
            val remote = entry.vaccine
            if (remote.id in matchedRemoteIds) continue

            val effectiveRemoteTs = entry.meta.effectiveMillis.takeIf { it > 0L } ?: nowMillis
            val collidingLocal = unmatchedLocals.firstOrNull { it.id == remote.id }
            if (collidingLocal != null) {
                // Two DIFFERENT vaccines had the same legacy auto-increment ID!
                // Preserve BOTH: remote keeps its cloud ID, local gets relocated to a fresh sync-safe ID and pushed.
                unmatchedLocals.remove(collidingLocal)
                matchedLocalIds.add(collidingLocal.id)

                val freshId = generateNonCollidingId(remoteIdsSet + localById.keys, idGenerator)
                val relocatedLocal = collidingLocal.copy(id = freshId)
                val localMeta = localMetaProvider(collidingLocal.id)
                val pushStamp = maxOf(localMeta.localModifiedMillis, nowMillis)

                remappings[collidingLocal.id] = freshId
                toUpsertLocally.add(remote to effectiveRemoteTs)
                toUpsertLocally.add(relocatedLocal to pushStamp)
                toPushToCloud.add(relocatedLocal to pushStamp)
            } else {
                toUpsertLocally.add(remote to effectiveRemoteTs)
            }
            matchedRemoteIds.add(remote.id)
        }

        // Pass 4: Remaining local-only vaccines that don't exist in Cloud yet -> push to Cloud
        for (localOnly in unmatchedLocals) {
            val localMeta = localMetaProvider(localOnly.id)
            val stamp = maxOf(localMeta.localModifiedMillis, nowMillis)
            toPushToCloud.add(localOnly to stamp)
        }

        return VaccineMergePlan(
            toUpsertLocally = toUpsertLocally,
            toDeleteLocalDuplicateIds = toDeleteLocalDuplicateIds.distinct(),
            transactionVaccineIdRemappings = remappings,
            toPushToCloud = toPushToCloud,
            alignedCleanTimestamps = alignedCleanTimestamps
        )
    }

    /**
     * Incremental live-listener reconciliation for confirmed Firestore Vaccine documents.
     * Never deletes untouched local-only vaccines; only applies confirmed remote updates/adds
     * and canonical-ID convergence.
     */
    fun planLiveVaccineSnapshot(
        localVaccines: List<Vaccine>,
        changedRemoteEntries: List<RemoteVaccineEntry>,
        localMetaProvider: (Long) -> LocalSyncMeta,
        myDeviceId: String,
        nowMillis: Long,
        idGenerator: () -> Long = { generateSyncSafeId() }
    ): VaccineMergePlan {
        val toUpsertLocally = mutableListOf<Pair<Vaccine, Long>>()
        val toDeleteLocalDuplicateIds = mutableListOf<Long>()
        val remappings = linkedMapOf<Long, Long>()
        val toPushToCloud = mutableListOf<Pair<Vaccine, Long>>()
        val alignedCleanTimestamps = linkedMapOf<Long, Long>()

        val localById = localVaccines.associateBy { it.id }.toMutableMap()
        val usedIds = localById.keys.toMutableSet().apply { addAll(changedRemoteEntries.map { it.vaccine.id }) }

        for (entry in changedRemoteEntries) {
            val remote = entry.vaccine
            val effectiveRemoteTs = entry.meta.effectiveMillis.takeIf { it > 0L } ?: nowMillis

            // If this snapshot is the server confirmation of our OWN device's write,
            // align the clean server timestamp without overwriting any newer pending local edit.
            if (entry.meta.deviceId.isNotBlank() && entry.meta.deviceId == myDeviceId) {
                val local = localById[remote.id]
                if (local != null && local == remote) {
                    alignedCleanTimestamps[remote.id] = effectiveRemoteTs
                }
                continue
            }

            val exactLocal = localById[remote.id]
            if (exactLocal != null && isSameLogicalVaccine(exactLocal, remote)) {
                val localMeta = localMetaProvider(exactLocal.id)
                val winner = resolveRecordConflict(entry.meta, localMeta, myDeviceId, exactLocal != remote)
                when (winner) {
                    ConflictWinner.REMOTE -> {
                        toUpsertLocally.add(remote to effectiveRemoteTs)
                        localById[remote.id] = remote
                    }
                    ConflictWinner.EQUAL -> {
                        alignedCleanTimestamps[remote.id] = maxOf(effectiveRemoteTs, localMeta.syncedServerMillis)
                    }
                    ConflictWinner.LOCAL -> {
                        // Local has a newer dirty edit; leave for local push
                    }
                }
                continue
            }

            // Check if this remote vaccine matches an existing local vaccine under a different ID
            val canonicalLocal = localById.values.firstOrNull { it.id != remote.id && isSameLogicalVaccine(it, remote) }
            if (canonicalLocal != null) {
                val localMeta = localMetaProvider(canonicalLocal.id)
                val winner = resolveRecordConflict(
                    entry.meta,
                    localMeta,
                    myDeviceId,
                    canonicalLocal.copy(id = remote.id) != remote
                )
                toDeleteLocalDuplicateIds.add(canonicalLocal.id)
                remappings[canonicalLocal.id] = remote.id
                localById.remove(canonicalLocal.id)

                when (winner) {
                    ConflictWinner.REMOTE, ConflictWinner.EQUAL -> {
                        toUpsertLocally.add(remote to effectiveRemoteTs)
                        localById[remote.id] = remote
                    }
                    ConflictWinner.LOCAL -> {
                        val merged = canonicalLocal.copy(id = remote.id)
                        val stamp = maxOf(localMeta.localModifiedMillis, effectiveRemoteTs + 1L, nowMillis)
                        toUpsertLocally.add(merged to stamp)
                        toPushToCloud.add(merged to stamp)
                        localById[remote.id] = merged
                    }
                }
                continue
            }

            if (exactLocal != null) {
                // ID collision with a DIFFERENT local vaccine! Relocate local vaccine to fresh ID & accept remote.
                val freshId = generateNonCollidingId(usedIds, idGenerator)
                usedIds.add(freshId)
                val relocatedLocal = exactLocal.copy(id = freshId)
                val pushStamp = maxOf(localMetaProvider(exactLocal.id).localModifiedMillis, nowMillis)
                remappings[exactLocal.id] = freshId
                toUpsertLocally.add(remote to effectiveRemoteTs)
                toUpsertLocally.add(relocatedLocal to pushStamp)
                toPushToCloud.add(relocatedLocal to pushStamp)
                localById[remote.id] = remote
                localById[freshId] = relocatedLocal
            } else {
                // Brand new vaccine from another device
                toUpsertLocally.add(remote to effectiveRemoteTs)
                localById[remote.id] = remote
            }
        }

        return VaccineMergePlan(
            toUpsertLocally = toUpsertLocally,
            toDeleteLocalDuplicateIds = toDeleteLocalDuplicateIds.distinct(),
            transactionVaccineIdRemappings = remappings,
            toPushToCloud = toPushToCloud,
            alignedCleanTimestamps = alignedCleanTimestamps
        )
    }

    private fun generateNonCollidingId(existingIds: Set<Long>, generator: () -> Long): Long {
        var id = generator()
        while (id <= 0L || id in existingIds) {
            id = generator()
        }
        return id
    }
}
