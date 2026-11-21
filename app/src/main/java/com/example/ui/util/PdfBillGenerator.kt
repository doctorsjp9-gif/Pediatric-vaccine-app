package com.example.ui.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import com.example.R
import com.example.data.model.ClinicProfile
import com.example.data.model.PatientRegistration
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BillItem(
    val description: String,
    val batchNumber: String = "",
    val quantity: Int = 1,
    val unitPrice: Double,
    val totalAmount: Double = unitPrice * quantity
)

data class BillData(
    val invoiceNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val patientName: String,
    val parentName: String = "",
    val mobileNumber: String = "",
    val ageOrDob: String = "",
    val items: List<BillItem>,
    val nextVaccine: String = "",
    val nextDueDate: String = "",
    val discount: Double = 0.0,
    val notes: String = "",
    val isDigitallySigned: Boolean = false,
    val signatureBitmap: Bitmap? = null,
    val signatureDate: Long? = null,
    val signatureId: String = "",
    val signerName: String = "",
    val signerRegNo: String = "",
    val signatureType: String = "DRAWN"
) {
    val subtotal: Double get() = items.sumOf { it.totalAmount }
    val grandTotal: Double get() = (subtotal - discount).coerceAtLeast(0.0)
}

object PdfBillGenerator {

    private fun drawCleanAlignedHeader(
        context: Context,
        canvas: Canvas,
        pageWidth: Int,
        clinic: ClinicProfile,
        documentTitle: String,
        docRefLabel: String,
        docRefValue: String,
        dateLabel: String,
        dateValue: String
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Main Header Background (Rich Medical Teal)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#005B66")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 104f, paint)

        // Top subtle highlight bar
        paint.color = Color.parseColor("#00838F")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 6f, paint)

        // Bottom colorful dual accent strip
        paint.color = Color.parseColor("#26C6DA")
        canvas.drawRect(0f, 100f, pageWidth.toFloat(), 104f, paint)
        paint.color = Color.parseColor("#FFB300")
        canvas.drawRect(0f, 100f, 190f, 104f, paint)

