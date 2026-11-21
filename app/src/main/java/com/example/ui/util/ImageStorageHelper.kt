package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageStorageHelper {
    private const val TAG = "ImageStorageHelper"

    /**
     * Copies a chosen image Uri from the photo picker into app's internal storage
     * so that the Uri/file is permanently accessible across app restarts without permission expiration.
     */
    fun saveImageToInternalStorage(context: Context, sourceUri: Uri, prefix: String = "profile_logo"): String? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(sourceUri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for URI: $sourceUri")
                return null
            }

            val fileName = "${prefix}_${System.currentTimeMillis()}.jpg"
            val targetFile = File(context.filesDir, fileName)

            val outputStream = FileOutputStream(targetFile)
            val buffer = ByteArray(4 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Log.i(TAG, "Successfully saved image to: ${targetFile.absolutePath}")
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to internal storage", e)
            null
        }
    }

    /**
     * Saves a Bitmap directly to app's internal storage
     */
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, prefix: String = "profile_logo"): String? {
        return try {
            val fileName = "${prefix}_${System.currentTimeMillis()}.jpg"
            val targetFile = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(targetFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to internal storage", e)
            null
        }
    }

    /**
     * Checks if the stored file path or URI exists
     */
    fun doesFileExist(pathOrUri: String?): Boolean {
        if (pathOrUri.isNullOrBlank()) return false
        return try {
            val file = File(pathOrUri)
            file.exists() && file.length() > 0
        } catch (e: Exception) {
            false
        }
    }
}
