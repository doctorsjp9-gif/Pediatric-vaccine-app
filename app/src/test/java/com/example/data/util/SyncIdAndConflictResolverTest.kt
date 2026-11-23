package com.example.data.util

import com.example.data.model.Vaccine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncIdAndConflictResolverTest {

    private fun vaccine(
        id: Long,
        name: String,
        code: String,
        stock: Int,
        purchasePrice: Double = 500.0,
        sellingPrice: Double = 700.0
    ) = Vaccine(
        id = id,
        name = name,
        code = code,
        currentStock = stock,
        purchasePrice = purchasePrice,
        sellingPrice = sellingPrice
    )

    @Test
    fun generateSyncSafeId_isUniqueMonotonicAndAboveSeededRange() {
        val ids = (1..200).map { SyncIdAndConflictResolver.generateSyncSafeId(1_730_000_000_000L) }
        assertEquals(200, ids.toSet().size)
        assertTrue(ids.all { it > 1_000_000L })
        for (i in 1 until ids.size) {
            assertTrue("Expected strictly increasing IDs", ids[i] > ids[i - 1])
        }
    }

    @Test
    fun resolveRecordConflict_cleanDeviceBAcceptsRemoteEvenWhenClockIsTenMinutesAhead() {
        // Device A updated vaccine stock to 28 at server time T = 1_000_000.
        // Device B has a skewed local clock 10 minutes ahead (1_600_000), but has NO unpushed edits (isDirty = false).
        val remoteMeta = RemoteSyncMeta(
            serverTimestampMillis = 1_000_000L,
            clientModifiedMillis = 1_000_000L,
            deviceId = "device-A"
        )
        val localMeta = LocalSyncMeta(
            syncedServerMillis = 1_600_000L,
            localModifiedMillis = 1_600_000L,
            isDirty = false
        )

        val winner = SyncIdAndConflictResolver.resolveRecordConflict(
            remoteMeta = remoteMeta,
            localMeta = localMeta,
            myDeviceId = "device-B",
            contentDiffers = true
        )

        assertEquals(ConflictWinner.REMOTE, winner)
    }

    @Test
    fun resolveRecordConflict_dirtyLocalEditBeatsOlderRemoteSnapshot() {
        val remoteMeta = RemoteSyncMeta(
            serverTimestampMillis = 1_000_000L,
            clientModifiedMillis = 1_000_000L,
            deviceId = "device-A"
        )
        val localMeta = LocalSyncMeta(
            syncedServerMillis = 900_000L,
            localModifiedMillis = 1_200_000L,
            isDirty = true
        )

        val winner = SyncIdAndConflictResolver.resolveRecordConflict(
            remoteMeta = remoteMeta,
            localMeta = localMeta,
            myDeviceId = "device-B",
            contentDiffers = true
        )

        assertEquals(ConflictWinner.LOCAL, winner)
    }

    @Test
    fun planVaccineMerge_sameLogicalVaccineWithDifferentIdsConvergesAndRemapsTransactions() {
        // Device A saved Pentavalent with Cloud ID 9001 and updated stock to 42.
        // Device B had Pentavalent locally under ID 7 with old stock 10 (clean).
        val localVaccines = listOf(vaccine(id = 7L, name = "Pentavalent", code = "PENTA-5", stock = 10))
        val remoteEntries = listOf(
            RemoteVaccineEntry(
                vaccine = vaccine(id = 9001L, name = "Pentavalent", code = "penta-5", stock = 42),
                meta = RemoteSyncMeta(serverTimestampMillis = 5_000L, deviceId = "device-A")
            )
        )

        val plan = SyncIdAndConflictResolver.planVaccineMerge(
            localVaccines = localVaccines,
            remoteEntries = remoteEntries,
            localMetaProvider = { LocalSyncMeta(syncedServerMillis = 4_000L, isDirty = false) },
            myDeviceId = "device-B",
            nowMillis = 6_000L
        )

        assertEquals(listOf(7L), plan.toDeleteLocalDuplicateIds)
        assertEquals(mapOf(7L to 9001L), plan.transactionVaccineIdRemappings)
        assertEquals(1, plan.toUpsertLocally.size)
        assertEquals(9001L, plan.toUpsertLocally.first().first.id)
        assertEquals(42, plan.toUpsertLocally.first().first.currentStock)
        assertTrue(plan.toPushToCloud.isEmpty())
    }

    @Test
    fun planVaccineMerge_twoDifferentVaccinesCollidingOnSameLegacyIdPreservesBoth() {
        // Device A added "Typhoid TCV" as legacy ID 7.
        // Device B independently added "HPV Gardasil" as legacy ID 7.
        val localVaccines = listOf(vaccine(id = 7L, name = "HPV Gardasil", code = "HPV-9", stock = 12))
        val remoteEntries = listOf(
            RemoteVaccineEntry(
                vaccine = vaccine(id = 7L, name = "Typhoid TCV", code = "TCV-01", stock = 25),
                meta = RemoteSyncMeta(serverTimestampMillis = 5_000L, deviceId = "device-A")
            )
        )

        val plan = SyncIdAndConflictResolver.planVaccineMerge(
            localVaccines = localVaccines,
            remoteEntries = remoteEntries,
            localMetaProvider = { LocalSyncMeta(localModifiedMillis = 5_100L, isDirty = true) },
            myDeviceId = "device-B",
            nowMillis = 6_000L,
            idGenerator = { 555_000_001L }
        )

        // Both vaccines must be preserved locally: remote keeps ID 7, local relocated to 555_000_001L
        val upsertedByName = plan.toUpsertLocally.associate { it.first.name to it.first }
        assertEquals(2, upsertedByName.size)
        assertEquals(7L, upsertedByName["Typhoid TCV"]?.id)
        assertEquals(25, upsertedByName["Typhoid TCV"]?.currentStock)
        assertEquals(555_000_001L, upsertedByName["HPV Gardasil"]?.id)
        assertEquals(12, upsertedByName["HPV Gardasil"]?.currentStock)

        // Transactions referencing old local ID 7 get remapped to relocated ID 555_000_001L
        assertEquals(mapOf(7L to 555_000_001L), plan.transactionVaccineIdRemappings)
        // Relocated local vaccine is scheduled to push to Cloud so Device A gets it too
        assertEquals(1, plan.toPushToCloud.size)
        assertEquals(555_000_001L, plan.toPushToCloud.first().first.id)
    }

    @Test
    fun planLiveVaccineSnapshot_appliesStockDecrementFromOtherDeviceImmediately() {
        val localVaccines = listOf(vaccine(id = 1L, name = "Hexavalent", code = "HEXA", stock = 35))
        val changedRemote = listOf(
            RemoteVaccineEntry(
                vaccine = vaccine(id = 1L, name = "Hexavalent", code = "HEXA", stock = 28),
                meta = RemoteSyncMeta(serverTimestampMillis = 10_000L, deviceId = "device-A")
            )
        )

        val plan = SyncIdAndConflictResolver.planLiveVaccineSnapshot(
            localVaccines = localVaccines,
            changedRemoteEntries = changedRemote,
            localMetaProvider = { LocalSyncMeta(syncedServerMillis = 12_000L, isDirty = false) },
            myDeviceId = "device-B",
            nowMillis = 12_500L
        )

        assertEquals(1, plan.toUpsertLocally.size)
        assertEquals(28, plan.toUpsertLocally.first().first.currentStock)
        assertFalse(plan.toUpsertLocally.isEmpty())
    }
}
