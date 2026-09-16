package com.example.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ImageCaptureHelper {

    /**
     * Creates a new unique JPEG file in internal storage (context.filesDir/images)
     * and returns the File together with its FileProvider content Uri.
     */
    fun createCameraDestination(context: Context): Pair<File, Uri> {
        val imagesDir = File(context.filesDir, "images").apply {
            if (!exists()) mkdirs()
        }
        val fileName = "prod_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
        val photoFile = File(imagesDir, fileName)
        val photoUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        return Pair(photoFile, photoUri)
    }

    /**
     * Cleans up an empty or aborted camera file.
     */
    fun cleanupIfEmpty(file: File?) {
        try {
            if (file != null && file.exists() && file.length() == 0L) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }
}
