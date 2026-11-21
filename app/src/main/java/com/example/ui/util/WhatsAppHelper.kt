package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import java.io.File
import java.net.URLEncoder

object WhatsAppHelper {

    /**
     * Cleans phone number string to international format (defaulting to +91 India if 10 digits).
     */
    fun cleanPhoneNumber(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits" // Standard Indian mobile without country code
            digits.startsWith("0") && digits.length == 11 -> "91${digits.substring(1)}"
            digits.startsWith("91") && digits.length == 12 -> digits
            else -> digits
        }
    }

    /**
     * Formats a polite, clear, professional vaccine reminder message for parents in
     * Hindi (हिंदी), English, or Bilingual (हिंदी + English).
     */
    fun buildVaccineReminderMessage(
        patient: PatientRegistration,
        clinic: ClinicProfile,
        language: ReminderLanguage = AppLanguageManager.currentWhatsAppLanguage
    ): String {
        return when (language) {
            ReminderLanguage.HINDI -> buildHindiVaccineReminderMessage(patient, clinic)
            ReminderLanguage.ENGLISH -> buildEnglishVaccineReminderMessage(patient, clinic)
            ReminderLanguage.BILINGUAL -> {
                buildHindiVaccineReminderMessage(patient, clinic) +
                    "\n\n━━━━━━━━━━━━━━━━━━━━\n\n" +
                    buildEnglishVaccineReminderMessage(patient, clinic)
            }
        }
    }

    /**
     * Formats a polite, clear Hindi (हिंदी) vaccine reminder message for parents on WhatsApp.
     */
    fun buildHindiVaccineReminderMessage(
        patient: PatientRegistration,
        clinic: ClinicProfile
    ): String {
        val clinicName = clinic.clinicName.ifBlank { "Dr Priyank Vaccine Clinic" }
        val parentName = patient.parentName.ifBlank { "अभिभावक (Parent)" }
        val plannedVaccines = patient.nextPlannedVaccine.ifBlank { "निर्धारित टीका (Scheduled Vaccine)" }

        val nextVaccinesText = if (plannedVaccines.contains(",")) {
            val list = plannedVaccines.split(",").map { it.trim() }.filter { it.isNotBlank() }
            "💉 *अगले निर्धारित टीके (Next Vaccines):*\n" + list.joinToString("\n") { "   • $it" }
        } else {
            "💉 *अगला निर्धारित टीका (Next Vaccine):* $plannedVaccines"
        }

        val prevDoseText = if (patient.vaccineGivenToday.isNotBlank()) {
            val givenList = patient.vaccineGivenToday.split(",").map { it.trim() }.filter { it.isNotBlank() }
            "📌 *पिछला दिया गया टीका:* ${givenList.joinToString(", ")} (${patient.vaccineGivenDose})"
        } else ""

        return """
*${clinicName}* 🩺
*टीकाकरण (वैक्सीन) रिमाइंडर — ${patient.patientName}*

नमस्ते ${parentName} जी 🙏,

यह आपको सूचित करने के लिए एक विनम्र रिमाइंडर है कि आपके बच्चे *${patient.patientName}* (${patient.dobOrAge}) के अगले टीकाकरण (Vaccination) का समय हो गया है:

$nextVaccinesText
📅 *टीकाकरण की निर्धारित तिथि (Due Date):* ${patient.nextDueDate}
${if (prevDoseText.isNotBlank()) "$prevDoseText\n" else ""}
समय पर टीकाकरण आपके बच्चे को गंभीर बीमारियों से सुरक्षित रखता है और मजबूत रोग-प्रतिरोधक क्षमता (Immunity) प्रदान करता है। कृपया टीका लगवाने के लिए हमारे क्लिनिक पधारें या अपॉइंटमेंट के लिए संपर्क करें:

📍 *क्लिनिक का पता:* ${clinic.address}
📞 *संपर्क / WhatsApp:* ${clinic.phone}
👨‍⚕️ *डॉक्टर:* ${clinic.doctorName}

_आपके बच्चे के उत्तम स्वास्थ्य और उज्ज्वल भविष्य की कामना!_ 🌟
        """.trimIndent()
    }

    /**
     * Formats a polite, clear English vaccine reminder message for parents.
     */
    fun buildEnglishVaccineReminderMessage(
        patient: PatientRegistration,
        clinic: ClinicProfile
    ): String {
        val clinicName = clinic.clinicName.ifBlank { "Dr Priyank Vaccine Clinic" }
        val nextVaccinesText = if (patient.nextPlannedVaccine.contains(",")) {
            val list = patient.nextPlannedVaccine.split(",").map { it.trim() }.filter { it.isNotBlank() }
            "💉 *Next Planned Vaccines:*\n" + list.joinToString("\n") { "   • $it" }
        } else {
            "💉 *Next Planned Vaccine:* ${patient.nextPlannedVaccine}"
        }

        val prevDoseText = if (patient.vaccineGivenToday.isNotBlank()) {
            if (patient.vaccineGivenToday.contains(",")) {
                val list = patient.vaccineGivenToday.split(",").map { it.trim() }.filter { it.isNotBlank() }
                "📌 *Administered Vaccines:* ${list.joinToString(", ")} (${patient.vaccineGivenDose})"
            } else {
                "📌 *Administered Dose:* ${patient.vaccineGivenToday} (${patient.vaccineGivenDose})"
            }
        } else ""

        return """
*${clinicName}* 🩺
*VACCINATION REMINDER FOR ${patient.patientName.uppercase()}*

Dear ${patient.parentName.ifBlank { "Parent" }},

This is a gentle reminder that your child *${patient.patientName}* (${patient.dobOrAge}) is due for scheduled vaccination:

$nextVaccinesText
📅 *Scheduled Due Date:* ${patient.nextDueDate}
${if (prevDoseText.isNotBlank()) "$prevDoseText\n" else ""}
Timely immunization provides vital defense against preventable diseases. Please visit our clinic or contact us to schedule the dose:

📍 *Clinic:* ${clinic.address}
📞 *Call / WhatsApp:* ${clinic.phone}
👨‍⚕️ *Pediatrician:* ${clinic.doctorName}

_${clinic.billFooterNote}_ 🌟
        """.trimIndent()
    }

    /**
     * Formats a professional text bill summary for WhatsApp (supports Hindi & English).
     */
    fun buildTextBillMessage(
        billData: BillData,
        clinic: ClinicProfile
    ): String {
        val useHindi = AppLanguageManager.currentAppLanguage == AppLanguage.HINDI ||
            AppLanguageManager.currentWhatsAppLanguage == ReminderLanguage.HINDI

        val itemsList = billData.items.mapIndexed { index, item ->
            " ${index + 1}. *${item.description}* (Batch: ${item.batchNumber.ifBlank { "N/A" }}) - Qty: ${item.quantity} × ₹${String.format("%.2f", item.unitPrice)} = *₹${String.format("%.2f", item.totalAmount)}*"
        }.joinToString("\n")

        if (useHindi) {
            val nextVaccineNote = if (billData.nextVaccine.isNotBlank()) {
                "\n\n🗓️ *अगला निर्धारित टीका:* ${billData.nextVaccine} (तिथि: ${billData.nextDueDate})"
            } else ""

            val signatureNote = if (billData.isDigitallySigned) {
                val signer = billData.signerName.ifBlank { clinic.doctorName }
                "\n━━━━━━━━━━━━━━━━━━━━\n✍️ *डिजिटल हस्ताक्षर:* $signer\n🔐 *सत्यापन ID:* ${billData.signatureId.ifBlank { "VERIFIED" }}"
            } else ""

            return """
*${clinic.clinicName}* 🩺
*टीकाकरण रसीद / बिल (IMMUNIZATION BILL)*
━━━━━━━━━━━━━━━━━━━━
📄 *बिल नं:* ${billData.invoiceNumber}
👶 *बच्चे का नाम:* ${billData.patientName} (${billData.ageOrDob})
👨‍👩‍👦 *अभिभावक:* ${billData.parentName}
📞 *मोबाइल:* ${billData.mobileNumber}
━━━━━━━━━━━━━━━━━━━━
*दिए गए टीके (Administered Vaccines):*
$itemsList
━━━━━━━━━━━━━━━━━━━━
💵 *कुल भुगतान राशि:* ₹${String.format("%.2f", billData.grandTotal)}$nextVaccineNote$signatureNote

📍 *क्लिनिक का पता:* ${clinic.address}
📞 *हेल्पलाइन:* ${clinic.phone}

_आपके बच्चे के उत्तम स्वास्थ्य की कामना!_ 🌟
            """.trimIndent()
        }

        val nextVaccineNote = if (billData.nextVaccine.isNotBlank()) {
            "\n\n🗓️ *Next Planned Vaccine:* ${billData.nextVaccine} (Due: ${billData.nextDueDate})"
        } else ""

        val signatureNote = if (billData.isDigitallySigned) {
            val signer = billData.signerName.ifBlank { clinic.doctorName }
            "\n━━━━━━━━━━━━━━━━━━━━\n✍️ *Digitally Signed by:* $signer\n🔐 *Verification ID:* ${billData.signatureId.ifBlank { "VERIFIED" }}"
        } else ""

        return """
*${clinic.clinicName}* 🩺
*IMMUNIZATION RECEIPT / BILL*
━━━━━━━━━━━━━━━━━━━━
📄 *Bill No:* ${billData.invoiceNumber}
👶 *Patient:* ${billData.patientName} (${billData.ageOrDob})
👨‍👩‍👦 *Parent:* ${billData.parentName}
📞 *Mobile:* ${billData.mobileNumber}
━━━━━━━━━━━━━━━━━━━━
*Administered Vaccines:*
$itemsList
━━━━━━━━━━━━━━━━━━━━
💵 *Total Amount Paid:* ₹${String.format("%.2f", billData.grandTotal)}$nextVaccineNote$signatureNote

📍 *Address:* ${clinic.address}
📞 *Clinic Helpline:* ${clinic.phone}

_${clinic.billFooterNote}_
        """.trimIndent()
    }

    /**
     * Sends the generated PDF Bill directly via WhatsApp or standard Android Share.
     */
    fun shareBillPdf(
        context: Context,
        pdfFile: File,
        patientPhone: String,
        billData: BillData,
        clinic: ClinicProfile
    ) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val useHindi = AppLanguageManager.currentAppLanguage == AppLanguage.HINDI ||
                AppLanguageManager.currentWhatsAppLanguage == ReminderLanguage.HINDI

            val shareText = if (useHindi) {
                "नमस्ते ${billData.parentName.ifBlank { billData.patientName }} जी 🙏, ${clinic.clinicName} की ओर से आपके बच्चे (${billData.patientName}) का टीकाकरण बिल (बिल नं: ${billData.invoiceNumber}, कुल राशि: ₹${String.format("%.2f", billData.grandTotal)}) संलग्न है। उत्तम स्वास्थ्य की कामना!"
            } else {
                "Dear ${billData.parentName.ifBlank { billData.patientName }}, please find attached the vaccination bill (Bill No: ${billData.invoiceNumber}) from ${clinic.clinicName}. Total Amount: ₹${String.format("%.2f", billData.grandTotal)}. Wishing good health!"
            }

            val cleanPhone = cleanPhoneNumber(patientPhone)

            // Primary intent for WhatsApp
            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                if (cleanPhone.isNotBlank()) {
                    putExtra("jid", "$cleanPhone@s.whatsapp.net")
                }
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (whatsappIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(whatsappIntent)
            } else {
                // Fallback to chooser for WhatsApp Business, Telegram, or any other app
                val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(chooserIntent, "Share Vaccine Bill PDF").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing bill: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Shares official vaccination certificate PDF via WhatsApp or any available app.
     */
    fun shareVaccineCertificatePdf(
        context: Context,
        pdfFile: File,
        patientPhone: String,
        patientName: String,
        clinicName: String
    ) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val useHindi = AppLanguageManager.currentAppLanguage == AppLanguage.HINDI ||
                AppLanguageManager.currentWhatsAppLanguage == ReminderLanguage.HINDI

            val shareText = if (useHindi) {
                "$clinicName की ओर से $patientName का आधिकारिक टीकाकरण रिकॉर्ड और प्रमाण-पत्र (Vaccination Certificate PDF) संलग्न है। कृपया इसे स्वास्थ्य एवं स्कूल प्रवेश रिकॉर्ड के लिए सुरक्षित रखें।"
            } else {
                "Official Child Vaccination Record & Immunization Certificate for $patientName from $clinicName. Please preserve for health and school admission records."
            }
            val cleanPhone = cleanPhoneNumber(patientPhone)

            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                if (cleanPhone.isNotBlank()) {
                    putExtra("jid", "$cleanPhone@s.whatsapp.net")
                }
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (whatsappIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(whatsappIntent)
            } else {
                val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(chooserIntent, "Share Vaccination Record PDF").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing certificate: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens PDF file with any installed PDF viewer, falling back to Android's system PDF print/save preview.
     */
    fun openPdfFile(context: Context, pdfFile: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            PdfBillGenerator.printPdfFile(context, pdfFile, pdfFile.nameWithoutExtension)
        }
    }

    /**
     * Opens WhatsApp chat directly to send text message to patient/parent.
     */
    fun sendWhatsAppText(
        context: Context,
        phone: String,
        message: String
    ) {
        try {
            val cleanPhone = cleanPhoneNumber(phone)
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val url = if (cleanPhone.isNotBlank()) {
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage"
            } else {
                "https://api.whatsapp.com/send?text=$encodedMessage"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general text share
            try {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
                val chooser = Intent.createChooser(sendIntent, "Send via WhatsApp").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (ex: Exception) {
                Toast.makeText(context, "Could not open WhatsApp: ${ex.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
