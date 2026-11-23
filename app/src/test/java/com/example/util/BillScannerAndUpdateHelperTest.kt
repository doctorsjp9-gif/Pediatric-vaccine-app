package com.example.util

import com.example.data.model.Vaccine
import com.example.data.model.Vendor
import com.example.ui.util.AppUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BillScannerAndUpdateHelperTest {

    private val sampleCatalog = listOf(
        Vaccine(id = 1730000001001L, name = "Hexavalent (6-in-1)", code = "HEXA", currentStock = 10, purchasePrice = 1800.0, sellingPrice = 2400.0),
        Vaccine(id = 1730000002002L, name = "Pneumococcal (PCV 13)", code = "PCV", currentStock = 8, purchasePrice = 2800.0, sellingPrice = 3800.0),
        Vaccine(id = 1730000003003L, name = "Rotavirus Oral", code = "ROTA", currentStock = 15, purchasePrice = 900.0, sellingPrice = 1250.0)
    )

    private val sampleVendors = listOf(
        Vendor(id = 101L, name = "Apex Pharma Distributors", phone = "9876543210")
    )

    @Test
    fun parseStructuredBillJson_matchesByCatalogIndexAddsFreeQtyAndSkipsTaxRows() {
        val json = """
            {
              "vendorName": "Apex Pharma",
              "vendorPhone": "9876543210",
              "invoiceNumber": "INV-2026-441",
              "invoiceDate": "21/11/2026",
              "items": [
                {
                  "extractedName": "Hexaxim 0.5ml PFS",
                  "catalogIndex": 1,
                  "quantity": 10,
                  "freeQuantity": 2,
                  "unitPurchasePrice": 1750.0,
                  "sellingPriceOrMrp": 2450.0,
                  "batchNumber": "HX992A",
                  "expiryDate": "08/2028"
                },
                {
                  "extractedName": "Prevenar 13 Vial Inj",
                  "catalogIndex": 0,
                  "quantity": 5,
                  "freeQuantity": 0,
                  "unitPurchasePrice": 2700.0,
                  "sellingPriceOrMrp": 3800.0,
                  "batchNumber": "PV114",
                  "expiryDate": "10/2027"
                },
                {
                  "extractedName": "Grand Total",
                  "catalogIndex": 0,
                  "quantity": 1,
                  "freeQuantity": 0,
                  "unitPurchasePrice": 31000.0,
                  "sellingPriceOrMrp": 0.0,
                  "batchNumber": "",
                  "expiryDate": ""
                },
                {
                  "extractedName": "Cervavac HPV 0.5ml",
                  "catalogIndex": 0,
                  "quantity": 4,
                  "freeQuantity": 1,
                  "unitPurchasePrice": 1400.0,
                  "sellingPriceOrMrp": 2000.0,
                  "batchNumber": "CV77",
                  "expiryDate": "12/2028"
                }
              ]
            }
        """.trimIndent()

        val state = BillScannerHelper.parseStructuredBillJson(json, sampleCatalog, sampleVendors)
        assertTrue("Expected Success state, got $state", state is BillScanUiState.Success)
        val result = (state as BillScanUiState.Success).result

        assertEquals(101L, result.matchedVendorId)
        assertEquals("INV-2026-441", result.invoiceNumber)
        // Grand Total row must be filtered out -> 3 real vaccines remain
        assertEquals(3, result.items.size)

        // Item 1: matched by 1-based catalogIndex = 1 to 13-digit Long ID, billed 10 + free 2 = 12
        val item1 = result.items[0]
        assertEquals(1730000001001L, item1.matchedVaccineId)
        assertEquals("12", item1.quantityText)
        assertEquals("HX992A", item1.batchNumber)

        // Item 2: catalogIndex was 0, but brand synonym "Prevenar 13" must auto-match PCV catalog item!
        val item2 = result.items[1]
        assertEquals(1730000002002L, item2.matchedVaccineId)
        assertEquals("5", item2.quantityText)

        // Item 3: genuinely new vaccine (Cervavac HPV), billed 4 + free 1 = 5
        val item3 = result.items[2]
        assertEquals(null, item3.matchedVaccineId)
        assertEquals("5", item3.quantityText)
    }

    @Test
    fun parseStructuredBillJson_returnsManualReviewWhenNoItemsExtracted() {
        val emptyJson = """
            {
              "vendorName": "Unknown Pharmacy",
              "vendorPhone": "",
              "invoiceNumber": "B-001",
              "invoiceDate": "",
              "items": []
            }
        """.trimIndent()

        val state = BillScannerHelper.parseStructuredBillJson(emptyJson, sampleCatalog, sampleVendors)
        assertTrue("Expected ManualReviewNeeded when 0 items found, got $state", state is BillScanUiState.ManualReviewNeeded)
    }

    @Test
    fun normalizeDownloadUrl_convertsGoogleDriveDropboxAndGithubLinksCorrectly() {
        val driveShare = "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/view?usp=sharing"
        assertEquals(
            "https://drive.usercontent.google.com/download?id=1AbCdEfGhIjKlMnOpQrStUvWxYz&export=download&confirm=t",
            AppUpdateManager.normalizeDownloadUrl(driveShare)
        )

        val driveLegacyUc = "https://drive.google.com/uc?export=download&id=9ZyXwVuTsRqPoNm"
        assertEquals(
            "https://drive.usercontent.google.com/download?id=9ZyXwVuTsRqPoNm&export=download&confirm=t",
            AppUpdateManager.normalizeDownloadUrl(driveLegacyUc)
        )

        val dropbox = "https://www.dropbox.com/s/abc123/clinic_v2.7.apk?dl=0"
        assertEquals(
            "https://www.dropbox.com/s/abc123/clinic_v2.7.apk?dl=1",
            AppUpdateManager.normalizeDownloadUrl(dropbox)
        )

        val ghReleaseAsset = "https://github.com/drpriyank/clinic/releases/download/v2.7.0/app-release.apk"
        assertEquals(ghReleaseAsset, AppUpdateManager.normalizeDownloadUrl(ghReleaseAsset))

        val ghBlob = "https://github.com/drpriyank/clinic/blob/main/releases/app-release.apk"
        assertEquals(
            "https://raw.githubusercontent.com/drpriyank/clinic/main/releases/app-release.apk",
            AppUpdateManager.normalizeDownloadUrl(ghBlob)
        )
    }

    @Test
    fun isRemoteNewer_comparesVersionCodeAndSemVerAccurately() {
        assertTrue(AppUpdateManager.isRemoteNewer(3, "2.6.0", 2, "2.6.0"))
        assertTrue(AppUpdateManager.isRemoteNewer(2, "2.7.0", 2, "2.6.0"))
        assertTrue(AppUpdateManager.isRemoteNewer(2, "2.10.0", 2, "2.9.5"))
        assertFalse(AppUpdateManager.isRemoteNewer(2, "2.6.0", 3, "2.7.0"))
        assertFalse(AppUpdateManager.isRemoteNewer(3, "2.7.0", 3, "2.7.0"))
    }
}
