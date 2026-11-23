package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.R
import com.example.data.dao.ClinicProfileDao
import com.example.data.dao.PatientRegistrationDao
import com.example.data.dao.StockTransactionDao
import com.example.data.dao.VaccineDao
import com.example.data.dao.VendorDao
import com.example.data.database.AppDatabase
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.StockTransaction
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.data.util.ConflictWinner
import com.example.data.util.FirestoreEntityMapper
import com.example.data.util.FirestoreEntityMapper.toFirestoreMap
import com.example.data.util.LocalBackupManager
import com.example.data.util.OperationType
import com.example.data.util.RemoteSyncMeta
import com.example.data.util.RemoteVaccineEntry
import com.example.data.util.SyncIdAndConflictResolver
import com.example.data.util.SyncTimestampTracker
import com.example.data.util.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class CategorySyncAction(val label: String, val description: String) {
    PRESERVE_BOTH("Preserve All (Merge)", "Smart two-way sync: latest changes win & all records preserved"),
    PRESERVE_LOCAL_ONLY("Keep Device Only", "Preserve device data & delete unmatched cloud data"),
    RESTORE_CLOUD_ONLY("Keep Cloud Only", "Restore cloud data & replace local device data"),
    DELETE_ALL("Delete All", "Delete this category from both device & cloud"),
    SKIP("Skip", "Do not sync or modify this category")
}

data class CloudSyncConfig(
    val vaccinesAction: CategorySyncAction = CategorySyncAction.PRESERVE_BOTH,
    val patientsAction: CategorySyncAction = CategorySyncAction.PRESERVE_BOTH,
    val transactionsAction: CategorySyncAction = CategorySyncAction.PRESERVE_BOTH,
    val vendorsAction: CategorySyncAction = CategorySyncAction.PRESERVE_BOTH,
    val clinicProfileAction: CategorySyncAction = CategorySyncAction.PRESERVE_BOTH,
    val vaccineIdsToDelete: Set<Long> = emptySet(),
    val patientIdsToDelete: Set<Long> = emptySet(),
    val vendorIdsToDelete: Set<Long> = emptySet(),
    val transactionIdsToDelete: Set<Long> = emptySet(),
    val deleteDefaultDemoRecords: Boolean = false
)

data class SyncStatusState(
    val isSyncing: Boolean = false,
    val lastSyncTimeMillis: Long? = null,
    val statusMessage: String = "Ready to sync",
    val itemsSyncedCount: Int = 0,
    val lastError: String? = null,
    val isRealtimeActive: Boolean = false
)

data class CloudDataSnapshot(
    val vaccines: List<Vaccine> = emptyList(),
    val patients: List<PatientRegistration> = emptyList(),
    val transactions: List<StockTransaction> = emptyList(),
    val vendors: List<Vendor> = emptyList(),
    val clinicProfile: ClinicProfile? = null,
    val fetchedAtMillis: Long = System.currentTimeMillis()
) {
    val totalRecords: Int
        get() = vaccines.size + patients.size + transactions.size + vendors.size + (if (clinicProfile != null) 1 else 0)
}

data class SelectiveCloudTransferRequest(
    val includeVaccines: Boolean = true,
    val selectedVaccineIds: Set<Long>? = null, // null means all items in the category
    val includePatients: Boolean = true,
    val selectedPatientIds: Set<Long>? = null,
    val includeTransactions: Boolean = true,
    val selectedTransactionIds: Set<Long>? = null,
    val includeVendors: Boolean = true,
    val selectedVendorIds: Set<Long>? = null,
    val includeClinicProfile: Boolean = true,
    val replaceExistingOnTarget: Boolean = false
)

