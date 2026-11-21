package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.StockTransaction
import com.example.data.model.TransactionType
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.data.repository.CloudDataSnapshot
import com.example.data.repository.CloudSyncConfig
import com.example.data.repository.CloudSyncRepository
import com.example.data.repository.PurchaseLineItem
import com.example.data.repository.SaleLineItem
import com.example.data.repository.ScannedBillStockItem
import com.example.data.repository.SelectiveCloudTransferRequest
import com.example.data.repository.SyncStatusState
import com.example.data.repository.VaccineStockRepository
import com.example.data.util.LocalBackupManager
import com.example.ui.auth.AuthManager
import com.example.ui.auth.UserSessionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class ProfitStats(
    val profit: Double = 0.0,
    val revenue: Double = 0.0,
    val costOfGoodsSold: Double = 0.0,
    val dosesSold: Int = 0,
    val transactionsCount: Int = 0
)

data class DashboardSummary(
    val daily: ProfitStats = ProfitStats(),
    val weekly: ProfitStats = ProfitStats(),
    val monthly: ProfitStats = ProfitStats(),
    val totalStockUnits: Int = 0,
    val totalStockValuation: Double = 0.0, // Total purchase cost of stock on hand
    val totalRetailValuation: Double = 0.0, // Potential revenue at selling price
    val potentialProfit: Double = 0.0,      // Retail valuation - Stock valuation
    val totalVaccinesCount: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0
)

sealed class UiEvent {
    data class ShowMessage(val message: String) : UiEvent()
    data class ShowError(val error: String) : UiEvent()
}

class VaccineStockViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase
    private val repository: VaccineStockRepository
    val cloudSyncRepository: CloudSyncRepository
    val authManager: AuthManager

    init {
        database = AppDatabase.getDatabase(application, viewModelScope)
        repository = VaccineStockRepository(
            vaccineDao = database.vaccineDao(),
            vendorDao = database.vendorDao(),
            transactionDao = database.transactionDao(),
            patientDao = database.patientRegistrationDao(),
            profileDao = database.clinicProfileDao()
        )
        cloudSyncRepository = CloudSyncRepository(
            context = application,
            vaccineDao = database.vaccineDao(),
            vendorDao = database.vendorDao(),
            transactionDao = database.transactionDao(),
            patientDao = database.patientRegistrationDao(),
            clinicProfileDao = database.clinicProfileDao()
        )
        authManager = AuthManager(application)

        // Restore any missing data from persistent backup on startup and keep auto-backup updated
        viewModelScope.launch {
            LocalBackupManager.restoreFromAutoBackupIfNeeded(application, database)
            combine(
                repository.allVaccines,
                repository.allPatients,
                repository.allTransactions,
                repository.allVendors,
                repository.clinicProfile
            ) { v, p, t, ven, prof ->
                v.size + p.size + t.size + ven.size + (if (prof != null) 1 else 0)
            }.collectLatest { totalCount ->
                if (totalCount > 0) {
                    LocalBackupManager.markUserIntentionallyClearedData(application, false)
                    LocalBackupManager.saveAutoBackup(application, database)
                }
            }
        }

        // Silent auto sign-in and safe non-destructive merge sync on app startup
        authManager.attemptSilentAutoSignIn(
            scope = viewModelScope,
            onSuccess = {
                val user = authManager.sessionState.value.firebaseUser
                if (user != null) {
                    viewModelScope.launch {
                        // Safe merge: preserves both old cloud data and local data without deleting anything
                        cloudSyncRepository.syncAll(user.uid, purgeStaleCloudRecords = false)
                    }
                }
            }
        )
    }

    val syncState: StateFlow<SyncStatusState> = cloudSyncRepository.syncState
    val sessionState: StateFlow<UserSessionState> = authManager.sessionState
    val cloudSnapshot: StateFlow<CloudDataSnapshot?> = cloudSyncRepository.cloudSnapshot
    val isLoadingCloudPreview: StateFlow<Boolean> = cloudSyncRepository.isLoadingCloudPreview

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    // Vaccine search & filter
    private val _vaccineSearchQuery = MutableStateFlow("")
    val vaccineSearchQuery: StateFlow<String> = _vaccineSearchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("All")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    // Patient search & filter
    private val _patientSearchQuery = MutableStateFlow("")
    val patientSearchQuery: StateFlow<String> = _patientSearchQuery.asStateFlow()

    private val _patientFilter = MutableStateFlow("All") // All, Upcoming, Overdue
    val patientFilter: StateFlow<String> = _patientFilter.asStateFlow()

    // Observable data streams
    val allVaccines: StateFlow<List<Vaccine>> = repository.allVaccines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockVaccines: StateFlow<List<Vaccine>> = repository.lowStockVaccines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVendors: StateFlow<List<Vendor>> = repository.allVendors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<StockTransaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPurchases: StateFlow<List<StockTransaction>> = repository.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSales: StateFlow<List<StockTransaction>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPatients: StateFlow<List<PatientRegistration>> = repository.allPatients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clinicProfile: StateFlow<ClinicProfile> = repository.clinicProfile
        .map { it ?: ClinicProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ClinicProfile())

    // Dashboard metrics derived reactively from vaccines and transactions
    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        allVaccines,
        allTransactions
    ) { vaccines, transactions ->
        calculateDashboardMetrics(vaccines, transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    fun updateVaccineSearch(query: String) {
        _vaccineSearchQuery.value = query
    }

    fun updateCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    // --- Actions ---

    fun recordPurchase(
        vaccineId: Long,
        vendorId: Long?,
        quantity: Int,
        unitPurchasePrice: Double,
        batchNumber: String,
        expiryDate: String,
        invoiceNumber: String,
        notes: String
    ) {
        viewModelScope.launch {
            val result = repository.recordPurchase(
                vaccineId = vaccineId,
                vendorId = vendorId,
                quantity = quantity,
                unitPurchasePrice = unitPurchasePrice,
                batchNumber = batchNumber,
                expiryDate = expiryDate,
                invoiceNumber = invoiceNumber,
                notes = notes
            )
            result.onSuccess { tx ->
                _eventFlow.emit(UiEvent.ShowMessage("Added ${tx.quantity} units to stock! Current stock updated."))
            }.onFailure { ex ->
                _eventFlow.emit(UiEvent.ShowError(ex.message ?: "Failed to record purchase"))
            }
        }
    }

    fun recordMultiPurchase(
        vendorId: Long?,
        items: List<PurchaseLineItem>,
        invoiceNumber: String,
        notes: String
    ) {
        viewModelScope.launch {
            val result = repository.recordMultiPurchase(
                vendorId = vendorId,
                items = items,
                invoiceNumber = invoiceNumber,
                notes = notes
            )
            result.onSuccess { txList ->
                val totalDoses = txList.sumOf { it.quantity }
                val totalCost = txList.sumOf { it.totalAmount }
                _eventFlow.emit(
                    UiEvent.ShowMessage(
                        "Restocked ${txList.size} vaccines ($totalDoses doses total)! Total Cost: ₹${String.format("%.2f", totalCost)}"
                    )
                )
            }.onFailure { ex ->
                _eventFlow.emit(UiEvent.ShowError(ex.message ?: "Failed to record purchase"))
            }
        }
    }

    fun applyScannedBillStockUpdate(
        vendorId: Long?,
        newVendorName: String,
        newVendorPhone: String,
        invoiceNumber: String,
        notes: String,
        items: List<ScannedBillStockItem>
    ) {
        viewModelScope.launch {
            val result = repository.applyScannedBillStockUpdate(
                vendorId = vendorId,
                newVendorName = newVendorName,
                newVendorPhone = newVendorPhone,
                invoiceNumber = invoiceNumber,
                notes = notes,
                items = items
            )
            result.onSuccess { (txList, newVaccinesCount) ->
                LocalBackupManager.saveAutoBackup(getApplication(), database)
                val user = authManager.sessionState.value.firebaseUser
                val mobile = authManager.sessionState.value.mobileNumber
                val targetUid = user?.uid ?: mobile
                if (targetUid != null) {
                    launch {
                        cloudSyncRepository.syncAll(targetUid, purgeStaleCloudRecords = false)
                    }
                }
                val totalDoses = txList.sumOf { it.quantity }
                val totalCost = txList.sumOf { it.totalAmount }
                val newVacSuffix = if (newVaccinesCount > 0) " ($newVaccinesCount new vaccine added to catalog)" else ""
                _eventFlow.emit(
                    UiEvent.ShowMessage(
                        "Bill stock updated: +$totalDoses doses across ${txList.size} vaccines$newVacSuffix! Total: ₹${String.format("%.2f", totalCost)}"
                    )
                )
            }.onFailure { ex ->
                _eventFlow.emit(UiEvent.ShowError(ex.message ?: "Failed to apply bill stock update"))
            }
        }
    }

    fun recordSale(
        vaccineId: Long,
        quantity: Int,
        unitSellingPrice: Double,
        customerOrPatient: String,
        batchNumber: String,
        notes: String
    ) {
        viewModelScope.launch {
            val result = repository.recordSale(
                vaccineId = vaccineId,
                quantity = quantity,
                unitSellingPrice = unitSellingPrice,
                customerOrPatient = customerOrPatient,
                batchNumber = batchNumber,
                notes = notes
            )
            result.onSuccess { tx ->
                _eventFlow.emit(
                    UiEvent.ShowMessage(
                        "Sold ${tx.quantity} units! Stock reduced. Profit: ₹${String.format("%.2f", tx.profit)}"
                    )
                )
            }.onFailure { ex ->
                _eventFlow.emit(UiEvent.ShowError(ex.message ?: "Failed to record sale"))
            }
        }
    }

    fun recordMultiSale(
        items: List<SaleLineItem>,
        customerOrPatient: String,
        notes: String
    ) {
        viewModelScope.launch {
            val result = repository.recordMultiSale(
                items = items,
                customerOrPatient = customerOrPatient,
                notes = notes
            )
            result.onSuccess { txList ->
                val totalDoses = txList.sumOf { it.quantity }
                val totalProfit = txList.sumOf { it.profit }
                val totalRevenue = txList.sumOf { it.totalAmount }
                _eventFlow.emit(
                    UiEvent.ShowMessage(
                        "Dispensed ${txList.size} vaccines ($totalDoses doses)! Revenue: ₹${String.format("%.2f", totalRevenue)} | Total Profit: +₹${String.format("%.2f", totalProfit)}"
                    )
                )
            }.onFailure { ex ->
                _eventFlow.emit(UiEvent.ShowError(ex.message ?: "Failed to record sale"))
            }
        }
    }

    fun adjustStock(
        vaccineId: Long,
        isAddition: Boolean,
        quantity: Int,
        reason: String
    ) {
        viewModelScope.launch {
            val result = repository.recordStockAdjustment(
                vaccineId = vaccineId,
                isAddition = isAddition,
                quantity = quantity,
                reason = reason
            )
            result.onSuccess {
                val action = if (isAddition) "added to" else "reduced from"
                _eventFlow.emit(UiEvent.ShowMessage("Stock successfully $action inventory."))
            }.onFailure { ex ->
                _eventFlow.emit(UiEvent.ShowError(ex.message ?: "Failed to adjust stock"))
            }
        }
    }

    fun saveVaccine(vaccine: Vaccine) {
        viewModelScope.launch {
            try {
                if (vaccine.id == 0L) {
                    repository.insertVaccine(vaccine)
                    _eventFlow.emit(UiEvent.ShowMessage("Vaccine '${vaccine.name}' created"))
                } else {
                    repository.updateVaccine(vaccine)
                    _eventFlow.emit(UiEvent.ShowMessage("Vaccine '${vaccine.name}' updated"))
                }
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to save vaccine"))
            }
        }
    }

    fun deleteVaccine(vaccine: Vaccine) {
        viewModelScope.launch {
            try {
                repository.deleteVaccine(vaccine)
                val user = authManager.sessionState.value.firebaseUser
                val mobile = authManager.sessionState.value.mobileNumber
                val targetUid = user?.uid ?: mobile
                if (targetUid != null) {
                    cloudSyncRepository.deleteVaccineFromCloud(vaccine.id, targetUid)
                }
                _eventFlow.emit(UiEvent.ShowMessage("Vaccine '${vaccine.name}' deleted completely"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to delete vaccine"))
            }
        }
    }

    fun deleteOutOfStockVaccines() {
        viewModelScope.launch {
            try {
                val outOfStockList = allVaccines.value.filter { it.currentStock <= 0 }
                if (outOfStockList.isEmpty()) {
                    _eventFlow.emit(UiEvent.ShowMessage("No out-of-stock vaccines to delete"))
                    return@launch
                }
                repository.deleteOutOfStockVaccines()
                val user = authManager.sessionState.value.firebaseUser
                val mobile = authManager.sessionState.value.mobileNumber
                val targetUid = user?.uid ?: mobile
                if (targetUid != null) {
                    for (vac in outOfStockList) {
                        cloudSyncRepository.deleteVaccineFromCloud(vac.id, targetUid)
                    }
                }
                _eventFlow.emit(UiEvent.ShowMessage("Deleted ${outOfStockList.size} out-of-stock vaccines completely"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to delete out-of-stock vaccines"))
            }
        }
    }

    fun deleteAllVaccines() {
        viewModelScope.launch {
            try {
                val count = allVaccines.value.size
                repository.deleteAllVaccines()
                val user = authManager.sessionState.value.firebaseUser
                val mobile = authManager.sessionState.value.mobileNumber
                val targetUid = user?.uid ?: mobile
                if (targetUid != null) {
                    cloudSyncRepository.purgeStaleCloudData(targetUid)
                }
                _eventFlow.emit(UiEvent.ShowMessage("Cleared all $count vaccines from inventory and cloud"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to clear vaccines"))
            }
        }
    }

    fun purgeStaleCloudData() {
        viewModelScope.launch {
            val user = authManager.sessionState.value.firebaseUser
            val mobile = authManager.sessionState.value.mobileNumber
            val targetUid = user?.uid ?: mobile ?: run {
                _eventFlow.emit(UiEvent.ShowError("Please login with Gmail or Mobile to purge cloud data"))
                return@launch
            }
            _eventFlow.emit(UiEvent.ShowMessage("Purging stale cloud records..."))
            val result = cloudSyncRepository.purgeStaleCloudData(targetUid)
            if (result.isSuccess) {
                _eventFlow.emit(UiEvent.ShowMessage("Purged ${result.getOrNull()} old records from cloud storage!"))
            } else {
                _eventFlow.emit(UiEvent.ShowError("Failed to purge cloud records: ${result.exceptionOrNull()?.localizedMessage}"))
            }
        }
    }

    fun eraseAllCloudData() {
        viewModelScope.launch {
            val user = authManager.sessionState.value.firebaseUser
            val mobile = authManager.sessionState.value.mobileNumber
            val targetUid = user?.uid ?: mobile ?: run {
                _eventFlow.emit(UiEvent.ShowError("Please login with Gmail or Mobile to erase cloud data"))
                return@launch
            }
            _eventFlow.emit(UiEvent.ShowMessage("Erasing all cloud data..."))
            val result = cloudSyncRepository.eraseAllCloudData(targetUid)
            if (result.isSuccess) {
                _eventFlow.emit(UiEvent.ShowMessage("All cloud data completely erased (${result.getOrNull()} items)!"))
            } else {
                _eventFlow.emit(UiEvent.ShowError("Failed to erase cloud data: ${result.exceptionOrNull()?.localizedMessage}"))
            }
        }
    }

    fun eraseAllData(eraseCloud: Boolean = true, eraseLocal: Boolean = true) {
        viewModelScope.launch {
            val user = authManager.sessionState.value.firebaseUser
            val mobile = authManager.sessionState.value.mobileNumber
            val targetUid = user?.uid ?: mobile
            _eventFlow.emit(UiEvent.ShowMessage("Erasing all old data..."))
            val result = cloudSyncRepository.eraseAllData(targetUid, eraseCloud = eraseCloud, eraseLocal = eraseLocal)
            if (result.isSuccess) {
                _eventFlow.emit(UiEvent.ShowMessage("All old data erased completely. Pristine clean slate!"))
            } else {
                _eventFlow.emit(UiEvent.ShowError("Error erasing data: ${result.exceptionOrNull()?.localizedMessage}"))
            }
        }
    }

    fun saveVendor(vendor: Vendor) {
        viewModelScope.launch {
            try {
                if (vendor.id == 0L) {
                    repository.insertVendor(vendor)
                    _eventFlow.emit(UiEvent.ShowMessage("Vendor '${vendor.name}' added"))
                } else {
                    repository.updateVendor(vendor)
                    _eventFlow.emit(UiEvent.ShowMessage("Vendor '${vendor.name}' updated"))
                }
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to save vendor"))
            }
        }
    }

    fun deleteVendor(vendor: Vendor) {
        viewModelScope.launch {
            try {
                repository.deleteVendor(vendor)
                val user = authManager.sessionState.value.firebaseUser
                val mobile = authManager.sessionState.value.mobileNumber
                val targetUid = user?.uid ?: mobile
                if (targetUid != null) {
                    cloudSyncRepository.deleteVendorFromCloud(vendor.id, targetUid)
                }
                _eventFlow.emit(UiEvent.ShowMessage("Vendor removed"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to delete vendor"))
            }
        }
    }

    // Patient & Clinic Profile actions
    fun updatePatientSearch(query: String) {
        _patientSearchQuery.value = query
    }

    fun updatePatientFilter(filter: String) {
        _patientFilter.value = filter
    }

    fun savePatient(
        patient: PatientRegistration,
        alsoRecordSale: Boolean = false,
        vaccineIdToDeduct: Long? = null,
        sellingPrice: Double = 0.0,
        multipleVaccineIdsToDeduct: List<Long> = emptyList()
    ) {
        viewModelScope.launch {
            try {
                if (alsoRecordSale) {
                    val idsToDeduct = if (multipleVaccineIdsToDeduct.isNotEmpty()) {
                        multipleVaccineIdsToDeduct
                    } else if (vaccineIdToDeduct != null && vaccineIdToDeduct > 0L) {
                        listOf(vaccineIdToDeduct)
                    } else {
                        emptyList()
                    }

                    if (idsToDeduct.isNotEmpty()) {
                        val currentVaccines = allVaccines.value
                        val saleItems = idsToDeduct.mapNotNull { vacId ->
                            val vac = currentVaccines.find { it.id == vacId }
                            if (vac != null) {
                                SaleLineItem(
                                    vaccineId = vac.id,
                                    quantity = 1,
                                    unitSellingPrice = vac.sellingPrice,
                                    batchNumber = patient.vaccineGivenBatch.ifBlank { vac.code }
                                )
                            } else null
                        }

                        if (saleItems.isNotEmpty()) {
                            val result = repository.recordMultiSale(
                                customerOrPatient = "${patient.patientName} (${patient.parentName})",
                                notes = "Administered: ${patient.vaccineGivenToday} (${patient.vaccineGivenDose})",
                                items = saleItems
                            )
                            result.onFailure {
                                _eventFlow.emit(UiEvent.ShowError("Could not deduct stock: ${it.message}"))
                            }
                        }
                    }
                }

                if (patient.id == 0L) {
                    repository.insertPatient(patient)
                    _eventFlow.emit(UiEvent.ShowMessage("Patient '${patient.patientName}' registered successfully"))
                } else {
                    repository.updatePatient(patient)
                    _eventFlow.emit(UiEvent.ShowMessage("Patient record updated"))
                }
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to save patient"))
            }
        }
    }

    fun deletePatient(patient: PatientRegistration) {
        viewModelScope.launch {
            try {
                repository.deletePatient(patient)
                val user = authManager.sessionState.value.firebaseUser
                val mobile = authManager.sessionState.value.mobileNumber
                val targetUid = user?.uid ?: mobile
                if (targetUid != null) {
                    cloudSyncRepository.deletePatientFromCloud(patient.id, targetUid)
                }
                _eventFlow.emit(UiEvent.ShowMessage("Patient '${patient.patientName}' deleted completely from device & online cloud"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to remove patient"))
            }
        }
    }

    fun deleteCompletedOrRemindedPatients() {
        viewModelScope.launch {
            try {
                val remindedList = repository.getRemindedPatients()
                if (remindedList.isEmpty()) {
                    _eventFlow.emit(UiEvent.ShowMessage("No reminded patient records found to delete"))
                    return@launch
                }
                repository.deleteRemindedPatients()
                val user = authManager.sessionState.value.firebaseUser
                val mobile = authManager.sessionState.value.mobileNumber
                val targetUid = user?.uid ?: mobile
                if (targetUid != null) {
                    for (p in remindedList) {
                        cloudSyncRepository.deletePatientFromCloud(p.id, targetUid)
                    }
                }
                _eventFlow.emit(UiEvent.ShowMessage("Deleted ${remindedList.size} completed/reminded patient records from device & cloud"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to delete reminded patients"))
            }
        }
    }

    fun deleteOverdueOldPatients() {
        viewModelScope.launch {
            try {
                val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
                val oldList = repository.getOverduePatientsOlderThan(ninetyDaysAgo)
                if (oldList.isEmpty()) {
                    _eventFlow.emit(UiEvent.ShowMessage("No inactive overdue patient records older than 90 days found"))
                    return@launch
                }
                repository.deleteOverduePatientsOlderThan(ninetyDaysAgo)
                val user = authManager.sessionState.value.firebaseUser
                val mobile = authManager.sessionState.value.mobileNumber
                val targetUid = user?.uid ?: mobile
                if (targetUid != null) {
                    for (p in oldList) {
                        cloudSyncRepository.deletePatientFromCloud(p.id, targetUid)
                    }
                }
                _eventFlow.emit(UiEvent.ShowMessage("Deleted ${oldList.size} inactive patient records from device & cloud"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to delete old overdue patients"))
            }
        }
    }

    fun deleteAllPatients(eraseCloud: Boolean = true) {
        viewModelScope.launch {
            try {
                val count = allPatients.value.size
                repository.deleteAllPatients()
                val user = authManager.sessionState.value.firebaseUser
                val mobile = authManager.sessionState.value.mobileNumber
                val targetUid = user?.uid ?: mobile
                if (eraseCloud && targetUid != null) {
                    cloudSyncRepository.eraseAllCloudPatients(targetUid)
                }
                _eventFlow.emit(UiEvent.ShowMessage("Cleared all $count patient records completely from device & online cloud"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to clear all patients"))
            }
        }
    }

    fun purgeStaleCloudPatients() {
        viewModelScope.launch {
            val user = authManager.sessionState.value.firebaseUser
            val mobile = authManager.sessionState.value.mobileNumber
            val targetUid = user?.uid ?: mobile ?: run {
                _eventFlow.emit(UiEvent.ShowError("Please login to purge cloud patient records"))
                return@launch
            }
            _eventFlow.emit(UiEvent.ShowMessage("Purging stale cloud patient records..."))
            val result = cloudSyncRepository.purgeStaleCloudPatients(targetUid)
            if (result.isSuccess) {
                _eventFlow.emit(UiEvent.ShowMessage("Purged ${result.getOrNull()} old patient records from cloud storage. Sync is clean!"))
            } else {
                _eventFlow.emit(UiEvent.ShowError("Failed to purge cloud patient records: ${result.exceptionOrNull()?.localizedMessage}"))
            }
        }
    }

    fun markPatientReminderSent(patientId: Long) {
        viewModelScope.launch {
            try {
                repository.markPatientReminderSent(patientId)
                _eventFlow.emit(UiEvent.ShowMessage("WhatsApp reminder sent & logged!"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to update reminder status"))
            }
        }
    }

    fun updatePatientReminderDate(patientId: Long, nextDueDate: String, nextDueDateMillis: Long) {
        viewModelScope.launch {
            try {
                repository.updatePatientReminderDate(patientId, nextDueDate, nextDueDateMillis)
                _eventFlow.emit(UiEvent.ShowMessage("Next reminder set for $nextDueDate"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to update reminder date"))
            }
        }
    }

    fun updatePatientAge(patientId: Long, dobOrAge: String) {
        viewModelScope.launch {
            try {
                repository.updatePatientAge(patientId, dobOrAge)
                _eventFlow.emit(UiEvent.ShowMessage("Patient age updated to $dobOrAge"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to update age"))
            }
        }
    }

    fun saveClinicProfile(profile: ClinicProfile) {
        viewModelScope.launch {
            try {
                repository.updateClinicProfile(profile)
                _eventFlow.emit(UiEvent.ShowMessage("Clinic Profile saved"))
            } catch (e: Exception) {
                _eventFlow.emit(UiEvent.ShowError(e.message ?: "Failed to save profile"))
            }
        }
    }

    private fun calculateDashboardMetrics(
        vaccines: List<Vaccine>,
        transactions: List<StockTransaction>
    ): DashboardSummary {
        val now = System.currentTimeMillis()

        // Midnight today
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 7 days ago or start of current week
        val startOfWeek = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis.coerceAtMost(now - (7 * 24 * 60 * 60 * 1000L))

        // 1st of month or 30 days ago
        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis.coerceAtMost(now - (30 * 24 * 60 * 60 * 1000L))

        val sales = transactions.filter { it.type == TransactionType.SALE }

        fun aggregateStats(filterSince: Long): ProfitStats {
            val filtered = sales.filter { it.timestamp >= filterSince }
            val revenue = filtered.sumOf { it.totalAmount }
            val profit = filtered.sumOf { it.profit }
            val cost = filtered.sumOf { it.costPricePerUnit * it.quantity }
            val doses = filtered.sumOf { it.quantity }
            return ProfitStats(
                profit = profit,
                revenue = revenue,
                costOfGoodsSold = cost,
                dosesSold = doses,
                transactionsCount = filtered.size
            )
        }

        val daily = aggregateStats(startOfDay)
        val weekly = aggregateStats(startOfWeek)
        val monthly = aggregateStats(startOfMonth)

        val totalUnits = vaccines.sumOf { it.currentStock }
        val stockValuation = vaccines.sumOf { it.currentStock * it.purchasePrice }
        val retailValuation = vaccines.sumOf { it.currentStock * it.sellingPrice }
        val potentialProfit = retailValuation - stockValuation

        val lowStock = vaccines.count { it.currentStock in 1..it.minStockAlert }
        val outOfStock = vaccines.count { it.currentStock <= 0 }

        return DashboardSummary(
            daily = daily,
            weekly = weekly,
            monthly = monthly,
            totalStockUnits = totalUnits,
            totalStockValuation = stockValuation,
            totalRetailValuation = retailValuation,
            potentialProfit = potentialProfit,
            totalVaccinesCount = vaccines.size,
            lowStockCount = lowStock,
            outOfStockCount = outOfStock
        )
    }

    fun syncDataNow() {
        syncDataWithConfig(CloudSyncConfig())
    }

    fun syncDataWithConfig(config: CloudSyncConfig) {
        viewModelScope.launch {
            val user = authManager.sessionState.value.firebaseUser
            val mobile = authManager.sessionState.value.mobileNumber
            val targetUid = user?.uid ?: mobile ?: run {
                _eventFlow.emit(UiEvent.ShowError("Please login with Gmail or Mobile Number to sync across devices"))
                return@launch
            }

            _eventFlow.emit(UiEvent.ShowMessage("Synchronizing selected data with Cloud..."))
            val result = cloudSyncRepository.syncWithConfig(targetUid, config)
            if (result.isSuccess) {
                com.example.data.util.LocalBackupManager.saveAutoBackup(getApplication(), database)
                _eventFlow.emit(UiEvent.ShowMessage("Sync complete! ${result.getOrNull()} records synchronized with your preserve/delete choices."))
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Sync failed"
                _eventFlow.emit(UiEvent.ShowError("Sync issue: $err"))
            }
        }
    }

    suspend fun runPreUpdateSafetyBackupAndSync() {
        try {
            LocalBackupManager.saveAutoBackup(getApplication(), database)
            val user = authManager.sessionState.value.firebaseUser
            val mobile = authManager.sessionState.value.mobileNumber
            val targetUid = user?.uid ?: mobile
            if (!targetUid.isNullOrBlank()) {
                cloudSyncRepository.syncAll(targetUid, purgeStaleCloudRecords = false)
            }
        } catch (_: Exception) {
        }
    }

    fun fetchCloudPreview() {
        viewModelScope.launch {
            val user = authManager.sessionState.value.firebaseUser
            val mobile = authManager.sessionState.value.mobileNumber
            val targetUid = user?.uid ?: mobile ?: run {
                _eventFlow.emit(UiEvent.ShowError("Please login first to view Cloud records"))
                return@launch
            }
            val result = cloudSyncRepository.fetchCloudSnapshot(targetUid)
            if (result.isFailure) {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to load Cloud preview"
                _eventFlow.emit(UiEvent.ShowError(err))
            }
        }
    }

    fun uploadSelectedToCloud(request: SelectiveCloudTransferRequest) {
        viewModelScope.launch {
            val user = authManager.sessionState.value.firebaseUser
            val mobile = authManager.sessionState.value.mobileNumber
            val targetUid = user?.uid ?: mobile ?: run {
                _eventFlow.emit(UiEvent.ShowError("Please login with Gmail or Mobile Number to upload to Cloud"))
                return@launch
            }

            _eventFlow.emit(UiEvent.ShowMessage("Uploading selected records to Cloud..."))
            val result = cloudSyncRepository.uploadSelectedToCloud(targetUid, request)
            if (result.isSuccess) {
                _eventFlow.emit(UiEvent.ShowMessage("Uploaded ${result.getOrNull()} selected records to Cloud!"))
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Upload failed"
                _eventFlow.emit(UiEvent.ShowError("Upload issue: $err"))
            }
        }
    }

    fun downloadSelectedFromCloud(request: SelectiveCloudTransferRequest) {
        viewModelScope.launch {
            val user = authManager.sessionState.value.firebaseUser
            val mobile = authManager.sessionState.value.mobileNumber
            val targetUid = user?.uid ?: mobile ?: run {
                _eventFlow.emit(UiEvent.ShowError("Please login with Gmail or Mobile Number to download from Cloud"))
                return@launch
            }

            _eventFlow.emit(UiEvent.ShowMessage("Downloading selected records from Cloud..."))
            val result = cloudSyncRepository.downloadSelectedFromCloud(targetUid, request)
            if (result.isSuccess) {
                _eventFlow.emit(UiEvent.ShowMessage("Downloaded ${result.getOrNull()} selected records from Cloud!"))
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Download failed"
                _eventFlow.emit(UiEvent.ShowError("Download issue: $err"))
            }
        }
    }

    fun signOutUser() {
        authManager.signOut(viewModelScope) {
            viewModelScope.launch {
                _eventFlow.emit(UiEvent.ShowMessage("Signed out. Device will use local offline storage."))
            }
        }
    }
}
