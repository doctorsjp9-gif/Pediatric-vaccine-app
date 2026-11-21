package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Vaccine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun authenticatedUserCanWriteAndReadOwnVaccine() = runBlocking {
        val uid = signInTestUser("doctor.test@clinic.com")
        val vaccine = Vaccine(
            id = 1L,
            name = "MMR Vaccine",
            category = "Pediatric",
            currentStock = 25
        )

        firestore.collection("users").document(uid).collection("vaccines")
            .document("1").set(vaccine).await()

        val snapshot = firestore.collection("users").document(uid).collection("vaccines")
            .document("1").get().await()

        assertTrue(snapshot.exists())
        val fetched = snapshot.toObject(Vaccine::class.java)
        assertEquals("MMR Vaccine", fetched?.name)
        assertEquals(25, fetched?.currentStock)
    }

    @Test
    fun crossUserAccessIsBlockedBySecurityRules() = runBlocking {
        val user1Uid = signInTestUser("doctor1@clinic.com")

        // Sign in as a different user
        val user2Uid = signInTestUser("doctor2@clinic.com")

        var permissionDenied = false
        try {
            // User 2 attempts to read User 1's private vaccine records
            firestore.collection("users").document(user1Uid).collection("vaccines")
                .document("1").get().await()
        } catch (e: Exception) {
            permissionDenied = true
        }

        assertTrue("Cross-user access must be rejected by security rules", permissionDenied)
    }
}
