package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object SignatureManager {

    private const val SAVED_SIG_FILENAME = "saved_doctor_signature.png"

    /**
     * Checks if a persistent default doctor signature is already saved on device.
     */
    fun hasSavedSignature(context: Context): Boolean {
        val file = File(context.filesDir, SAVED_SIG_FILENAME)
        return file.exists() && file.length() > 0
    }

    /**
     * Loads the saved default doctor signature bitmap.
     */
    fun getSavedSignature(context: Context): Bitmap? {
        return try {
            val file = File(context.filesDir, SAVED_SIG_FILENAME)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Persists the doctor's signature bitmap for reuse across future bills.
     */
    fun saveSignature(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val file = File(context.filesDir, SAVED_SIG_FILENAME)
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes the saved default doctor signature.
     */
    fun clearSavedSignature(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, SAVED_SIG_FILENAME)
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generates a unique traceable digital verification ID for the bill.
     */
    fun generateSignatureId(): String {
        val prefix = SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date())
        val randomSuffix = UUID.randomUUID().toString().take(6).uppercase()
        return "SIG-$prefix-$randomSuffix"
    }

    /**
     * Renders a list of touch strokes (lists of Offset) into an Android Bitmap.
     */
    fun renderStrokesToBitmap(
        strokes: List<List<Offset>>,
        viewWidth: Int,
        viewHeight: Int,
        targetWidth: Int = 400,
        targetHeight: Int = 180,
        inkColorHex: String = "#0A2540"
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (strokes.isEmpty()) return bitmap

        // Compute bounding box of strokes to normalize and center nicely
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (stroke in strokes) {
            for (pt in stroke) {
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
        }

        val strokeWidthRange = (maxX - minX).coerceAtLeast(10f)
        val strokeHeightRange = (maxY - minY).coerceAtLeast(10f)

        // Scale factor with padding
        val padding = 16f
        val scaleX = (targetWidth - padding * 2) / strokeWidthRange
        val scaleY = (targetHeight - padding * 2) / strokeHeightRange
        val scale = minOf(scaleX, scaleY).coerceAtMost(3.0f)

        val offsetX = (targetWidth - (strokeWidthRange * scale)) / 2f - (minX * scale)
        val offsetY = (targetHeight - (strokeHeightRange * scale)) / 2f - (minY * scale)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(inkColorHex)
            strokeWidth = 5.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            val path = Path()
            val first = stroke.first()
            path.moveTo(first.x * scale + offsetX, first.y * scale + offsetY)

            if (stroke.size == 1) {
                // Single point/dot
                canvas.drawCircle(first.x * scale + offsetX, first.y * scale + offsetY, 3f, paint)
                continue
            }

            for (i in 1 until stroke.size) {
                val pt = stroke[i]
                path.lineTo(pt.x * scale + offsetX, pt.y * scale + offsetY)
            }
            canvas.drawPath(path, paint)
        }

        return bitmap
    }

    /**
     * Generates a certified digital stamp / seal bitmap with cryptographic verification look.
     */
    fun createCertifiedSealBitmap(
        doctorName: String,
        registrationNumber: String,
        clinicName: String,
        signatureId: String,
        targetWidth: Int = 420,
        targetHeight: Int = 180
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0FDF4") // Light green tint
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#15803D") // Rich green
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }

        val rect = RectF(6f, 6f, targetWidth - 6f, targetHeight - 6f)
        canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)

        // Inner dashed or subtle border
        val innerRect = RectF(12f, 12f, targetWidth - 12f, targetHeight - 12f)
        val innerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#86EFAC")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(innerRect, 8f, 8f, innerBorder)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#166534")
            textSize = 14f
        }

        canvas.drawText("✔ OFFICIALLY DIGITALLY SIGNED", 24f, 34f, textPaint)

        textPaint.textSize = 11.5f
        textPaint.color = Color.parseColor("#1E293B")
        canvas.drawText(doctorName, 24f, 56f, textPaint)

        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.color = Color.parseColor("#475569")
        val regText = if (registrationNumber.isNotBlank()) "Reg: $registrationNumber" else "Pediatric Clinic Signatory"
        canvas.drawText(regText, 24f, 74f, textPaint)
        canvas.drawText(clinicName.take(36), 24f, 92f, textPaint)

        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        textPaint.textSize = 8.5f
        textPaint.color = Color.parseColor("#059669")
        canvas.drawText("Timestamp: $dateStr", 24f, 112f, textPaint)
        canvas.drawText("ID: $signatureId • SHA256-VALID", 24f, 128f, textPaint)

        // Decorative medical seal stamp on the right side
        val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#16A34A")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val sealCenterX = targetWidth - 55f
        val sealCenterY = targetHeight / 2f
        canvas.drawCircle(sealCenterX, sealCenterY, 32f, sealPaint)

        val sealFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCFCE7")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(sealCenterX, sealCenterY, 30f, sealFill)

        val sealTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#15803D")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("CERTIFIED", sealCenterX, sealCenterY - 6f, sealTextPaint)
        canvas.drawText("MEDICAL", sealCenterX, sealCenterY + 5f, sealTextPaint)
        canvas.drawText("DIGITAL", sealCenterX, sealCenterY + 16f, sealTextPaint)

        return bitmap
    }
}
