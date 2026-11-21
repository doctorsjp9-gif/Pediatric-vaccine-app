package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.example.data.util.LocalBackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Vaccine::class,
        Vendor::class,
        StockTransaction::class,
        PatientRegistration::class,
        ClinicProfile::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vaccineDao(): VaccineDao
    abstract fun vendorDao(): VendorDao
    abstract fun transactionDao(): StockTransactionDao
    abstract fun patientRegistrationDao(): PatientRegistrationDao
    abstract fun clinicProfileDao(): ClinicProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun safeExec(db: SupportSQLiteDatabase, sql: String) {
            try {
                db.execSQL(sql)
            } catch (_: Exception) {
                // Column or table already exists — safe to ignore during non-destructive upgrade
            }
        }

        private fun applyNonDestructiveSchemaUpgrades(db: SupportSQLiteDatabase) {
            // Ensure all tables exist without dropping any existing user data
            safeExec(
                db,
                """CREATE TABLE IF NOT EXISTS `vaccines` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL DEFAULT '',
                    `category` TEXT NOT NULL DEFAULT 'Routine',
                    `code` TEXT NOT NULL DEFAULT '',
                    `currentStock` INTEGER NOT NULL DEFAULT 0,
                    `purchasePrice` REAL NOT NULL DEFAULT 0.0,
                    `sellingPrice` REAL NOT NULL DEFAULT 0.0,
                    `minStockAlert` INTEGER NOT NULL DEFAULT 10,
                    `storageTemp` TEXT NOT NULL DEFAULT '2°C to 8°C',
                    `unit` TEXT NOT NULL DEFAULT 'doses',
                    `notes` TEXT NOT NULL DEFAULT ''
                )"""
            )
            safeExec(
                db,
                """CREATE TABLE IF NOT EXISTS `vendors` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL DEFAULT '',
                    `contactPerson` TEXT NOT NULL DEFAULT '',
                    `phone` TEXT NOT NULL DEFAULT '',
                    `email` TEXT NOT NULL DEFAULT '',
                    `address` TEXT NOT NULL DEFAULT '',
                    `notes` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL DEFAULT 0
                )"""
            )
            safeExec(
                db,
                """CREATE TABLE IF NOT EXISTS `stock_transactions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `type` TEXT NOT NULL DEFAULT 'SALE',
                    `vaccineId` INTEGER NOT NULL DEFAULT 0,
                    `vaccineName` TEXT NOT NULL DEFAULT '',
                    `vendorId` INTEGER,
                    `vendorName` TEXT,
                    `quantity` INTEGER NOT NULL DEFAULT 1,
                    `unitPrice` REAL NOT NULL DEFAULT 0.0,
                    `costPricePerUnit` REAL NOT NULL DEFAULT 0.0,
                    `totalAmount` REAL NOT NULL DEFAULT 0.0,
                    `profit` REAL NOT NULL DEFAULT 0.0,
                    `batchNumber` TEXT NOT NULL DEFAULT '',
                    `expiryDate` TEXT NOT NULL DEFAULT '',
                    `referenceOrCustomer` TEXT NOT NULL DEFAULT '',
                    `timestamp` INTEGER NOT NULL DEFAULT 0,
                    `notes` TEXT NOT NULL DEFAULT ''
                )"""
            )
            safeExec(
                db,
                """CREATE TABLE IF NOT EXISTS `patient_registrations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `patientName` TEXT NOT NULL DEFAULT '',
                    `dobOrAge` TEXT NOT NULL DEFAULT '',
                    `gender` TEXT NOT NULL DEFAULT 'Male',
                    `parentName` TEXT NOT NULL DEFAULT '',
                    `mobileNumber` TEXT NOT NULL DEFAULT '',
                    `vaccineGivenToday` TEXT NOT NULL DEFAULT '',
                    `vaccineGivenBatch` TEXT NOT NULL DEFAULT '',
                    `vaccineGivenDose` TEXT NOT NULL DEFAULT 'Dose 1',
                    `dateGivenMillis` INTEGER NOT NULL DEFAULT 0,
                    `nextPlannedVaccine` TEXT NOT NULL DEFAULT '',
                    `nextDueDate` TEXT NOT NULL DEFAULT '',
                    `nextDueDateMillis` INTEGER NOT NULL DEFAULT 0,
                    `reminderStatus` TEXT NOT NULL DEFAULT 'PENDING',
                    `lastReminderMillis` INTEGER NOT NULL DEFAULT 0,
                    `notes` TEXT NOT NULL DEFAULT '',
                    `previousVaccinesHistory` TEXT NOT NULL DEFAULT ''
                )"""
            )
            safeExec(
                db,
                """CREATE TABLE IF NOT EXISTS `clinic_profile` (
                    `id` INTEGER NOT NULL PRIMARY KEY,
                    `clinicName` TEXT NOT NULL DEFAULT 'Dr Priyank Vaccine Clinic',
                    `doctorName` TEXT NOT NULL DEFAULT 'Dr. Priyank, MD (Pediatrics)',
                    `phone` TEXT NOT NULL DEFAULT '+91 98765 43210',
                    `email` TEXT NOT NULL DEFAULT 'drpriyank.clinic@gmail.com',
                    `address` TEXT NOT NULL DEFAULT '102, Shivalik Medical Center, Ring Road, Ahmedabad',
                    `registrationNumber` TEXT NOT NULL DEFAULT 'GMC-REG-29182',
                    `logoType` TEXT NOT NULL DEFAULT 'CUTE_TEDDY',
                    `themeColor` TEXT NOT NULL DEFAULT 'TEAL',
                    `billFooterNote` TEXT NOT NULL DEFAULT 'Wishing your little one good health, bright smiles & strong immunity! 🌟',
                    `customWhatsappMessage` TEXT NOT NULL DEFAULT '',
                    `photoUri` TEXT NOT NULL DEFAULT ''
                )"""
            )
            // Safely add columns introduced in versions 2, 3, 4 if upgrading from an older version
            safeExec(db, "ALTER TABLE `patient_registrations` ADD COLUMN `previousVaccinesHistory` TEXT NOT NULL DEFAULT ''")
            safeExec(db, "ALTER TABLE `patient_registrations` ADD COLUMN `vaccineGivenBatch` TEXT NOT NULL DEFAULT ''")
            safeExec(db, "ALTER TABLE `patient_registrations` ADD COLUMN `vaccineGivenDose` TEXT NOT NULL DEFAULT 'Dose 1'")
            safeExec(db, "ALTER TABLE `clinic_profile` ADD COLUMN `customWhatsappMessage` TEXT NOT NULL DEFAULT ''")
            safeExec(db, "ALTER TABLE `clinic_profile` ADD COLUMN `photoUri` TEXT NOT NULL DEFAULT ''")
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = applyNonDestructiveSchemaUpgrades(db)
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = applyNonDestructiveSchemaUpgrades(db)
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = applyNonDestructiveSchemaUpgrades(db)
        }
        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = applyNonDestructiveSchemaUpgrades(db)
        }
        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = applyNonDestructiveSchemaUpgrades(db)
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "dr_priyank_vaccine_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_1_4,
                        MIGRATION_2_4
                    )
                    .addCallback(DatabaseCallback(appContext, scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getInstance(context: Context): AppDatabase {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "dr_priyank_vaccine_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_1_4,
                        MIGRATION_2_4
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val appContext: Context,
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        // First check if a persistent auto-backup exists from before an update
                        val restored = LocalBackupManager.restoreFromAutoBackupIfNeeded(appContext, database)
                        if (!restored && !LocalBackupManager.isInitialSeedDone(appContext) && !LocalBackupManager.didUserIntentionallyClearData(appContext)) {
                            populateInitialData(database)
                            LocalBackupManager.markInitialSeedDone(appContext)
                            LocalBackupManager.saveAutoBackup(appContext, database)
                        }
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        LocalBackupManager.restoreFromAutoBackupIfNeeded(appContext, database)
                        LocalBackupManager.saveAutoBackup(appContext, database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val vendorDao = database.vendorDao()
            val vaccineDao = database.vaccineDao()
            val transactionDao = database.transactionDao()
            val patientDao = database.patientRegistrationDao()
            val profileDao = database.clinicProfileDao()

            // Never overwrite existing user data if any records are already present
            if (vaccineDao.getAllVaccinesList().isNotEmpty() ||
                patientDao.getAllPatientsList().isNotEmpty() ||
                transactionDao.getAllTransactionsList().isNotEmpty()
            ) {
                return
            }

            // 1. Initial Vendors (Indian Distributors)
            val v1 = Vendor(
                id = 1,
                name = "Serum Institute Distribution Hub",
                contactPerson = "Rajesh Sharma",
                phone = "+91 98230 45678",
                email = "orders@seruminstitute-dist.in",
                address = "Hadapsar Cold-Storage Complex, Pune",
                notes = "Authorized distributor for pediatric vaccines & polio vaccines."
            )
            val v2 = Vendor(
                id = 2,
                name = "Bharat Biotech Regional Depot",
                contactPerson = "Pooja Reddy",
                phone = "+91 94401 22334",
                email = "supply@bharatbiotech-depot.in",
                address = "Genome Valley, Phase II, Hyderabad",
                notes = "Certified cold chain logistics 2°C to 8°C with digital temperature loggers."
            )
            val v3 = Vendor(
                id = 3,
                name = "Panacea & Sanofi Clinic Supplies",
                contactPerson = "Amit Patel",
                phone = "+91 98795 67890",
                email = "apatel@panaceaclinic.com",
                address = "Medical Enclave, Ring Road, Ahmedabad",
                notes = "Same-day delivery for seasonal influenza & rabies post-exposure vaccines."
            )
            vendorDao.insertAll(listOf(v1, v2, v3))

            // 2. Initial Vaccines with Rupee (₹) Pricing
            val vac1 = Vaccine(
                id = 1,
                name = "Hepatitis B (Recombinant)",
                category = "Routine Pediatric",
                code = "VAC-HEPB-01",
                currentStock = 35,
                purchasePrice = 320.0,
                sellingPrice = 750.0,
                minStockAlert = 15,
                storageTemp = "2°C to 8°C",
                unit = "doses",
                notes = "Single dose vial with autodisable syringe."
            )
            val vac2 = Vaccine(
                id = 2,
                name = "MMR (Measles, Mumps, Rubella)",
                category = "Routine Pediatric",
                code = "VAC-MMR-02",
                currentStock = 18,
                purchasePrice = 550.0,
                sellingPrice = 1200.0,
                minStockAlert = 12,
                storageTemp = "2°C to 8°C",
                unit = "vials",
                notes = "Freeze-dried with sterile water diluent."
            )
            val vac3 = Vaccine(
                id = 3,
                name = "Influenza Quadrivalent",
                category = "Seasonal",
                code = "VAC-FLU-24",
                currentStock = 45,
                purchasePrice = 950.0,
                sellingPrice = 1800.0,
                minStockAlert = 20,
                storageTemp = "2°C to 8°C",
                unit = "pre-filled syringes",
                notes = "Annual northern/southern hemisphere strain formulation."
            )
            val vac4 = Vaccine(
                id = 4,
                name = "Rabies Inactivated (Rabivax-S)",
                category = "Emergency",
                code = "VAC-RAB-05",
                currentStock = 6, // Triggers Low Stock Alert!
                purchasePrice = 380.0,
                sellingPrice = 850.0,
                minStockAlert = 10,
                storageTemp = "2°C to 8°C",
                unit = "vials",
                notes = "Post-exposure prophylaxis priority stock."
            )
            val vac5 = Vaccine(
                id = 5,
                name = "Tdap (Boostrix / Adacel)",
                category = "Adult & Booster",
                code = "VAC-TDAP-09",
                currentStock = 24,
                purchasePrice = 620.0,
                sellingPrice = 1350.0,
                minStockAlert = 12,
                storageTemp = "2°C to 8°C",
                unit = "doses",
                notes = "Tetanus, Diphtheria, Pertussis booster."
            )
            val vac6 = Vaccine(
                id = 6,
                name = "Typhoid Conjugate (Typbar-TCV)",
                category = "Travel & Pediatric",
                code = "VAC-TYP-11",
                currentStock = 15,
                purchasePrice = 450.0,
                sellingPrice = 950.0,
                minStockAlert = 10,
                storageTemp = "2°C to 8°C",
                unit = "vials",
                notes = "Long-lasting conjugate protection."
            )
            vaccineDao.insertAll(listOf(vac1, vac2, vac3, vac4, vac5, vac6))

            // 3. Transactions spanning Today, This Week, and This Month
            val now = System.currentTimeMillis()
            val oneDayMillis = 24 * 60 * 60 * 1000L
            val twoHoursAgo = now - (2 * 60 * 60 * 1000L)
            val fourHoursAgo = now - (4 * 60 * 60 * 1000L)
            val twoDaysAgo = now - (2 * oneDayMillis)
            val fourDaysAgo = now - (4 * oneDayMillis)
            val tenDaysAgo = now - (10 * oneDayMillis)
            val twentyDaysAgo = now - (20 * oneDayMillis)

            val transactions = listOf(
                // Purchases
                StockTransaction(
                    id = 1,
                    type = TransactionType.PURCHASE,
                    vaccineId = 1,
                    vaccineName = "Hepatitis B (Recombinant)",
                    vendorId = 1,
                    vendorName = "Serum Institute Distribution Hub",
                    quantity = 40,
                    unitPrice = 320.0,
                    costPricePerUnit = 320.0,
                    totalAmount = 12800.0,
                    profit = 0.0,
                    batchNumber = "HPB-2026-X1",
                    expiryDate = "10/2027",
                    referenceOrCustomer = "PO-8901",
                    timestamp = twentyDaysAgo,
                    notes = "Monthly batch restock"
                ),
                StockTransaction(
                    id = 2,
                    type = TransactionType.PURCHASE,
                    vaccineId = 2,
                    vaccineName = "MMR (Measles, Mumps, Rubella)",
                    vendorId = 2,
                    vendorName = "Bharat Biotech Regional Depot",
                    quantity = 25,
                    unitPrice = 550.0,
                    costPricePerUnit = 550.0,
                    totalAmount = 13750.0,
                    profit = 0.0,
                    batchNumber = "MMR-904-B",
                    expiryDate = "08/2027",
                    referenceOrCustomer = "PO-8942",
                    timestamp = tenDaysAgo,
                    notes = "Cold chain verified at 4.2°C"
                ),
                StockTransaction(
                    id = 3,
                    type = TransactionType.PURCHASE,
                    vaccineId = 3,
                    vaccineName = "Influenza Quadrivalent",
                    vendorId = 3,
                    vendorName = "Panacea & Sanofi Clinic Supplies",
                    quantity = 50,
                    unitPrice = 950.0,
                    costPricePerUnit = 950.0,
                    totalAmount = 47500.0,
                    profit = 0.0,
                    batchNumber = "FLU-26Q",
                    expiryDate = "05/2027",
                    referenceOrCustomer = "PO-8990",
                    timestamp = twoDaysAgo,
                    notes = "Seasonal replenishment"
                ),

                // Sales - Today
                StockTransaction(
                    id = 4,
                    type = TransactionType.SALE,
                    vaccineId = 1,
                    vaccineName = "Hepatitis B (Recombinant)",
                    quantity = 2,
                    unitPrice = 750.0,
                    costPricePerUnit = 320.0,
                    totalAmount = 1500.0,
                    profit = (750.0 - 320.0) * 2, // ₹860.00 profit
                    referenceOrCustomer = "Baby Aarav Sharma (Pediatric)",
                    timestamp = fourHoursAgo,
                    notes = "Dose 1 vaccination"
                ),
                StockTransaction(
                    id = 5,
                    type = TransactionType.SALE,
                    vaccineId = 3,
                    vaccineName = "Influenza Quadrivalent",
                    quantity = 3,
                    unitPrice = 1800.0,
                    costPricePerUnit = 950.0,
                    totalAmount = 5400.0,
                    profit = (1800.0 - 950.0) * 3, // ₹2,550.00 profit
                    referenceOrCustomer = "Verma Family (Walk-in)",
                    timestamp = twoHoursAgo,
                    notes = "Annual flu prevention doses"
                ),

                // Sales - Earlier This Week
                StockTransaction(
                    id = 6,
                    type = TransactionType.SALE,
                    vaccineId = 2,
                    vaccineName = "MMR (Measles, Mumps, Rubella)",
                    quantity = 3,
                    unitPrice = 1200.0,
                    costPricePerUnit = 550.0,
                    totalAmount = 3600.0,
                    profit = (1200.0 - 550.0) * 3, // ₹1,950.00 profit
                    referenceOrCustomer = "Child Wellness Program",
                    timestamp = twoDaysAgo,
                    notes = "15-month immunization dose"
                ),
                StockTransaction(
                    id = 7,
                    type = TransactionType.SALE,
                    vaccineId = 4,
                    vaccineName = "Rabies Inactivated (Rabivax-S)",
                    quantity = 2,
                    unitPrice = 850.0,
                    costPricePerUnit = 380.0,
                    totalAmount = 1700.0,
                    profit = (850.0 - 380.0) * 2, // ₹940.00 profit
                    referenceOrCustomer = "Emergency Patient #402",
                    timestamp = fourDaysAgo,
                    notes = "Post-exposure Day 0 regimen"
                ),

                // Sales - Earlier This Month
                StockTransaction(
                    id = 8,
                    type = TransactionType.SALE,
                    vaccineId = 5,
                    vaccineName = "Tdap (Boostrix / Adacel)",
                    quantity = 4,
                    unitPrice = 1350.0,
                    costPricePerUnit = 620.0,
                    totalAmount = 5400.0,
                    profit = (1350.0 - 620.0) * 4, // ₹2,920.00 profit
                    referenceOrCustomer = "Maternal Health Clinic",
                    timestamp = tenDaysAgo,
                    notes = "Third trimester maternal vaccination"
                ),
                StockTransaction(
                    id = 9,
                    type = TransactionType.SALE,
                    vaccineId = 6,
                    vaccineName = "Typhoid Conjugate (Typbar-TCV)",
                    quantity = 3,
                    unitPrice = 950.0,
                    costPricePerUnit = 450.0,
                    totalAmount = 2850.0,
                    profit = (950.0 - 450.0) * 3, // ₹1,500.00 profit
                    referenceOrCustomer = "Travel Medicine Consultation",
                    timestamp = twentyDaysAgo,
                    notes = "Pre-travel immunization"
                )
            )

            transactionDao.insertAll(transactions)

            // 4. Default Clinic Profile (only if not already configured by user)
            if (profileDao.getProfileSync() == null) {
                profileDao.insertOrUpdate(
                    ClinicProfile(
                        id = 1,
                        clinicName = "Dr Priyank Vaccine Clinic",
                        doctorName = "Dr. Priyank, MD (Pediatrics)",
                        phone = "+91 98765 43210",
                        email = "drpriyank.clinic@gmail.com",
                        address = "102, Shivalik Medical Center, Ring Road, Ahmedabad",
                        registrationNumber = "GMC-REG-29182",
                        logoType = "CUTE_TEDDY",
                        themeColor = "TEAL",
                        billFooterNote = "Wishing your little one good health, bright smiles & strong immunity! 🌟"
                    )
                )
            }

            // 5. Initial Sample Patient Registrations with upcoming due dates
            val fiveDaysLater = now + (5 * oneDayMillis)
            val fourteenDaysLater = now + (14 * oneDayMillis)
            val threeDaysAgo = now - (3 * oneDayMillis)

            val aaravHistory = com.example.ui.util.PatientVaccineRecordHelper.serializeEntries(
                listOf(
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "BCG", doseName = "At Birth", dateAdministeredMillis = now - 180 * oneDayMillis, batchNumber = "BCG-902", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "OPV-0", doseName = "At Birth", dateAdministeredMillis = now - 180 * oneDayMillis, batchNumber = "OPV-11", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Hepatitis B-0", doseName = "Dose 1 (Birth)", dateAdministeredMillis = now - 180 * oneDayMillis, batchNumber = "HPB-01", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Pentavalent", doseName = "Dose 1 (6 Wks)", dateAdministeredMillis = now - 135 * oneDayMillis, batchNumber = "PNT-44", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Rotavirus", doseName = "Dose 1 (6 Wks)", dateAdministeredMillis = now - 135 * oneDayMillis, batchNumber = "ROTA-22", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "PCV", doseName = "Dose 1 (6 Wks)", dateAdministeredMillis = now - 135 * oneDayMillis, batchNumber = "PCV-78", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Pentavalent", doseName = "Dose 2 (10 Wks)", dateAdministeredMillis = now - 105 * oneDayMillis, batchNumber = "PNT-45", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Rotavirus", doseName = "Dose 2 (10 Wks)", dateAdministeredMillis = now - 105 * oneDayMillis, batchNumber = "ROTA-23", isHistorical = true)
                )
            )

            val ananyaHistory = com.example.ui.util.PatientVaccineRecordHelper.serializeEntries(
                listOf(
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "BCG", doseName = "At Birth", dateAdministeredMillis = now - 450 * oneDayMillis, batchNumber = "BCG-881", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "OPV-0", doseName = "At Birth", dateAdministeredMillis = now - 450 * oneDayMillis, batchNumber = "OPV-09", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Hepatitis B", doseName = "Dose 1", dateAdministeredMillis = now - 450 * oneDayMillis, batchNumber = "HPB-90", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Pentavalent", doseName = "Dose 1, 2 & 3", dateAdministeredMillis = now - 350 * oneDayMillis, batchNumber = "PNT-COMBO", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Rotavirus", doseName = "Full Series", dateAdministeredMillis = now - 350 * oneDayMillis, batchNumber = "ROTA-ALL", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Influenza Quadrivalent", doseName = "Dose 1 (6m)", dateAdministeredMillis = now - 270 * oneDayMillis, batchNumber = "FLU-25Q", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Typhoid (TCV)", doseName = "Primary (9m)", dateAdministeredMillis = now - 180 * oneDayMillis, batchNumber = "TYP-99", isHistorical = true)
                )
            )

            val kabirHistory = com.example.ui.util.PatientVaccineRecordHelper.serializeEntries(
                listOf(
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "BCG & OPV-0", doseName = "At Birth", dateAdministeredMillis = now - 730 * oneDayMillis, batchNumber = "BIRTH-01", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Hexavalent 6-in-1", doseName = "Dose 1, 2, 3", dateAdministeredMillis = now - 620 * oneDayMillis, batchNumber = "HEXA-SERIES", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "MMR", doseName = "Dose 1 (9m)", dateAdministeredMillis = now - 460 * oneDayMillis, batchNumber = "MMR-11", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Varicella", doseName = "Dose 1 (15m)", dateAdministeredMillis = now - 270 * oneDayMillis, batchNumber = "VAR-55", isHistorical = true),
                    com.example.ui.util.PatientVaccineEntry(vaccineName = "Hepatitis A", doseName = "Dose 1 (18m)", dateAdministeredMillis = now - 180 * oneDayMillis, batchNumber = "HAV-33", isHistorical = true)
                )
            )

            patientDao.insertAll(
                listOf(
                    PatientRegistration(
                        id = 1,
                        patientName = "Baby Aarav Sharma",
                        dobOrAge = "6 Months",
                        gender = "Male",
                        parentName = "Rahul Sharma",
                        mobileNumber = "+91 98230 45678",
                        vaccineGivenToday = "Hepatitis B (Recombinant)",
                        vaccineGivenBatch = "HPB-2026-X1",
                        vaccineGivenDose = "Dose 2",
                        dateGivenMillis = fourHoursAgo,
                        nextPlannedVaccine = "MMR (Measles, Mumps, Rubella)",
                        nextDueDate = "In 14 Days",
                        nextDueDateMillis = fourteenDaysLater,
                        reminderStatus = "PENDING",
                        notes = "Very active, mild fever after dose 1. Advised paracetamol syrup if needed.",
                        previousVaccinesHistory = aaravHistory
                    ),
                    PatientRegistration(
                        id = 2,
                        patientName = "Ananya Patel",
                        dobOrAge = "15 Months",
                        gender = "Female",
                        parentName = "Sneha Patel",
                        mobileNumber = "+91 98795 11223",
                        vaccineGivenToday = "MMR (Measles, Mumps, Rubella)",
                        vaccineGivenBatch = "MMR-904-B",
                        vaccineGivenDose = "Dose 1",
                        dateGivenMillis = twoDaysAgo,
                        nextPlannedVaccine = "Typhoid Conjugate (Typbar-TCV)",
                        nextDueDate = "In 5 Days",
                        nextDueDateMillis = fiveDaysLater,
                        reminderStatus = "PENDING",
                        notes = "Immunization card verified. Growth milestone normal.",
                        previousVaccinesHistory = ananyaHistory
                    ),
                    PatientRegistration(
                        id = 3,
                        patientName = "Kabir Verma",
                        dobOrAge = "2 Years",
                        gender = "Male",
                        parentName = "Vikram Verma",
                        mobileNumber = "+91 94401 99887",
                        vaccineGivenToday = "Influenza Quadrivalent",
                        vaccineGivenBatch = "FLU-26Q",
                        vaccineGivenDose = "Annual Booster",
                        dateGivenMillis = twoHoursAgo,
                        nextPlannedVaccine = "Tdap (Boostrix / Adacel)",
                        nextDueDate = "Overdue by 3 Days",
                        nextDueDateMillis = threeDaysAgo,
                        reminderStatus = "PENDING",
                        notes = "Annual flu vaccination completed. Next DTP booster overdue.",
                        previousVaccinesHistory = kabirHistory
                    )
                )
            )
        }
    }
}
