package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testMultiVaccinePurchaseTotals() {
    // Vaccine 1: 20 doses @ ₹320 = ₹6,400
    // Vaccine 2: 15 doses @ ₹550 = ₹8,250
    // Vaccine 3: 30 doses @ ₹950 = ₹28,500
    val item1Total = 20 * 320.0
    val item2Total = 15 * 550.0
    val item3Total = 30 * 950.0
    val grandTotal = item1Total + item2Total + item3Total
    val totalDoses = 20 + 15 + 30

    assertEquals(65, totalDoses)
    assertEquals(43150.0, grandTotal, 0.001)
  }

  @Test
  fun testMultiVaccineSaleProfitCalculation() {
    // Item 1: Hepatitis B (2 doses @ ₹750 sell, cost ₹320) -> Revenue ₹1500, Cost ₹640, Profit ₹860
    val rev1 = 2 * 750.0
    val cost1 = 2 * 320.0
    val profit1 = rev1 - cost1

    // Item 2: MMR (3 doses @ ₹1200 sell, cost ₹550) -> Revenue ₹3600, Cost ₹1650, Profit ₹1950
    val rev2 = 3 * 1200.0
    val cost2 = 3 * 550.0
    val profit2 = rev2 - cost2

    val totalRevenue = rev1 + rev2
    val totalCost = cost1 + cost2
    val totalProfit = profit1 + profit2

    assertEquals(5100.0, totalRevenue, 0.001)
    assertEquals(2290.0, totalCost, 0.001)
    assertEquals(2810.0, totalProfit, 0.001)
    assertTrue(totalProfit > 0)
  }
}
