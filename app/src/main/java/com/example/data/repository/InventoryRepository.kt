package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.firebase.FirestoreSyncManager
import com.example.data.local.dao.InventoryDao
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.SectionHistoryEntity
import com.example.data.local.model.DashboardStats
import com.example.data.local.model.DepartmentWithStats
import com.example.data.local.model.ProductWithLocation
import com.example.data.local.model.SectionWithStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventoryRepository(
    private val dao: InventoryDao,
    private val context: Context,
    private val syncManager: FirestoreSyncManager? = null
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
        syncManager?.syncDepartmentToCloud(dept.copy(id = id))
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
        syncManager?.syncDepartmentToCloud(updated)
        Result.success(Unit)
    }

    suspend fun deleteDepartment(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val sectionCount = dao.countSectionsByDepartment(id)
        if (sectionCount > 0) {
            return@withContext Result.failure(IllegalStateException("Tidak dapat menghapus departemen: masih memiliki $sectionCount Section terkait."))
        }
        val productCount = dao.countProductsByDepartment(id)
        if (productCount > 0) {
            return@withContext Result.failure(IllegalStateException("Tidak dapat menghapus departemen: masih memiliki $productCount Produk terkait."))
        }
        val current = dao.getDepartmentById(id) ?: return@withContext Result.failure(IllegalArgumentException("Departemen tidak ditemukan"))
        dao.deleteDepartment(current)
        Result.success(Unit)
    }

    // --- SECTIONS ---
    val allSections: Flow<List<SectionEntity>> = dao.getAllSections()
    val sectionsWithStats: Flow<List<SectionWithStats>> = dao.getSectionsWithStats()
    val recentSections: Flow<List<SectionWithStats>> = dao.getRecentSectionsWithStats(5)

    suspend fun getSectionById(id: Long) = dao.getSectionById(id)
    suspend fun getSectionByCode(code: String) = dao.getSectionByCode(code)

    suspend fun createSection(
        code: String,
        name: String,
        address: String,
        departmentId: Long,
        description: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val trimmedCode = code.trim().uppercase(Locale.getDefault())
        val trimmedName = name.trim().ifEmpty { "Section $trimmedCode" }
        val trimmedAddress = address.trim()

        if (trimmedCode.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Kode Section tidak boleh kosong"))
        if (trimmedAddress.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Alamat Section tidak boleh kosong"))
        if (departmentId <= 0) return@withContext Result.failure(IllegalArgumentException("Departemen wajib dipilih"))

        val existing = dao.getSectionByCode(trimmedCode)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("Kode Section '$trimmedCode' sudah digunakan"))
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
        syncManager?.syncSectionToCloud(section.copy(id = id))
        Result.success(id)
    }

    suspend fun updateSection(
        id: Long,
        code: String,
        name: String,
        address: String,
        departmentId: Long,
        description: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmedCode = code.trim().uppercase(Locale.getDefault())
        val trimmedName = name.trim().ifEmpty { "Section $trimmedCode" }
        val trimmedAddress = address.trim()

        if (trimmedCode.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Kode Section tidak boleh kosong"))
        if (trimmedAddress.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Alamat Section tidak boleh kosong"))
        if (departmentId <= 0) return@withContext Result.failure(IllegalArgumentException("Departemen wajib dipilih"))

        val existing = dao.getSectionByCode(trimmedCode)
        if (existing != null && existing.id != id) {
            return@withContext Result.failure(IllegalArgumentException("Kode Section '$trimmedCode' sudah digunakan oleh section lain"))
        }

        val current = dao.getSectionById(id) ?: return@withContext Result.failure(IllegalArgumentException("Section tidak ditemukan"))
        val updated = current.copy(
            code = trimmedCode,
            name = trimmedName,
            address = trimmedAddress,
            departmentId = departmentId,
            description = description.trim(),
            updatedAt = System.currentTimeMillis()
        )
        dao.updateSection(updated)
        syncManager?.syncSectionToCloud(updated)
        Result.success(Unit)
    }

    suspend fun deleteSection(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val productCount = dao.countProductsBySection(id)
        if (productCount > 0) {
            return@withContext Result.failure(IllegalStateException("Tidak dapat menghapus Section ini karena masih berisi $productCount produk."))
        }
        val current = dao.getSectionById(id) ?: return@withContext Result.failure(IllegalArgumentException("Section tidak ditemukan"))
        dao.deleteSection(current)
        Result.success(Unit)
    }

    // --- PRODUCTS ---
    val allProductsWithLocation: Flow<List<ProductWithLocation>> = dao.getAllProductsWithLocation()

    fun getProductsBySection(sectionId: Long) = dao.getProductsBySection(sectionId)
    fun observeProductCountInSection(sectionId: Long) = dao.observeProductCountInSection(sectionId)
    suspend fun getProductById(id: Long) = dao.getProductById(id)
    suspend fun getProductByArticle(articleNumber: String) = dao.getProductByArticle(articleNumber.trim())
    suspend fun getProductByBarcodeOrArticle(barcode: String) = dao.getProductByBarcodeOrArticle(barcode.trim())
    fun searchProducts(query: String) = dao.searchProducts(query.trim())

    suspend fun createProduct(
        articleNumber: String,
        name: String,
        departmentId: Long,
        sectionId: Long,
        stockQuantity: Int,
        imageUri: String? = null,
        description: String = "",
        responsiblePerson: String = "",
        lastProcessedBy: String = ""
    ): Result<Long> = withContext(Dispatchers.IO) {
        val trimmedArticle = articleNumber.trim()
        val trimmedName = name.trim()
        if (trimmedArticle.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nomor Artikel wajib diisi"))
        if (trimmedName.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nama Produk wajib diisi"))
        if (departmentId <= 0) return@withContext Result.failure(IllegalArgumentException("Departemen wajib dipilih"))
        if (sectionId <= 0) return@withContext Result.failure(IllegalArgumentException("Section wajib dipilih"))
        if (stockQuantity < 0) return@withContext Result.failure(IllegalArgumentException("Jumlah Stok tidak boleh negatif"))

        val existing = dao.getProductByArticle(trimmedArticle)
        if (existing != null) {
            return@withContext Result.failure(IllegalStateException("Artikel '$trimmedArticle' sudah terdaftar di Section ${existing.sectionCode}."))
        }

        val now = System.currentTimeMillis()
        val product = ProductEntity(
            articleNumber = trimmedArticle,
            name = trimmedName,
            departmentId = departmentId,
            sectionId = sectionId,
            stockQuantity = stockQuantity,
            imageUri = imageUri,
            description = description.trim(),
            responsiblePerson = responsiblePerson,
            lastProcessedBy = lastProcessedBy,
            lastProcessedAt = now,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        val id = dao.insertProduct(product)
        syncManager?.syncProductToCloud(product.copy(id = id))
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
        description: String,
        responsiblePerson: String = "",
        lastProcessedBy: String = "",
        isActive: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmedArticle = articleNumber.trim()
        val trimmedName = name.trim()
        if (trimmedArticle.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nomor Artikel wajib diisi"))
        if (trimmedName.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Nama Produk wajib diisi"))
        if (stockQuantity < 0) return@withContext Result.failure(IllegalArgumentException("Jumlah Stok tidak boleh negatif"))

        val existing = dao.getProductByArticle(trimmedArticle)
        if (existing != null && existing.product.id != id) {
            return@withContext Result.failure(IllegalStateException("Nomor Artikel '$trimmedArticle' sudah digunakan oleh produk lain!"))
        }

        val current = dao.getProductById(id) ?: return@withContext Result.failure(IllegalArgumentException("Produk tidak ditemukan"))
        val now = System.currentTimeMillis()

        // If section changed, record history
        if (current.product.sectionId != sectionId) {
            val targetSection = dao.getSectionById(sectionId)
            if (targetSection != null) {
                dao.insertSectionHistory(
                    SectionHistoryEntity(
                        productId = id,
                        articleNumber = trimmedArticle,
                        productName = trimmedName,
                        fromSectionId = current.product.sectionId,
                        fromSectionCode = current.sectionCode,
                        fromSectionAddress = current.sectionAddress,
                        toSectionId = targetSection.id,
                        toSectionCode = targetSection.code,
                        toSectionAddress = targetSection.address,
                        notes = "Diperbarui oleh $lastProcessedBy melalui form Edit"
                    )
                )
            }
        }

        val updated = current.product.copy(
            articleNumber = trimmedArticle,
            name = trimmedName,
            departmentId = departmentId,
            sectionId = sectionId,
            stockQuantity = stockQuantity,
            imageUri = imageUri,
            description = description.trim(),
            responsiblePerson = responsiblePerson.ifEmpty { current.product.responsiblePerson },
            lastProcessedBy = lastProcessedBy.ifEmpty { current.product.lastProcessedBy },
            lastProcessedAt = now,
            isActive = isActive,
            updatedAt = now
        )
        dao.updateProduct(updated)
        syncManager?.syncProductToCloud(updated)
        Result.success(Unit)
    }

    suspend fun updateProductStock(
        productId: Long,
        newStock: Int,
        lastProcessedBy: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (newStock < 0) return@withContext Result.failure(IllegalArgumentException("Jumlah Stok tidak boleh negatif"))
        val current = dao.getProductById(productId) ?: return@withContext Result.failure(IllegalArgumentException("Produk tidak ditemukan"))
        val now = System.currentTimeMillis()

        val rows = dao.updateProductStock(productId, newStock, lastProcessedBy, now)
        if (rows > 0) {
            syncManager?.syncProductStockToCloud(current.product.articleNumber, newStock, lastProcessedBy, now)
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Gagal memperbarui stok di database"))
        }
    }

    suspend fun moveProductSection(
        productId: Long,
        targetSectionId: Long,
        lastProcessedBy: String = "",
        notes: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val current = dao.getProductById(productId) ?: return@withContext Result.failure(IllegalArgumentException("Produk tidak ditemukan"))
        val targetSection = dao.getSectionById(targetSectionId) ?: return@withContext Result.failure(IllegalArgumentException("Section tujuan tidak ditemukan"))

        if (current.product.sectionId == targetSectionId) {
            return@withContext Result.success(Unit) // already there
        }

        val now = System.currentTimeMillis()
        // Update product's section and department to target
        dao.updateProductLocation(
            productId = productId,
            newSectionId = targetSection.id,
            newDepartmentId = targetSection.departmentId,
            updatedAt = now
        )

        val updatedProd = current.product.copy(
            sectionId = targetSection.id,
            departmentId = targetSection.departmentId,
            lastProcessedBy = lastProcessedBy.ifEmpty { current.product.lastProcessedBy },
            lastProcessedAt = now,
            updatedAt = now
        )
        dao.updateProduct(updatedProd)
        syncManager?.syncProductToCloud(updatedProd)

        // Record history
        dao.insertSectionHistory(
            SectionHistoryEntity(
                productId = productId,
                articleNumber = current.product.articleNumber,
                productName = current.product.name,
                fromSectionId = current.product.sectionId,
                fromSectionCode = current.sectionCode,
                fromSectionAddress = current.sectionAddress,
                toSectionId = targetSection.id,
                toSectionCode = targetSection.code,
                toSectionAddress = targetSection.address,
                notes = if (notes.isNotEmpty()) notes else "Dipindahkan oleh $lastProcessedBy"
            )
        )
        Result.success(Unit)
    }

    suspend fun deleteProduct(productId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val current = dao.getProductById(productId) ?: return@withContext Result.failure(IllegalArgumentException("Produk tidak ditemukan"))
        dao.deleteProduct(current.product)
        syncManager?.syncProductDeleteToCloud(current.product.articleNumber)
        Result.success(Unit)
    }

    fun getHistoryForProduct(productId: Long): Flow<List<SectionHistoryEntity>> = dao.getHistoryByProduct(productId)
    fun getRecentHistories(limit: Int = 20): Flow<List<SectionHistoryEntity>> = dao.getRecentHistories(limit)

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
    suspend fun persistImage(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val imagesDir = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }
            val fileName = "prod_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val destFile = File(imagesDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            sourceUri.toString()
        }
    }

    // --- CSV EXPORT ---
    suspend fun exportProductsToCsv(products: List<ProductWithLocation>): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("Artikel,Nama Produk,Jumlah Stok,Departemen,Section,Alamat Section,Deskripsi,Tanggal Ditambahkan,Tanggal Diperbarui\n")
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

        // Check if first row is header
        val startIndex = if (lines[0].contains("Artikel", ignoreCase = true) || lines[0].contains("Article", ignoreCase = true)) 1 else 0

        val seenArticlesInFile = mutableSetOf<String>()

        for (i in startIndex until lines.size) {
            val line = lines[i]
            val cols = parseCsvLine(line)
            val rowNum = i + 1

            val article = cols.getOrNull(0)?.trim() ?: ""
            val name = cols.getOrNull(1)?.trim() ?: ""
            val stockRaw = cols.getOrNull(2)?.trim() ?: "0"
            val stockQty = stockRaw.toIntOrNull() ?: 0
            val deptName = cols.getOrNull(3)?.trim() ?: ""
            val sectionCode = cols.getOrNull(4)?.trim() ?: ""
            val sectionAddress = cols.getOrNull(5)?.trim() ?: ""
            val description = cols.getOrNull(6)?.trim() ?: ""

            val errors = mutableListOf<String>()
            if (article.isEmpty()) errors.add("Artikel kosong")
            if (name.isEmpty()) errors.add("Nama produk kosong")
            if (deptName.isEmpty()) errors.add("Departemen kosong")
            if (sectionCode.isEmpty()) errors.add("Section kosong")
            if (stockQty < 0) errors.add("Stok tidak boleh negatif")

            if (article.isNotEmpty()) {
                if (seenArticlesInFile.contains(article)) {
                    errors.add("Duplikasi artikel '$article' dalam file")
                } else {
                    seenArticlesInFile.add(article)
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
            totalFound = validList.size + problemList.size,
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
            // Find or create department
            val deptId = deptCache.getOrPut(item.departmentName.lowercase(Locale.getDefault())) {
                val found = dao.getDepartmentByName(item.departmentName)
                found?.id ?: dao.insertDepartment(DepartmentEntity(name = item.departmentName))
            }

            // Find or create section
            val sectionId = sectionCache.getOrPut(item.sectionCode.lowercase(Locale.getDefault())) {
                val found = dao.getSectionByCode(item.sectionCode)
                found?.id ?: dao.insertSection(
                    SectionEntity(
                        code = item.sectionCode.uppercase(Locale.getDefault()),
                        name = "Section ${item.sectionCode.uppercase(Locale.getDefault())}",
                        address = item.sectionAddress,
                        departmentId = deptId
                    )
                )
            }

            // Upsert product
            val existing = dao.getProductByArticle(item.article)
            if (existing != null) {
                dao.updateProduct(
                    existing.product.copy(
                        name = item.name,
                        stockQuantity = item.stockQuantity,
                        departmentId = deptId,
                        sectionId = sectionId,
                        description = item.description.ifEmpty { existing.product.description },
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
