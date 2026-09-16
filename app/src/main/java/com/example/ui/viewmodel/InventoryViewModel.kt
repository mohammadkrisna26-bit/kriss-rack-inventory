package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DataSeeder
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.model.DashboardStats
import com.example.data.local.model.DepartmentWithStats
import com.example.data.local.model.ProductWithLocation
import com.example.data.local.model.SectionWithStats
import com.example.data.repository.BackupRestoreResult
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

enum class PhotoFilter { ALL, WITH_PHOTO, WITHOUT_PHOTO }
enum class ProductSortOption { ARTICLE_ASC, ARTICLE_DESC, NAME_ASC, DATE_DESC }

data class DuplicateArticlePrompt(
    val articleNumber: String,
    val existingLocations: List<ProductWithLocation>,
    val targetSectionId: Long,
    val targetSectionCode: String,
    val targetSectionAddress: String
)

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val repository = InventoryRepository(db.inventoryDao(), application)
    private val sharedPrefs = application.getSharedPreferences("azko_inventory_prefs", Context.MODE_PRIVATE)

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // --- INITIALIZATION ---
    init {
        viewModelScope.launch {
            DataSeeder.seedIfEmpty(db.inventoryDao())
            loadLastUsedSection()
        }
    }

    // --- REPOSITORY DATA FLOWS ---
    val allDepartments: StateFlow<List<DepartmentEntity>> = repository.allDepartments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val departmentsWithStats: StateFlow<List<DepartmentWithStats>> = repository.departmentsWithStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSections: StateFlow<List<SectionEntity>> = repository.allSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sectionsWithStats: StateFlow<List<SectionWithStats>> = repository.sectionsWithStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSections: StateFlow<List<SectionWithStats>> = repository.recentSections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProducts: StateFlow<List<ProductWithLocation>> = repository.allProductsWithLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardStats: StateFlow<DashboardStats> = repository.dashboardStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    // --- SEARCH FLOW ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<ProductWithLocation>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allProductsWithLocation
            } else {
                repository.searchProducts(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- CATALOG FILTERS ---
    val selectedDeptFilter = MutableStateFlow<Long?>(null)
    val selectedSectionFilter = MutableStateFlow<Long?>(null)
    val photoFilter = MutableStateFlow(PhotoFilter.ALL)
    val sortOption = MutableStateFlow(ProductSortOption.ARTICLE_ASC)

    val filteredProducts: StateFlow<List<ProductWithLocation>> = combine(
        searchResults,
        selectedDeptFilter,
        selectedSectionFilter,
        photoFilter,
        sortOption
    ) { products, deptId, secId, photo, sort ->
        var list = products

        if (deptId != null) {
            list = list.filter { it.product.departmentId == deptId }
        }
        if (secId != null) {
            list = list.filter { it.product.sectionId == secId }
        }
        list = when (photo) {
            PhotoFilter.ALL -> list
            PhotoFilter.WITH_PHOTO -> list.filter { it.product.hasPhoto }
            PhotoFilter.WITHOUT_PHOTO -> list.filter { !it.product.hasPhoto }
        }

        when (sort) {
            ProductSortOption.ARTICLE_ASC -> list.sortedBy { it.product.articleNumber }
            ProductSortOption.ARTICLE_DESC -> list.sortedByDescending { it.product.articleNumber }
            ProductSortOption.NAME_ASC -> list.sortedBy { it.product.name.lowercase() }
            ProductSortOption.DATE_DESC -> list.sortedByDescending { it.product.updatedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- DATA ENTRY (RAPID INVENTORY) STATE ---
    private val _entryDepartmentId = MutableStateFlow<Long?>(null)
    val entryDepartmentId: StateFlow<Long?> = _entryDepartmentId.asStateFlow()

    private val _entrySectionId = MutableStateFlow<Long?>(null)
    val entrySectionId: StateFlow<Long?> = _entrySectionId.asStateFlow()

    private val _lastUsedSectionInfo = MutableStateFlow<SectionWithStats?>(null)
    val lastUsedSectionInfo: StateFlow<SectionWithStats?> = _lastUsedSectionInfo.asStateFlow()

    private val _duplicateArticleFound = MutableStateFlow<ProductWithLocation?>(null)
    val duplicateArticleFound: StateFlow<ProductWithLocation?> = _duplicateArticleFound.asStateFlow()

    private val _duplicateArticlePrompt = MutableStateFlow<DuplicateArticlePrompt?>(null)
    val duplicateArticlePrompt: StateFlow<DuplicateArticlePrompt?> = _duplicateArticlePrompt.asStateFlow()

    private val _entrySuccessMessage = MutableStateFlow<String?>(null)
    val entrySuccessMessage: StateFlow<String?> = _entrySuccessMessage.asStateFlow()

    // Form states for rapid article entry
    private val _rapidInputArticle = MutableStateFlow("")
    val rapidInputArticle: StateFlow<String> = _rapidInputArticle.asStateFlow()

    private val _rapidInputName = MutableStateFlow("")
    val rapidInputName: StateFlow<String> = _rapidInputName.asStateFlow()

    private val _rapidInputStock = MutableStateFlow("1")
    val rapidInputStock: StateFlow<String> = _rapidInputStock.asStateFlow()

    private val _rapidInputDescription = MutableStateFlow("")
    val rapidInputDescription: StateFlow<String> = _rapidInputDescription.asStateFlow()

    private val _rapidInputImageUri = MutableStateFlow<String?>(null)
    val rapidInputImageUri: StateFlow<String?> = _rapidInputImageUri.asStateFlow()

    private val _rapidInputImageUri2 = MutableStateFlow<String?>(null)
    val rapidInputImageUri2: StateFlow<String?> = _rapidInputImageUri2.asStateFlow()

    private val _rapidInputImageUri3 = MutableStateFlow<String?>(null)
    val rapidInputImageUri3: StateFlow<String?> = _rapidInputImageUri3.asStateFlow()

    private val _isShowingNewProductForm = MutableStateFlow(false)
    val isShowingNewProductForm: StateFlow<Boolean> = _isShowingNewProductForm.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val entrySectionProductCount: StateFlow<Int> = _entrySectionId
        .flatMapLatest { secId ->
            if (secId != null && secId > 0) repository.observeProductCountInSection(secId)
            else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentSelectedSection: StateFlow<SectionWithStats?> = combine(
        sectionsWithStats,
        _entrySectionId
    ) { sections, id ->
        sections.firstOrNull { it.section.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun loadLastUsedSection() {
        val savedSecId = sharedPrefs.getLong("last_used_section_id", -1L)
        if (savedSecId > 0) {
            viewModelScope.launch {
                val stats = repository.sectionsWithStats.first()
                val found = stats.firstOrNull { it.section.id == savedSecId }
                _lastUsedSectionInfo.value = found
            }
        }
    }

    fun selectEntryDepartment(deptId: Long?) {
        _entryDepartmentId.value = deptId
        _entrySectionId.value = null
    }

    fun selectEntrySection(section: SectionEntity) {
        _entryDepartmentId.value = section.departmentId
        _entrySectionId.value = section.id
        sharedPrefs.edit().putLong("last_used_section_id", section.id).apply()
        _lastUsedSectionInfo.value = SectionWithStats(section, "", 0)
    }

    fun resumeLastUsedSection() {
        _lastUsedSectionInfo.value?.let {
            selectEntrySection(it.section)
        }
    }

    fun setRapidArticle(article: String) {
        _rapidInputArticle.value = article
    }

    fun setRapidName(name: String) {
        _rapidInputName.value = name
    }

    fun setRapidStock(stock: String) {
        _rapidInputStock.value = stock
    }

    fun setRapidDescription(desc: String) {
        _rapidInputDescription.value = desc
    }

    fun setRapidImageUri(uri: String?) {
        _rapidInputImageUri.value = uri
    }

    fun setRapidImageUri2(uri: String?) {
        _rapidInputImageUri2.value = uri
    }

    fun setRapidImageUri3(uri: String?) {
        _rapidInputImageUri3.value = uri
    }

    fun clearDuplicatePrompt() {
        _duplicateArticleFound.value = null
        _duplicateArticlePrompt.value = null
    }

    fun dismissDuplicatePrompt() {
        _duplicateArticlePrompt.value = null
        _duplicateArticleFound.value = null
        _rapidInputArticle.value = ""
    }

    fun proceedWithDifferentLocation() {
        val prompt = _duplicateArticlePrompt.value ?: return
        val sample = prompt.existingLocations.firstOrNull()
        _rapidInputArticle.value = prompt.articleNumber
        _rapidInputName.value = sample?.product?.name ?: ""
        _rapidInputStock.value = "1"
        _rapidInputDescription.value = sample?.product?.description ?: ""
        _rapidInputImageUri.value = sample?.product?.imageUri
        _rapidInputImageUri2.value = sample?.product?.imageUri2
        _rapidInputImageUri3.value = sample?.product?.imageUri3
        _duplicateArticlePrompt.value = null
        _duplicateArticleFound.value = null
        _isShowingNewProductForm.value = true
    }

    fun mergeStockWithExisting(productId: Long, addedStock: Int) {
        viewModelScope.launch {
            val result = repository.mergeProductStock(productId, addedStock)
            result.onSuccess { newTotal ->
                _entrySuccessMessage.value = "✓ Stok berhasil digabungkan! Total stok sekarang: $newTotal unit"
                _duplicateArticlePrompt.value = null
                _duplicateArticleFound.value = null
                _rapidInputArticle.value = ""
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menggabungkan stok")
            }
        }
    }

    fun cancelNewProductForm() {
        _isShowingNewProductForm.value = false
        _rapidInputArticle.value = ""
        _rapidInputName.value = ""
        _rapidInputStock.value = "1"
        _rapidInputDescription.value = ""
        _rapidInputImageUri.value = null
        _rapidInputImageUri2.value = null
        _rapidInputImageUri3.value = null
    }

    // Process article entry (typed or scanned)
    fun processArticleScanOrInput(scannedValue: String) {
        val trimmed = scannedValue.trim()
        if (trimmed.isEmpty()) return

        val curSecId = _entrySectionId.value
        if (curSecId == null || curSecId <= 0) {
            emitMessage("Pilih Departemen dan Komuditi terlebih dahulu!")
            return
        }
        val sec = currentSelectedSection.value?.section

        viewModelScope.launch {
            // Check if article already exists in any location
            val existingList = repository.getAllLocationsForArticle(trimmed)
            if (existingList.isNotEmpty()) {
                _duplicateArticleFound.value = existingList.first()
                _duplicateArticlePrompt.value = DuplicateArticlePrompt(
                    articleNumber = trimmed,
                    existingLocations = existingList,
                    targetSectionId = curSecId,
                    targetSectionCode = sec?.code ?: "",
                    targetSectionAddress = sec?.address ?: ""
                )
            } else {
                _duplicateArticleFound.value = null
                _duplicateArticlePrompt.value = null
                _rapidInputArticle.value = trimmed
                _rapidInputName.value = ""
                _rapidInputStock.value = "1"
                _rapidInputDescription.value = ""
                _rapidInputImageUri.value = null
                _isShowingNewProductForm.value = true
            }
        }
    }

    fun canUserManageDepartment(deptId: Long): Boolean = true

    fun saveRapidProduct() {
        val article = _rapidInputArticle.value.trim()
        val name = _rapidInputName.value.trim()
        val stockStr = _rapidInputStock.value.trim()
        val desc = _rapidInputDescription.value.trim()
        val image = _rapidInputImageUri.value
        val image2 = _rapidInputImageUri2.value
        val image3 = _rapidInputImageUri3.value
        val deptId = _entryDepartmentId.value ?: return
        val secId = _entrySectionId.value ?: return

        if (article.isEmpty()) {
            emitMessage("Nomor Artikel tidak boleh kosong")
            return
        }
        if (name.isEmpty()) {
            emitMessage("Nama Produk tidak boleh kosong")
            return
        }
        val stock = stockStr.toIntOrNull()
        if (stock == null || stock < 0) {
            emitMessage("Jumlah Stok harus berupa angka bulat dan minimal 0")
            return
        }

        viewModelScope.launch {
            val result = repository.createProduct(
                articleNumber = article,
                name = name,
                departmentId = deptId,
                sectionId = secId,
                stockQuantity = stock,
                imageUri = image,
                imageUri2 = image2,
                imageUri3 = image3,
                description = desc
            )
            result.onSuccess {
                _entrySuccessMessage.value = "✓ Artikel $article berhasil disimpan (Stok: $stock)"
                _isShowingNewProductForm.value = false
                _rapidInputArticle.value = ""
                _rapidInputName.value = ""
                _rapidInputStock.value = "1"
                _rapidInputDescription.value = ""
                _rapidInputImageUri.value = null
                _rapidInputImageUri2.value = null
                _rapidInputImageUri3.value = null
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menyimpan artikel")
            }
        }
    }

    fun moveExistingToCurrentSection(existing: ProductWithLocation) {
        val curSecId = _entrySectionId.value ?: return

        viewModelScope.launch {
            val result = repository.moveProductSection(
                productId = existing.product.id,
                targetSectionId = curSecId,
                notes = "Dipindahkan saat Mode Pendataan"
            )
            result.onSuccess {
                _entrySuccessMessage.value = "✓ Artikel ${existing.product.articleNumber} berhasil dipindahkan ke Komuditi ini"
                _duplicateArticleFound.value = null
                _rapidInputArticle.value = ""
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal memindahkan artikel")
            }
        }
    }

    // --- PRODUCT ACTIONS ---
    fun createProduct(
        articleNumber: String,
        name: String,
        departmentId: Long,
        sectionId: Long,
        stockQuantity: Int = 0,
        imageUri: String? = null,
        imageUri2: String? = null,
        imageUri3: String? = null,
        description: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (stockQuantity < 0) {
            emitMessage("Jumlah Stok tidak boleh negatif")
            onComplete(false)
            return
        }

        viewModelScope.launch {
            val res = repository.createProduct(
                articleNumber = articleNumber,
                name = name,
                departmentId = departmentId,
                sectionId = sectionId,
                stockQuantity = stockQuantity,
                imageUri = imageUri,
                imageUri2 = imageUri2,
                imageUri3 = imageUri3,
                description = description
            )
            res.onSuccess {
                emitMessage("Produk '$name' berhasil ditambahkan")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menambahkan produk")
                onComplete(false)
            }
        }
    }

    fun moveProduct(productId: Long, targetSectionId: Long, notes: String = "", onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val current = repository.getProductById(productId)
            if (current == null) {
                emitMessage("Produk tidak ditemukan")
                onComplete(false)
                return@launch
            }
            val targetSection = repository.getSectionById(targetSectionId)
            if (targetSection == null) {
                emitMessage("Komuditi tujuan tidak ditemukan")
                onComplete(false)
                return@launch
            }

            val res = repository.moveProductSection(
                productId = productId,
                targetSectionId = targetSectionId,
                notes = if (notes.isNotEmpty()) notes else "Dipindahkan ke ${targetSection.code}"
            )
            res.onSuccess {
                emitMessage("Lokasi produk berhasil dipindahkan")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal memindahkan produk")
                onComplete(false)
            }
        }
    }

    fun updateProduct(
        id: Long,
        article: String,
        name: String,
        departmentId: Long,
        sectionId: Long,
        stockQuantity: Int,
        imageUri: String?,
        imageUri2: String? = null,
        imageUri3: String? = null,
        description: String,
        isActive: Boolean = true,
        onComplete: (Boolean) -> Unit
    ) {
        if (stockQuantity < 0) {
            emitMessage("Jumlah Stok tidak boleh negatif")
            onComplete(false)
            return
        }

        viewModelScope.launch {
            val res = repository.updateProduct(
                id = id,
                articleNumber = article,
                name = name,
                departmentId = departmentId,
                sectionId = sectionId,
                stockQuantity = stockQuantity,
                imageUri = imageUri,
                imageUri2 = imageUri2,
                imageUri3 = imageUri3,
                description = description,
                isActive = isActive
            )
            res.onSuccess {
                emitMessage("Produk berhasil diperbarui")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal memperbarui produk")
                onComplete(false)
            }
        }
    }

    fun updateProductStock(
        productId: Long,
        newStock: Int,
        onComplete: (Boolean) -> Unit
    ) {
        if (newStock < 0) {
            emitMessage("Jumlah Stok tidak boleh negatif")
            onComplete(false)
            return
        }

        viewModelScope.launch {
            val res = repository.updateProductStock(productId, newStock)
            res.onSuccess {
                emitMessage("Jumlah stok berhasil diperbarui")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal memperbarui stok")
                onComplete(false)
            }
        }
    }

    suspend fun findProductByArticle(articleNumber: String): ProductWithLocation? {
        return repository.getProductByArticle(articleNumber.trim())
    }

    fun deleteProduct(productId: Long, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteProduct(productId)
            res.onSuccess {
                emitMessage("Produk berhasil dihapus")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menghapus produk")
                onComplete(false)
            }
        }
    }

    // --- SECTION (KOMUDITI) ACTIONS ---
    fun createSection(
        code: String,
        name: String,
        address: String,
        departmentId: Long,
        description: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.createSection(code, name, address, departmentId, description)
            res.onSuccess {
                emitMessage("Komuditi $code berhasil ditambahkan")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menambahkan Komuditi")
                onComplete(false)
            }
        }
    }

    fun updateSection(
        id: Long,
        code: String,
        name: String,
        address: String,
        departmentId: Long,
        description: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.updateSection(id, code, name, address, departmentId, description)
            res.onSuccess {
                emitMessage("Komuditi $code berhasil diperbarui")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal memperbarui Komuditi")
                onComplete(false)
            }
        }
    }

    fun deleteSection(id: Long, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteSection(id)
            res.onSuccess {
                emitMessage("Komuditi berhasil dihapus")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menghapus Komuditi")
                onComplete(false)
            }
        }
    }

    // --- DEPARTMENT ACTIONS ---
    fun createDepartment(name: String, description: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = repository.createDepartment(name, description)
            res.onSuccess {
                emitMessage("Departemen '$name' berhasil ditambahkan")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menambahkan departemen")
                onComplete(false)
            }
        }
    }

    fun updateDepartment(id: Long, name: String, description: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = repository.updateDepartment(id, name, description)
            res.onSuccess {
                emitMessage("Departemen berhasil diperbarui")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal memperbarui departemen")
                onComplete(false)
            }
        }
    }

    fun deleteDepartment(id: Long, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteDepartment(id)
            res.onSuccess {
                emitMessage("Departemen berhasil dihapus")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menghapus departemen")
                onComplete(false)
            }
        }
    }

    // --- IMAGE SAVING HELPER ---
    fun createCameraDestination(): Pair<File, Uri> {
        return repository.createCameraDestination()
    }

    fun persistImage(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val path = repository.persistImage(uri)
            onResult(path)
        }
    }

    // --- STANDALONE OFFLINE ZIP BACKUP & RESTORE WITH IMAGES ---
    suspend fun createZipBackup(): File {
        return repository.createZipBackup()
    }

    suspend fun restoreBackupStream(inputStream: InputStream): Result<BackupRestoreResult> {
        return repository.restoreBackup(inputStream)
    }

    // --- FULL DATABASE BACKUP & RESTORE (JSON) ---
    suspend fun createFullBackup(): String {
        return repository.createFullBackupJson()
    }

    suspend fun restoreFullBackup(jsonString: String): Result<BackupRestoreResult> {
        return repository.restoreFromBackupJson(jsonString)
    }

    // --- EXCEL EXPORT ---
    suspend fun exportProductsToExcel(products: List<ProductWithLocation>): File {
        return repository.exportProductsToExcel(products)
    }

    // --- CSV IMPORT / EXPORT ---
    suspend fun parseCsv(text: String): InventoryRepository.CsvImportPreview {
        return repository.parseAndValidateCsv(text)
    }

    fun executeImport(items: List<InventoryRepository.CsvImportItem>, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.executeCsvImport(items)
            emitMessage("Berhasil mengimpor $count produk!")
            onComplete(count)
        }
    }

    suspend fun generateExportCsv(products: List<ProductWithLocation>): String {
        return repository.exportProductsToCsv(products)
    }

    fun persistBitmap(bitmap: Bitmap, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val path = repository.persistBitmap(bitmap)
            onResult(path)
        }
    }

    fun emitMessage(msg: String) {
        viewModelScope.launch {
            _userMessage.emit(msg)
        }
    }
}
