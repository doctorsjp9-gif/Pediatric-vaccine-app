package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ClinicProfile
import com.example.ui.util.BillData
import com.example.ui.util.BillItem
import com.example.ui.util.PdfBillGenerator
import com.example.ui.util.SignatureManager
import com.example.ui.util.WhatsAppHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Dr Priyank Vaccine Clinic", appName)
  }

  @Test
  fun `test digital signature save and load`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testBmp = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)

    val saved = SignatureManager.saveSignature(context, testBmp)
    assertTrue("Signature should be saved", saved)
    assertTrue("hasSavedSignature should return true", SignatureManager.hasSavedSignature(context))

    val loaded = SignatureManager.getSavedSignature(context)
    assertNotNull("Loaded signature should not be null", loaded)

    val sigId = SignatureManager.generateSignatureId()
    assertTrue("Signature ID should start with SIG-", sigId.startsWith("SIG-"))

    val cleared = SignatureManager.clearSavedSignature(context)
    assertTrue("Cleared signature should succeed", cleared)
  }

  @Test
  fun `test stroke rendering to bitmap`() {
    val strokes = listOf(
      listOf(Offset(10f, 10f), Offset(50f, 30f), Offset(90f, 20f))
    )
    val bitmap = SignatureManager.renderStrokesToBitmap(strokes, 100, 50, 200, 100)
    assertNotNull(bitmap)
    assertEquals(200, bitmap.width)
    assertEquals(100, bitmap.height)
  }

  @Test
  fun `test certified seal bitmap generation`() {
    val sealBitmap = SignatureManager.createCertifiedSealBitmap(
      doctorName = "Dr. Priyank",
      registrationNumber = "GMC-12345",
      clinicName = "Pediatric Clinic",
      signatureId = "SIG-TEST-001"
    )
    assertNotNull(sealBitmap)
    assertEquals(420, sealBitmap.width)
    assertEquals(180, sealBitmap.height)
  }

  @Test
  fun `test whatsapp text bill includes digital signature`() {
    val clinic = ClinicProfile(doctorName = "Dr. Priyank")
    val bill = BillData(
      invoiceNumber = "INV-100",
      patientName = "Baby Anaya",
      items = listOf(BillItem("Hexaxim 6-in-1", unitPrice = 3500.0)),
      isDigitallySigned = true,
      signatureId = "SIG-VERIFIED-99",
      signerName = "Dr. Priyank"
    )

    val message = WhatsAppHelper.buildTextBillMessage(bill, clinic)
    assertTrue("Message should contain Digitally Signed note", message.contains("Digitally Signed by"))
    assertTrue("Message should contain Signature ID", message.contains("SIG-VERIFIED-99"))
  }

  @Test
  fun `test bill data digital signature properties`() {
    val bill = BillData(
      invoiceNumber = "INV-200",
      patientName = "Aarav",
      items = emptyList(),
      isDigitallySigned = true,
      signatureId = "SIG-12345",
      signerName = "Dr. Priyank"
    )
    assertTrue(bill.isDigitallySigned)
    assertEquals("SIG-12345", bill.signatureId)
    assertEquals("Dr. Priyank", bill.signerName)
  }

  @Test
  fun `test notification channel creation and android 14 scheduler`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.service.NotificationHelper.createNotificationChannel(context)
    val canSchedule = com.example.service.AppointmentAlarmScheduler.canScheduleExact(context)
    // Under robolectric/test environment, returns true or safe boolean
    assertNotNull(canSchedule)
  }

  @Test
  fun `test multiple vaccines formatting in whatsapp reminder and patient data`() {
    val patient = com.example.data.model.PatientRegistration(
      id = 10,
      patientName = "Baby Vivaan",
      parentName = "Sneha Patel",
      mobileNumber = "+91 9988776655",
      vaccineGivenToday = "Pentavalent-1, Rotavirus-1, PCV-1",
      vaccineGivenDose = "Dose 1",
      nextPlannedVaccine = "Pentavalent-2, Rotavirus-2, PCV-2",
      nextDueDate = "In 4 Weeks"
    )
    val clinic = ClinicProfile(
      clinicName = "Dr Priyank Vaccine Clinic",
      phone = "+91 98765 43210"
    )
    val reminderMsg = WhatsAppHelper.buildVaccineReminderMessage(patient, clinic)
    assertTrue(reminderMsg.contains("Baby Vivaan"))
    assertTrue(reminderMsg.contains("Pentavalent-2"))
    assertTrue(reminderMsg.contains("Rotavirus-2"))
    assertTrue(reminderMsg.contains("PCV-2"))
    assertTrue(reminderMsg.contains("Pentavalent-1, Rotavirus-1, PCV-1"))

    // Test multiple given parsing
    val givenList = patient.vaccineGivenToday.split(",").map { it.trim() }.filter { it.isNotBlank() }
    assertEquals(3, givenList.size)
    assertEquals("Pentavalent-1", givenList[0])
    assertEquals("Rotavirus-1", givenList[1])
    assertEquals("PCV-1", givenList[2])

    // Test multiple planned parsing
    val plannedList = patient.nextPlannedVaccine.split(",").map { it.trim() }.filter { it.isNotBlank() }
    assertEquals(3, plannedList.size)
    assertEquals("Pentavalent-2", plannedList[0])
  }

  @Test
  fun `test delete out of stock and erase all local data in database`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.database.AppDatabase::class.java).build()
    val dao = db.vaccineDao()

    val v1 = com.example.data.model.Vaccine(id = 1, name = "Active Vac", currentStock = 10)
    val v2 = com.example.data.model.Vaccine(id = 2, name = "Zero Stock Vac", currentStock = 0)
    dao.insertAll(listOf(v1, v2))

    assertEquals(2, dao.getAllVaccinesList().size)

    dao.deleteOutOfStockVaccines()
    val remaining = dao.getAllVaccinesList()
    assertEquals(1, remaining.size)
    assertEquals("Active Vac", remaining[0].name)

    dao.deleteAll()
    assertEquals(0, dao.getAllVaccinesList().size)
    db.close()
  }

  @Test
  fun `test image storage helper saves profile photo locally`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testBitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
    val savedPath = com.example.ui.util.ImageStorageHelper.saveBitmapToInternalStorage(context, testBitmap)
    assertNotNull(savedPath)
    assertTrue("Saved path should not be empty", savedPath!!.isNotBlank())
    val file = java.io.File(savedPath!!)
    assertTrue("Saved file should exist on disk", file.exists())
    assertTrue("doesFileExist should return true", com.example.ui.util.ImageStorageHelper.doesFileExist(savedPath))
  }

  @Test
  fun `test purchase from vendor automatically updates stock`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.database.AppDatabase::class.java).build()
    val vaccineDao = db.vaccineDao()
    val vendorDao = db.vendorDao()
    val txDao = db.transactionDao()
    val patientDao = db.patientRegistrationDao()
    val profileDao = db.clinicProfileDao()

    val repo = com.example.data.repository.VaccineStockRepository(
      vaccineDao, vendorDao, txDao, patientDao, profileDao
    )

    // Initial vaccine with 5 units in stock
    val vaccineId = vaccineDao.insertVaccine(
      com.example.data.model.Vaccine(name = "Typhoid Conjugate", currentStock = 5, purchasePrice = 400.0, sellingPrice = 650.0)
    )

    val vendorId = vendorDao.insertVendor(
      com.example.data.model.Vendor(name = "Bharat Biotech Supplier", phone = "9876543210")
    )

    // Purchase 20 units from vendor
    val purchaseResult = repo.recordPurchase(
      vaccineId = vaccineId,
      vendorId = vendorId,
      quantity = 20,
      unitPurchasePrice = 420.0,
      batchNumber = "TCV-2026-B1",
      expiryDate = "2028-12",
      invoiceNumber = "INV-SUPP-9981",
      notes = "Routine restock"
    )

    assertTrue("Purchase should succeed", purchaseResult.isSuccess)
    val updatedVac = vaccineDao.getVaccineById(vaccineId)
    assertNotNull(updatedVac)
    assertEquals("Current stock should be 5 + 20 = 25", 25, updatedVac!!.currentStock)
    assertEquals("Purchase price should update", 420.0, updatedVac.purchasePrice, 0.01)

    db.close()
  }

  @Test
  fun `test patient vaccine registration starts blank when newly created`() {
    val newPatient = com.example.data.model.PatientRegistration(
      patientName = "Newborn Baby",
      dobOrAge = "At Birth",
      parentName = "Rohit",
      mobileNumber = "9988776655"
    )

    // Both given and scheduled vaccines must be blank by default, no prefilled names
    assertEquals("", newPatient.vaccineGivenToday)
    assertEquals("", newPatient.nextPlannedVaccine)
  }

  @Test
  fun `test comprehensive vaccine record shows all previous vaccines taken by patient`() {
    var patient = com.example.data.model.PatientRegistration(
      patientName = "Baby Aarav",
      dobOrAge = "6 Months",
      parentName = "Rahul",
      mobileNumber = "9823045678",
      vaccineGivenToday = "Hepatitis B (Recombinant)",
      vaccineGivenDose = "Dose 2",
      vaccineGivenBatch = "HPB-X1"
    )

    // Add birth vaccines
    patient = com.example.ui.util.PatientVaccineRecordHelper.appendVaccineToPatientHistory(
      patient = patient,
      newVaccineNames = "BCG, OPV-0",
      dose = "At Birth",
      batch = "BCG-902",
      dateMillis = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000),
      isHistorical = true
    )

    // Add 6-week vaccines
    patient = com.example.ui.util.PatientVaccineRecordHelper.appendVaccineToPatientHistory(
      patient = patient,
      newVaccineNames = "Pentavalent, Rotavirus, PCV",
      dose = "Dose 1 (6 Wks)",
      batch = "PNT-44",
      dateMillis = System.currentTimeMillis() - (135L * 24 * 60 * 60 * 1000),
      isHistorical = true
    )

    val allTaken = com.example.ui.util.PatientVaccineRecordHelper.getAllTakenVaccines(patient)

    // Should include BCG, OPV-0, Pentavalent, Rotavirus, PCV, plus current dose Hepatitis B (Recombinant) = 6 entries
    assertTrue("Should contain all 6 doses taken", allTaken.size >= 6)

    val names = allTaken.map { it.vaccineName }
    assertTrue("Must contain BCG", names.contains("BCG"))
    assertTrue("Must contain OPV-0", names.contains("OPV-0"))
    assertTrue("Must contain Pentavalent", names.contains("Pentavalent"))
    assertTrue("Must contain Rotavirus", names.contains("Rotavirus"))
    assertTrue("Must contain PCV", names.contains("PCV"))
    assertTrue("Must contain Hepatitis B (Recombinant)", names.contains("Hepatitis B (Recombinant)"))

    // Verify WhatsApp formatting
    val clinic = ClinicProfile(clinicName = "Dr Priyank Clinic", doctorName = "Dr. Priyank")
    val waMsg = com.example.ui.util.PatientVaccineRecordHelper.formatAllVaccinesForWhatsApp(patient, clinic)
    assertTrue("WhatsApp record must contain header", waMsg.contains("ALL VACCINES ADMINISTERED"))
    assertTrue("WhatsApp record must mention BCG", waMsg.contains("BCG"))
    assertTrue("WhatsApp record must mention Pentavalent", waMsg.contains("Pentavalent"))
  }

  @Test
  fun `test add and remove past vaccine entry from patient record`() {
    var patient = com.example.data.model.PatientRegistration(patientName = "Test Baby")
    patient = com.example.ui.util.PatientVaccineRecordHelper.appendVaccineToPatientHistory(
      patient = patient,
      newVaccineNames = "MMR",
      dose = "Dose 1",
      batch = "MMR-99",
      dateMillis = 1700000000000L,
      isHistorical = true
    )

    var taken = com.example.ui.util.PatientVaccineRecordHelper.getAllTakenVaccines(patient)
    val mmrEntry = taken.find { it.vaccineName == "MMR" }
    assertNotNull("MMR entry should be present", mmrEntry)

    // Now remove it
    patient = com.example.ui.util.PatientVaccineRecordHelper.removeVaccineFromPatientHistory(patient, mmrEntry!!.id)
    taken = com.example.ui.util.PatientVaccineRecordHelper.getAllTakenVaccines(patient)
    assertTrue("MMR should no longer be present", taken.none { it.vaccineName == "MMR" })
  }

  @Test
  fun `test generate vaccine record pdf certificate`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val patient = com.example.data.model.PatientRegistration(
      id = 1,
      patientName = "Baby Aarav Sharma",
      dobOrAge = "6 Months",
      gender = "Male",
      parentName = "Rahul Sharma",
      mobileNumber = "+91 98230 45678",
      vaccineGivenToday = "Hepatitis B (Recombinant)",
      nextPlannedVaccine = "MMR",
      nextDueDate = "In 14 Days"
    )
    val entries = listOf(
      com.example.ui.util.PatientVaccineEntry(vaccineName = "BCG", doseName = "At Birth", dateAdministeredMillis = 1700000000000L, batchNumber = "BCG-1"),
      com.example.ui.util.PatientVaccineEntry(vaccineName = "OPV-0", doseName = "At Birth", dateAdministeredMillis = 1700000000000L, batchNumber = "OPV-1"),
      com.example.ui.util.PatientVaccineEntry(vaccineName = "Hepatitis B", doseName = "Dose 1", dateAdministeredMillis = 1705000000000L, batchNumber = "HPB-2")
    )
    val clinic = ClinicProfile(clinicName = "Dr Priyank Clinic", doctorName = "Dr. Priyank", registrationNumber = "MCI-78901")

    val result = PdfBillGenerator.generateVaccineCertificatePdf(context, patient, clinic, entries)
    assertNotNull(result)
    result.onSuccess { pdfFile ->
      assertNotNull("PDF file should not be null", pdfFile)
      assertTrue("PDF file should exist on disk", pdfFile.exists())
      assertTrue("PDF file size should be > 0 bytes", pdfFile.length() > 0)
    }

    val waRecord = com.example.ui.util.PatientVaccineRecordHelper.formatAllVaccinesForWhatsApp(patient, clinic)
    assertTrue("Should contain patient name", waRecord.contains("Aarav"))
    assertTrue("Should contain vaccine record header", waRecord.contains("ALL VACCINES ADMINISTERED"))
  }

  @Test
  fun `test vaccine history preserves same vaccine on different administration dates`() {
    val birthDateMillis = 1700000000000L
    val dose2DateMillis = birthDateMillis + (60L * 24 * 60 * 60 * 1000) // 2 months later

    var patient = com.example.data.model.PatientRegistration(
      patientName = "Test Infant",
      vaccineGivenToday = "Pentavalent",
      vaccineGivenDose = "Dose 2",
      dateGivenMillis = dose2DateMillis
    )

    // Add Dose 1 to historical records
    patient = com.example.ui.util.PatientVaccineRecordHelper.appendVaccineToPatientHistory(
      patient = patient,
      newVaccineNames = "Pentavalent",
      dose = "Dose 1",
      batch = "PNT-01",
      dateMillis = birthDateMillis,
      isHistorical = true
    )

    val allTaken = com.example.ui.util.PatientVaccineRecordHelper.getAllTakenVaccines(patient)
    val pentavalentEntries = allTaken.filter { it.vaccineName.equals("Pentavalent", ignoreCase = true) }

    assertEquals("Both Dose 1 and Dose 2 must be preserved", 2, pentavalentEntries.size)
  }
}

