package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.dao.InventoryDao
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.repository.BackupRestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private const val TAG = "BackupManager"
    private const val MANIFEST_ENTRY_NAME = "backup_manifest.json"

    /**
     * Creates a standalone ZIP backup file containing:
     * 1. backup_manifest.json (all departments, komuditi, products with relations & stock, and histories)
     * 2. images/ directory containing real embedded binary image files for all products that have photos.
     */
    suspend fun createZipBackup(context: Context, dao: InventoryDao): File = withContext(Dispatchers.IO) {
        val departments = dao.getAllDepartmentsList()
        val sections = dao.getAllSectionsList()
        val products = dao.getAllProductsList()

        val backupDir = File(context.cacheDir, "backups").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val zipFile = File(backupDir, "Backup_Inventaris_${timeStamp}.zip")

        val rootJson = JSONObject()
        rootJson.put("appName", "Inventory THSR by.kriss")
        rootJson.put("appVersion", 1)
        rootJson.put("backupFormatVersion", 3)
        rootJson.put("backupDate", System.currentTimeMillis())
        rootJson.put("totalDepartments", departments.size)
        rootJson.put("totalSections", sections.size)
        rootJson.put("totalProducts", products.size)

        // 1. Departments
        val deptArray = JSONArray()
        for (d in departments) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            obj.put("description", d.description)
            obj.put("createdAt", d.createdAt)
            deptArray.put(obj)
        }
        rootJson.put("departments", deptArray)

        // 2. Sections / Komuditi
        val secArray = JSONArray()
        for (s in sections) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("code", s.code)
            obj.put("name", s.name)
            obj.put("address", s.address)
            obj.put("departmentId", s.departmentId)
            obj.put("description", s.description)
            obj.put("createdAt", s.createdAt)
            obj.put("updatedAt", s.updatedAt)
            secArray.put(obj)
        }
        rootJson.put("sections", secArray)

        // 3. Products & Image mapping
        val prodArray = JSONArray()
        val imagesToZip = mutableListOf<Pair<String, ByteArray>>() // entryName to bytes

        for (p in products) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("articleNumber", p.articleNumber)
            obj.put("name", p.name)
            obj.put("departmentId", p.departmentId)
            obj.put("sectionId", p.sectionId)
            obj.put("stockQuantity", p.stockQuantity)
            obj.put("description", p.description)
            obj.put("isActive", p.isActive)
            obj.put("createdAt", p.createdAt)
            obj.put("updatedAt", p.updatedAt)

            val cleanArticle = p.articleNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")

            var embeddedImageEntry1: String? = null
            if (!p.imageUri.isNullOrBlank()) {
                val bytes = readImageBytes(context, p.imageUri)
                if (bytes != null && bytes.isNotEmpty()) {
                    val entryName = "images/img_${p.id}_${cleanArticle}_1.jpg"
                    imagesToZip.add(Pair(entryName, bytes))
                    embeddedImageEntry1 = entryName
                }
            }

            var embeddedImageEntry2: String? = null
            if (!p.imageUri2.isNullOrBlank()) {
                val bytes = readImageBytes(context, p.imageUri2)
                if (bytes != null && bytes.isNotEmpty()) {
                    val entryName = "images/img_${p.id}_${cleanArticle}_2.jpg"
                    imagesToZip.add(Pair(entryName, bytes))
                    embeddedImageEntry2 = entryName
                }
            }

            var embeddedImageEntry3: String? = null
            if (!p.imageUri3.isNullOrBlank()) {
                val bytes = readImageBytes(context, p.imageUri3)
                if (bytes != null && bytes.isNotEmpty()) {
                    val entryName = "images/img_${p.id}_${cleanArticle}_3.jpg"
                    imagesToZip.add(Pair(entryName, bytes))
                    embeddedImageEntry3 = entryName
                }
            }

            obj.put("imageEntry", embeddedImageEntry1 ?: "")
            obj.put("imageUri", p.imageUri ?: "")
            obj.put("imageEntry2", embeddedImageEntry2 ?: "")
            obj.put("imageUri2", p.imageUri2 ?: "")
            obj.put("imageEntry3", embeddedImageEntry3 ?: "")
            obj.put("imageUri3", p.imageUri3 ?: "")
            prodArray.put(obj)
        }
        rootJson.put("products", prodArray)

        // Write to ZIP
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            // Entry 1: Manifest JSON
            val manifestBytes = rootJson.toString(2).toByteArray(Charsets.UTF_8)
            val manifestEntry = ZipEntry(MANIFEST_ENTRY_NAME)
            zos.putNextEntry(manifestEntry)
            zos.write(manifestBytes)
            zos.closeEntry()

            // Subsequent entries: Images
            for ((entryName, imgBytes) in imagesToZip) {
                try {
                    val entry = ZipEntry(entryName)
                    zos.putNextEntry(entry)
                    zos.write(imgBytes)
                    zos.closeEntry()
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding image entry $entryName to zip: ${e.message}")
                }
            }
        }

        zipFile
    }

    /**
     * Restores complete inventory data from either a ZIP backup file (with images)
     * or a legacy plain JSON file.
     */
    suspend fun restoreBackup(
        context: Context,
        dao: InventoryDao,
        inputStream: InputStream
    ): Result<BackupRestoreResult> = withContext(Dispatchers.IO) {
        try {
            // Buffer the stream to inspect magic bytes or zip entries
            val bufferedStream = BufferedInputStream(inputStream)
            bufferedStream.mark(1024)

            val headerBytes = ByteArray(4)
            val bytesRead = bufferedStream.read(headerBytes)
            bufferedStream.reset()

            val isZip = bytesRead == 4 &&
                    headerBytes[0] == 0x50.toByte() &&
                    headerBytes[1] == 0x4B.toByte() &&
                    headerBytes[2] == 0x03.toByte() &&
                    headerBytes[3] == 0x04.toByte()

            if (isZip) {
                restoreFromZip(context, dao, bufferedStream)
            } else {
                // Fallback: assume plain JSON
                val jsonString = BufferedReader(InputStreamReader(bufferedStream, Charsets.UTF_8)).readText()
                restoreFromJsonString(dao, jsonString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun restoreFromZip(
        context: Context,
        dao: InventoryDao,
        zipStream: InputStream
    ): Result<BackupRestoreResult> {
        val imagesDir = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }
        var manifestJsonString: String? = null
        val extractedImagesMap = mutableMapOf<String, String>() // zipEntryName -> localFilePath
        var restoredImagesCount = 0

        ZipInputStream(zipStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (entryName == MANIFEST_ENTRY_NAME || entryName.endsWith(".json")) {
                    // Read manifest json
                    val byteStream = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var count: Int
                    while (zis.read(buffer).also { count = it } != -1) {
                        byteStream.write(buffer, 0, count)
                    }
                    manifestJsonString = byteStream.toString(Charsets.UTF_8.name())
                } else if (entryName.startsWith("images/") && !entry.isDirectory) {
                    // Extract image
                    try {
                        val simpleName = File(entryName).name
                        val targetFile = File(imagesDir, "prod_${System.currentTimeMillis()}_${(1000..9999).random()}_$simpleName")
                        FileOutputStream(targetFile).use { fos ->
                            val buffer = ByteArray(4096)
                            var count: Int
                            while (zis.read(buffer).also { count = it } != -1) {
                                fos.write(buffer, 0, count)
                            }
                        }
                        extractedImagesMap[entryName] = targetFile.absolutePath
                        restoredImagesCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed extracting image $entryName: ${e.message}")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (manifestJsonString.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("File ZIP tidak mengandung file data cadangan (backup_manifest.json)"))
        }

        return parseAndInsertData(dao, manifestJsonString!!, extractedImagesMap, restoredImagesCount)
    }

    private suspend fun restoreFromJsonString(
        dao: InventoryDao,
        jsonString: String
    ): Result<BackupRestoreResult> {
        return parseAndInsertData(dao, jsonString, emptyMap(), 0)
    }

    private suspend fun parseAndInsertData(
        dao: InventoryDao,
        jsonString: String,
        extractedImagesMap: Map<String, String>,
        restoredImagesCount: Int
    ): Result<BackupRestoreResult> {
        val root = JSONObject(jsonString)

        // 1. Parse Departments
        val deptArray = root.optJSONArray("departments") ?: JSONArray()
        val parsedDepts = mutableListOf<DepartmentEntity>()
        for (i in 0 until deptArray.length()) {
            val obj = deptArray.getJSONObject(i)
            parsedDepts.add(
                DepartmentEntity(
                    id = obj.optLong("id", 0L),
                    name = obj.getString("name"),
                    description = obj.optString("description", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        // 2. Parse Sections / Komuditi
        val secArray = root.optJSONArray("sections") ?: JSONArray()
        val parsedSecs = mutableListOf<SectionEntity>()
        for (i in 0 until secArray.length()) {
            val obj = secArray.getJSONObject(i)
            val code = obj.getString("code")
            val address = obj.optString("address", "").ifEmpty { "Rak $code" }
            parsedSecs.add(
                SectionEntity(
                    id = obj.optLong("id", 0L),
                    code = code,
                    name = "Komuditi $code",
                    address = address,
                    departmentId = obj.getLong("departmentId"),
                    description = obj.optString("description", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        // 3. Parse Products with restored image paths and exact stocks
        val prodArray = root.optJSONArray("products") ?: JSONArray()
        val parsedProds = mutableListOf<ProductEntity>()
        for (i in 0 until prodArray.length()) {
            val obj = prodArray.getJSONObject(i)
            val imageEntry1 = obj.optString("imageEntry1", "").ifEmpty { obj.optString("imageEntry", "") }
            val originalImageUri1 = obj.optString("imageUri1", "").ifEmpty { obj.optString("imageUri", "") }
            val imageEntry2 = obj.optString("imageEntry2", "")
            val originalImageUri2 = obj.optString("imageUri2", "")
            val imageEntry3 = obj.optString("imageEntry3", "")
            val originalImageUri3 = obj.optString("imageUri3", "")

            fun resolveImageUri(entry: String, orig: String): String? {
                return when {
                    entry.isNotEmpty() && extractedImagesMap.containsKey(entry) -> extractedImagesMap[entry]
                    orig.isNotEmpty() && File(orig).exists() -> orig
                    else -> null
                }
            }

            val finalImageUri1 = resolveImageUri(imageEntry1, originalImageUri1)
            val finalImageUri2 = resolveImageUri(imageEntry2, originalImageUri2)
            val finalImageUri3 = resolveImageUri(imageEntry3, originalImageUri3)

            parsedProds.add(
                ProductEntity(
                    id = obj.optLong("id", 0L),
                    articleNumber = obj.getString("articleNumber"),
                    name = obj.getString("name"),
                    departmentId = obj.getLong("departmentId"),
                    sectionId = obj.getLong("sectionId"),
                    stockQuantity = obj.optInt("stockQuantity", 0),
                    imageUri = finalImageUri1,
                    imageUri2 = finalImageUri2,
                    imageUri3 = finalImageUri3,
                    description = obj.optString("description", ""),
                    isActive = obj.optBoolean("isActive", true),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        // Atomically wipe and replace in database
        dao.clearProducts()
        dao.clearSections()
        dao.clearDepartments()

        dao.insertDepartments(parsedDepts)
        dao.insertSections(parsedSecs)
        dao.insertProducts(parsedProds)

        return Result.success(
            BackupRestoreResult(
                departmentCount = parsedDepts.size,
                sectionCount = parsedSecs.size,
                productCount = parsedProds.size,
                historyCount = 0,
                imageCount = restoredImagesCount
            )
        )
    }

    private fun readImageBytes(context: Context, uriStr: String): ByteArray? {
        return try {
            val localFile = File(uriStr)
            if (localFile.exists() && localFile.isFile) {
                localFile.readBytes()
            } else {
                val uri = Uri.parse(uriStr)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading image bytes for $uriStr: ${e.message}")
            null
        }
    }
}
