package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.data.local.dao.InventoryDao
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.model.DashboardStats
import com.example.data.local.model.DepartmentWithStats
import com.example.data.local.model.ProductWithLocation
import com.example.data.local.model.SectionWithStats
import com.example.util.BackupManager
import com.example.util.ExcelExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupRestoreResult(
    val departmentCount: Int,
    val sectionCount: Int,
    val productCount: Int,
    val historyCount: Int = 0,
    val imageCount: Int = 0
) {
    val departmentsRestored: Int get() = departmentCount
    val sectionsRestored: Int get() = sectionCount
    val productsRestored: Int get() = productCount
    val imagesRestored: Int get() = imageCount
}

class InventoryRepository(
    private val dao: InventoryDao,
    private val context: Context
) {

    // --- DEPARTMENTS ---
    val allDepartments: Flow<List<DepartmentEntity>> = dao.getAllDepartments()
    val departmentsWithStats: Flow<List<DepartmentWithStats>> = dao.getDepartmentsWithStats()

    suspend fun getDepartmentById(id: Long) = dao.getDepartmentById(id)
    suspend fun getDepartmentByName(name: String) = dao.getDepartmentByName(name)

    suspend fun createDepartment(name: String, description: String): Result<Long> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nama Departemen tidak boleh kosong"))
        val existing = dao.getDepartmentByName(trimmed)
        if (existing != null) return@withContext Result.failure(IllegalArgumentException("Departemen '$trimmed' sudah ada"))
        val dept = DepartmentEntity(name = trimmed, description = description.trim())
        val id = dao.insertDepartment(dept)
        Result.success(id)
    }

    suspend fun updateDepartment(id: Long, name: String, description: String): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nama Departemen tidak boleh kosong"))
        val existing = dao.getDepartmentByName(trimmed)
        if (existing != null && existing.id != id) {
            return@withContext Result.failure(IllegalArgumentException("Nama Departemen '$trimmed' sudah digunakan oleh departemen lain"))
        }
        val current = dao.getDepartmentById(id) ?: return@withContext Result.failure(IllegalArgumentException("Departemen tidak ditemukan"))
        val updated = current.copy(name = trimmed, description = description.trim())
        dao.updateDepartment(updated)
        Result.success(Unit)
    }

    suspend fun deleteDepartment(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val sectionCount = dao.countSectionsByDepartment(id)
        if (sectionCount > 0) {
            return@withContext Result.failure(IllegalStateException("Tidak dapat menghapus departemen: masih memiliki $sectionCount Komuditi terkait."))
        }
        val productCount = dao.countProductsByDepartment(id)
        if (productCount > 0) {
            return@withContext Result.failure(IllegalStateException("Tidak dapat menghapus departemen: masih memiliki $productCount Produk terkait."))
        }
        val current = dao.getDepartmentById(id) ?: return@withContext Result.failure(IllegalArgumentException("Departemen tidak ditemukan"))
        dao.deleteDepartment(current)
        Result.success(Unit)
    }

    // --- SECTIONS (KOMUDITI) ---
    val allSections: Flow<List<SectionEntity>> = dao.getAllSections()
    val sectionsWithStats: Flow<List<SectionWithStats>> = dao.getSectionsWithStats()
    val recentSections: Flow<List<SectionWithStats>> = dao.getRecentSectionsWithStats(5)

    suspend fun getSectionById(id: Long) = dao.getSectionById(id)
    suspend fun getSectionByCode(code: String) = dao.getSectionByCode(code)

    suspend fun createSection(
        code: String,
        name: String = "",
        address: String,
        departmentId: Long,
        description: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val trimmedCode = code.trim().uppercase(Locale.getDefault())
        val trimmedAddress = address.trim()
        val trimmedName = name.trim().ifEmpty { "Komuditi $trimmedCode" }

        if (trimmedCode.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Kode Komuditi tidak boleh kosong"))
        if (trimmedAddress.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Alamat Komuditi tidak boleh kosong"))
        if (departmentId <= 0) return@withContext Result.failure(IllegalArgumentException("Departemen wajib dipilih"))

        val existingAddress = dao.getSectionByAddress(trimmedAddress)
        if (existingAddress != null) {
            return@withContext Result.failure(IllegalArgumentException("Alamat Komuditi '$trimmedAddress' sudah terdaftar"))
        }

        val section = SectionEntity(
            code = trimmedCode,
            name = trimmedName,
            address = trimmedAddress,
            departmentId = departmentId,
            description = description.trim(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = dao.insertSection(section)
        Result.success(id)
    }

    suspend fun updateSection(
        id: Long,
        code: String,
        name: String = "",
        address: String,
        departmentId: Long,
        description: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmedCode = code.trim().uppercase(Locale.getDefault())
        val trimmedAddress = address.trim()
        val trimmedName = name.trim().ifEmpty { "Komuditi $trimmedCode" }

        if (trimmedCode.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Kode Komuditi tidak boleh kosong"))
        if (trimmedAddress.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Alamat Komuditi tidak boleh kosong"))
        if (departmentId <= 0) return@withContext Result.failure(IllegalArgumentException("Departemen wajib dipilih"))

        val existingAddress = dao.getSectionByAddress(trimmedAddress)
        if (existingAddress != null && existingAddress.id != id) {
            return@withContext Result.failure(IllegalArgumentException("Alamat Komuditi '$trimmedAddress' sudah terdaftar pada komuditi lain"))
        }

        val current = dao.getSectionById(id) ?: return@withContext Result.failure(IllegalArgumentException("Komuditi tidak ditemukan"))
        val updated = current.copy(
            code = trimmedCode,
            name = trimmedName,
            address = trimmedAddress,
            departmentId = departmentId,
            description = description.trim(),
            updatedAt = System.currentTimeMillis()
        )
        dao.updateSection(updated)
        Result.success(Unit)
    }

    suspend fun deleteSection(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val productCount = dao.countProductsBySection(id)
        if (productCount > 0) {
            return@withContext Result.failure(IllegalStateException("Tidak dapat menghapus Komuditi ini karena masih berisi $productCount produk."))
        }
        val current = dao.getSectionById(id) ?: return@withContext Result.failure(IllegalArgumentException("Komuditi tidak ditemukan"))
        dao.deleteSection(current)
        Result.success(Unit)
    }

    // --- PRODUCTS ---
    val allProductsWithLocation: Flow<List<ProductWithLocation>> = dao.getAllProductsWithLocation()

    fun getProductsBySection(sectionId: Long) = dao.getProductsBySection(sectionId)
    fun observeProductCountInSection(sectionId: Long) = dao.observeProductCountInSection(sectionId)
    suspend fun getProductById(id: Long) = dao.getProductById(id)
    suspend fun getProductByArticle(articleNumber: String) = dao.getProductByArticle(articleNumber.trim())
    suspend fun getAllLocationsForArticle(articleNumber: String) = dao.getAllLocationsForArticle(articleNumber.trim())
    suspend fun getProductByArticleAndSection(articleNumber: String, sectionId: Long) = dao.getProductByArticleAndSection(articleNumber.trim(), sectionId)
    suspend fun getProductByBarcodeOrArticle(barcode: String) = dao.getProductByBarcodeOrArticle(barcode.trim())
    fun searchProducts(query: String) = dao.searchProducts(query.trim())

    suspend fun createProduct(
        articleNumber: String,
        name: String,
        departmentId: Long,
        sectionId: Long,
        stockQuantity: Int,
        imageUri: String? = null,
        imageUri2: String? = null,
        imageUri3: String? = null,
        description: String = ""
    ): Result<Long> = withContext(Dispatchers.IO) {
        val trimmedArticle = articleNumber.trim()
        val trimmedName = name.trim()
        if (trimmedArticle.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nomor Artikel wajib diisi"))
        if (trimmedName.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nama Produk wajib diisi"))
        if (departmentId <= 0) return@withContext Result.failure(IllegalArgumentException("Departemen wajib dipilih"))
        if (sectionId <= 0) return@withContext Result.failure(IllegalArgumentException("Komuditi wajib dipilih"))
        if (stockQuantity < 0) return@withContext Result.failure(IllegalArgumentException("Jumlah Stok tidak boleh negatif"))

        val existingInSection = dao.getProductByArticleAndSection(trimmedArticle, sectionId)
        if (existingInSection != null) {
            return@withContext Result.failure(IllegalStateException("Artikel '$trimmedArticle' sudah terdaftar di Komuditi ini (${existingInSection.sectionCode}). Silakan gabungkan stok atau gunakan Komuditi lain."))
        }

        val now = System.currentTimeMillis()
        val product = ProductEntity(
            articleNumber = trimmedArticle,
            name = trimmedName,
            departmentId = departmentId,
            sectionId = sectionId,
            stockQuantity = stockQuantity,
            imageUri = imageUri,
            imageUri2 = imageUri2,
            imageUri3 = imageUri3,
            description = description.trim(),
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        val id = dao.insertProduct(product)
        Result.success(id)
    }

    suspend fun updateProduct(
        id: Long,
        articleNumber: String,
        name: String,
        departmentId: Long,
        sectionId: Long,
        stockQuantity: Int,
        imageUri: String?,
        imageUri2: String? = null,
        imageUri3: String? = null,
        description: String,
        isActive: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmedArticle = articleNumber.trim()
        val trimmedName = name.trim()
        if (trimmedArticle.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nomor Artikel wajib diisi"))
        if (trimmedName.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nama Produk wajib diisi"))
        if (stockQuantity < 0) return@withContext Result.failure(IllegalArgumentException("Jumlah Stok tidak boleh negatif"))

        val existingInSection = dao.getProductByArticleAndSection(trimmedArticle, sectionId)
        if (existingInSection != null && existingInSection.product.id != id) {
            return@withContext Result.failure(IllegalStateException("Nomor Artikel '$trimmedArticle' sudah digunakan oleh produk lain di Komuditi ini!"))
        }

        val current = dao.getProductById(id) ?: return@withContext Result.failure(IllegalArgumentException("Produk tidak ditemukan"))
        val now = System.currentTimeMillis()

        val updated = current.product.copy(
            articleNumber = trimmedArticle,
            name = trimmedName,
            departmentId = departmentId,
            sectionId = sectionId,
            stockQuantity = stockQuantity,
            imageUri = imageUri,
            imageUri2 = imageUri2,
            imageUri3 = imageUri3,
            description = description.trim(),
            isActive = isActive,
            updatedAt = now
        )
        dao.updateProduct(updated)
        Result.success(Unit)
    }

    suspend fun updateProductStock(
        productId: Long,
        newStock: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (newStock < 0) return@withContext Result.failure(IllegalArgumentException("Jumlah Stok tidak boleh negatif"))
        val current = dao.getProductById(productId) ?: return@withContext Result.failure(IllegalArgumentException("Produk tidak ditemukan"))
        val now = System.currentTimeMillis()

        val rows = dao.updateProductStock(productId, newStock, now)
        if (rows > 0) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Gagal memperbarui stok di database"))
        }
    }

    suspend fun mergeProductStock(
        productId: Long,
        additionalStock: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (additionalStock <= 0) return@withContext Result.failure(IllegalArgumentException("Tambahan stok harus lebih dari 0"))
        val current = dao.getProductById(productId) ?: return@withContext Result.failure(IllegalArgumentException("Produk tidak ditemukan"))
        val newStock = current.product.stockQuantity + additionalStock
        val now = System.currentTimeMillis()
        val rows = dao.updateProductStock(productId, newStock, now)
        if (rows > 0) {
            Result.success(newStock)
        } else {
            Result.failure(IllegalStateException("Gagal menggabungkan stok di database"))
        }
    }

    suspend fun moveProductSection(
        productId: Long,
        targetSectionId: Long,
        notes: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val current = dao.getProductById(productId) ?: return@withContext Result.failure(IllegalArgumentException("Produk tidak ditemukan"))
        val targetSection = dao.getSectionById(targetSectionId) ?: return@withContext Result.failure(IllegalArgumentException("Komuditi tujuan tidak ditemukan"))

        if (current.product.sectionId == targetSectionId) {
            return@withContext Result.success(Unit)
        }

        val now = System.currentTimeMillis()
        dao.updateProductLocation(
            productId = productId,
            newSectionId = targetSection.id,
            newDepartmentId = targetSection.departmentId,
            updatedAt = now
        )

        val updatedProd = current.product.copy(
            sectionId = targetSection.id,
            departmentId = targetSection.departmentId,
            updatedAt = now
        )
        dao.updateProduct(updatedProd)
        Result.success(Unit)
    }

    suspend fun deleteProduct(productId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val current = dao.getProductById(productId) ?: return@withContext Result.failure(IllegalArgumentException("Produk tidak ditemukan"))
        dao.deleteProduct(current.product)
        Result.success(Unit)
    }

    // --- DASHBOARD STATS ---
    val dashboardStats: Flow<DashboardStats> = combine(
        dao.getTotalProductsCount(),
        dao.getTotalArticlesCount(),
        dao.getTotalStockCount(),
        dao.getTotalDepartmentsCount(),
        dao.getTotalSectionsCount(),
        dao.getProductsWithoutPhotoCount()
    ) { args: Array<Int> ->
        DashboardStats(
            totalProducts = args[0],
            totalArticles = args[1],
            totalStock = args[2],
            totalDepartments = args[3],
            totalSections = args[4],
            productsWithoutPhoto = args[5]
        )
    }

    // --- IMAGE PERSISTENCE ---
    fun createCameraDestination(): Pair<File, Uri> {
        return com.example.util.ImageCaptureHelper.createCameraDestination(context)
    }

    suspend fun persistImage(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val imagesDir = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

            // If already pointing to an existing file inside imagesDir, keep it directly
            val rawPath = sourceUri.path
            if (rawPath != null) {
                val candidateFile = File(rawPath)
                if (candidateFile.exists() && candidateFile.parentFile?.absolutePath == imagesDir.absolutePath && candidateFile.length() > 0) {
                    return@withContext candidateFile.absolutePath
                }
            }

            val fileName = "prod_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val destFile = File(imagesDir, fileName)

            var copied = false
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                        copied = true
                    }
                }
            } catch (_: Exception) {
                if (rawPath != null) {
                    val fallbackFile = File(rawPath)
                    if (fallbackFile.exists() && fallbackFile.isFile) {
                        fallbackFile.copyTo(destFile, overwrite = true)
                        copied = true
                    }
                }
            }

            if (copied && destFile.exists() && destFile.length() > 0) {
                destFile.absolutePath
            } else {
                if (destFile.exists()) destFile.delete()
                sourceUri.toString()
            }
        } catch (e: Exception) {
            Log.e("InventoryRepo", "Error persisting image $sourceUri: ${e.message}", e)
            sourceUri.toString()
        }
    }

    suspend fun persistBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            val imagesDir = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }
            val fileName = "prod_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val destFile = File(imagesDir, fileName)
            FileOutputStream(destFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    // --- STANDALONE OFFLINE ZIP BACKUP & RESTORE WITH IMAGES ---
    suspend fun createZipBackup(): File {
        return BackupManager.createZipBackup(context, dao)
    }

    suspend fun restoreBackup(inputStream: InputStream): Result<BackupRestoreResult> {
        return BackupManager.restoreBackup(context, dao, inputStream)
    }

    // --- FULL JSON BACKUP & RESTORE ---
    suspend fun createFullBackupJson(): String = withContext(Dispatchers.IO) {
        val departments = dao.getAllDepartmentsList()
        val sections = dao.getAllSectionsList()
        val products = dao.getAllProductsList()

        val root = JSONObject()
        root.put("version", 2)
        root.put("appName", "Inventaris Toko Offline")
        root.put("backupDate", System.currentTimeMillis())

        val deptArray = JSONArray()
        for (d in departments) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            obj.put("description", d.description)
            obj.put("createdAt", d.createdAt)
            deptArray.put(obj)
        }
        root.put("departments", deptArray)

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
        root.put("sections", secArray)

        val prodArray = JSONArray()
        for (p in products) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("articleNumber", p.articleNumber)
            obj.put("name", p.name)
            obj.put("departmentId", p.departmentId)
            obj.put("sectionId", p.sectionId)
            obj.put("stockQuantity", p.stockQuantity)
            obj.put("imageUri", p.imageUri ?: "")
            obj.put("imageUri2", p.imageUri2 ?: "")
            obj.put("imageUri3", p.imageUri3 ?: "")
            obj.put("description", p.description)
            obj.put("isActive", p.isActive)
            obj.put("createdAt", p.createdAt)
            obj.put("updatedAt", p.updatedAt)
            prodArray.put(obj)
        }
        root.put("products", prodArray)

        root.toString(2)
    }

    suspend fun restoreFromBackupJson(jsonString: String): Result<BackupRestoreResult> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)

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

            val secArray = root.optJSONArray("sections") ?: JSONArray()
            val parsedSecs = mutableListOf<SectionEntity>()
            for (i in 0 until secArray.length()) {
                val obj = secArray.getJSONObject(i)
                parsedSecs.add(
                    SectionEntity(
                        id = obj.optLong("id", 0L),
                        code = obj.getString("code"),
                        name = obj.optString("name", "Komuditi ${obj.getString("code")}"),
                        address = obj.getString("address"),
                        departmentId = obj.getLong("departmentId"),
                        description = obj.optString("description", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val prodArray = root.optJSONArray("products") ?: JSONArray()
            val parsedProds = mutableListOf<ProductEntity>()
            for (i in 0 until prodArray.length()) {
                val obj = prodArray.getJSONObject(i)
                val img = obj.optString("imageUri", "")
                val img2 = obj.optString("imageUri2", "")
                val img3 = obj.optString("imageUri3", "")
                parsedProds.add(
                    ProductEntity(
                        id = obj.optLong("id", 0L),
                        articleNumber = obj.getString("articleNumber"),
                        name = obj.getString("name"),
                        departmentId = obj.getLong("departmentId"),
                        sectionId = obj.getLong("sectionId"),
                        stockQuantity = obj.optInt("stockQuantity", 0),
                        imageUri = if (img.isNotEmpty()) img else null,
                        imageUri2 = if (img2.isNotEmpty()) img2 else null,
                        imageUri3 = if (img3.isNotEmpty()) img3 else null,
                        description = obj.optString("description", ""),
                        isActive = obj.optBoolean("isActive", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            dao.clearProducts()
            dao.clearSections()
            dao.clearDepartments()

            dao.insertDepartments(parsedDepts)
            dao.insertSections(parsedSecs)
            dao.insertProducts(parsedProds)

            Result.success(
                BackupRestoreResult(
                    departmentCount = parsedDepts.size,
                    sectionCount = parsedSecs.size,
                    productCount = parsedProds.size,
                    historyCount = 0
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- EXCEL EXPORT (OFFLINE WITH EMBEDDED IMAGES) ---
    suspend fun exportProductsToExcel(products: List<ProductWithLocation>): File {
        return ExcelExporter.exportToExcel(context, products)
    }

    // --- CSV EXPORT ---
    suspend fun exportProductsToCsv(products: List<ProductWithLocation>): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("Artikel,Nama Produk,Jumlah Stok,Departemen,Komuditi,Alamat Komuditi,Deskripsi,Tanggal Ditambahkan,Tanggal Diperbarui\n")
        for (item in products) {
            val p = item.product
            val row = listOf(
                escapeCsv(p.articleNumber),
                escapeCsv(p.name),
                escapeCsv(p.stockQuantity.toString()),
                escapeCsv(item.departmentName),
                escapeCsv(item.sectionCode),
                escapeCsv(item.sectionAddress),
                escapeCsv(p.description),
                dateFormat.format(Date(p.createdAt)),
                dateFormat.format(Date(p.updatedAt))
            ).joinToString(",")
            sb.append(row).append("\n")
        }
        sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    // --- CSV IMPORT PARSER & VALIDATOR ---
    data class CsvImportItem(
        val rowNumber: Int,
        val article: String,
        val name: String,
        val stockQuantity: Int,
        val departmentName: String,
        val sectionCode: String,
        val sectionAddress: String,
        val description: String,
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    data class CsvImportPreview(
        val totalFound: Int,
        val readyToImport: Int,
        val problematicCount: Int,
        val validItems: List<CsvImportItem>,
        val problemItems: List<CsvImportItem>
    )

    suspend fun parseAndValidateCsv(csvText: String): CsvImportPreview = withContext(Dispatchers.IO) {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return@withContext CsvImportPreview(0, 0, 0, emptyList(), emptyList())

        val validList = mutableListOf<CsvImportItem>()
        val problemList = mutableListOf<CsvImportItem>()

        val firstLineCols = parseCsvLine(lines[0]).map { it.trim().lowercase(Locale.getDefault()) }
        val hasHeader = firstLineCols.any {
            it.contains("artikel") || it.contains("article") || it.contains("produk") || it.contains("nama")
        }
        val startIndex = if (hasHeader) 1 else 0

        var colArticle = -1
        var colName = -1
        var colStock = -1
        var colDept = -1
        var colSection = -1
        var colAddress = -1
        var colDesc = -1

        if (hasHeader) {
            for ((idx, col) in firstLineCols.withIndex()) {
                when {
                    col.contains("artikel") || col.contains("article") -> colArticle = idx
                    col.contains("nama") || col.contains("name") -> colName = idx
                    col.contains("stok") || col.contains("stock") || col.contains("jumlah") || col.contains("qty") -> colStock = idx
                    col.contains("depart") || col.contains("dept") -> colDept = idx
                    col.contains("alamat") || col.contains("address") -> colAddress = idx
                    col.contains("komuditi") || col.contains("section") || col.contains("kode") -> colSection = idx
                    col.contains("deskripsi") || col.contains("description") || col.contains("ket") -> colDesc = idx
                }
            }
        }

        // Fallback default index positions if no header was detected or matched
        if (colArticle == -1) colArticle = 0
        if (colName == -1) colName = 1
        if (colStock == -1) colStock = 2
        if (colDept == -1) colDept = 3
        if (colSection == -1) colSection = 4
        if (colAddress == -1) colAddress = 5
        if (colDesc == -1) colDesc = 6

        val seenArticleSectionInFile = mutableSetOf<String>()

        for (i in startIndex until lines.size) {
            val line = lines[i]
            val cols = parseCsvLine(line)
            val rowNum = i + 1

            val article = cols.getOrNull(colArticle)?.trim() ?: ""
            val name = cols.getOrNull(colName)?.trim() ?: ""
            val stockRaw = cols.getOrNull(colStock)?.trim() ?: "0"
            val stockQty = stockRaw.toIntOrNull() ?: 0
            val deptName = cols.getOrNull(colDept)?.trim() ?: ""
            val sectionCode = cols.getOrNull(colSection)?.trim() ?: ""
            val sectionAddress = cols.getOrNull(colAddress)?.trim() ?: ""
            val description = if (colDesc >= 0) cols.getOrNull(colDesc)?.trim() ?: "" else ""

            val errors = mutableListOf<String>()
            if (article.isEmpty()) errors.add("Artikel kosong")
            if (name.isEmpty()) errors.add("Nama produk kosong")
            if (deptName.isEmpty()) errors.add("Departemen kosong")
            if (sectionCode.isEmpty()) errors.add("Komuditi kosong")
            if (stockQty < 0) errors.add("Stok tidak boleh negatif")

            if (article.isNotEmpty()) {
                val effectiveAddress = sectionAddress.trim().ifEmpty { "Rak $sectionCode" }
                val fileKey = "${article.lowercase(Locale.getDefault())}_${effectiveAddress.lowercase(Locale.getDefault())}"
                if (seenArticleSectionInFile.contains(fileKey)) {
                    errors.add("Duplikasi artikel '$article' pada Alamat '$effectiveAddress' dalam file")
                } else {
                    seenArticleSectionInFile.add(fileKey)
                }
            }

            if (errors.isEmpty()) {
                validList.add(
                    CsvImportItem(
                        rowNumber = rowNum,
                        article = article,
                        name = name,
                        stockQuantity = stockQty,
                        departmentName = deptName,
                        sectionCode = sectionCode,
                        sectionAddress = sectionAddress.ifEmpty { "Rak $sectionCode" },
                        description = description,
                        isValid = true
                    )
                )
            } else {
                problemList.add(
                    CsvImportItem(
                        rowNumber = rowNum,
                        article = article,
                        name = name,
                        stockQuantity = stockQty,
                        departmentName = deptName,
                        sectionCode = sectionCode,
                        sectionAddress = sectionAddress,
                        description = description,
                        isValid = false,
                        errorMessage = errors.joinToString(", ")
                    )
                )
            }
        }

        CsvImportPreview(
            totalFound = lines.size - startIndex,
            readyToImport = validList.size,
            problematicCount = problemList.size,
            validItems = validList,
            problemItems = problemList
        )
    }

    suspend fun executeCsvImport(items: List<CsvImportItem>): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        val deptCache = mutableMapOf<String, Long>()
        val sectionCache = mutableMapOf<String, Long>()

        for (item in items) {
            if (!item.isValid) continue

            // 1. Resolve or create department
            val normDept = item.departmentName.trim()
            val deptId = deptCache.getOrPut(normDept.lowercase(Locale.getDefault())) {
                val existing = dao.getDepartmentByName(normDept)
                if (existing != null) {
                    existing.id
                } else {
                    dao.insertDepartment(DepartmentEntity(name = normDept, description = "Dibuat otomatis via CSV"))
                }
            }

            // 2. Resolve or create section (Komuditi) by address and code
            val normCode = item.sectionCode.trim().uppercase(Locale.getDefault())
            val normAddress = item.sectionAddress.trim().ifEmpty { "Rak $normCode" }
            val cacheKey = "${normCode}_${normAddress.lowercase(Locale.getDefault())}"
            val sectionId = sectionCache.getOrPut(cacheKey) {
                val existing = dao.getSectionByAddress(normAddress) ?: dao.getSectionByCodeAndAddress(normCode, normAddress)
                if (existing != null) {
                    existing.id
                } else {
                    dao.insertSection(
                        SectionEntity(
                            code = normCode,
                            name = "Komuditi $normCode",
                            address = normAddress,
                            departmentId = deptId,
                            description = "Dibuat otomatis via CSV"
                        )
                    )
                }
            }

            // 3. Upsert product for this specific section/location
            val existingInLocation = dao.getProductByArticleAndSection(item.article, sectionId)
            if (existingInLocation != null) {
                dao.updateProduct(
                    existingInLocation.product.copy(
                        name = item.name,
                        stockQuantity = existingInLocation.product.stockQuantity + item.stockQuantity,
                        departmentId = deptId,
                        sectionId = sectionId,
                        description = item.description.ifEmpty { existingInLocation.product.description },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                dao.insertProduct(
                    ProductEntity(
                        articleNumber = item.article,
                        name = item.name,
                        departmentId = deptId,
                        sectionId = sectionId,
                        stockQuantity = item.stockQuantity,
                        description = item.description,
                        isActive = true
                    )
                )
            }
            importedCount++
        }
        importedCount
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }
}