        // Clinic Logo / Doctor Photo on the Left (Custom photoUri or bundled Dr. Priyank Patel Logo)
        var leftStartX = 28f
        try {
            val photoBitmap: Bitmap? = if (clinic.photoUri.isNotBlank()) {
                val photoFile = File(clinic.photoUri)
                if (photoFile.exists()) {
                    android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                } else {
                    android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.dr_priyank_logo_full)
                }
            } else {
                android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.dr_priyank_logo_full)
            }
            if (photoBitmap != null) {
                val scaledPhoto = Bitmap.createScaledBitmap(photoBitmap, 60, 60, true)
                val photoX = 24f
                val photoY = 20f
                paint.color = Color.WHITE
                canvas.drawRoundRect(RectF(photoX - 2f, photoY - 2f, photoX + 62f, photoY + 62f), 8f, 8f, paint)
                canvas.drawBitmap(scaledPhoto, photoX, photoY, null)
                leftStartX = 96f
            }
        } catch (_: Exception) {
        }

        // Left Column: Clinic Name, Doctor Name, Aligned Reg. No., and Phone/Address
        val safeClinicName = clinic.clinicName.ifBlank { "Pediatric & Vaccine Clinic" }
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 17f
        val displayClinicName = if (safeClinicName.length > 30) safeClinicName.take(28) + "…" else safeClinicName
        canvas.drawText(displayClinicName, leftStartX, 30f, textPaint)

        // Doctor Name
        textPaint.color = Color.parseColor("#E0F7FA")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 11.5f
        val safeDoctorName = clinic.doctorName.ifBlank { "Consultant Pediatrician" }
        canvas.drawText(safeDoctorName.take(36), leftStartX, 49f, textPaint)

        // Strictly Aligned Registration Number Row in Header
        val safeRegNo = clinic.registrationNumber.ifBlank { "N/A" }
        textPaint.color = Color.parseColor("#80DEEA")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 9.5f
        canvas.drawText("Reg. No.", leftStartX, 67f, textPaint)
        canvas.drawText(":", leftStartX + 48f, 67f, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText(safeRegNo, leftStartX + 56f, 67f, textPaint)

        // Strictly Aligned Phone & Address Row in Header
        textPaint.color = Color.parseColor("#80DEEA")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 9f
        canvas.drawText("Clinic Ph", leftStartX, 84f, textPaint)
        canvas.drawText(":", leftStartX + 48f, 84f, textPaint)
        textPaint.color = Color.parseColor("#E0F2F1")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val contactAndAddr = buildString {
            if (clinic.phone.isNotBlank()) append(clinic.phone)
            if (clinic.address.isNotBlank()) {
                if (isNotEmpty()) append("  |  ")
                append(clinic.address.take(32))
            }
        }.ifBlank { "Authorized Immunization Center" }
        canvas.drawText(contactAndAddr, leftStartX + 56f, 84f, textPaint)

        // Right Column: Structured Header Metadata Box (Always cleanly aligned on right)
        val rightBoxLeft = 366f
        val rightBoxRight = pageWidth - 26f
        paint.color = Color.parseColor("#00444D")
        canvas.drawRoundRect(RectF(rightBoxLeft, 13f, rightBoxRight, 91f), 8f, 8f, paint)

        paint.color = Color.parseColor("#4DD0E1")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(rightBoxLeft, 13f, rightBoxRight, 91f), 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Badge Header inside Right Box
        paint.color = Color.parseColor("#00838F")
        canvas.drawRoundRect(RectF(rightBoxLeft + 1f, 14f, rightBoxRight - 1f, 34f), 7f, 7f, paint)

        textPaint.color = Color.parseColor("#FFF59D")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 10f
        val titleWidth = textPaint.measureText(documentTitle)
        val titleX = rightBoxLeft + ((rightBoxRight - rightBoxLeft) - titleWidth) / 2f
        canvas.drawText(documentTitle, titleX, 27.5f, textPaint)

        // Aligned Key-Value Rows inside Right Header Box
        val rLabelX = rightBoxLeft + 10f
        val rColonX = rightBoxLeft + 68f
        val rValueX = rightBoxLeft + 76f

        // Row 1: Invoice / Cert Ref
        textPaint.textSize = 9f
        textPaint.color = Color.parseColor("#B2EBF2")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(docRefLabel, rLabelX, 50f, textPaint)
        canvas.drawText(":", rColonX, 50f, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText(docRefValue.take(18), rValueX, 50f, textPaint)

        // Row 2: Date
        textPaint.color = Color.parseColor("#B2EBF2")
        canvas.drawText(dateLabel, rLabelX, 66f, textPaint)
        canvas.drawText(":", rColonX, 66f, textPaint)
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(dateValue.take(20), rValueX, 66f, textPaint)

        // Row 3: Doctor Registration No (Aligned)
        textPaint.color = Color.parseColor("#B2EBF2")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Reg. No.", rLabelX, 82f, textPaint)
        canvas.drawText(":", rColonX, 82f, textPaint)
        textPaint.color = Color.parseColor("#FFF59D")
        canvas.drawText(safeRegNo.take(18), rValueX, 82f, textPaint)
    }

    /**
     * Generates a clean, professional medical invoice PDF (Standard A4: 595 x 842 points)
     * with strictly aligned header and Registration Number columns.
     */
    fun generateBillPdf(
        context: Context,
        billData: BillData,
        clinic: ClinicProfile
    ): Result<File> {
        return try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            val dateOnlyFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val timeOnlyFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val billDateStr = dateOnlyFormat.format(Date(billData.timestamp))
            val billTimeStr = timeOnlyFormat.format(Date(billData.timestamp))

            // 1. Clean Aligned Header Banner
            drawCleanAlignedHeader(
                context = context,
                canvas = canvas,
                pageWidth = pageWidth,
                clinic = clinic,
                documentTitle = "IMMUNIZATION BILL / RECEIPT",
                docRefLabel = "Bill No.",
                docRefValue = billData.invoiceNumber,
                dateLabel = "Bill Date",
                dateValue = billDateStr
            )

            // 2. Patient & Clinical Registration Info Card (Two-column aligned grid)
            var currentY = 118f
            val cardHeight = 82f
            val cardLeft = 26f
            val cardRight = pageWidth - 26f

            // Card fill
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F4FBFB")
            canvas.drawRoundRect(RectF(cardLeft, currentY, cardRight, currentY + cardHeight), 8f, 8f, paint)

            // Card border
            paint.color = Color.parseColor("#B2DFDB")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(RectF(cardLeft, currentY, cardRight, currentY + cardHeight), 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            // Center vertical divider inside info card
            val midDividerX = 310f
            paint.color = Color.parseColor("#CFE8E8")
            canvas.drawLine(midDividerX, currentY + 10f, midDividerX, currentY + cardHeight - 10f, paint)

            // Helper to draw strictly aligned label : value rows
            fun drawAlignedGridRow(
                labelX: Float,
                colonX: Float,
                valueX: Float,
                y: Float,
                label: String,
                value: String,
                highlightValue: Boolean = false
            ) {
                textPaint.textSize = 9.5f
                textPaint.color = Color.parseColor("#455A64")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(label, labelX, y, textPaint)
                canvas.drawText(":", colonX, y, textPaint)

                textPaint.color = if (highlightValue) Color.parseColor("#005B66") else Color.parseColor("#191C1D")
                textPaint.typeface = Typeface.create(
                    Typeface.DEFAULT,
                    if (highlightValue) Typeface.BOLD else Typeface.NORMAL
                )
                canvas.drawText(value, valueX, y, textPaint)
            }

            // Left Column: Patient Details
            val leftLabelX = 38f
            val leftColonX = 122f
            val leftValX = 130f

            drawAlignedGridRow(
                leftLabelX, leftColonX, leftValX, currentY + 20f,
                "Patient / Child", billData.patientName.ifBlank { "Walk-in Patient" }.take(28),
                highlightValue = true
            )
            drawAlignedGridRow(
                leftLabelX, leftColonX, leftValX, currentY + 38f,
                "Age / DOB", billData.ageOrDob.ifBlank { "Not specified" }.take(26)
            )
            drawAlignedGridRow(
                leftLabelX, leftColonX, leftValX, currentY + 56f,
                "Parent / Guard.", billData.parentName.ifBlank { "—" }.take(26)
            )
            drawAlignedGridRow(
                leftLabelX, leftColonX, leftValX, currentY + 74f,
                "Mobile No.", billData.mobileNumber.ifBlank { "—" }.take(20)
            )

            // Right Column: Doctor Registration & Bill Metadata
            val rightLabelX = 322f
            val rightColonX = 406f
            val rightValX = 414f

            drawAlignedGridRow(
                rightLabelX, rightColonX, rightValX, currentY + 20f,
                "Consultant Dr.", clinic.doctorName.ifBlank { "Doctor" }.take(24),
                highlightValue = true
            )
            drawAlignedGridRow(
                rightLabelX, rightColonX, rightValX, currentY + 38f,
                "Reg. Number", clinic.registrationNumber.ifBlank { "N/A" }.take(24),
                highlightValue = true
            )
            drawAlignedGridRow(
                rightLabelX, rightColonX, rightValX, currentY + 56f,
                "Date & Time", "$billDateStr, $billTimeStr"
            )
            drawAlignedGridRow(
                rightLabelX, rightColonX, rightValX, currentY + 74f,
                "Department", "Pediatric Immunization"
            )

            currentY += cardHeight + 16f

            // 3. Itemized Vaccine Table Header
            paint.color = Color.parseColor("#006874")
            canvas.drawRoundRect(RectF(cardLeft, currentY, cardRight, currentY + 26f), 5f, 5f, paint)

            textPaint.color = Color.WHITE
            textPaint.textSize = 9.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            canvas.drawText("#", 36f, currentY + 17f, textPaint)
            canvas.drawText("Vaccine / Immunization Description", 62f, currentY + 17f, textPaint)
            canvas.drawText("Batch No.", 290f, currentY + 17f, textPaint)
            canvas.drawText("Qty", 385f, currentY + 17f, textPaint)
            canvas.drawText("Unit Price", 430f, currentY + 17f, textPaint)
            canvas.drawText("Total (INR)", 502f, currentY + 17f, textPaint)

            currentY += 26f

            // Table Rows
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.color = Color.parseColor("#263238")
            textPaint.textSize = 9.5f

            var itemIndex = 1
            for (item in billData.items) {
                if (itemIndex % 2 == 0) {
                    paint.color = Color.parseColor("#F4FAFA")
                    canvas.drawRect(cardLeft, currentY, cardRight, currentY + 28f, paint)
                }

                paint.color = Color.parseColor("#E0E0E0")
                canvas.drawLine(cardLeft, currentY + 28f, cardRight, currentY + 28f, paint)

                textPaint.color = Color.parseColor("#263238")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("$itemIndex", 36f, currentY + 18f, textPaint)

                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(item.description.take(36), 62f, currentY + 18f, textPaint)

                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(item.batchNumber.ifBlank { "—" }.take(14), 290f, currentY + 18f, textPaint)
                canvas.drawText("${item.quantity}", 390f, currentY + 18f, textPaint)
                canvas.drawText(String.format(Locale.getDefault(), "Rs. %.2f", item.unitPrice), 430f, currentY + 18f, textPaint)

                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.color = Color.parseColor("#004D40")
                canvas.drawText(String.format(Locale.getDefault(), "Rs. %.2f", item.totalAmount), 502f, currentY + 18f, textPaint)

                currentY += 28f
                itemIndex++
            }

            currentY += 14f

            // 4. Totals Summary Box (Right aligned, clean label & amount alignment)
            val totalsBoxLeft = 336f
            val totalsHeight = if (billData.discount > 0.0) 70f else 56f
            paint.color = Color.parseColor("#E8F5E9")
            canvas.drawRoundRect(RectF(totalsBoxLeft, currentY, cardRight, currentY + totalsHeight), 8f, 8f, paint)

            paint.color = Color.parseColor("#81C784")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(RectF(totalsBoxLeft, currentY, cardRight, currentY + totalsHeight), 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            textPaint.color = Color.parseColor("#2E7D32")
            textPaint.textSize = 10f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Subtotal", totalsBoxLeft + 14f, currentY + 20f, textPaint)
            canvas.drawText(":", totalsBoxLeft + 96f, currentY + 20f, textPaint)
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(String.format(Locale.getDefault(), "Rs. %.2f", billData.subtotal), totalsBoxLeft + 106f, currentY + 20f, textPaint)

            var grandTotalY = currentY + 44f
            if (billData.discount > 0.0) {
                canvas.drawText("Discount", totalsBoxLeft + 14f, currentY + 36f, textPaint)
                canvas.drawText(":", totalsBoxLeft + 96f, currentY + 36f, textPaint)
                canvas.drawText(String.format(Locale.getDefault(), "- Rs. %.2f", billData.discount), totalsBoxLeft + 106f, currentY + 36f, textPaint)
                grandTotalY = currentY + 56f
            }

            paint.color = Color.parseColor("#A5D6A7")
            canvas.drawLine(totalsBoxLeft + 10f, grandTotalY - 14f, cardRight - 10f, grandTotalY - 14f, paint)

            textPaint.color = Color.parseColor("#1B5E20")
            textPaint.textSize = 12f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Total Paid", totalsBoxLeft + 14f, grandTotalY, textPaint)
            canvas.drawText(":", totalsBoxLeft + 96f, grandTotalY, textPaint)
            canvas.drawText(String.format(Locale.getDefault(), "Rs. %.2f", billData.grandTotal), totalsBoxLeft + 106f, grandTotalY, textPaint)

            currentY += totalsHeight + 16f

            // 5. Next Planned Vaccine Callout Box (if specified)
            if (billData.nextVaccine.isNotBlank()) {
                paint.color = Color.parseColor("#FFF8E1")
                canvas.drawRoundRect(RectF(cardLeft, currentY, cardRight, currentY + 52f), 8f, 8f, paint)

                paint.color = Color.parseColor("#FFE082")
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(RectF(cardLeft, currentY, cardRight, currentY + 52f), 8f, 8f, paint)
                paint.style = Paint.Style.FILL

                textPaint.color = Color.parseColor("#E65100")
                textPaint.textSize = 10f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("NEXT VACCINATION APPOINTMENT SCHEDULE", 40f, currentY + 19f, textPaint)

                textPaint.color = Color.parseColor("#37474F")
                textPaint.textSize = 9.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Planned Vaccine :  ${billData.nextVaccine}", 40f, currentY + 37f, textPaint)
                if (billData.nextDueDate.isNotBlank()) {
                    canvas.drawText("Due Date :  ${billData.nextDueDate}", 350f, currentY + 37f, textPaint)
                }

                currentY += 66f
            }

            // 6. Footer, Doctor Signature & Aligned Registration Number
            val footerY = pageHeight - 105f

            if (billData.isDigitallySigned) {
                val sigBitmap = billData.signatureBitmap
                if (sigBitmap != null) {
                    val sigRect = RectF((pageWidth - 210).toFloat(), footerY - 38f, (pageWidth - 32).toFloat(), footerY + 20f)
                    canvas.drawBitmap(sigBitmap, null, sigRect, null)
                }

                paint.color = Color.parseColor("#15803D")
                paint.strokeWidth = 1.5f
                canvas.drawLine((pageWidth - 210).toFloat(), footerY + 24f, (pageWidth - 32).toFloat(), footerY + 24f, paint)

                textPaint.color = Color.parseColor("#15803D")
                textPaint.textSize = 9f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("✔ DIGITALLY SIGNED & VERIFIED", (pageWidth - 208).toFloat(), footerY + 37f, textPaint)

                textPaint.color = Color.parseColor("#191C1D")
                textPaint.textSize = 9f
                val signerDisplay = billData.signerName.ifBlank { clinic.doctorName }
                canvas.drawText(signerDisplay, (pageWidth - 208).toFloat(), footerY + 49f, textPaint)

                textPaint.color = Color.parseColor("#005B66")
                textPaint.textSize = 8.5f
                val regDisplay = billData.signerRegNo.ifBlank { clinic.registrationNumber }
                canvas.drawText("Reg. No. : $regDisplay", (pageWidth - 208).toFloat(), footerY + 61f, textPaint)

                textPaint.textSize = 7.5f
                textPaint.color = Color.parseColor("#059669")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val sigDate = billData.signatureDate ?: billData.timestamp
                val sigDateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(sigDate))
                val refStr = "ID: ${billData.signatureId.ifBlank { "VALID" }} • $sigDateStr"
                canvas.drawText(refStr.take(36), (pageWidth - 208).toFloat(), footerY + 72f, textPaint)
            } else {
                paint.color = Color.parseColor("#90A4AE")
                paint.strokeWidth = 1f
                canvas.drawLine((pageWidth - 210).toFloat(), footerY + 24f, (pageWidth - 32).toFloat(), footerY + 24f, paint)

                textPaint.color = Color.parseColor("#263238")
                textPaint.textSize = 9.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(clinic.doctorName, (pageWidth - 205).toFloat(), footerY + 38f, textPaint)

                textPaint.color = Color.parseColor("#005B66")
                textPaint.textSize = 8.5f
                canvas.drawText("Reg. No. : ${clinic.registrationNumber.ifBlank { "N/A" }}", (pageWidth - 205).toFloat(), footerY + 50f, textPaint)

                textPaint.color = Color.parseColor("#607D8B")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Authorized Signatory", (pageWidth - 205).toFloat(), footerY + 62f, textPaint)
            }

            // Clinic Note / Wishes on left
            textPaint.color = Color.parseColor("#006874")
            textPaint.textSize = 9.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(clinic.billFooterNote.ifBlank { "Thank you! Wishing your child good health." }, 28f, footerY + 36f, textPaint)

            textPaint.color = Color.parseColor("#78909C")
            textPaint.textSize = 8f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Official Computer Generated Immunization Receipt • Preserve for pediatric health records", 28f, footerY + 52f, textPaint)

            pdfDocument.finishPage(page)

            val billsDir = File(context.cacheDir, "bills").apply { mkdirs() }
            val sanitizedInvoice = billData.invoiceNumber.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val pdfFile = File(billsDir, "Bill_${sanitizedInvoice}_${System.currentTimeMillis()}.pdf")

            FileOutputStream(pdfFile).use { outStream ->
                pdfDocument.writeTo(outStream)
                outStream.flush()
            }
            pdfDocument.close()

            Result.success(pdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates an official Pediatric Vaccination Record & Certificate PDF listing all previous vaccines taken.
     */
    fun generateVaccineCertificatePdf(
        context: Context,
        patient: PatientRegistration,
        clinic: ClinicProfile,
        entries: List<PatientVaccineEntry>,
        signatureBitmap: Bitmap? = null,
        signerName: String = "",
        signatureId: String = ""
    ): Result<File> {
        return try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val issueDateStr = dateFormat.format(Date())

            fun drawHeader(c: Canvas, isFirstPage: Boolean) {
                drawCleanAlignedHeader(
                    context = context,
                    canvas = c,
                    pageWidth = pageWidth,
                    clinic = clinic,
                    documentTitle = if (isFirstPage) "VACCINATION CERTIFICATE" else "VACCINATION RECORD (P.$pageNumber)",
                    docRefLabel = "Cert ID",
                    docRefValue = "CERT-${patient.id.toString().padStart(4, '0')}",
                    dateLabel = "Issued On",
                    dateValue = issueDateStr
                )
            }

            fun drawTableHeader(c: Canvas, topY: Float) {
                paint.color = Color.parseColor("#006874")
                c.drawRoundRect(RectF(26f, topY, pageWidth - 26f, topY + 24f), 4f, 4f, paint)

                textPaint.color = Color.WHITE
                textPaint.textSize = 9.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                c.drawText("#", 36f, topY + 16f, textPaint)
                c.drawText("Vaccine Administered", 60f, topY + 16f, textPaint)
                c.drawText("Dose / Milestone", 245f, topY + 16f, textPaint)
                c.drawText("Date Given", 365f, topY + 16f, textPaint)
                c.drawText("Batch #", 445f, topY + 16f, textPaint)
                c.drawText("Source", 510f, topY + 16f, textPaint)
            }

            // 1. First Page Header & Patient Info Box
            drawHeader(canvas, true)

            val boxTop = 116f
            val boxHeight = 76f
            paint.color = Color.parseColor("#F0F7F7")
            canvas.drawRoundRect(RectF(26f, boxTop, pageWidth - 26f, boxTop + boxHeight), 8f, 8f, paint)

            paint.color = Color.parseColor("#B2DFDB")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(RectF(26f, boxTop, pageWidth - 26f, boxTop + boxHeight), 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            textPaint.color = Color.parseColor("#00363D")
            textPaint.textSize = 12.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val genderPart = if (patient.gender.isNotBlank()) " (${patient.gender})" else ""
            canvas.drawText("Child / Patient :  ${patient.patientName}$genderPart", 38f, boxTop + 22f, textPaint)

            textPaint.textSize = 9.5f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.color = Color.parseColor("#37474F")
            canvas.drawText(
                "Age / DOB :  ${patient.dobOrAge}    |    Parent :  ${patient.parentName}    |    Reg. No. :  ${clinic.registrationNumber}",
                38f,
                boxTop + 43f,
                textPaint
            )
            val clinicCount = entries.count { !it.isHistorical }
            val pastCount = entries.count { it.isHistorical }
            canvas.drawText(
                "Contact :  ${patient.mobileNumber}    |    Total Doses Recorded :  ${entries.size} ($clinicCount Clinic, $pastCount Past/Birth)",
                38f,
                boxTop + 62f,
                textPaint
            )

            // 2. Table Header
            val tableTop = boxTop + boxHeight + 14f
            drawTableHeader(canvas, tableTop)

            var currentY = tableTop + 24f
            val rowHeight = 22f

            if (entries.isEmpty()) {
                paint.color = Color.parseColor("#F9FBFB")
                canvas.drawRect(26f, currentY, pageWidth - 26f, currentY + 32f, paint)
                textPaint.color = Color.parseColor("#607D8B")
                textPaint.textSize = 10f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No vaccination doses recorded yet.", 40f, currentY + 20f, textPaint)
                currentY += 32f
            } else {
                entries.forEachIndexed { index, entry ->
                    if (currentY + rowHeight > pageHeight - 165f) {
                        textPaint.color = Color.parseColor("#78909C")
                        textPaint.textSize = 8f
                        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        canvas.drawText(
                            "Page $pageNumber • Official Child Immunization Record • ${clinic.clinicName}",
                            26f,
                            pageHeight - 25f,
                            textPaint
                        )
                        pdfDocument.finishPage(page)

                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        drawHeader(canvas, false)
                        drawTableHeader(canvas, 118f)
                        currentY = 142f
                    }

                    if (index % 2 == 1) {
                        paint.color = Color.parseColor("#F4FAFA")
                        canvas.drawRect(26f, currentY, pageWidth - 26f, currentY + rowHeight, paint)
                    }

                    textPaint.color = Color.parseColor("#263238")
                    textPaint.textSize = 9f
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                    canvas.drawText("${index + 1}", 36f, currentY + 15f, textPaint)

                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val vNameDisplay = if (entry.vaccineName.length > 30) entry.vaccineName.take(28) + "…" else entry.vaccineName
                    canvas.drawText(vNameDisplay, 60f, currentY + 15f, textPaint)

                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val doseDisplay = entry.doseName.ifBlank { "Dose" }
                    canvas.drawText(if (doseDisplay.length > 22) doseDisplay.take(20) + "…" else doseDisplay, 245f, currentY + 15f, textPaint)

                    val dateStr = dateFormat.format(Date(entry.dateAdministeredMillis))
                    canvas.drawText(dateStr, 365f, currentY + 15f, textPaint)

                    val batchStr = if (entry.batchNumber.isNotBlank()) entry.batchNumber.take(12) else "—"
                    canvas.drawText(batchStr, 445f, currentY + 15f, textPaint)

                    textPaint.textSize = 8f
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textPaint.color = if (entry.isHistorical) Color.parseColor("#0277BD") else Color.parseColor("#2E7D32")
                    canvas.drawText(if (entry.isHistorical) "Past / Birth" else "At Clinic", 510f, currentY + 15f, textPaint)

                    currentY += rowHeight
                }
            }

            paint.color = Color.parseColor("#CFD8DC")
            canvas.drawLine(26f, currentY, pageWidth - 26f, currentY, paint)

            if (patient.nextPlannedVaccine.isNotBlank()) {
                currentY += 12f
                paint.color = Color.parseColor("#FFF8E1")
                canvas.drawRoundRect(RectF(26f, currentY, pageWidth - 26f, currentY + 36f), 6f, 6f, paint)

                textPaint.color = Color.parseColor("#E65100")
                textPaint.textSize = 9f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("UPCOMING SCHEDULED VACCINE:", 38f, currentY + 15f, textPaint)

                textPaint.color = Color.parseColor("#424242")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("${patient.nextPlannedVaccine}  •  Scheduled Due: ${patient.nextDueDate}", 38f, currentY + 28f, textPaint)
                currentY += 36f
            }

            val footerY = (pageHeight - 115f).coerceAtLeast(currentY + 16f)

            if (signatureBitmap != null) {
                val sigDestRect = RectF(pageWidth - 205f, footerY - 8f, pageWidth - 28f, footerY + 46f)
                canvas.drawBitmap(signatureBitmap, null, sigDestRect, null)

                paint.color = Color.parseColor("#2E7D32")
                paint.strokeWidth = 1f
                canvas.drawLine(pageWidth - 205f, footerY + 48f, pageWidth - 28f, footerY + 48f, paint)

                textPaint.color = Color.parseColor("#1B5E20")
                textPaint.textSize = 8.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val verifiedName = signerName.ifBlank { clinic.doctorName }
                canvas.drawText("✔ Digitally Signed: $verifiedName", pageWidth - 205f, footerY + 60f, textPaint)

                textPaint.color = Color.parseColor("#005B66")
                textPaint.textSize = 8f
                canvas.drawText("Reg. No. : ${clinic.registrationNumber}", pageWidth - 205f, footerY + 71f, textPaint)
            } else {
                paint.color = Color.parseColor("#B0BEC5")
                canvas.drawLine(pageWidth - 205f, footerY + 32f, pageWidth - 28f, footerY + 32f, paint)

                textPaint.color = Color.parseColor("#37474F")
                textPaint.textSize = 9.5f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(clinic.doctorName, pageWidth - 200f, footerY + 46f, textPaint)

                textPaint.color = Color.parseColor("#005B66")
                textPaint.textSize = 8.5f
                canvas.drawText("Reg. No. : ${clinic.registrationNumber}", pageWidth - 200f, footerY + 58f, textPaint)
            }

            textPaint.color = Color.parseColor("#006874")
            textPaint.textSize = 9f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("This certifies that the above child has received the listed immunizations.", 28f, footerY + 46f, textPaint)

            textPaint.color = Color.parseColor("#78909C")
            textPaint.textSize = 8f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(
                "Official Child Immunization Record • ${clinic.clinicName} • Preserve for school admission & health records",
                28f,
                pageHeight - 22f,
                textPaint
            )

            pdfDocument.finishPage(page)

            val certDir = File(context.cacheDir, "certificates").apply { mkdirs() }
            val sanitizedName = patient.patientName.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifBlank { "Patient" }
            val pdfFile = File(certDir, "Vaccine_Certificate_${sanitizedName}_${System.currentTimeMillis()}.pdf")

            FileOutputStream(pdfFile).use { outStream ->
                pdfDocument.writeTo(outStream)
                outStream.flush()
            }
            pdfDocument.close()

            Result.success(pdfFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Renders all pages of a generated PDF file into high-resolution Bitmaps for in-app preview.
     */
    fun renderPdfPagesToBitmaps(pdfFile: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            if (!pdfFile.exists()) return emptyList()
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val width = page.width * 2
                            val height = page.height * 2
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bmp)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return bitmaps
    }

    /**
     * Saves a generated PDF file directly into the device's public Downloads folder.
     */
    fun savePdfToDownloads(
        context: Context,
        pdfFile: File,
        displayFileName: String = pdfFile.name
    ): Result<String> {
        return try {
            val cleanName = if (displayFileName.endsWith(".pdf", ignoreCase = true)) {
                displayFileName
            } else {
                "$displayFileName.pdf"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return Result.failure(IllegalStateException("Could not create file in Downloads"))

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(pdfFile).use { input ->
                        input.copyTo(out)
                    }
                }
                Result.success("Downloads/$cleanName")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .apply { mkdirs() }
                val destFile = File(downloadsDir, cleanName)
                pdfFile.copyTo(destFile, overwrite = true)
                Result.success(destFile.absolutePath)
            }
        } catch (e: Exception) {
            try {
                val fallbackDir = File(context.filesDir, "docs").apply { mkdirs() }
                val destFile = File(fallbackDir, displayFileName)
                pdfFile.copyTo(destFile, overwrite = true)
                Result.success(destFile.absolutePath)
            } catch (ex: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Opens Android's native system Print / Save-as-PDF dialog for the given PDF file.
     */
    fun printPdfFile(context: Context, pdfFile: File, jobName: String) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "Print service not available on this device", Toast.LENGTH_SHORT).show()
                return
            }
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder(pdfFile.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (destination == null) {
                        callback?.onWriteFailed("No destination file descriptor")
                        return
                    }
                    try {
                        FileInputStream(pdfFile).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch print preview: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
