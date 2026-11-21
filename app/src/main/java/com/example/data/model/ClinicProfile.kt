package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clinic_profile")
data class ClinicProfile(
    @PrimaryKey
    val id: Int = 1,
    val clinicName: String = "Dr Priyank Vaccine Clinic",
    val doctorName: String = "Dr. Priyank, MD (Pediatrics)",
    val phone: String = "+91 98765 43210",
    val email: String = "drpriyank.clinic@gmail.com",
    val address: String = "102, Shivalik Medical Center, Ring Road, Ahmedabad",
    val registrationNumber: String = "GMC-REG-29182",
    val logoType: String = "CUTE_TEDDY", // CUTE_TEDDY, SHIELD, SYRINGE, STETHOSCOPE, HEART
    val themeColor: String = "TEAL", // TEAL, BLUE, EMERALD, CORAL, PURPLE
    val billFooterNote: String = "Wishing your little one good health, bright smiles & strong immunity! 🌟",
    val customWhatsappMessage: String = "",
    val photoUri: String = ""
)
