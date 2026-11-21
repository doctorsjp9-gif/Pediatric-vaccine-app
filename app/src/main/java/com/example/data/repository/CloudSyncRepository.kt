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
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.StockTransaction
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class CategorySyncAction(val label: String, val description: String) {
    PRESERVE_BOTH("Preserve All (Merge)", "Keep both device & cloud records safely"),
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
    val lastError: String? = null
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
    // Secondary constructor resolving database ID from string resources
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

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before syncing data.")
    }

    suspend fun deleteVaccineFromCloud(id: Long, explicitUserId: String? = null) = withContext(Dispatchers.IO) {
        val uid = try { explicitUserId ?: auth.currentUser?.uid } catch (e: Exception) { null } ?: return@withContext
        try {
            db.collection("users").document(uid).collection("vaccines").document(id.toString()).delete().await()
            Log.d("CloudSync", "Deleted vaccine $id from cloud")
        } catch (e: Exception) {
            Log.w("CloudSync", "Failed to delete vaccine $id from cloud", e)
        }
    }

    suspend fun deletePatientFromCloud(id: Long, explicitUserId: String? = null) = withContext(Dispatchers.IO) {
        val uid = try { explicitUserId ?: auth.currentUser?.uid } catch (e: Exception) { null } ?: return@withContext
        try {
            db.collection("users").document(uid).collection("patients").document(id.toString()).delete().await()
            Log.d("CloudSync", "Deleted patient $id from cloud")
        } catch (e: Exception) {
            Log.w("CloudSync", "Failed to delete patient $id from cloud", e)
        }
    }

    suspend fun deleteVendorFromCloud(id: Long, explicitUserId: String? = null) = withContext(Dispatchers.IO) {
        val uid = try { explicitUserId ?: auth.currentUser?.uid } catch (e: Exception) { null } ?: return@withContext
        try {
            db.collection("users").document(uid).collection("vendors").document(id.toString()).delete().await()
            Log.d("CloudSync", "Deleted vendor $id from cloud")
        } catch (e: Exception) {
            Log.w("CloudSync", "Failed to delete vendor $id from cloud", e)
        }
    }

    suspend fun deleteTransactionFromCloud(id: Long, explicitUserId: String? = null) = withContext(Dispatchers.IO) {
        val uid = try { explicitUserId ?: auth.currentUser?.uid } catch (e: Exception) { null } ?: return@withContext
        try {
            db.collection("users").document(uid).collection("transactions").document(id.toString()).delete().await()
            Log.d("CloudSync", "Deleted transaction $id from cloud")
        } catch (e: Exception) {
            Log.w("CloudSync", "Failed to delete transaction $id from cloud", e)
        }
    }

    suspend fun purgeStaleCloudData(explicitUserId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try {
            explicitUserId ?: requireUserId()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        try {
            var purgedCount = 0

            // Purge vaccines not in local database
            val localVaccines = vaccineDao.getAllVaccinesList()
            val localVaccineIds = localVaccines.map { it.id.toString() }.toSet()
            val remoteVaccinesSnap = db.collection("users").document(uid).collection("vaccines").get().await()
            for (doc in remoteVaccinesSnap.documents) {
                if (!localVaccineIds.contains(doc.id)) {
                    doc.reference.delete().await()
                    purgedCount++
                }
            }

            // Purge patients not in local database
            val localPatients = patientDao.getAllPatientsList()
            val localPatientIds = localPatients.map { it.id.toString() }.toSet()
            val remotePatientsSnap = db.collection("users").document(uid).collection("patients").get().await()
            for (doc in remotePatientsSnap.documents) {
                if (!localPatientIds.contains(doc.id)) {
                    doc.reference.delete().await()
                    purgedCount++
                }
            }

            // Purge vendors not in local database
            val localVendors = vendorDao.getAllVendorsList()
            val localVendorIds = localVendors.map { it.id.toString() }.toSet()
            val remoteVendorsSnap = db.collection("users").document(uid).collection("vendors").get().await()
            for (doc in remoteVendorsSnap.documents) {
                if (!localVendorIds.contains(doc.id)) {
                    doc.reference.delete().await()
                    purgedCount++
                }
            }

            Log.i("CloudSync", "Purged $purgedCount stale items from cloud.")
            Result.success(purgedCount)
        } catch (e: Exception) {
            Log.e("CloudSync", "Error purging stale cloud data", e)
            Result.failure(e)
        }
    }

    suspend fun purgeStaleCloudPatients(explicitUserId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try {
            explicitUserId ?: requireUserId()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        try {
            var count = 0
            val localPatients = patientDao.getAllPatientsList()
            val localPatientIds = localPatients.map { it.id.toString() }.toSet()
            val snap = db.collection("users").document(uid).collection("patients").get().await()
            for (doc in snap.documents) {
                if (!localPatientIds.contains(doc.id)) {
                    doc.reference.delete().await()
                    count++
                }
            }
            Log.i("CloudSync", "Purged $count stale patient records from cloud")
            Result.success(count)
        } catch (e: Exception) {
            Log.e("CloudSync", "Error purging stale patient records", e)
            Result.failure(e)
        }
    }

    suspend fun eraseAllCloudPatients(explicitUserId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try {
            explicitUserId ?: requireUserId()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        try {
            var count = 0
            val snap = db.collection("users").document(uid).collection("patients").get().await()
            for (doc in snap.documents) {
                doc.reference.delete().await()
                count++
            }
            Log.i("CloudSync", "Erased all $count patient records from cloud")
            Result.success(count)
        } catch (e: Exception) {
            Log.e("CloudSync", "Error erasing all cloud patients", e)
            Result.failure(e)
        }
    }

    suspend fun eraseAllCloudData(explicitUserId: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try {
            explicitUserId ?: requireUserId()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        try {
            var erasedDocs = 0
            val collections = listOf("vaccines", "patients", "vendors", "transactions")
            for (colName in collections) {
                val snap = db.collection("users").document(uid).collection(colName).get().await()
                for (doc in snap.documents) {
                    doc.reference.delete().await()
                    erasedDocs++
                }
            }
            prefs.edit().remove("last_sync_timestamp").apply()
            _syncState.value = SyncStatusState(
                isSyncing = false,
                lastSyncTimeMillis = null,
                statusMessage = "All cloud data erased",
                itemsSyncedCount = 0,
                lastError = null
            )
            Log.i("CloudSync", "Erased $erasedDocs documents from cloud.")
            Result.success(erasedDocs)
        } catch (e: Exception) {
            Log.e("CloudSync", "Error erasing all cloud data", e)
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
                com.example.data.util.LocalBackupManager.markUserIntentionallyClearedData(context, true)
                vaccineDao.deleteAll()
                patientDao.deleteAll()
                transactionDao.deleteAll()
                vendorDao.deleteAll()
            }
            if (eraseCloud) {
                val uid = try { explicitUserId ?: auth.currentUser?.uid } catch (e: Exception) { null }
                if (uid != null) {
                    eraseAllCloudData(uid)
                }
            }
            prefs.edit().remove("last_sync_timestamp").apply()
            _syncState.value = SyncStatusState(
                isSyncing = false,
                lastSyncTimeMillis = null,
                statusMessage = "All data erased cleanly",
                itemsSyncedCount = 0,
                lastError = null
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CloudSync", "Error erasing data", e)
            Result.failure(e)
        }
    }

    /**
     * Synchronizes with Cloud using customizable preservation and deletion options.
     * By default (purgeStaleCloudRecords = false), it safely merges & preserves both local and cloud data
     * so updating or reinstalling the app NEVER deletes old user data.
     */
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
            explicitUserId ?: requireUserId()
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = "Authentication required for cross-device sync"
            )
            return@withContext Result.failure(e)
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusMessage = "Synchronizing selected data with Cloud...",
            lastError = null
        )

        var totalItemsProcessed = 0

        try {
            // Optional: Remove default pre-populated demo/sample records if requested by user
            val demoVaccineCodes = setOf("VAC-HEPB-01", "VAC-MMR-02", "VAC-FLU-24", "VAC-RAB-05", "VAC-TDAP-09", "VAC-TYP-11")
            val demoPatientNames = setOf("Baby Aarav Sharma", "Ananya Patel", "Kabir Verma")
            val demoVendorEmails = setOf("orders@seruminstitute-dist.in", "supply@bharatbiotech-depot.in", "apatel@panaceaclinic.com")
            val demoTxRefs = setOf("PO-8901", "PO-8942", "PO-8990", "Baby Aarav Sharma (Pediatric)", "Verma Family (Walk-in)", "Child Wellness Program", "Emergency Patient #402", "Maternal Health Clinic", "Travel Medicine Consultation")

            // 0. Process explicit item-level deletions first (Local & Cloud)
            val vaccinesCollection = db.collection("users").document(uid).collection("vaccines")
            val patientsCollection = db.collection("users").document(uid).collection("patients")
            val vendorsCollection = db.collection("users").document(uid).collection("vendors")
            val txCollection = db.collection("users").document(uid).collection("transactions")

            if (config.deleteDefaultDemoRecords) {
                vaccineDao.getAllVaccinesList().filter { it.code in demoVaccineCodes }.forEach {
                    vaccineDao.deleteVaccine(it)
                    vaccinesCollection.document(it.id.toString()).delete().await()
                }
                patientDao.getAllPatientsList().filter { it.patientName in demoPatientNames }.forEach {
                    patientDao.delete(it)
                    patientsCollection.document(it.id.toString()).delete().await()
                }
                vendorDao.getAllVendorsList().filter { it.email in demoVendorEmails }.forEach {
                    vendorDao.deleteVendor(it)
                    vendorsCollection.document(it.id.toString()).delete().await()
                }
                transactionDao.getAllTransactionsList().filter { it.referenceOrCustomer in demoTxRefs }.forEach {
                    transactionDao.deleteTransaction(it.id)
                    txCollection.document(it.id.toString()).delete().await()
                }
            }

            for (delVacId in config.vaccineIdsToDelete) {
                vaccineDao.getVaccineById(delVacId)?.let { vaccineDao.deleteVaccine(it) }
                vaccinesCollection.document(delVacId.toString()).delete().await()
                totalItemsProcessed++
            }
            for (delPatId in config.patientIdsToDelete) {
                patientDao.getPatientById(delPatId)?.let { patientDao.delete(it) }
                patientsCollection.document(delPatId.toString()).delete().await()
                totalItemsProcessed++
            }
            for (delVenId in config.vendorIdsToDelete) {
                vendorDao.getVendorById(delVenId)?.let { vendorDao.deleteVendor(it) }
                vendorsCollection.document(delVenId.toString()).delete().await()
                totalItemsProcessed++
            }
            for (delTxId in config.transactionIdsToDelete) {
                transactionDao.deleteTransaction(delTxId)
                txCollection.document(delTxId.toString()).delete().await()
                totalItemsProcessed++
            }

            // 1. Sync Vaccines according to vaccinesAction
            when (config.vaccinesAction) {
                CategorySyncAction.SKIP -> {}
                CategorySyncAction.DELETE_ALL -> {
                    vaccineDao.deleteAll()
                    val snap = vaccinesCollection.get().await()
                    for (doc in snap.documents) {
                        doc.reference.delete().await()
                        totalItemsProcessed++
                    }
                }
                CategorySyncAction.RESTORE_CLOUD_ONLY -> {
                    val snap = vaccinesCollection.get().await()
                    val remoteVaccines = snap.toObjects(Vaccine::class.java)
                        .filter { it.id !in config.vaccineIdsToDelete && (!config.deleteDefaultDemoRecords || it.code !in demoVaccineCodes) }
                    vaccineDao.deleteAll()
                    if (remoteVaccines.isNotEmpty()) {
                        vaccineDao.insertAll(remoteVaccines)
                        totalItemsProcessed += remoteVaccines.size
                    }
                }
                CategorySyncAction.PRESERVE_LOCAL_ONLY -> {
                    val localVaccines = vaccineDao.getAllVaccinesList()
                    val localIds = localVaccines.map { it.id.toString() }.toSet()
                    for (vaccine in localVaccines) {
                        vaccinesCollection.document(vaccine.id.toString()).set(vaccine).await()
                        totalItemsProcessed++
                    }
                    val snap = vaccinesCollection.get().await()
                    for (doc in snap.documents) {
                        if (!localIds.contains(doc.id)) {
                            doc.reference.delete().await()
                        }
                    }
                }
                CategorySyncAction.PRESERVE_BOTH -> {
                    // Safe two-way merge: pull remote vaccines missing locally, and push local vaccines to cloud
                    val localVaccines = vaccineDao.getAllVaccinesList()
                    val localIds = localVaccines.map { it.id }.toSet()
                    val snap = vaccinesCollection.get().await()
                    val remoteVaccines = snap.toObjects(Vaccine::class.java)
                        .filter { it.id !in config.vaccineIdsToDelete && (!config.deleteDefaultDemoRecords || it.code !in demoVaccineCodes) }
                    val missingLocally = remoteVaccines.filter { it.id != 0L && it.id !in localIds }
                    if (missingLocally.isNotEmpty()) {
                        vaccineDao.insertAll(missingLocally)
                        totalItemsProcessed += missingLocally.size
                    }
                    for (vaccine in vaccineDao.getAllVaccinesList()) {
                        vaccinesCollection.document(vaccine.id.toString()).set(vaccine).await()
                        totalItemsProcessed++
                    }
                }
            }

            // 2. Sync Vendors according to vendorsAction
            when (config.vendorsAction) {
                CategorySyncAction.SKIP -> {}
                CategorySyncAction.DELETE_ALL -> {
                    vendorDao.deleteAll()
                    val snap = vendorsCollection.get().await()
                    for (doc in snap.documents) {
                        doc.reference.delete().await()
                        totalItemsProcessed++
                    }
                }
                CategorySyncAction.RESTORE_CLOUD_ONLY -> {
                    val snap = vendorsCollection.get().await()
                    val remoteVendors = snap.toObjects(Vendor::class.java)
                        .filter { it.id !in config.vendorIdsToDelete && (!config.deleteDefaultDemoRecords || it.email !in demoVendorEmails) }
                    vendorDao.deleteAll()
                    if (remoteVendors.isNotEmpty()) {
                        vendorDao.insertAll(remoteVendors)
                        totalItemsProcessed += remoteVendors.size
                    }
                }
                CategorySyncAction.PRESERVE_LOCAL_ONLY -> {
                    val localVendors = vendorDao.getAllVendorsList()
                    val localIds = localVendors.map { it.id.toString() }.toSet()
                    for (vendor in localVendors) {
                        vendorsCollection.document(vendor.id.toString()).set(vendor).await()
                        totalItemsProcessed++
                    }
                    val snap = vendorsCollection.get().await()
                    for (doc in snap.documents) {
                        if (!localIds.contains(doc.id)) {
                            doc.reference.delete().await()
                        }
                    }
                }
                CategorySyncAction.PRESERVE_BOTH -> {
                    val localVendors = vendorDao.getAllVendorsList()
                    val localIds = localVendors.map { it.id }.toSet()
                    val snap = vendorsCollection.get().await()
                    val remoteVendors = snap.toObjects(Vendor::class.java)
                        .filter { it.id !in config.vendorIdsToDelete && (!config.deleteDefaultDemoRecords || it.email !in demoVendorEmails) }
                    val missingLocally = remoteVendors.filter { it.id != 0L && it.id !in localIds }
                    if (missingLocally.isNotEmpty()) {
                        vendorDao.insertAll(missingLocally)
                        totalItemsProcessed += missingLocally.size
                    }
                    for (vendor in vendorDao.getAllVendorsList()) {
                        vendorsCollection.document(vendor.id.toString()).set(vendor).await()
                        totalItemsProcessed++
                    }
                }
            }

            // 3. Sync Transactions according to transactionsAction
            when (config.transactionsAction) {
                CategorySyncAction.SKIP -> {}
                CategorySyncAction.DELETE_ALL -> {
                    transactionDao.deleteAll()
                    val snap = txCollection.get().await()
                    for (doc in snap.documents) {
                        doc.reference.delete().await()
                        totalItemsProcessed++
                    }
                }
                CategorySyncAction.RESTORE_CLOUD_ONLY -> {
                    val snap = txCollection.get().await()
                    val remoteTx = snap.toObjects(StockTransaction::class.java)
                        .filter { it.id !in config.transactionIdsToDelete && (!config.deleteDefaultDemoRecords || it.referenceOrCustomer !in demoTxRefs) }
                    transactionDao.deleteAll()
                    if (remoteTx.isNotEmpty()) {
                        transactionDao.insertAll(remoteTx)
                        totalItemsProcessed += remoteTx.size
                    }
                }
                CategorySyncAction.PRESERVE_LOCAL_ONLY -> {
                    val localTx = transactionDao.getAllTransactionsList()
                    val localIds = localTx.map { it.id.toString() }.toSet()
                    for (tx in localTx) {
                        txCollection.document(tx.id.toString()).set(tx).await()
                        totalItemsProcessed++
                    }
                    val snap = txCollection.get().await()
                    for (doc in snap.documents) {
                        if (!localIds.contains(doc.id)) {
                            doc.reference.delete().await()
                        }
                    }
                }
                CategorySyncAction.PRESERVE_BOTH -> {
                    val localTx = transactionDao.getAllTransactionsList()
                    val localIds = localTx.map { it.id }.toSet()
                    val snap = txCollection.get().await()
                    val remoteTx = snap.toObjects(StockTransaction::class.java)
                        .filter { it.id !in config.transactionIdsToDelete && (!config.deleteDefaultDemoRecords || it.referenceOrCustomer !in demoTxRefs) }
                    val missingLocally = remoteTx.filter { it.id != 0L && it.id !in localIds }
                    if (missingLocally.isNotEmpty()) {
                        transactionDao.insertAll(missingLocally)
                        totalItemsProcessed += missingLocally.size
                    }
                    for (tx in transactionDao.getAllTransactionsList()) {
                        txCollection.document(tx.id.toString()).set(tx).await()
                        totalItemsProcessed++
                    }
                }
            }

            // 4. Sync Patients according to patientsAction
            when (config.patientsAction) {
                CategorySyncAction.SKIP -> {}
                CategorySyncAction.DELETE_ALL -> {
                    patientDao.deleteAll()
                    val snap = patientsCollection.get().await()
                    for (doc in snap.documents) {
                        doc.reference.delete().await()
                        totalItemsProcessed++
                    }
                }
                CategorySyncAction.RESTORE_CLOUD_ONLY -> {
                    val snap = patientsCollection.get().await()
                    val remotePatients = snap.toObjects(PatientRegistration::class.java)
                        .filter { it.id !in config.patientIdsToDelete && (!config.deleteDefaultDemoRecords || it.patientName !in demoPatientNames) }
                    patientDao.deleteAll()
                    if (remotePatients.isNotEmpty()) {
                        patientDao.insertAll(remotePatients)
                        totalItemsProcessed += remotePatients.size
                    }
                }
                CategorySyncAction.PRESERVE_LOCAL_ONLY -> {
                    val localPatients = patientDao.getAllPatientsList()
                    val localIds = localPatients.map { it.id.toString() }.toSet()
                    for (patient in localPatients) {
                        patientsCollection.document(patient.id.toString()).set(patient).await()
                        totalItemsProcessed++
                    }
                    val snap = patientsCollection.get().await()
                    for (doc in snap.documents) {
                        if (!localIds.contains(doc.id)) {
                            doc.reference.delete().await()
                        }
                    }
                }
                CategorySyncAction.PRESERVE_BOTH -> {
                    val localPatients = patientDao.getAllPatientsList()
                    val localIds = localPatients.map { it.id }.toSet()
                    val snap = patientsCollection.get().await()
                    val remotePatients = snap.toObjects(PatientRegistration::class.java)
                        .filter { it.id !in config.patientIdsToDelete && (!config.deleteDefaultDemoRecords || it.patientName !in demoPatientNames) }
                    val missingLocally = remotePatients.filter { it.id != 0L && it.id !in localIds }
                    if (missingLocally.isNotEmpty()) {
                        patientDao.insertAll(missingLocally)
                        totalItemsProcessed += missingLocally.size
                    }
                    for (patient in patientDao.getAllPatientsList()) {
                        patientsCollection.document(patient.id.toString()).set(patient).await()
                        totalItemsProcessed++
                    }
                }
            }

            // 5. Sync Clinic Profile
            when (config.clinicProfileAction) {
                CategorySyncAction.SKIP, CategorySyncAction.DELETE_ALL -> {}
                CategorySyncAction.RESTORE_CLOUD_ONLY -> {
                    val remoteProfileDoc = db.collection("users").document(uid).collection("clinicProfile")
                        .document("profile").get().await()
                    if (remoteProfileDoc.exists()) {
                        remoteProfileDoc.toObject(ClinicProfile::class.java)?.let {
                            clinicProfileDao.insertOrUpdate(it)
                            totalItemsProcessed++
                        }
                    }
                }
                CategorySyncAction.PRESERVE_LOCAL_ONLY, CategorySyncAction.PRESERVE_BOTH -> {
                    val localProfile = clinicProfileDao.getProfileSync()
                    if (localProfile != null) {
                        db.collection("users").document(uid).collection("clinicProfile")
                            .document("profile").set(localProfile).await()
                        totalItemsProcessed++
                    } else {
                        val remoteProfileDoc = db.collection("users").document(uid).collection("clinicProfile")
                            .document("profile").get().await()
                        if (remoteProfileDoc.exists()) {
                            remoteProfileDoc.toObject(ClinicProfile::class.java)?.let {
                                clinicProfileDao.insertOrUpdate(it)
                                totalItemsProcessed++
                            }
                        }
                    }
                }
            }

            // Save persistent local backup after sync
            com.example.data.database.AppDatabase.getInstance(context).let { database ->
                com.example.data.util.LocalBackupManager.saveAutoBackup(context, database)
            }

            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_timestamp", now).apply()

            _syncState.value = SyncStatusState(
                isSyncing = false,
                lastSyncTimeMillis = now,
                statusMessage = "Synced ($totalItemsProcessed records aligned)",
                itemsSyncedCount = totalItemsProcessed,
                lastError = null
            )

            Log.i("CloudSync", "Sync completed successfully: $totalItemsProcessed records aligned.")
            Result.success(totalItemsProcessed)
        } catch (e: Exception) {
            val errorPath = "users/$uid"
            handleFirestoreError(e, OperationType.WRITE, errorPath)
            val friendlyError = e.localizedMessage ?: "Sync error occurred"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                statusMessage = "Sync failed: $friendlyError",
                lastError = friendlyError
            )
            Result.failure(e)
        }
    }

    /**
     * Fetches a live preview of all records currently stored in Firebase Cloud for the user
     * so they can inspect and selectively choose which items or categories to download.
     */
    suspend fun fetchCloudSnapshot(explicitUserId: String? = null): Result<CloudDataSnapshot> = withContext(Dispatchers.IO) {
        val uid = try {
            explicitUserId ?: requireUserId()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        _isLoadingCloudPreview.value = true
        try {
            val userDoc = db.collection("users").document(uid)
            val vaccines = userDoc.collection("vaccines").get().await().toObjects(Vaccine::class.java)
                .sortedBy { it.name.lowercase() }
            val patients = userDoc.collection("patients").get().await().toObjects(PatientRegistration::class.java)
                .sortedBy { it.patientName.lowercase() }
            val transactions = userDoc.collection("transactions").get().await().toObjects(StockTransaction::class.java)
                .sortedByDescending { it.timestamp }
            val vendors = userDoc.collection("vendors").get().await().toObjects(Vendor::class.java)
                .sortedBy { it.name.lowercase() }
            val profileDoc = userDoc.collection("clinicProfile").document("profile").get().await()
            val profile = if (profileDoc.exists()) profileDoc.toObject(ClinicProfile::class.java) else null

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
            Log.i("CloudSync", "Fetched cloud preview: ${snapshot.totalRecords} total records.")
            Result.success(snapshot)
        } catch (e: Exception) {
            _isLoadingCloudPreview.value = false
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            Result.failure(e)
        }
    }

    /**
     * Selectively uploads ONLY the chosen categories and/or individually selected local records
     * from this device to Firebase Cloud without downloading or overwriting unselected data.
     */
    suspend fun uploadSelectedToCloud(
        explicitUserId: String? = null,
        request: SelectiveCloudTransferRequest
    ): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try {
            explicitUserId ?: requireUserId()
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = "Authentication required to upload to Cloud"
            )
            return@withContext Result.failure(e)
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusMessage = "Uploading selected records to Cloud...",
            lastError = null
        )

        var uploadedCount = 0
        try {
            val userDoc = db.collection("users").document(uid)

            // 1. Upload Selected Vaccines
            if (request.includeVaccines) {
                val col = userDoc.collection("vaccines")
                val localItems = vaccineDao.getAllVaccinesList().let { all ->
                    if (request.selectedVaccineIds != null) all.filter { it.id in request.selectedVaccineIds } else all
                }
                val uploadedIds = localItems.map { it.id.toString() }.toSet()
                for (item in localItems) {
                    col.document(item.id.toString()).set(item).await()
                    uploadedCount++
                }
                if (request.replaceExistingOnTarget) {
                    val snap = col.get().await()
                    for (doc in snap.documents) {
                        if (doc.id !in uploadedIds) {
                            doc.reference.delete().await()
                        }
                    }
                }
            }

            // 2. Upload Selected Patients
            if (request.includePatients) {
                val col = userDoc.collection("patients")
                val localItems = patientDao.getAllPatientsList().let { all ->
                    if (request.selectedPatientIds != null) all.filter { it.id in request.selectedPatientIds } else all
                }
                val uploadedIds = localItems.map { it.id.toString() }.toSet()
                for (item in localItems) {
                    col.document(item.id.toString()).set(item).await()
                    uploadedCount++
                }
                if (request.replaceExistingOnTarget) {
                    val snap = col.get().await()
                    for (doc in snap.documents) {
                        if (doc.id !in uploadedIds) {
                            doc.reference.delete().await()
                        }
                    }
                }
            }

            // 3. Upload Selected Transactions
            if (request.includeTransactions) {
                val col = userDoc.collection("transactions")
                val localItems = transactionDao.getAllTransactionsList().let { all ->
                    if (request.selectedTransactionIds != null) all.filter { it.id in request.selectedTransactionIds } else all
                }
                val uploadedIds = localItems.map { it.id.toString() }.toSet()
                for (item in localItems) {
                    col.document(item.id.toString()).set(item).await()
                    uploadedCount++
                }
                if (request.replaceExistingOnTarget) {
                    val snap = col.get().await()
                    for (doc in snap.documents) {
                        if (doc.id !in uploadedIds) {
                            doc.reference.delete().await()
                        }
                    }
                }
            }

            // 4. Upload Selected Vendors
            if (request.includeVendors) {
                val col = userDoc.collection("vendors")
                val localItems = vendorDao.getAllVendorsList().let { all ->
                    if (request.selectedVendorIds != null) all.filter { it.id in request.selectedVendorIds } else all
                }
                val uploadedIds = localItems.map { it.id.toString() }.toSet()
                for (item in localItems) {
                    col.document(item.id.toString()).set(item).await()
                    uploadedCount++
                }
                if (request.replaceExistingOnTarget) {
                    val snap = col.get().await()
                    for (doc in snap.documents) {
                        if (doc.id !in uploadedIds) {
                            doc.reference.delete().await()
                        }
                    }
                }
            }

            // 5. Upload Clinic Profile
            if (request.includeClinicProfile) {
                clinicProfileDao.getProfileSync()?.let { profile ->
                    userDoc.collection("clinicProfile").document("profile").set(profile).await()
                    uploadedCount++
                }
            }

            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_timestamp", now).apply()
            _syncState.value = SyncStatusState(
                isSyncing = false,
                lastSyncTimeMillis = now,
                statusMessage = "Uploaded $uploadedCount selected items to Cloud",
                itemsSyncedCount = uploadedCount,
                lastError = null
            )

            // Refresh cached cloud snapshot so download preview stays up to date
            fetchCloudSnapshot(uid)
            Result.success(uploadedCount)
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

    /**
     * Selectively downloads ONLY the chosen categories and/or individually selected records
     * from Firebase Cloud into the local Room database without pushing or modifying cloud data.
     */
    suspend fun downloadSelectedFromCloud(
        explicitUserId: String? = null,
        request: SelectiveCloudTransferRequest
    ): Result<Int> = withContext(Dispatchers.IO) {
        val uid = try {
            explicitUserId ?: requireUserId()
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastError = "Authentication required to download from Cloud"
            )
            return@withContext Result.failure(e)
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusMessage = "Downloading selected records from Cloud...",
            lastError = null
        )

        var downloadedCount = 0
        try {
            val userDoc = db.collection("users").document(uid)

            // 1. Download Selected Vaccines
            if (request.includeVaccines) {
                val remoteItems = userDoc.collection("vaccines").get().await()
                    .toObjects(Vaccine::class.java)
                    .filter { it.id != 0L && (request.selectedVaccineIds == null || it.id in request.selectedVaccineIds) }
                if (request.replaceExistingOnTarget) {
                    vaccineDao.deleteAll()
                }
                if (remoteItems.isNotEmpty()) {
                    vaccineDao.insertAll(remoteItems)
                    downloadedCount += remoteItems.size
                }
            }

            // 2. Download Selected Patients
            if (request.includePatients) {
                val remoteItems = userDoc.collection("patients").get().await()
                    .toObjects(PatientRegistration::class.java)
                    .filter { it.id != 0L && (request.selectedPatientIds == null || it.id in request.selectedPatientIds) }
                if (request.replaceExistingOnTarget) {
                    patientDao.deleteAll()
                }
                if (remoteItems.isNotEmpty()) {
                    patientDao.insertAll(remoteItems)
                    downloadedCount += remoteItems.size
                }
            }

            // 3. Download Selected Transactions
            if (request.includeTransactions) {
                val remoteItems = userDoc.collection("transactions").get().await()
                    .toObjects(StockTransaction::class.java)
                    .filter { it.id != 0L && (request.selectedTransactionIds == null || it.id in request.selectedTransactionIds) }
                if (request.replaceExistingOnTarget) {
                    transactionDao.deleteAll()
                }
                if (remoteItems.isNotEmpty()) {
                    transactionDao.insertAll(remoteItems)
                    downloadedCount += remoteItems.size
                }
            }

            // 4. Download Selected Vendors
            if (request.includeVendors) {
                val remoteItems = userDoc.collection("vendors").get().await()
                    .toObjects(Vendor::class.java)
                    .filter { it.id != 0L && (request.selectedVendorIds == null || it.id in request.selectedVendorIds) }
                if (request.replaceExistingOnTarget) {
                    vendorDao.deleteAll()
                }
                if (remoteItems.isNotEmpty()) {
                    vendorDao.insertAll(remoteItems)
                    downloadedCount += remoteItems.size
                }
            }

            // 5. Download Clinic Profile
            if (request.includeClinicProfile) {
                val profileDoc = userDoc.collection("clinicProfile").document("profile").get().await()
                if (profileDoc.exists()) {
                    profileDoc.toObject(ClinicProfile::class.java)?.let { remoteProfile ->
                        clinicProfileDao.insertOrUpdate(remoteProfile)
                        downloadedCount++
                    }
                }
            }

            // Update local auto-backup so downloaded records are safely persisted across app updates
            com.example.data.database.AppDatabase.getInstance(context).let { database ->
                com.example.data.util.LocalBackupManager.markUserIntentionallyClearedData(context, false)
                com.example.data.util.LocalBackupManager.saveAutoBackup(context, database)
            }

            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_timestamp", now).apply()
            _syncState.value = SyncStatusState(
                isSyncing = false,
                lastSyncTimeMillis = now,
                statusMessage = "Downloaded $downloadedCount selected items from Cloud",
                itemsSyncedCount = downloadedCount,
                lastError = null
            )

            Result.success(downloadedCount)
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
