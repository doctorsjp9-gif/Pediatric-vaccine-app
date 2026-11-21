package com.example.data.repository

import com.example.data.dao.ClinicProfileDao
import com.example.data.dao.PatientRegistrationDao
import com.example.data.dao.StockTransactionDao
import com.example.data.dao.VaccineDao
import com.example.data.dao.VendorDao
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import com.example.data.model.StockTransaction
import com.example.data.model.TransactionType
import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

data class PurchaseLineItem(
    val vaccineId: Long,
    val quantity: Int,
    val unitPurchasePrice: Double,
    val batchNumber: String = "",
    val expiryDate: String = ""
)

data class SaleLineItem(
    val vaccineId: Long,
    val quantity: Int,
    val unitSellingPrice: Double,
    val batchNumber: String = ""
)

data class ScannedBillStockItem(
    val matchedVaccineId: Long?,
    val vaccineName: String,
    val category: String = "Routine Pediatric",
    val code: String = "",
    val quantity: Int,
    val unitPurchasePrice: Double,
    val sellingPrice: Double = 0.0,
    val batchNumber: String = "",
    val expiryDate: String = ""
)

class VaccineStockRepository(
    private val vaccineDao: VaccineDao,
    private val vendorDao: VendorDao,
    private val transactionDao: StockTransactionDao,
    private val patientDao: PatientRegistrationDao,
    private val profileDao: ClinicProfileDao
) {
    // Flow streams for UI
    val allVaccines: Flow<List<Vaccine>> = vaccineDao.getAllVaccines()
    val lowStockVaccines: Flow<List<Vaccine>> = vaccineDao.getLowStockVaccines()
    val allVendors: Flow<List<Vendor>> = vendorDao.getAllVendors()
    val allTransactions: Flow<List<StockTransaction>> = transactionDao.getAllTransactions()
    val allSales: Flow<List<StockTransaction>> = transactionDao.getAllSales()
    val allPurchases: Flow<List<StockTransaction>> = transactionDao.getAllPurchases()
    val allPatients: Flow<List<PatientRegistration>> = patientDao.getAllPatients()
    val clinicProfile: Flow<ClinicProfile?> = profileDao.getProfile()

    suspend fun getClinicProfileSync(): ClinicProfile? = profileDao.getProfileSync()
    suspend fun updateClinicProfile(profile: ClinicProfile) = profileDao.insertOrUpdate(profile)

    // Patient operations
    suspend fun insertPatient(patient: PatientRegistration): Long = patientDao.insert(patient)
    suspend fun updatePatient(patient: PatientRegistration) = patientDao.update(patient)
    suspend fun deletePatient(patient: PatientRegistration) = patientDao.delete(patient)
    suspend fun markPatientReminderSent(patientId: Long, sentMillis: Long = System.currentTimeMillis()) =
        patientDao.markReminderSent(patientId, sentMillis)
    suspend fun updatePatientReminderDate(patientId: Long, nextDueDate: String, nextDueDateMillis: Long) =
        patientDao.updateReminderDate(patientId, nextDueDate, nextDueDateMillis)
    suspend fun updatePatientAge(patientId: Long, dobOrAge: String) =
        patientDao.updatePatientAge(patientId, dobOrAge)

    suspend fun getVaccineById(id: Long): Vaccine? = vaccineDao.getVaccineById(id)
    suspend fun getVendorById(id: Long): Vendor? = vendorDao.getVendorById(id)

    // Vaccine operations
    suspend fun insertVaccine(vaccine: Vaccine): Long = vaccineDao.insertVaccine(vaccine)
    suspend fun updateVaccine(vaccine: Vaccine) = vaccineDao.updateVaccine(vaccine)
    suspend fun deleteVaccine(vaccine: Vaccine) = vaccineDao.deleteVaccine(vaccine)
    suspend fun deleteAllVaccines() = vaccineDao.deleteAll()
    suspend fun deleteOutOfStockVaccines() = vaccineDao.deleteOutOfStockVaccines()

    // Patient operations
    suspend fun deleteAllPatients() = patientDao.deleteAll()
    suspend fun deleteRemindedPatients() = patientDao.deleteRemindedPatients()
    suspend fun deleteOverduePatientsOlderThan(beforeMillis: Long) = patientDao.deleteOverduePatientsOlderThan(beforeMillis)
    suspend fun getRemindedPatients() = patientDao.getRemindedPatients()
    suspend fun getOverduePatientsOlderThan(beforeMillis: Long) = patientDao.getOverduePatientsOlderThan(beforeMillis)

    // Transaction & History operations
    suspend fun deleteAllTransactions() = transactionDao.deleteAll()
    suspend fun deleteOldTransactions(beforeMillis: Long) = transactionDao.deleteOldTransactions(beforeMillis)

    // Vendor operations
    suspend fun insertVendor(vendor: Vendor): Long = vendorDao.insertVendor(vendor)
    suspend fun updateVendor(vendor: Vendor) = vendorDao.updateVendor(vendor)
    suspend fun deleteVendor(vendor: Vendor) = vendorDao.deleteVendor(vendor)
    suspend fun deleteAllVendors() = vendorDao.deleteAll()

    // Wipe all local database tables
    suspend fun eraseAllLocalData() {
        vaccineDao.deleteAll()
        patientDao.deleteAll()
        transactionDao.deleteAll()
        vendorDao.deleteAll()
    }

    /**
     * Record a Purchase order for single vaccine:
     * - Stock updates automatically by adding the purchased quantity!
     * - Records the purchase transaction with vendor and batch info.
     */
    suspend fun recordPurchase(
        vaccineId: Long,
        vendorId: Long?,
        quantity: Int,
        unitPurchasePrice: Double,
        batchNumber: String,
        expiryDate: String,
        invoiceNumber: String,
        notes: String
    ): Result<StockTransaction> {
        return recordMultiPurchase(
            vendorId = vendorId,
            items = listOf(
                PurchaseLineItem(
                    vaccineId = vaccineId,
                    quantity = quantity,
                    unitPurchasePrice = unitPurchasePrice,
                    batchNumber = batchNumber,
                    expiryDate = expiryDate
                )
            ),
            invoiceNumber = invoiceNumber,
            notes = notes
        ).map { it.first() }
    }

    /**
     * Record a Multiple Vaccine Purchase Order from a Vendor:
     * - Adds multiple vaccines in a single supplier invoice
     * - Automatically increments stock for all items
     * - Updates latest cost basis for each vaccine
     */
    suspend fun recordMultiPurchase(
        vendorId: Long?,
        items: List<PurchaseLineItem>,
        invoiceNumber: String,
        notes: String
    ): Result<List<StockTransaction>> {
        if (items.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please add at least one vaccine to the order"))
        }

        for (item in items) {
            if (item.quantity <= 0) {
                return Result.failure(IllegalArgumentException("Quantity must be greater than 0 for all items"))
            }
            if (item.unitPurchasePrice < 0) {
                return Result.failure(IllegalArgumentException("Purchase price cannot be negative"))
            }
        }

        val vendor = vendorId?.let { vendorDao.getVendorById(it) }
        val now = System.currentTimeMillis()
        val createdTransactions = mutableListOf<StockTransaction>()

        for (item in items) {
            val vaccine = vaccineDao.getVaccineById(item.vaccineId)
                ?: return Result.failure(IllegalStateException("Vaccine ID ${item.vaccineId} not found"))

            // Update vaccine stock automatically
            val updatedStock = vaccine.currentStock + item.quantity
            val updatedVaccine = vaccine.copy(
                currentStock = updatedStock,
                purchasePrice = item.unitPurchasePrice
            )
            vaccineDao.updateVaccine(updatedVaccine)

            val totalAmount = item.unitPurchasePrice * item.quantity
            val transaction = StockTransaction(
                type = TransactionType.PURCHASE,
                vaccineId = vaccine.id,
                vaccineName = vaccine.name,
                vendorId = vendor?.id,
                vendorName = vendor?.name,
                quantity = item.quantity,
                unitPrice = item.unitPurchasePrice,
                costPricePerUnit = item.unitPurchasePrice,
                totalAmount = totalAmount,
                profit = 0.0,
                batchNumber = item.batchNumber,
                expiryDate = item.expiryDate,
                referenceOrCustomer = invoiceNumber.ifBlank { "Vendor PO #${now.toString().takeLast(4)}" },
                timestamp = now,
                notes = notes
            )
            val txId = transactionDao.insertTransaction(transaction)
            createdTransactions.add(transaction.copy(id = txId))
        }

        return Result.success(createdTransactions)
    }

    /**
     * Apply a Scanned / Uploaded Vendor Bill (Photo or PDF):
     * - Optionally creates a new Vendor if not already in the clinic's vendor list
     * - Updates stock and cost/selling price for matched existing vaccines
     * - Automatically creates new Vaccine catalog entries for any brand-new vaccines on the bill
     * - Records StockTransaction(PURCHASE) entries for all items on the bill
     */
    suspend fun applyScannedBillStockUpdate(
        vendorId: Long?,
        newVendorName: String,
        newVendorPhone: String,
        invoiceNumber: String,
        notes: String,
        items: List<ScannedBillStockItem>
    ): Result<Pair<List<StockTransaction>, Int>> {
        if (items.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please select at least one vaccine item from the bill"))
        }

        for (item in items) {
            if (item.vaccineName.isBlank()) {
                return Result.failure(IllegalArgumentException("Vaccine name cannot be blank"))
            }
            if (item.quantity <= 0) {
                return Result.failure(IllegalArgumentException("Quantity must be greater than 0 for '${item.vaccineName}'"))
            }
            if (item.unitPurchasePrice < 0.0) {
                return Result.failure(IllegalArgumentException("Purchase price cannot be negative for '${item.vaccineName}'"))
            }
        }

        var resolvedVendor: Vendor? = vendorId?.let { vendorDao.getVendorById(it) }
        if (resolvedVendor == null && newVendorName.isNotBlank()) {
            val newVendor = Vendor(
                name = newVendorName.trim(),
                contactPerson = "Bill Supplier",
                phone = newVendorPhone.trim(),
                notes = "Auto-added from Scanned Bill ${invoiceNumber.ifBlank { "" }}".trim()
            )
            val insertedVendorId = vendorDao.insertVendor(newVendor)
            resolvedVendor = newVendor.copy(id = insertedVendorId)
        }

        val now = System.currentTimeMillis()
        val createdTransactions = mutableListOf<StockTransaction>()
        var newlyCreatedVaccinesCount = 0
        val catalogList = vaccineDao.getAllVaccinesList().toMutableList()

        for (item in items) {
            val cleanName = item.vaccineName.trim()
            val cleanCode = item.code.trim()

            val existingIdx = catalogList.indexOfFirst { v ->
                (item.matchedVaccineId != null && v.id == item.matchedVaccineId) ||
                    (item.matchedVaccineId == null && (
                        v.name.equals(cleanName, ignoreCase = true) ||
                            (cleanCode.isNotBlank() && v.code.equals(cleanCode, ignoreCase = true))
                        ))
            }

            val finalVaccine: Vaccine = if (existingIdx >= 0) {
                val existingVac = catalogList[existingIdx]
                val updatedStock = existingVac.currentStock + item.quantity
                val updatedPurchasePrice = if (item.unitPurchasePrice > 0.0) {
                    item.unitPurchasePrice
                } else {
                    existingVac.purchasePrice
                }
                val updatedSellingPrice = if (item.sellingPrice > 0.0) {
                    item.sellingPrice
                } else {
                    existingVac.sellingPrice
                }
                val updated = existingVac.copy(
                    currentStock = updatedStock,
                    purchasePrice = updatedPurchasePrice,
                    sellingPrice = updatedSellingPrice
                )
                vaccineDao.updateVaccine(updated)
                catalogList[existingIdx] = updated
                updated
            } else {
                val computedSellingPrice = when {
                    item.sellingPrice > 0.0 -> item.sellingPrice
                    item.unitPurchasePrice > 0.0 -> kotlin.math.round(item.unitPurchasePrice * 1.25)
                    else -> 0.0
                }
                val newVac = Vaccine(
                    name = cleanName,
                    category = item.category.ifBlank { "Routine Pediatric" },
                    code = cleanCode.ifBlank {
                        cleanName.take(6).uppercase().filter { it.isLetterOrDigit() }
                    },
                    currentStock = item.quantity,
                    purchasePrice = item.unitPurchasePrice,
                    sellingPrice = computedSellingPrice,
                    minStockAlert = 10,
                    storageTemp = "2°C to 8°C",
                    unit = "doses",
                    notes = "Added via Scanned Bill ${invoiceNumber.ifBlank { "" }}".trim()
                )
                val newVacId = vaccineDao.insertVaccine(newVac)
                newlyCreatedVaccinesCount++
                val savedNewVac = newVac.copy(id = newVacId)
                catalogList.add(savedNewVac)
                savedNewVac
            }

            val effectiveUnitCost = if (item.unitPurchasePrice > 0.0) {
                item.unitPurchasePrice
            } else {
                finalVaccine.purchasePrice
            }
            val totalAmount = effectiveUnitCost * item.quantity
            val transaction = StockTransaction(
                type = TransactionType.PURCHASE,
                vaccineId = finalVaccine.id,
                vaccineName = finalVaccine.name,
                vendorId = resolvedVendor?.id,
                vendorName = resolvedVendor?.name ?: newVendorName.takeIf { it.isNotBlank() },
                quantity = item.quantity,
                unitPrice = effectiveUnitCost,
                costPricePerUnit = effectiveUnitCost,
                totalAmount = totalAmount,
                profit = 0.0,
                batchNumber = item.batchNumber,
                expiryDate = item.expiryDate,
                referenceOrCustomer = invoiceNumber.ifBlank { "Scanned Bill #${now.toString().takeLast(4)}" },
                timestamp = now,
                notes = notes.ifBlank { "Stock updated via Bill Scanner" }
            )
            val txId = transactionDao.insertTransaction(transaction)
            createdTransactions.add(transaction.copy(id = txId))
        }

        return Result.success(createdTransactions to newlyCreatedVaccinesCount)
    }

    /**
     * Record a Sale / Dispensation for single vaccine:
     * - Automatically reduces vaccine stock!
     * - Calculates profit = (sellingPrice - costBasis) * quantity
     */
    suspend fun recordSale(
        vaccineId: Long,
        quantity: Int,
        unitSellingPrice: Double,
        customerOrPatient: String,
        batchNumber: String,
        notes: String
    ): Result<StockTransaction> {
        return recordMultiSale(
            items = listOf(
                SaleLineItem(
                    vaccineId = vaccineId,
                    quantity = quantity,
                    unitSellingPrice = unitSellingPrice,
                    batchNumber = batchNumber
                )
            ),
            customerOrPatient = customerOrPatient,
            notes = notes
        ).map { it.first() }
    }

    /**
     * Record a Multiple Vaccine Sale to a Patient or Customer:
     * - Sells multiple vaccine types in a single bill/visit (e.g. routine combo or family package)
     * - Validates sufficient stock for all vaccines
     * - Automatically decrements stock for each item
     * - Calculates line item and total profit
     */
    suspend fun recordMultiSale(
        items: List<SaleLineItem>,
        customerOrPatient: String,
        notes: String
    ): Result<List<StockTransaction>> {
        if (items.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please add at least one vaccine to the bill"))
        }

        // 1. Validation phase (Pre-check all stocks to prevent partial updates)
        val vaccinesMap = mutableMapOf<Long, Vaccine>()
        for (item in items) {
            if (item.quantity <= 0) {
                return Result.failure(IllegalArgumentException("Quantity must be greater than 0"))
            }
            val vaccine = vaccineDao.getVaccineById(item.vaccineId)
                ?: return Result.failure(IllegalStateException("Vaccine ID ${item.vaccineId} not found"))

            if (vaccine.currentStock < item.quantity) {
                return Result.failure(
                    IllegalStateException("Insufficient stock for '${vaccine.name}'! Available: ${vaccine.currentStock}, Requested: ${item.quantity}")
                )
            }
            vaccinesMap[item.vaccineId] = vaccine
        }

        // 2. Execution phase
        val now = System.currentTimeMillis()
        val createdTransactions = mutableListOf<StockTransaction>()

        for (item in items) {
            val vaccine = vaccinesMap[item.vaccineId]!!

            // Automatically reduce stock
            val updatedStock = max(0, vaccine.currentStock - item.quantity)
            vaccineDao.updateVaccine(vaccine.copy(currentStock = updatedStock))

            val costBasis = vaccine.purchasePrice
            val totalSale = item.unitSellingPrice * item.quantity
            val profit = (item.unitSellingPrice - costBasis) * item.quantity

            val transaction = StockTransaction(
                type = TransactionType.SALE,
                vaccineId = vaccine.id,
                vaccineName = vaccine.name,
                quantity = item.quantity,
                unitPrice = item.unitSellingPrice,
                costPricePerUnit = costBasis,
                totalAmount = totalSale,
                profit = profit,
                batchNumber = item.batchNumber,
                referenceOrCustomer = customerOrPatient.ifBlank { "Patient / Client" },
                timestamp = now,
                notes = notes
            )
            val txId = transactionDao.insertTransaction(transaction)
            createdTransactions.add(transaction.copy(id = txId))
        }

        return Result.success(createdTransactions)
    }

    /**
     * Direct stock adjustment (Add or Reduce stock directly)
     * e.g., Spoilage, damaged cold-chain, audit physical count adjustment
     */
    suspend fun recordStockAdjustment(
        vaccineId: Long,
        isAddition: Boolean,
        quantity: Int,
        reason: String
    ): Result<StockTransaction> {
        if (quantity <= 0) {
            return Result.failure(IllegalArgumentException("Quantity must be greater than 0"))
        }

        val vaccine = vaccineDao.getVaccineById(vaccineId)
            ?: return Result.failure(IllegalStateException("Vaccine not found"))

        val newStock = if (isAddition) {
            vaccine.currentStock + quantity
        } else {
            max(0, vaccine.currentStock - quantity)
        }

        vaccineDao.updateVaccine(vaccine.copy(currentStock = newStock))

        val txType = if (isAddition) TransactionType.ADJUSTMENT_ADD else TransactionType.ADJUSTMENT_REDUCE
        val transaction = StockTransaction(
            type = txType,
            vaccineId = vaccine.id,
            vaccineName = vaccine.name,
            quantity = quantity,
            unitPrice = vaccine.purchasePrice,
            costPricePerUnit = vaccine.purchasePrice,
            totalAmount = vaccine.purchasePrice * quantity,
            profit = if (isAddition) 0.0 else -(vaccine.purchasePrice * quantity),
            referenceOrCustomer = reason,
            timestamp = System.currentTimeMillis(),
            notes = "Manual adjustment: $reason"
        )

        val txId = transactionDao.insertTransaction(transaction)
        return Result.success(transaction.copy(id = txId))
    }

    suspend fun deleteTransaction(id: Long) = transactionDao.deleteTransaction(id)
}