class CloudSyncRepository(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val vaccineDao: VaccineDao,
    private val vendorDao: VendorDao,
    private val transactionDao: StockTransactionDao,
    private val patientDao: PatientRegistrationDao,
    private val clinicProfileDao: ClinicProfileDao
) {
    // Secondary constructor resolving database ID from string resources (NEVER parameterless Firebase.firestore)
    constructor(
        context: Context,
        vaccineDao: VaccineDao,
        vendorDao: VendorDao,
        transactionDao: StockTransactionDao,
        patientDao: PatientRegistrationDao,
        clinicProfileDao: ClinicProfileDao
    ) : this(
        context = context,
        db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        auth = FirebaseAuth.getInstance(),
        vaccineDao = vaccineDao,
        vendorDao = vendorDao,
        transactionDao = transactionDao,
        patientDao = patientDao,
        clinicProfileDao = clinicProfileDao
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vaccine_sync_prefs", Context.MODE_PRIVATE)

    private val deviceId: String
        get() = SyncTimestampTracker.getDeviceId(context)

    // Serializes all Room DB read-reconcile-write operations across manual sync, live listeners, and pushes
    private val syncMutex = Mutex()

    private val _syncState = MutableStateFlow(
        SyncStatusState(
            lastSyncTimeMillis = prefs.getLong("last_sync_timestamp", 0L).takeIf { it > 0 }
        )
    )
    val syncState: StateFlow<SyncStatusState> = _syncState.asStateFlow()

    private val _cloudSnapshot = MutableStateFlow<CloudDataSnapshot?>(null)
    val cloudSnapshot: StateFlow<CloudDataSnapshot?> = _cloudSnapshot.asStateFlow()

    private val _isLoadingCloudPreview = MutableStateFlow(false)
    val isLoadingCloudPreview: StateFlow<Boolean> = _isLoadingCloudPreview.asStateFlow()

    private val listenerJobs = mutableListOf<Job>()
    private var activeListeningUid: String? = null

    private fun resolveAuthenticatedUid(explicitUserId: String?): String {
        val authUid = auth.currentUser?.uid
        if (!authUid.isNullOrBlank()) return authUid
        if (!explicitUserId.isNullOrBlank() && auth.currentUser != null) return explicitUserId
        throw IllegalStateException("Please sign in with Google (Gmail) to sync data across devices.")
    }

    /**
     * Unwraps nested CancellationException causes to distinguish real Firestore errors
     * (like PERMISSION_DENIED / UNAVAILABLE) from cooperative coroutine cancellation.
     */
    private fun unwrapRootCause(error: Throwable): Throwable {
        var root: Throwable = error
        while (root is CancellationException && root.cause != null) {
            root = root.cause!!
        }
        return root
    }

    private suspend fun getFreshCollection(col: CollectionReference): QuerySnapshot {
        return try {
            col.get(Source.SERVER).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            col.get(Source.DEFAULT).await()
        }
    }

    private suspend fun getFreshDocument(docRef: DocumentReference): DocumentSnapshot {
        return try {
            docRef.get(Source.SERVER).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            docRef.get(Source.DEFAULT).await()
        }
    }

    // --- Real-Time Multi-Device Sync Listeners (First-Party Firestore Flow + Mutex Serialized) ---

    fun startRealtimeSyncListeners(uid: String, scope: CoroutineScope) {
        if (uid.isBlank() || auth.currentUser == null) return
        if (activeListeningUid == uid && listenerJobs.any { it.isActive }) return

        stopRealtimeSyncListeners()
        activeListeningUid = uid
        val userDoc = db.collection("users").document(uid)

        // 1. Live Vaccines Flow Listener (Canonical ID convergence + clock-skew-proof stock count updates)
        listenerJobs += scope.launch(Dispatchers.IO) {
            userDoc.collection("vaccines")
                .snapshots(MetadataChanges.INCLUDE)
                .catch { error ->
                    val root = unwrapRootCause(error)
                    if (root is CancellationException) throw root
                    handleFirestoreError(root, OperationType.LIST, "users/$uid/vaccines")
                    _syncState.value = _syncState.value.copy(
                        isRealtimeActive = false,
                        lastError = (root as? FirebaseFirestoreException)?.localizedMessage ?: root.localizedMessage
                    )
                }
                .collect { snapshot ->
                    applyLiveVaccinesSnapshot(uid, snapshot)
                }
        }

        // 2. Live Patients Flow Listener
        listenerJobs += scope.launch(Dispatchers.IO) {
            userDoc.collection("patients")
                .snapshots(MetadataChanges.INCLUDE)
                .catch { error ->
                    val root = unwrapRootCause(error)
                    if (root is CancellationException) throw root
                    handleFirestoreError(root, OperationType.LIST, "users/$uid/patients")
                }
                .collect { snapshot ->
                    applyLivePatientsSnapshot(snapshot)
                }
        }

        // 3. Live Vendors Flow Listener
        listenerJobs += scope.launch(Dispatchers.IO) {
            userDoc.collection("vendors")
                .snapshots(MetadataChanges.INCLUDE)
                .catch { error ->
                    val root = unwrapRootCause(error)
                    if (root is CancellationException) throw root
                    handleFirestoreError(root, OperationType.LIST, "users/$uid/vendors")
                }
                .collect { snapshot ->
                    applyLiveVendorsSnapshot(snapshot)
                }
        }

        // 4. Live Transactions Flow Listener
        listenerJobs += scope.launch(Dispatchers.IO) {
            userDoc.collection("transactions")
                .snapshots(MetadataChanges.INCLUDE)
                .catch { error ->
                    val root = unwrapRootCause(error)
                    if (root is CancellationException) throw root
                    handleFirestoreError(root, OperationType.LIST, "users/$uid/transactions")
                }
                .collect { snapshot ->
                    applyLiveTransactionsSnapshot(snapshot)
                }
        }

        // 5. Live Clinic Profile Flow Listener
        listenerJobs += scope.launch(Dispatchers.IO) {
            userDoc.collection("clinicProfile").document("profile")
                .snapshots(MetadataChanges.INCLUDE)
                .catch { error ->
                    val root = unwrapRootCause(error)
                    if (root is CancellationException) throw root
                    handleFirestoreError(root, OperationType.GET, "users/$uid/clinicProfile/profile")
                }
                .collect { doc ->
                    applyLiveClinicProfileSnapshot(doc)
                }
        }

        _syncState.value = _syncState.value.copy(isRealtimeActive = true)
        Log.i("CloudSync", "Flow-based real-time multi-device sync listeners active for uid=$uid")
    }

    fun stopRealtimeSyncListeners() {
        listenerJobs.forEach { it.cancel() }
        listenerJobs.clear()
        activeListeningUid = null
        _syncState.value = _syncState.value.copy(isRealtimeActive = false)
    }

    private suspend fun applyLiveVaccinesSnapshot(uid: String, snapshot: QuerySnapshot) {
        val now = System.currentTimeMillis()
        val vaccinesCol = db.collection("users").document(uid).collection("vaccines")

        // Collect all confirmed (non-pending-write) documents on server snapshots, or confirmed changes
        val docsToEvaluate = if (!snapshot.metadata.isFromCache) {
            snapshot.documents.filter { !it.metadata.hasPendingWrites() }
        } else {
            snapshot.documentChanges
                .filter { it.type != DocumentChange.Type.REMOVED && !it.document.metadata.hasPendingWrites() }
                .map { it.document }
        }

        val removedIds = if (!snapshot.metadata.isFromCache) {
            snapshot.documentChanges
                .filter { it.type == DocumentChange.Type.REMOVED && !it.document.metadata.hasPendingWrites() }
                .mapNotNull { it.document.id.toLongOrNull() }
        } else {
            emptyList()
        }

        val remoteEntries = docsToEvaluate.mapNotNull { doc ->
            val vac = FirestoreEntityMapper.toVaccineSafe(doc) ?: return@mapNotNull null
            RemoteVaccineEntry(vaccine = vac, meta = FirestoreEntityMapper.extractMeta(doc))
        }

        var updatedCount = 0
        var deletedCount = 0
        var pushedRelocated = listOf<Pair<Vaccine, Long>>()

        syncMutex.withLock {
            val localVaccines = vaccineDao.getAllVaccinesList()
            val plan = SyncIdAndConflictResolver.planLiveVaccineSnapshot(
                localVaccines = localVaccines,
                changedRemoteEntries = remoteEntries,
                localMetaProvider = { id -> SyncTimestampTracker.getVaccineSyncMeta(context, id) },
                myDeviceId = deviceId,
                nowMillis = now
            )

            // 1. Remap local transactions if duplicate IDs converged or collided
            for ((oldId, newId) in plan.transactionVaccineIdRemappings) {
                transactionDao.remapVaccineId(oldId, newId)
            }

            // 2. Delete superseded duplicate local rows
            for (dupId in plan.toDeleteLocalDuplicateIds) {
                vaccineDao.getVaccineById(dupId)?.let { vaccineDao.deleteVaccine(it) }
                SyncTimestampTracker.removeVaccineTimestamp(context, dupId)
            }

            // 3. Upsert winning remote / converged vaccines
            if (plan.toUpsertLocally.isNotEmpty()) {
                vaccineDao.insertAll(plan.toUpsertLocally.map { it.first })
                plan.toUpsertLocally.forEach { (vac, ts) ->
                    SyncTimestampTracker.setVaccineSyncedAt(context, vac.id, ts)
                }
                updatedCount = plan.toUpsertLocally.size
            }

            // 4. Align clean server timestamps for identical/own-confirmed vaccines
            for ((id, serverTs) in plan.alignedCleanTimestamps) {
                if (serverTs > 0L) {
                    SyncTimestampTracker.setVaccineSyncedAt(context, id, serverTs)
                }
            }

            // 5. Apply confirmed server deletions (only if not dirty locally)
            for (remId in removedIds) {
                val localMeta = SyncTimestampTracker.getVaccineSyncMeta(context, remId)
                if (!localMeta.isDirty) {
                    vaccineDao.getVaccineById(remId)?.let {
                        vaccineDao.deleteVaccine(it)
                        SyncTimestampTracker.removeVaccineTimestamp(context, remId)
                        deletedCount++
                    }
                }
            }

            pushedRelocated = plan.toPushToCloud
        }

        // Push any relocated/merged vaccines outside Room lock
        for ((vac, ts) in pushedRelocated) {
            try {
                vaccinesCol.document(vac.id.toString()).set(vac.toFirestoreMap(ts, deviceId)).await()
                SyncTimestampTracker.setVaccineSyncedAt(context, vac.id, ts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CloudSync", "Failed pushing relocated vaccine ${vac.id}", e)
            }
        }

        if (updatedCount > 0 || deletedCount > 0) {
            val completed = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_timestamp", completed).apply()
            _syncState.value = _syncState.value.copy(
                lastSyncTimeMillis = completed,
                isRealtimeActive = true,
                lastError = null,
                statusMessage = "Live synced $updatedCount vaccine update(s) from Cloud"
            )
            LocalBackupManager.saveAutoBackup(context, AppDatabase.getInstance(context))
        }
    }

    private suspend fun applyLivePatientsSnapshot(snapshot: QuerySnapshot) {
        val now = System.currentTimeMillis()
        syncMutex.withLock {
            val localMap = patientDao.getAllPatientsList().associateBy { it.id }
            val toUpsert = mutableListOf<Pair<PatientRegistration, Long>>()
            val toDeleteIds = mutableListOf<Long>()

            for (change in snapshot.documentChanges) {
                val doc = change.document
                if (doc.metadata.hasPendingWrites()) continue
                val meta = FirestoreEntityMapper.extractMeta(doc)
                val effectiveTs = meta.effectiveMillis.takeIf { it > 0L } ?: now

                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        val remote = FirestoreEntityMapper.toPatientSafe(doc) ?: continue
                        val local = localMap[remote.id]
                        if (meta.deviceId == deviceId) {
                            if (local != null && local == remote) {
                                SyncTimestampTracker.setPatientSyncedAt(context, remote.id, effectiveTs)
                            }
                            continue
                        }
                        val localMeta = SyncTimestampTracker.getPatientSyncMeta(context, remote.id)
                        if (local == null || SyncIdAndConflictResolver.resolveRecordConflict(
                                remoteMeta = meta,
                                localMeta = localMeta,
                                myDeviceId = deviceId,
                                contentDiffers = local != remote
                            ) == ConflictWinner.REMOTE
                        ) {
                            toUpsert.add(remote to effectiveTs)
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        if (!snapshot.metadata.isFromCache) {
                            doc.id.toLongOrNull()?.let { id ->
                                val localMeta = SyncTimestampTracker.getPatientSyncMeta(context, id)
                                if (localMap.containsKey(id) && !localMeta.isDirty) toDeleteIds.add(id)
                            }
                        }
                    }
                }
            }

            if (toUpsert.isNotEmpty()) {
                patientDao.insertAll(toUpsert.map { it.first })
                toUpsert.forEach { (p, ts) -> SyncTimestampTracker.setPatientSyncedAt(context, p.id, ts) }
            }
            for (id in toDeleteIds) {
                patientDao.getPatientById(id)?.let { patientDao.delete(it) }
                SyncTimestampTracker.removePatientTimestamp(context, id)
            }
        }
    }

    private suspend fun applyLiveVendorsSnapshot(snapshot: QuerySnapshot) {
        val now = System.currentTimeMillis()
        syncMutex.withLock {
            val localMap = vendorDao.getAllVendorsList().associateBy { it.id }
            val toUpsert = mutableListOf<Pair<Vendor, Long>>()
            val toDeleteIds = mutableListOf<Long>()

            for (change in snapshot.documentChanges) {
                val doc = change.document
                if (doc.metadata.hasPendingWrites()) continue
                val meta = FirestoreEntityMapper.extractMeta(doc)
                val effectiveTs = meta.effectiveMillis.takeIf { it > 0L } ?: now

                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        val remote = FirestoreEntityMapper.toVendorSafe(doc) ?: continue
                        val local = localMap[remote.id]
                        if (meta.deviceId == deviceId) {
                            if (local != null && local == remote) {
                                SyncTimestampTracker.setVendorSyncedAt(context, remote.id, effectiveTs)
                            }
                            continue
                        }
                        val localMeta = SyncTimestampTracker.getVendorSyncMeta(context, remote.id)
                        if (local == null || SyncIdAndConflictResolver.resolveRecordConflict(
                                remoteMeta = meta,
                                localMeta = localMeta,
                                myDeviceId = deviceId,
                                contentDiffers = local != remote
                            ) == ConflictWinner.REMOTE
                        ) {
                            toUpsert.add(remote to effectiveTs)
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        if (!snapshot.metadata.isFromCache) {
                            doc.id.toLongOrNull()?.let { id ->
                                val localMeta = SyncTimestampTracker.getVendorSyncMeta(context, id)
                                if (localMap.containsKey(id) && !localMeta.isDirty) toDeleteIds.add(id)
                            }
                        }
                    }
                }
            }

            if (toUpsert.isNotEmpty()) {
                vendorDao.insertAll(toUpsert.map { it.first })
                toUpsert.forEach { (v, ts) -> SyncTimestampTracker.setVendorSyncedAt(context, v.id, ts) }
            }
            for (id in toDeleteIds) {
                vendorDao.getVendorById(id)?.let { vendorDao.deleteVendor(it) }
                SyncTimestampTracker.removeVendorTimestamp(context, id)
            }
        }
    }

    private suspend fun applyLiveTransactionsSnapshot(snapshot: QuerySnapshot) {
        val now = System.currentTimeMillis()
        syncMutex.withLock {
            val localMap = transactionDao.getAllTransactionsList().associateBy { it.id }
            val toUpsert = mutableListOf<Pair<StockTransaction, Long>>()
            val toDeleteIds = mutableListOf<Long>()

            for (change in snapshot.documentChanges) {
                val doc = change.document
                if (doc.metadata.hasPendingWrites()) continue
                val meta = FirestoreEntityMapper.extractMeta(doc)
                val effectiveTs = meta.effectiveMillis.takeIf { it > 0L } ?: now

                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                        val remote = FirestoreEntityMapper.toTransactionSafe(doc) ?: continue
                        val local = localMap[remote.id]
                        if (meta.deviceId == deviceId) {
                            if (local != null && local == remote) {
                                SyncTimestampTracker.setTransactionSyncedAt(context, remote.id, effectiveTs)
                            }
                            continue
                        }
                        val localMeta = SyncTimestampTracker.getTransactionSyncMeta(context, remote.id)
                        if (local == null || SyncIdAndConflictResolver.resolveRecordConflict(
                                remoteMeta = meta,
                                localMeta = localMeta,
                                myDeviceId = deviceId,
                                contentDiffers = local != remote
                            ) == ConflictWinner.REMOTE
                        ) {
                            toUpsert.add(remote to effectiveTs)
                        }
                    }
                    DocumentChange.Type.REMOVED -> {
                        if (!snapshot.metadata.isFromCache) {
                            doc.id.toLongOrNull()?.let { id ->
                                val localMeta = SyncTimestampTracker.getTransactionSyncMeta(context, id)
                                if (localMap.containsKey(id) && !localMeta.isDirty) toDeleteIds.add(id)
                            }
                        }
                    }
                }
            }

            if (toUpsert.isNotEmpty()) {
                transactionDao.insertAll(toUpsert.map { it.first })
                toUpsert.forEach { (t, ts) -> SyncTimestampTracker.setTransactionSyncedAt(context, t.id, ts) }
            }
            for (id in toDeleteIds) {
                transactionDao.deleteTransaction(id)
                SyncTimestampTracker.removeTransactionTimestamp(context, id)
            }
        }
    }

    private suspend fun applyLiveClinicProfileSnapshot(doc: DocumentSnapshot) {
        if (!doc.exists() || doc.metadata.hasPendingWrites()) return
        val now = System.currentTimeMillis()
        syncMutex.withLock {
            val meta = FirestoreEntityMapper.extractMeta(doc)
            val effectiveTs = meta.effectiveMillis.takeIf { it > 0L } ?: now
            val remote = FirestoreEntityMapper.toClinicProfileSafe(doc) ?: return@withLock
            val local = clinicProfileDao.getProfileSync()
            if (meta.deviceId == deviceId) {
                if (local != null && local == remote) {
                    SyncTimestampTracker.setProfileSyncedAt(context, effectiveTs)
                }
                return@withLock
            }
            val localMeta = SyncTimestampTracker.getProfileSyncMeta(context)
            if (local == null || SyncIdAndConflictResolver.resolveRecordConflict(
                    remoteMeta = meta,
                    localMeta = localMeta,
                    myDeviceId = deviceId,
                    contentDiffers = local != remote
                ) == ConflictWinner.REMOTE
            ) {
                clinicProfileDao.insertOrUpdate(remote)
                SyncTimestampTracker.setProfileSyncedAt(context, effectiveTs)
            }
        }
    }

    // --- Instant Single / Batch Cloud Push Helpers (called on local mutations when signed in) ---

    suspend fun pushVaccineToCloud(vaccine: Vaccine, modifiedAtMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        if (vaccine.id <= 0L) return@withContext
        try {
            db.collection("users").document(uid).collection("vaccines")
                .document(vaccine.id.toString())
                .set(vaccine.toFirestoreMap(modifiedAtMillis, deviceId))
                .await()
            SyncTimestampTracker.setVaccineSyncedAt(context, vaccine.id, modifiedAtMillis)
            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_timestamp", now).apply()
            _syncState.value = _syncState.value.copy(
                lastSyncTimeMillis = now,
                lastError = null,
                statusMessage = "Auto-synced '${vaccine.name}' (Stock: ${vaccine.currentStock})"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CloudSync", "Background vaccine push failed (remains dirty for next sync)", e)
        }
    }

    suspend fun pushVaccinesByIdsToCloud(vaccineIds: Collection<Long>, modifiedAtMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        try {
            val col = db.collection("users").document(uid).collection("vaccines")
            var pushed = 0
            for (id in vaccineIds.distinct()) {
                val vac = vaccineDao.getVaccineById(id) ?: continue
                col.document(vac.id.toString()).set(vac.toFirestoreMap(modifiedAtMillis, deviceId)).await()
                SyncTimestampTracker.setVaccineSyncedAt(context, vac.id, modifiedAtMillis)
                pushed++
            }
            if (pushed > 0) {
                val now = System.currentTimeMillis()
                prefs.edit().putLong("last_sync_timestamp", now).apply()
                _syncState.value = _syncState.value.copy(
                    lastSyncTimeMillis = now,
                    lastError = null,
                    statusMessage = "Auto-synced $pushed vaccine stock update(s)"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CloudSync", "Background batch vaccine push failed (remains dirty for next sync)", e)
        }
    }

    suspend fun pushTransactionsToCloud(transactions: List<StockTransaction>, modifiedAtMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        try {
            val col = db.collection("users").document(uid).collection("transactions")
            for (tx in transactions) {
                if (tx.id <= 0L) continue
                col.document(tx.id.toString()).set(tx.toFirestoreMap(modifiedAtMillis, deviceId)).await()
                SyncTimestampTracker.setTransactionSyncedAt(context, tx.id, modifiedAtMillis)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CloudSync", "Background transactions push failed", e)
        }
    }

    suspend fun pushPatientToCloud(patient: PatientRegistration, modifiedAtMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        if (patient.id <= 0L) return@withContext
        try {
            db.collection("users").document(uid).collection("patients")
                .document(patient.id.toString())
                .set(patient.toFirestoreMap(modifiedAtMillis, deviceId))
                .await()
            SyncTimestampTracker.setPatientSyncedAt(context, patient.id, modifiedAtMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CloudSync", "Background patient push failed", e)
        }
    }

    suspend fun pushPatientByIdToCloud(patientId: Long, modifiedAtMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val patient = patientDao.getPatientById(patientId) ?: return@withContext
        pushPatientToCloud(patient, modifiedAtMillis)
    }

    suspend fun pushVendorToCloud(vendor: Vendor, modifiedAtMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        if (vendor.id <= 0L) return@withContext
        try {
            db.collection("users").document(uid).collection("vendors")
                .document(vendor.id.toString())
                .set(vendor.toFirestoreMap(modifiedAtMillis, deviceId))
                .await()
            SyncTimestampTracker.setVendorSyncedAt(context, vendor.id, modifiedAtMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CloudSync", "Background vendor push failed", e)
        }
    }

    suspend fun pushClinicProfileToCloud(profile: ClinicProfile, modifiedAtMillis: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        try {
            db.collection("users").document(uid).collection("clinicProfile")
                .document("profile")
                .set(profile.toFirestoreMap(modifiedAtMillis, deviceId))
                .await()
            SyncTimestampTracker.setProfileSyncedAt(context, modifiedAtMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("CloudSync", "Background clinic profile push failed", e)
        }
    }

    // --- Cloud Deletes ---

    suspend fun deleteVaccineFromCloud(id: Long, explicitUserId: String? = null) = withContext(Dispatchers.IO) {
        val uid = try { resolveAuthenticatedUid(explicitUserId) } catch (_: Exception) { null } ?: return@withContext
        try {
            db.collection("users").document(uid).collection("vaccines").document(id.toString()).delete().await()
            SyncTimestampTracker.removeVaccineTimestamp(context, id)
        } catch (e: Exception) {
            Log.w("CloudSync", "Failed to delete vaccine $id from cloud", e)
        }
    }

    suspend fun deletePatientFromCloud(id: Long, explicitUserId: String? = null) = withContext(Dispatchers.IO) {
        val uid = try { resolveAuthenticatedUid(explicitUserId) } catch (_: Exception) { null } ?: return@withContext
        try {
            db.collection("users").document(uid).collection("patients").document(id.toString()).delete().await()
            SyncTimestampTracker.removePatientTimestamp(context, id)
        } catch (e: Exception) {
            Log.w("CloudSync", "Failed to delete patient $id from cloud", e)
        }
    }

    suspend fun deleteVendorFromCloud(id: Long, explicitUserId: String? = null) = withContext(Dispatchers.IO) {
        val uid = try { resolveAuthenticatedUid(explicitUserId) } catch (_: Exception) { null } ?: return@withContext
        try {
            db.collection("users").document(uid).collection("vendors").document(id.toString()).delete().await()
            SyncTimestampTracker.removeVendorTimestamp(context, id)
        } catch (e: Exception) {
            Log.w("CloudSync", "Failed to delete vendor $id from cloud", e)
        }
    }

    suspend fun deleteTransactionFromCloud(id: Long, explicitUserId: String? = null) = withContext(Dispatchers.IO) {
        val uid = try { resolveAuthenticatedUid(explicitUserId) } catch (_: Exception) { null } ?: return@withContext
        try {
            db.collection("users").document(uid).collection("transactions").document(id.toString()).delete().await()
            SyncTimestampTracker.removeTransactionTimestamp(context, id)
        } catch (e: Exception) {
            Log.w("CloudSync", "Failed to delete transaction $id from cloud", e)
        }
    }

    suspend fun purgeStaleCloudData(explicitUserId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try { resolveAuthenticatedUid(explicitUserId) } catch (e: Exception) { return@withContext Result.failure(e) }
        try {
            var purgedCount = 0
            val localVaccineIds = vaccineDao.getAllVaccinesList().map { it.id.toString() }.toSet()
            for (doc in getFreshCollection(db.collection("users").document(uid).collection("vaccines")).documents) {
                if (doc.id !in localVaccineIds) { doc.reference.delete().await(); purgedCount++ }
            }
            val localPatientIds = patientDao.getAllPatientsList().map { it.id.toString() }.toSet()
            for (doc in getFreshCollection(db.collection("users").document(uid).collection("patients")).documents) {
                if (doc.id !in localPatientIds) { doc.reference.delete().await(); purgedCount++ }
            }
            val localVendorIds = vendorDao.getAllVendorsList().map { it.id.toString() }.toSet()
            for (doc in getFreshCollection(db.collection("users").document(uid).collection("vendors")).documents) {
                if (doc.id !in localVendorIds) { doc.reference.delete().await(); purgedCount++ }
            }
            Result.success(purgedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun purgeStaleCloudPatients(explicitUserId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try { resolveAuthenticatedUid(explicitUserId) } catch (e: Exception) { return@withContext Result.failure(e) }
        try {
            var count = 0
            val localPatientIds = patientDao.getAllPatientsList().map { it.id.toString() }.toSet()
            for (doc in getFreshCollection(db.collection("users").document(uid).collection("patients")).documents) {
                if (doc.id !in localPatientIds) { doc.reference.delete().await(); count++ }
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/patients")
            Result.failure(e)
        }
    }

    suspend fun eraseAllCloudPatients(explicitUserId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try { resolveAuthenticatedUid(explicitUserId) } catch (e: Exception) { return@withContext Result.failure(e) }
        try {
            var count = 0
            for (doc in getFreshCollection(db.collection("users").document(uid).collection("patients")).documents) {
                doc.reference.delete().await()
                count++
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/patients")
            Result.failure(e)
        }
    }

    suspend fun eraseAllCloudData(explicitUserId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try { resolveAuthenticatedUid(explicitUserId) } catch (e: Exception) { return@withContext Result.failure(e) }
        try {
            var erasedDocs = 0
            for (colName in listOf("vaccines", "patients", "vendors", "transactions")) {
                for (doc in getFreshCollection(db.collection("users").document(uid).collection(colName)).documents) {
                    doc.reference.delete().await()
                    erasedDocs++
                }
            }
            prefs.edit().remove("last_sync_timestamp").apply()
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTimeMillis = null,
                statusMessage = "All cloud data erased",
                itemsSyncedCount = 0,
                lastError = null
            )
            Result.success(erasedDocs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun eraseAllData(
        explicitUserId: String? = null,
        eraseCloud: Boolean = true,
        eraseLocal: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (eraseLocal) {
                syncMutex.withLock {
                    LocalBackupManager.markUserIntentionallyClearedData(context, true)
                    vaccineDao.deleteAll()
                    patientDao.deleteAll()
                    transactionDao.deleteAll()
                    vendorDao.deleteAll()
                    SyncTimestampTracker.clearAllRecordTimestamps(context)
                }
            }
            if (eraseCloud) {
                val uid = try { resolveAuthenticatedUid(explicitUserId) } catch (_: Exception) { null }
                if (uid != null) eraseAllCloudData(uid)
            }
            prefs.edit().remove("last_sync_timestamp").apply()
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTimeMillis = null,
                statusMessage = "All data erased cleanly",
                itemsSyncedCount = 0,
                lastError = null
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CloudSync", "Error erasing data", e)
            Result.failure(e)
        }
    }

    // --- Full Multi-Device Synchronization ---

    suspend fun syncAll(
        explicitUserId: String? = null,
        purgeStaleCloudRecords: Boolean = false
    ): Result<Int> {
        val defaultAction = if (purgeStaleCloudRecords) {
            CategorySyncAction.PRESERVE_LOCAL_ONLY
        } else {
            CategorySyncAction.PRESERVE_BOTH
        }
        return syncWithConfig(
            explicitUserId = explicitUserId,
            config = CloudSyncConfig(
                vaccinesAction = defaultAction,
                patientsAction = defaultAction,
                transactionsAction = CategorySyncAction.PRESERVE_BOTH,
                vendorsAction = defaultAction,
                clinicProfileAction = CategorySyncAction.PRESERVE_BOTH
            )
        )
    }

    suspend fun syncWithConfig(
        explicitUserId: String? = null,
        config: CloudSyncConfig
    ): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try {
            resolveAuthenticatedUid(explicitUserId)
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = e.message ?: "Google Sign-In required for cross-device sync"
            )
            return@withContext Result.failure(e)
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusMessage = "Synchronizing latest data with Cloud...",
            lastError = null
        )

        var totalItemsProcessed = 0
        var pulledUpdatesCount = 0
        var pushedUpdatesCount = 0
        val now = System.currentTimeMillis()

        try {
            val demoVaccineCodes = setOf("VAC-HEPB-01", "VAC-MMR-02", "VAC-FLU-24", "VAC-RAB-05", "VAC-TDAP-09", "VAC-TYP-11")
            val demoPatientNames = setOf("Baby Aarav Sharma", "Ananya Patel", "Kabir Verma")
            val demoVendorEmails = setOf("orders@seruminstitute-dist.in", "supply@bharatbiotech-depot.in", "apatel@panaceaclinic.com")
            val demoTxRefs = setOf(
                "PO-8901", "PO-8942", "PO-8990",
                "Baby Aarav Sharma (Pediatric)", "Verma Family (Walk-in)",
                "Child Wellness Program", "Emergency Patient #402",
                "Maternal Health Clinic", "Travel Medicine Consultation"
            )

            val userDoc = db.collection("users").document(uid)
            val vaccinesCol = userDoc.collection("vaccines")
            val patientsCol = userDoc.collection("patients")
            val vendorsCol = userDoc.collection("vendors")
            val txCol = userDoc.collection("transactions")
            val profileDocRef = userDoc.collection("clinicProfile").document("profile")

            // 0. Process explicit deletions first
            if (config.deleteDefaultDemoRecords) {
                syncMutex.withLock {
                    vaccineDao.getAllVaccinesList().filter { it.code in demoVaccineCodes }.forEach {
                        vaccineDao.deleteVaccine(it)
                        vaccinesCol.document(it.id.toString()).delete().await()
                        SyncTimestampTracker.removeVaccineTimestamp(context, it.id)
                    }
                    patientDao.getAllPatientsList().filter { it.patientName in demoPatientNames }.forEach {
                        patientDao.delete(it)
                        patientsCol.document(it.id.toString()).delete().await()
                        SyncTimestampTracker.removePatientTimestamp(context, it.id)
                    }
                    vendorDao.getAllVendorsList().filter { it.email in demoVendorEmails }.forEach {
                        vendorDao.deleteVendor(it)
                        vendorsCol.document(it.id.toString()).delete().await()
                        SyncTimestampTracker.removeVendorTimestamp(context, it.id)
                    }
                    transactionDao.getAllTransactionsList().filter { it.referenceOrCustomer in demoTxRefs }.forEach {
                        transactionDao.deleteTransaction(it.id)
                        txCol.document(it.id.toString()).delete().await()
                        SyncTimestampTracker.removeTransactionTimestamp(context, it.id)
                    }
                }
            }

            for (delVacId in config.vaccineIdsToDelete) {
                syncMutex.withLock { vaccineDao.getVaccineById(delVacId)?.let { vaccineDao.deleteVaccine(it) } }
                vaccinesCol.document(delVacId.toString()).delete().await()
                SyncTimestampTracker.removeVaccineTimestamp(context, delVacId)
                totalItemsProcessed++
            }
            for (delPatId in config.patientIdsToDelete) {
                syncMutex.withLock { patientDao.getPatientById(delPatId)?.let { patientDao.delete(it) } }
                patientsCol.document(delPatId.toString()).delete().await()
                SyncTimestampTracker.removePatientTimestamp(context, delPatId)
                totalItemsProcessed++
            }
            for (delVenId in config.vendorIdsToDelete) {
                syncMutex.withLock { vendorDao.getVendorById(delVenId)?.let { vendorDao.deleteVendor(it) } }
                vendorsCol.document(delVenId.toString()).delete().await()
                SyncTimestampTracker.removeVendorTimestamp(context, delVenId)
                totalItemsProcessed++
            }
            for (delTxId in config.transactionIdsToDelete) {
                syncMutex.withLock { transactionDao.deleteTransaction(delTxId) }
                txCol.document(delTxId.toString()).delete().await()
                SyncTimestampTracker.removeTransactionTimestamp(context, delTxId)
                totalItemsProcessed++
            }

            // 1. Sync Vaccines (Canonical ID Indexing + Clock-Skew-Proof Reconciliation)
            when (config.vaccinesAction) {
                CategorySyncAction.SKIP -> {}
                CategorySyncAction.DELETE_ALL -> {
                    syncMutex.withLock { vaccineDao.deleteAll() }
                    for (doc in getFreshCollection(vaccinesCol).documents) {
                        doc.reference.delete().await(); totalItemsProcessed++
                    }
                }
                CategorySyncAction.RESTORE_CLOUD_ONLY -> {
                    val snap = getFreshCollection(vaccinesCol)
                    val entries = snap.documents.mapNotNull { doc ->
                        val item = FirestoreEntityMapper.toVaccineSafe(doc) ?: return@mapNotNull null
                        if (item.id in config.vaccineIdsToDelete) return@mapNotNull null
                        if (config.deleteDefaultDemoRecords && item.code in demoVaccineCodes) return@mapNotNull null
                        item to FirestoreEntityMapper.extractMeta(doc)
                    }
                    syncMutex.withLock {
                        vaccineDao.deleteAll()
                        if (entries.isNotEmpty()) {
                            vaccineDao.insertAll(entries.map { it.first })
                            entries.forEach { (item, meta) ->
                                SyncTimestampTracker.setVaccineSyncedAt(context, item.id, meta.effectiveMillis.takeIf { it > 0L } ?: now)
                            }
                        }
                    }
                    pulledUpdatesCount += entries.size
                    totalItemsProcessed += entries.size
                }
                CategorySyncAction.PRESERVE_LOCAL_ONLY -> {
                    val localVaccines = syncMutex.withLock { vaccineDao.getAllVaccinesList() }
                    val localIds = localVaccines.map { it.id.toString() }.toSet()
                    for (vaccine in localVaccines) {
                        val stamp = maxOf(now, SyncTimestampTracker.getVaccineModifiedAt(context, vaccine.id) + 1L)
                        vaccinesCol.document(vaccine.id.toString()).set(vaccine.toFirestoreMap(stamp, deviceId)).await()
                        SyncTimestampTracker.setVaccineSyncedAt(context, vaccine.id, stamp)
                        pushedUpdatesCount++; totalItemsProcessed++
                    }
                    for (doc in getFreshCollection(vaccinesCol).documents) {
                        if (doc.id !in localIds) doc.reference.delete().await()
                    }
                }
                CategorySyncAction.PRESERVE_BOTH -> {
                    val snap = getFreshCollection(vaccinesCol)
                    val remoteEntries = snap.documents.mapNotNull { doc ->
                        val item = FirestoreEntityMapper.toVaccineSafe(doc) ?: return@mapNotNull null
                        if (item.id in config.vaccineIdsToDelete) return@mapNotNull null
                        if (config.deleteDefaultDemoRecords && item.code in demoVaccineCodes) return@mapNotNull null
                        RemoteVaccineEntry(vaccine = item, meta = FirestoreEntityMapper.extractMeta(doc))
                    }

                    val plan = syncMutex.withLock {
                        val localList = vaccineDao.getAllVaccinesList()
                        val computed = SyncIdAndConflictResolver.planVaccineMerge(
                            localVaccines = localList,
                            remoteEntries = remoteEntries,
                            localMetaProvider = { id -> SyncTimestampTracker.getVaccineSyncMeta(context, id) },
                            myDeviceId = deviceId,
                            nowMillis = now
                        )
                        for ((oldId, newId) in computed.transactionVaccineIdRemappings) {
                            transactionDao.remapVaccineId(oldId, newId)
                        }
                        for (dupId in computed.toDeleteLocalDuplicateIds) {
                            vaccineDao.getVaccineById(dupId)?.let { vaccineDao.deleteVaccine(it) }
                            SyncTimestampTracker.removeVaccineTimestamp(context, dupId)
                        }
                        if (computed.toUpsertLocally.isNotEmpty()) {
                            vaccineDao.insertAll(computed.toUpsertLocally.map { it.first })
                            computed.toUpsertLocally.forEach { (vac, ts) ->
                                SyncTimestampTracker.setVaccineSyncedAt(context, vac.id, ts)
                            }
                        }
                        for ((id, alignedTs) in computed.alignedCleanTimestamps) {
                            if (alignedTs > 0L) SyncTimestampTracker.setVaccineSyncedAt(context, id, alignedTs)
                        }
                        computed
                    }

                    pulledUpdatesCount += plan.toUpsertLocally.size
                    totalItemsProcessed += plan.toUpsertLocally.size

                    for ((vac, ts) in plan.toPushToCloud) {
                        vaccinesCol.document(vac.id.toString()).set(vac.toFirestoreMap(ts, deviceId)).await()
                        SyncTimestampTracker.setVaccineSyncedAt(context, vac.id, ts)
                        pushedUpdatesCount++; totalItemsProcessed++
                    }
                }
            }

            // 2. Sync Vendors
            val vendorCounts = syncGenericCollection(
                action = config.vendorsAction,
                collection = vendorsCol,
                now = now,
                fetchLocal = { vendorDao.getAllVendorsList() },
                deleteAllLocal = { vendorDao.deleteAll() },
                insertAllLocal = { vendorDao.insertAll(it) },
                parseRemote = { FirestoreEntityMapper.toVendorSafe(it) },
                filterRemote = { item ->
                    item.id !in config.vendorIdsToDelete &&
                        !(config.deleteDefaultDemoRecords && item.email in demoVendorEmails)
                },
                idOf = { it.id },
                toMap = { item, ts -> item.toFirestoreMap(ts, deviceId) },
                getLocalMeta = { SyncTimestampTracker.getVendorSyncMeta(context, it) },
                markSynced = { id, ts -> SyncTimestampTracker.setVendorSyncedAt(context, id, ts) }
            )
            pulledUpdatesCount += vendorCounts.first
            pushedUpdatesCount += vendorCounts.second
            totalItemsProcessed += vendorCounts.third

            // 3. Sync Transactions
            val txCounts = syncGenericCollection(
                action = config.transactionsAction,
                collection = txCol,
                now = now,
                fetchLocal = { transactionDao.getAllTransactionsList() },
                deleteAllLocal = { transactionDao.deleteAll() },
                insertAllLocal = { transactionDao.insertAll(it) },
                parseRemote = { FirestoreEntityMapper.toTransactionSafe(it) },
                filterRemote = { item ->
                    item.id !in config.transactionIdsToDelete &&
                        !(config.deleteDefaultDemoRecords && item.referenceOrCustomer in demoTxRefs)
                },
                idOf = { it.id },
                toMap = { item, ts -> item.toFirestoreMap(ts, deviceId) },
                getLocalMeta = { SyncTimestampTracker.getTransactionSyncMeta(context, it) },
                markSynced = { id, ts -> SyncTimestampTracker.setTransactionSyncedAt(context, id, ts) }
            )
            pulledUpdatesCount += txCounts.first
            pushedUpdatesCount += txCounts.second
            totalItemsProcessed += txCounts.third

            // 4. Sync Patients
            val patientCounts = syncGenericCollection(
                action = config.patientsAction,
                collection = patientsCol,
                now = now,
                fetchLocal = { patientDao.getAllPatientsList() },
                deleteAllLocal = { patientDao.deleteAll() },
                insertAllLocal = { patientDao.insertAll(it) },
                parseRemote = { FirestoreEntityMapper.toPatientSafe(it) },
                filterRemote = { item ->
                    item.id !in config.patientIdsToDelete &&
                        !(config.deleteDefaultDemoRecords && item.patientName in demoPatientNames)
                },
                idOf = { it.id },
                toMap = { item, ts -> item.toFirestoreMap(ts, deviceId) },
                getLocalMeta = { SyncTimestampTracker.getPatientSyncMeta(context, it) },
                markSynced = { id, ts -> SyncTimestampTracker.setPatientSyncedAt(context, id, ts) }
            )
            pulledUpdatesCount += patientCounts.first
            pushedUpdatesCount += patientCounts.second
            totalItemsProcessed += patientCounts.third

            // 5. Sync Clinic Profile
            when (config.clinicProfileAction) {
                CategorySyncAction.SKIP, CategorySyncAction.DELETE_ALL -> {}
                CategorySyncAction.RESTORE_CLOUD_ONLY -> {
                    val remoteDoc = getFreshDocument(profileDocRef)
                    val remoteProfile = FirestoreEntityMapper.toClinicProfileSafe(remoteDoc)
                    if (remoteProfile != null) {
                        val meta = FirestoreEntityMapper.extractMeta(remoteDoc)
                        syncMutex.withLock { clinicProfileDao.insertOrUpdate(remoteProfile) }
                        SyncTimestampTracker.setProfileSyncedAt(context, meta.effectiveMillis.takeIf { it > 0L } ?: now)
                        pulledUpdatesCount++; totalItemsProcessed++
                    }
                }
                CategorySyncAction.PRESERVE_LOCAL_ONLY -> {
                    val localProfile = syncMutex.withLock { clinicProfileDao.getProfileSync() }
                    if (localProfile != null) {
                        val stamp = maxOf(now, SyncTimestampTracker.getProfileModifiedAt(context) + 1L)
                        profileDocRef.set(localProfile.toFirestoreMap(stamp, deviceId)).await()
                        SyncTimestampTracker.setProfileSyncedAt(context, stamp)
                        pushedUpdatesCount++; totalItemsProcessed++
                    }
                }
                CategorySyncAction.PRESERVE_BOTH -> {
                    val remoteDoc = getFreshDocument(profileDocRef)
                    val remoteProfile = FirestoreEntityMapper.toClinicProfileSafe(remoteDoc)
                    val meta = if (remoteDoc.exists()) FirestoreEntityMapper.extractMeta(remoteDoc) else RemoteSyncMeta()
                    val localProfile = syncMutex.withLock { clinicProfileDao.getProfileSync() }
                    val localMeta = SyncTimestampTracker.getProfileSyncMeta(context)
                    val effectiveTs = meta.effectiveMillis.takeIf { it > 0L } ?: now

                    if (remoteProfile != null && (localProfile == null || SyncIdAndConflictResolver.resolveRecordConflict(
                            remoteMeta = meta,
                            localMeta = localMeta,
                            myDeviceId = deviceId,
                            contentDiffers = localProfile != remoteProfile
                        ) == ConflictWinner.REMOTE)
                    ) {
                        syncMutex.withLock { clinicProfileDao.insertOrUpdate(remoteProfile) }
                        SyncTimestampTracker.setProfileSyncedAt(context, effectiveTs)
                        pulledUpdatesCount++; totalItemsProcessed++
                    } else if (localProfile != null && (remoteProfile == null || localProfile != remoteProfile)) {
                        val stamp = maxOf(localMeta.localModifiedMillis, effectiveTs + 1L, now)
                        profileDocRef.set(localProfile.toFirestoreMap(stamp, deviceId)).await()
                        SyncTimestampTracker.setProfileSyncedAt(context, stamp)
                        pushedUpdatesCount++; totalItemsProcessed++
                    }
                }
            }

            LocalBackupManager.saveAutoBackup(context, AppDatabase.getInstance(context))

            val completedAt = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_timestamp", completedAt).apply()

            val summaryMsg = when {
                pulledUpdatesCount > 0 && pushedUpdatesCount > 0 ->
                    "Synced: $pulledUpdatesCount updated from cloud, $pushedUpdatesCount uploaded"
                pulledUpdatesCount > 0 ->
                    "Synced: $pulledUpdatesCount records updated from cloud"
                pushedUpdatesCount > 0 ->
                    "Synced: $pushedUpdatesCount records uploaded to cloud"
                else ->
                    "Up to date across devices"
            }

            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTimeMillis = completedAt,
                statusMessage = summaryMsg,
                itemsSyncedCount = totalItemsProcessed,
                lastError = null
            )

            Log.i("CloudSync", "Sync completed: pulled=$pulledUpdatesCount, pushed=$pushedUpdatesCount")
            Result.success(totalItemsProcessed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            val friendlyError = e.localizedMessage ?: "Sync error occurred"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                statusMessage = "Sync failed: $friendlyError",
                lastError = friendlyError
            )
            Result.failure(e)
        }
    }

    private suspend fun <T> syncGenericCollection(
        action: CategorySyncAction,
        collection: CollectionReference,
        now: Long,
        fetchLocal: suspend () -> List<T>,
        deleteAllLocal: suspend () -> Unit,
        insertAllLocal: suspend (List<T>) -> Unit,
        parseRemote: (DocumentSnapshot) -> T?,
        filterRemote: (T) -> Boolean,
        idOf: (T) -> Long,
        toMap: (T, Long) -> Map<String, Any>,
        getLocalMeta: (Long) -> com.example.data.util.LocalSyncMeta,
        markSynced: (Long, Long) -> Unit
    ): Triple<Int, Int, Int> {
        var pulled = 0
        var pushed = 0
        var total = 0

        when (action) {
            CategorySyncAction.SKIP -> {}
            CategorySyncAction.DELETE_ALL -> {
                syncMutex.withLock { deleteAllLocal() }
                for (doc in getFreshCollection(collection).documents) {
                    doc.reference.delete().await(); total++
                }
            }
            CategorySyncAction.RESTORE_CLOUD_ONLY -> {
                val snap = getFreshCollection(collection)
                val entries = snap.documents.mapNotNull { doc ->
                    val item = parseRemote(doc) ?: return@mapNotNull null
                    if (!filterRemote(item)) return@mapNotNull null
                    item to FirestoreEntityMapper.extractMeta(doc)
                }
                syncMutex.withLock {
                    deleteAllLocal()
                    if (entries.isNotEmpty()) {
                        insertAllLocal(entries.map { it.first })
                        entries.forEach { (item, meta) ->
                            markSynced(idOf(item), meta.effectiveMillis.takeIf { it > 0L } ?: now)
                        }
                    }
                }
                pulled += entries.size
                total += entries.size
            }
            CategorySyncAction.PRESERVE_LOCAL_ONLY -> {
                val localItems = syncMutex.withLock { fetchLocal() }
                val localIds = localItems.map { idOf(it).toString() }.toSet()
                for (item in localItems) {
                    val id = idOf(item)
                    val stamp = maxOf(now, getLocalMeta(id).localModifiedMillis + 1L)
                    collection.document(id.toString()).set(toMap(item, stamp)).await()
                    markSynced(id, stamp)
                    pushed++; total++
                }
                for (doc in getFreshCollection(collection).documents) {
                    if (doc.id !in localIds) doc.reference.delete().await()
                }
            }
            CategorySyncAction.PRESERVE_BOTH -> {
                val snap = getFreshCollection(collection)
                val remoteMap = linkedMapOf<Long, Pair<T, RemoteSyncMeta>>()
                for (doc in snap.documents) {
                    val item = parseRemote(doc) ?: continue
                    if (!filterRemote(item)) continue
                    remoteMap[idOf(item)] = item to FirestoreEntityMapper.extractMeta(doc)
                }

                val toUpsertLocally = mutableListOf<Pair<T, Long>>()
                val toPushToCloud = mutableListOf<Pair<T, Long>>()

                syncMutex.withLock {
                    val localMap = fetchLocal().associateBy { idOf(it) }
                    for ((id, remotePair) in remoteMap) {
                        val (remoteItem, meta) = remotePair
                        val localItem = localMap[id]
                        val localMeta = getLocalMeta(id)
                        val effectiveTs = meta.effectiveMillis.takeIf { it > 0L } ?: now

                        if (localItem == null) {
                            toUpsertLocally.add(remoteItem to effectiveTs)
                        } else {
                            when (SyncIdAndConflictResolver.resolveRecordConflict(
                                remoteMeta = meta,
                                localMeta = localMeta,
                                myDeviceId = deviceId,
                                contentDiffers = localItem != remoteItem
                            )) {
                                ConflictWinner.EQUAL -> {
                                    val aligned = maxOf(effectiveTs, localMeta.syncedServerMillis)
                                    if (aligned > 0L) markSynced(id, aligned)
                                }
                                ConflictWinner.REMOTE -> {
                                    toUpsertLocally.add(remoteItem to effectiveTs)
                                }
                                ConflictWinner.LOCAL -> {
                                    val stamp = maxOf(localMeta.localModifiedMillis, effectiveTs + 1L, now)
                                    toPushToCloud.add(localItem to stamp)
                                }
                            }
                        }
                    }

                    for ((id, localItem) in localMap) {
                        if (id !in remoteMap) {
                            val localMeta = getLocalMeta(id)
                            toPushToCloud.add(localItem to maxOf(localMeta.localModifiedMillis, now))
                        }
                    }

                    if (toUpsertLocally.isNotEmpty()) {
                        insertAllLocal(toUpsertLocally.map { it.first })
                        toUpsertLocally.forEach { (item, ts) -> markSynced(idOf(item), ts) }
                    }
                }

                pulled += toUpsertLocally.size
                total += toUpsertLocally.size

                for ((item, ts) in toPushToCloud) {
                    val id = idOf(item)
                    collection.document(id.toString()).set(toMap(item, ts)).await()
                    markSynced(id, ts)
                    pushed++; total++
                }
            }
        }
        return Triple(pulled, pushed, total)
    }

    // --- Live Cloud Preview & Selective Upload / Download ---

    suspend fun fetchCloudSnapshot(explicitUserId: String? = null): Result<CloudDataSnapshot> = withContext(Dispatchers.IO) {
        val uid = try { resolveAuthenticatedUid(explicitUserId) } catch (e: Exception) { return@withContext Result.failure(e) }
        _isLoadingCloudPreview.value = true
        try {
            val userDoc = db.collection("users").document(uid)
            val vaccines = getFreshCollection(userDoc.collection("vaccines")).documents
                .mapNotNull { FirestoreEntityMapper.toVaccineSafe(it) }
                .sortedBy { it.name.lowercase() }
            val patients = getFreshCollection(userDoc.collection("patients")).documents
                .mapNotNull { FirestoreEntityMapper.toPatientSafe(it) }
                .sortedBy { it.patientName.lowercase() }
            val transactions = getFreshCollection(userDoc.collection("transactions")).documents
                .mapNotNull { FirestoreEntityMapper.toTransactionSafe(it) }
                .sortedByDescending { it.timestamp }
            val vendors = getFreshCollection(userDoc.collection("vendors")).documents
                .mapNotNull { FirestoreEntityMapper.toVendorSafe(it) }
                .sortedBy { it.name.lowercase() }
            val profileDoc = getFreshDocument(userDoc.collection("clinicProfile").document("profile"))
            val profile = FirestoreEntityMapper.toClinicProfileSafe(profileDoc)

            val snapshot = CloudDataSnapshot(
                vaccines = vaccines,
                patients = patients,
                transactions = transactions,
                vendors = vendors,
                clinicProfile = profile,
                fetchedAtMillis = System.currentTimeMillis()
            )
            _cloudSnapshot.value = snapshot
            _isLoadingCloudPreview.value = false
            Result.success(snapshot)
        } catch (e: CancellationException) {
            _isLoadingCloudPreview.value = false
            throw e
        } catch (e: Exception) {
            _isLoadingCloudPreview.value = false
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun uploadSelectedToCloud(
        explicitUserId: String? = null,
        request: SelectiveCloudTransferRequest
    ): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try {
            resolveAuthenticatedUid(explicitUserId)
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = e.message ?: "Google Sign-In required to upload to Cloud"
            )
            return@withContext Result.failure(e)
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusMessage = "Uploading selected records to Cloud...",
            lastError = null
        )

        var uploadedCount = 0
        val uploadTimestamp = System.currentTimeMillis()
        try {
            val userDoc = db.collection("users").document(uid)

            if (request.includeVaccines) {
                val col = userDoc.collection("vaccines")
                val localItems = syncMutex.withLock {
                    vaccineDao.getAllVaccinesList().let { all ->
                        if (request.selectedVaccineIds != null) all.filter { it.id in request.selectedVaccineIds } else all
                    }
                }
                val uploadedIds = localItems.map { it.id.toString() }.toSet()
                for (item in localItems) {
                    val stamp = maxOf(uploadTimestamp, SyncTimestampTracker.getVaccineModifiedAt(context, item.id) + 1L)
                    col.document(item.id.toString()).set(item.toFirestoreMap(stamp, deviceId)).await()
                    SyncTimestampTracker.setVaccineSyncedAt(context, item.id, stamp)
                    uploadedCount++
                }
                if (request.replaceExistingOnTarget) {
                    for (doc in getFreshCollection(col).documents) {
                        if (doc.id !in uploadedIds) doc.reference.delete().await()
                    }
                }
            }

            if (request.includePatients) {
                val col = userDoc.collection("patients")
                val localItems = syncMutex.withLock {
                    patientDao.getAllPatientsList().let { all ->
                        if (request.selectedPatientIds != null) all.filter { it.id in request.selectedPatientIds } else all
                    }
                }
                val uploadedIds = localItems.map { it.id.toString() }.toSet()
                for (item in localItems) {
                    val stamp = maxOf(uploadTimestamp, SyncTimestampTracker.getPatientModifiedAt(context, item.id) + 1L)
                    col.document(item.id.toString()).set(item.toFirestoreMap(stamp, deviceId)).await()
                    SyncTimestampTracker.setPatientSyncedAt(context, item.id, stamp)
                    uploadedCount++
                }
                if (request.replaceExistingOnTarget) {
                    for (doc in getFreshCollection(col).documents) {
                        if (doc.id !in uploadedIds) doc.reference.delete().await()
                    }
                }
            }

            if (request.includeTransactions) {
                val col = userDoc.collection("transactions")
                val localItems = syncMutex.withLock {
                    transactionDao.getAllTransactionsList().let { all ->
                        if (request.selectedTransactionIds != null) all.filter { it.id in request.selectedTransactionIds } else all
                    }
                }
                val uploadedIds = localItems.map { it.id.toString() }.toSet()
                for (item in localItems) {
                    val stamp = maxOf(uploadTimestamp, SyncTimestampTracker.getTransactionModifiedAt(context, item.id) + 1L)
                    col.document(item.id.toString()).set(item.toFirestoreMap(stamp, deviceId)).await()
                    SyncTimestampTracker.setTransactionSyncedAt(context, item.id, stamp)
                    uploadedCount++
                }
                if (request.replaceExistingOnTarget) {
                    for (doc in getFreshCollection(col).documents) {
                        if (doc.id !in uploadedIds) doc.reference.delete().await()
                    }
                }
            }

            if (request.includeVendors) {
                val col = userDoc.collection("vendors")
                val localItems = syncMutex.withLock {
                    vendorDao.getAllVendorsList().let { all ->
                        if (request.selectedVendorIds != null) all.filter { it.id in request.selectedVendorIds } else all
                    }
                }
                val uploadedIds = localItems.map { it.id.toString() }.toSet()
                for (item in localItems) {
                    val stamp = maxOf(uploadTimestamp, SyncTimestampTracker.getVendorModifiedAt(context, item.id) + 1L)
                    col.document(item.id.toString()).set(item.toFirestoreMap(stamp, deviceId)).await()
                    SyncTimestampTracker.setVendorSyncedAt(context, item.id, stamp)
                    uploadedCount++
                }
                if (request.replaceExistingOnTarget) {
                    for (doc in getFreshCollection(col).documents) {
                        if (doc.id !in uploadedIds) doc.reference.delete().await()
                    }
                }
            }

            if (request.includeClinicProfile) {
                syncMutex.withLock { clinicProfileDao.getProfileSync() }?.let { profile ->
                    val stamp = maxOf(uploadTimestamp, SyncTimestampTracker.getProfileModifiedAt(context) + 1L)
                    userDoc.collection("clinicProfile").document("profile").set(profile.toFirestoreMap(stamp, deviceId)).await()
                    SyncTimestampTracker.setProfileSyncedAt(context, stamp)
                    uploadedCount++
                }
            }

            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_timestamp", now).apply()
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTimeMillis = now,
                statusMessage = "Uploaded $uploadedCount selected items to Cloud",
                itemsSyncedCount = uploadedCount,
                lastError = null
            )

            fetchCloudSnapshot(uid)
            Result.success(uploadedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            val friendlyError = e.localizedMessage ?: "Cloud upload failed"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                statusMessage = "Upload failed: $friendlyError",
                lastError = friendlyError
            )
            Result.failure(e)
        }
    }

    suspend fun downloadSelectedFromCloud(
        explicitUserId: String? = null,
        request: SelectiveCloudTransferRequest
    ): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try {
            resolveAuthenticatedUid(explicitUserId)
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = e.message ?: "Google Sign-In required to download from Cloud"
            )
            return@withContext Result.failure(e)
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusMessage = "Downloading selected records from Cloud...",
            lastError = null
        )

        var downloadedCount = 0
        val now = System.currentTimeMillis()
        try {
            val userDoc = db.collection("users").document(uid)

            if (request.includeVaccines) {
                val snap = getFreshCollection(userDoc.collection("vaccines"))
                val remoteEntries = snap.documents.mapNotNull { doc ->
                    val item = FirestoreEntityMapper.toVaccineSafe(doc) ?: return@mapNotNull null
                    if (request.selectedVaccineIds != null && item.id !in request.selectedVaccineIds) return@mapNotNull null
                    item to FirestoreEntityMapper.extractMeta(doc)
                }
                syncMutex.withLock {
                    if (request.replaceExistingOnTarget) {
                        vaccineDao.deleteAll()
                    } else if (remoteEntries.isNotEmpty()) {
                        // Deduplicate any local vaccine matching a downloaded cloud vaccine under a different ID
                        val locals = vaccineDao.getAllVaccinesList()
                        for ((remoteVac, _) in remoteEntries) {
                            locals.filter { it.id != remoteVac.id && SyncIdAndConflictResolver.isSameLogicalVaccine(it, remoteVac) }
                                .forEach { dup ->
                                    transactionDao.remapVaccineId(dup.id, remoteVac.id)
                                    vaccineDao.deleteVaccine(dup)
                                    SyncTimestampTracker.removeVaccineTimestamp(context, dup.id)
                                }
                        }
                    }
                    if (remoteEntries.isNotEmpty()) {
                        vaccineDao.insertAll(remoteEntries.map { it.first })
                        remoteEntries.forEach { (item, meta) ->
                            SyncTimestampTracker.setVaccineSyncedAt(context, item.id, meta.effectiveMillis.takeIf { it > 0L } ?: now)
                        }
                        downloadedCount += remoteEntries.size
                    }
                }
            }

            if (request.includePatients) {
                val snap = getFreshCollection(userDoc.collection("patients"))
                val remoteEntries = snap.documents.mapNotNull { doc ->
                    val item = FirestoreEntityMapper.toPatientSafe(doc) ?: return@mapNotNull null
                    if (request.selectedPatientIds != null && item.id !in request.selectedPatientIds) return@mapNotNull null
                    item to FirestoreEntityMapper.extractMeta(doc)
                }
                syncMutex.withLock {
                    if (request.replaceExistingOnTarget) patientDao.deleteAll()
                    if (remoteEntries.isNotEmpty()) {
                        patientDao.insertAll(remoteEntries.map { it.first })
                        remoteEntries.forEach { (item, meta) ->
                            SyncTimestampTracker.setPatientSyncedAt(context, item.id, meta.effectiveMillis.takeIf { it > 0L } ?: now)
                        }
                        downloadedCount += remoteEntries.size
                    }
                }
            }

            if (request.includeTransactions) {
                val snap = getFreshCollection(userDoc.collection("transactions"))
                val remoteEntries = snap.documents.mapNotNull { doc ->
                    val item = FirestoreEntityMapper.toTransactionSafe(doc) ?: return@mapNotNull null
                    if (request.selectedTransactionIds != null && item.id !in request.selectedTransactionIds) return@mapNotNull null
                    item to FirestoreEntityMapper.extractMeta(doc)
                }
                syncMutex.withLock {
                    if (request.replaceExistingOnTarget) transactionDao.deleteAll()
                    if (remoteEntries.isNotEmpty()) {
                        transactionDao.insertAll(remoteEntries.map { it.first })
                        remoteEntries.forEach { (item, meta) ->
                            SyncTimestampTracker.setTransactionSyncedAt(context, item.id, meta.effectiveMillis.takeIf { it > 0L } ?: now)
                        }
                        downloadedCount += remoteEntries.size
                    }
                }
            }

            if (request.includeVendors) {
                val snap = getFreshCollection(userDoc.collection("vendors"))
                val remoteEntries = snap.documents.mapNotNull { doc ->
                    val item = FirestoreEntityMapper.toVendorSafe(doc) ?: return@mapNotNull null
                    if (request.selectedVendorIds != null && item.id !in request.selectedVendorIds) return@mapNotNull null
                    item to FirestoreEntityMapper.extractMeta(doc)
                }
                syncMutex.withLock {
                    if (request.replaceExistingOnTarget) vendorDao.deleteAll()
                    if (remoteEntries.isNotEmpty()) {
                        vendorDao.insertAll(remoteEntries.map { it.first })
                        remoteEntries.forEach { (item, meta) ->
                            SyncTimestampTracker.setVendorSyncedAt(context, item.id, meta.effectiveMillis.takeIf { it > 0L } ?: now)
                        }
                        downloadedCount += remoteEntries.size
                    }
                }
            }

            if (request.includeClinicProfile) {
                val profileDoc = getFreshDocument(userDoc.collection("clinicProfile").document("profile"))
                val remoteProfile = FirestoreEntityMapper.toClinicProfileSafe(profileDoc)
                if (remoteProfile != null) {
                    val meta = FirestoreEntityMapper.extractMeta(profileDoc)
                    syncMutex.withLock { clinicProfileDao.insertOrUpdate(remoteProfile) }
                    SyncTimestampTracker.setProfileSyncedAt(context, meta.effectiveMillis.takeIf { it > 0L } ?: now)
                    downloadedCount++
                }
            }

            AppDatabase.getInstance(context).let { database ->
                LocalBackupManager.markUserIntentionallyClearedData(context, false)
                LocalBackupManager.saveAutoBackup(context, database)
            }

            val completedAt = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_timestamp", completedAt).apply()
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTimeMillis = completedAt,
                statusMessage = "Downloaded $downloadedCount selected items from Cloud",
                itemsSyncedCount = downloadedCount,
                lastError = null
            )

            Result.success(downloadedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            val friendlyError = e.localizedMessage ?: "Cloud download failed"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                statusMessage = "Download failed: $friendlyError",
                lastError = friendlyError
            )
            Result.failure(e)
        }
    }
}
