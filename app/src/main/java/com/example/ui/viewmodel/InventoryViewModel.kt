package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.CloudConnectionStatus
import com.example.data.firebase.FirestoreSyncManager
import com.example.data.local.AppDatabase
import com.example.data.local.DataSeeder
import com.example.data.local.entity.ActivityLogEntity
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.SectionHistoryEntity
import com.example.data.local.model.DashboardStats
import com.example.data.local.model.DepartmentWithStats
import com.example.data.local.model.ProductWithLocation
import com.example.data.local.model.SectionWithStats
import com.example.data.model.AppUser
import com.example.data.repository.AuthRepository
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

enum class PhotoFilter { ALL, WITH_PHOTO, WITHOUT_PHOTO }
enum class ProductSortOption { ARTICLE_ASC, ARTICLE_DESC, NAME_ASC, DATE_DESC }

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val syncManager = FirestoreSyncManager(application, db.inventoryDao())
    val authRepository = AuthRepository(application, db.inventoryDao(), syncManager)
    val repository = InventoryRepository(db.inventoryDao(), application, syncManager)
    private val sharedPrefs = application.getSharedPreferences("azko_inventory_prefs", Context.MODE_PRIVATE)

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    val currentUser: StateFlow<AppUser?> = authRepository.currentUser
    val cloudStatus: StateFlow<CloudConnectionStatus> = syncManager.connectionStatus
    val allUsers: StateFlow<List<AppUser>> = authRepository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentActivityLogs: StateFlow<List<ActivityLogEntity>> = db.inventoryDao().getRecentActivityLogs(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- INITIALIZATION ---
    init {
        viewModelScope.launch {
            DataSeeder.seedIfEmpty(db.inventoryDao())
            loadLastUsedSection()
            authRepository.seedDefaultUsersIfEmpty()
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
    val sortOption = MutableStateFlow(ProductSortOption.DATE_DESC)

    val filteredProducts: StateFlow<List<ProductWithLocation>> = combine(
        allProducts,
        selectedDeptFilter,
        selectedSectionFilter,
        photoFilter,
        sortOption
    ) { products, deptId, secId, photoOpt, sortOpt ->
        var list = products

        if (deptId != null) {
            list = list.filter { it.product.departmentId == deptId }
        }
        if (secId != null) {
            list = list.filter { it.product.sectionId == secId }
        }
        when (photoOpt) {
            PhotoFilter.WITH_PHOTO -> list = list.filter { !it.product.imageUri.isNullOrEmpty() }
            PhotoFilter.WITHOUT_PHOTO -> list = list.filter { it.product.imageUri.isNullOrEmpty() }
            PhotoFilter.ALL -> Unit
        }
        when (sortOpt) {
            ProductSortOption.ARTICLE_ASC -> list = list.sortedBy { it.product.articleNumber }
            ProductSortOption.ARTICLE_DESC -> list = list.sortedByDescending { it.product.articleNumber }
            ProductSortOption.NAME_ASC -> list = list.sortedBy { it.product.name.lowercase() }
            ProductSortOption.DATE_DESC -> list = list.sortedByDescending { it.product.updatedAt }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- RAPID ENTRY / MODE PENDATAAN STATE ---
    private val _entryDepartmentId = MutableStateFlow<Long?>(null)
    val entryDepartmentId: StateFlow<Long?> = _entryDepartmentId.asStateFlow()

    private val _entrySectionId = MutableStateFlow<Long?>(null)
    val entrySectionId: StateFlow<Long?> = _entrySectionId.asStateFlow()

    private val _lastUsedSectionInfo = MutableStateFlow<SectionWithStats?>(null)
    val lastUsedSectionInfo: StateFlow<SectionWithStats?> = _lastUsedSectionInfo.asStateFlow()

    private val _entrySuccessMessage = MutableStateFlow<String?>(null)
    val entrySuccessMessage: StateFlow<String?> = _entrySuccessMessage.asStateFlow()

    private val _duplicateArticleFound = MutableStateFlow<ProductWithLocation?>(null)
    val duplicateArticleFound: StateFlow<ProductWithLocation?> = _duplicateArticleFound.asStateFlow()

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

    fun clearDuplicatePrompt() {
        _duplicateArticleFound.value = null
    }

    fun cancelNewProductForm() {
        _isShowingNewProductForm.value = false
        _rapidInputArticle.value = ""
        _rapidInputName.value = ""
        _rapidInputStock.value = "1"
        _rapidInputDescription.value = ""
        _rapidInputImageUri.value = null
    }

    // Process article entry (typed or scanned)
    fun processArticleScanOrInput(scannedValue: String) {
        val trimmed = scannedValue.trim()
        if (trimmed.isEmpty()) return

        val curSecId = _entrySectionId.value
        if (curSecId == null || curSecId <= 0) {
            emitMessage("Pilih Departemen dan Section terlebih dahulu!")
            return
        }

        viewModelScope.launch {
            // Check if product exists by article number
            val existing = repository.getProductByArticle(trimmed)
            if (existing != null) {
                // Article already registered!
                _duplicateArticleFound.value = existing
                _rapidInputArticle.value = existing.product.articleNumber
            } else {
                // Product does not exist -> Open rapid entry form with article prefilled
                _duplicateArticleFound.value = null
                _rapidInputArticle.value = trimmed
                _rapidInputName.value = ""
                _rapidInputStock.value = "1"
                _rapidInputDescription.value = ""
                _rapidInputImageUri.value = null
                _isShowingNewProductForm.value = true
            }
        }
    }

    fun canUserManageDepartment(deptId: Long): Boolean {
        val user = currentUser.value ?: return false
        return user.canManageDepartment(deptId)
    }

    // --- AUTHENTICATION & EMPLOYEE MANAGEMENT ---
    fun login(email: String, pass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = authRepository.login(email, pass)
            res.onSuccess {
                emitMessage("Selamat datang, ${it.fullName}")
                onResult(true, "")
            }.onFailure {
                emitMessage(it.message ?: "Login gagal")
                onResult(false, it.message ?: "Login gagal")
            }
        }
    }

    fun logout() {
        authRepository.logout()
        emitMessage("Anda telah keluar.")
    }

    fun createEmployee(
        fullName: String,
        email: String,
        password: String,
        role: String,
        assignedDepartmentIds: List<Long>,
        assignedDepartmentNames: List<String>,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = authRepository.createEmployee(
                fullName, email, password, role, assignedDepartmentIds, assignedDepartmentNames
            )
            res.onSuccess {
                emitMessage("Karyawan '${it.fullName}' berhasil ditambahkan.")
                onResult(true, "")
            }.onFailure {
                emitMessage(it.message ?: "Gagal menambahkan karyawan")
                onResult(false, it.message ?: "Gagal")
            }
        }
    }

    fun updateEmployee(
        user: AppUser,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = authRepository.updateEmployee(
                user.id, user.fullName, user.role, user.assignedDepartmentIds, user.assignedDepartmentNames, user.isActive
            )
            res.onSuccess {
                emitMessage("Data karyawan '${user.fullName}' berhasil disimpan.")
                onResult(true, "")
            }.onFailure {
                emitMessage(it.message ?: "Gagal memperbarui karyawan")
                onResult(false, it.message ?: "Gagal")
            }
        }
    }

    fun saveRapidProduct() {
        val article = _rapidInputArticle.value.trim()
        val name = _rapidInputName.value.trim()
        val stockStr = _rapidInputStock.value.trim()
        val desc = _rapidInputDescription.value.trim()
        val image = _rapidInputImageUri.value
        val deptId = _entryDepartmentId.value ?: return
        val secId = _entrySectionId.value ?: return

        if (!canUserManageDepartment(deptId)) {
            emitMessage("Anda tidak memiliki tanggung jawab pada Departemen ini. Produk tidak dapat ditambahkan.")
            return
        }

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
            emitMessage("Jumlah Stok harus berupa angka bulat dan tidak boleh negatif (minimal 0)")
            return
        }

        viewModelScope.launch {
            val user = currentUser.value
            val processor = user?.fullName ?: "Karyawan"
            val pj = authRepository.getResponsibleNamesForDepartment(deptId)

            val result = repository.createProduct(
                articleNumber = article,
                name = name,
                departmentId = deptId,
                sectionId = secId,
                stockQuantity = stock,
                imageUri = image,
                description = desc,
                responsiblePerson = pj,
                lastProcessedBy = processor
            )
            result.onSuccess {
                val dept = repository.getDepartmentById(deptId)
                if (user != null) {
                    val secCode = currentSelectedSection.value?.section?.code ?: "Section"
                    syncManager.logActivity(
                        action = "TAMBAH_PRODUK",
                        articleNumber = article,
                        productName = name,
                        departmentName = dept?.name ?: "",
                        details = "Ditambahkan ke Section $secCode (Stok: $stock)",
                        user = user
                    )
                }
                _entrySuccessMessage.value = "✓ Artikel $article berhasil disimpan (Stok: $stock)"
                // Clear input form and reset to ready mode ONLY after success
                _isShowingNewProductForm.value = false
                _rapidInputArticle.value = ""
                _rapidInputName.value = ""
                _rapidInputStock.value = "1"
                _rapidInputDescription.value = ""
                _rapidInputImageUri.value = null
            }.onFailure { err ->
                // Keep form open so user doesn't lose input, and show error
                emitMessage(err.message ?: "Gagal menyimpan artikel")
            }
        }
    }

    fun moveExistingToCurrentSection(existing: ProductWithLocation) {
        val curSecId = _entrySectionId.value ?: return
        val curDeptId = _entryDepartmentId.value ?: existing.product.departmentId

        if (!canUserManageDepartment(existing.product.departmentId) || !canUserManageDepartment(curDeptId)) {
            emitMessage("Anda tidak memiliki izin memindahkan produk di luar departemen tanggung jawab Anda.")
            return
        }

        viewModelScope.launch {
            val user = currentUser.value
            val processor = user?.fullName ?: "Karyawan"
            val result = repository.moveProductSection(
                productId = existing.product.id,
                targetSectionId = curSecId,
                lastProcessedBy = processor,
                notes = "Dipindahkan saat Mode Pendataan oleh $processor"
            )
            result.onSuccess {
                if (user != null) {
                    val targetSecCode = currentSelectedSection.value?.section?.code ?: "Section"
                    syncManager.logActivity(
                        action = "PINDAH_SECTION",
                        articleNumber = existing.product.articleNumber,
                        productName = existing.product.name,
                        departmentName = existing.departmentName,
                        details = "${existing.sectionCode} → $targetSecCode",
                        user = user
                    )
                }
                _entrySuccessMessage.value = "✓ Artikel ${existing.product.articleNumber} berhasil dipindahkan ke Section ini"
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
        description: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!canUserManageDepartment(departmentId)) {
            emitMessage("Anda tidak memiliki tanggung jawab pada Departemen ini. Produk tidak dapat ditambahkan.")
            onComplete(false)
            return
        }

        if (stockQuantity < 0) {
            emitMessage("Jumlah Stok tidak boleh negatif")
            onComplete(false)
            return
        }

        viewModelScope.launch {
            val user = currentUser.value
            val processor = user?.fullName ?: "Karyawan"
            val pj = authRepository.getResponsibleNamesForDepartment(departmentId)

            val res = repository.createProduct(
                articleNumber = articleNumber,
                name = name,
                departmentId = departmentId,
                sectionId = sectionId,
                stockQuantity = stockQuantity,
                imageUri = imageUri,
                description = description,
                responsiblePerson = pj,
                lastProcessedBy = processor
            )
            res.onSuccess {
                val dept = repository.getDepartmentById(departmentId)
                if (user != null) {
                    syncManager.logActivity(
                        action = "TAMBAH_PRODUK",
                        articleNumber = articleNumber,
                        productName = name,
                        departmentName = dept?.name ?: "",
                        details = "Produk dibuat oleh $processor (Stok: $stockQuantity)",
                        user = user
                    )
                }
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
                emitMessage("Section tujuan tidak ditemukan")
                onComplete(false)
                return@launch
            }

            if (!canUserManageDepartment(current.product.departmentId) || !canUserManageDepartment(targetSection.departmentId)) {
                emitMessage("Anda tidak memiliki izin memindahkan produk di luar departemen tanggung jawab Anda.")
                onComplete(false)
                return@launch
            }

            val user = currentUser.value
            val processor = user?.fullName ?: "Karyawan"

            val res = repository.moveProductSection(
                productId = productId,
                targetSectionId = targetSectionId,
                lastProcessedBy = processor,
                notes = if (notes.isNotEmpty()) notes else "Dipindahkan oleh $processor"
            )
            res.onSuccess {
                if (user != null) {
                    syncManager.logActivity(
                        action = "PINDAH_SECTION",
                        articleNumber = current.product.articleNumber,
                        productName = current.product.name,
                        departmentName = current.departmentName,
                        details = "${current.sectionCode} → ${targetSection.code}",
                        user = user
                    )
                }
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
            val current = repository.getProductById(id)
            if (current == null) {
                emitMessage("Produk tidak ditemukan")
                onComplete(false)
                return@launch
            }

            if (!canUserManageDepartment(current.product.departmentId) || !canUserManageDepartment(departmentId)) {
                emitMessage("Anda tidak memiliki izin mengedit produk di luar departemen tanggung jawab Anda.")
                onComplete(false)
                return@launch
            }

            val user = currentUser.value
            val processor = user?.fullName ?: "Karyawan"
            val pj = authRepository.getResponsibleNamesForDepartment(departmentId)

            val res = repository.updateProduct(
                id = id,
                articleNumber = article,
                name = name,
                departmentId = departmentId,
                sectionId = sectionId,
                stockQuantity = stockQuantity,
                imageUri = imageUri,
                description = description,
                responsiblePerson = pj,
                lastProcessedBy = processor,
                isActive = isActive
            )
            res.onSuccess {
                if (user != null) {
                    syncManager.logActivity(
                        action = "EDIT_PRODUK",
                        articleNumber = article,
                        productName = name,
                        departmentName = current.departmentName,
                        details = "Diperbarui oleh $processor (Stok: $stockQuantity)",
                        user = user
                    )
                }
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
            val current = repository.getProductById(productId)
            if (current == null) {
                emitMessage("Produk tidak ditemukan")
                onComplete(false)
                return@launch
            }

            if (!canUserManageDepartment(current.product.departmentId)) {
                emitMessage("Anda tidak memiliki izin mengubah stok produk di luar departemen tanggung jawab Anda.")
                onComplete(false)
                return@launch
            }

            val user = currentUser.value
            val processor = user?.fullName ?: "Karyawan"
            val oldStock = current.product.stockQuantity

            val res = repository.updateProductStock(productId, newStock, processor)
            res.onSuccess {
                if (user != null) {
                    syncManager.logActivity(
                        action = "UBAH_STOK",
                        articleNumber = current.product.articleNumber,
                        productName = current.product.name,
                        departmentName = current.departmentName,
                        details = "Stok diubah dari $oldStock menjadi $newStock oleh $processor",
                        user = user
                    )
                }
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
            val current = repository.getProductById(productId)
            if (current == null) {
                emitMessage("Produk tidak ditemukan")
                onComplete(false)
                return@launch
            }

            if (!canUserManageDepartment(current.product.departmentId)) {
                emitMessage("Anda tidak memiliki izin menghapus produk di luar departemen tanggung jawab Anda.")
                onComplete(false)
                return@launch
            }

            val user = currentUser.value
            val processor = user?.fullName ?: "Karyawan"

            val res = repository.deleteProduct(productId)
            res.onSuccess {
                if (user != null) {
                    syncManager.logActivity(
                        action = "HAPUS_PRODUK",
                        articleNumber = current.product.articleNumber,
                        productName = current.product.name,
                        departmentName = current.departmentName,
                        details = "Dihapus oleh $processor",
                        user = user
                    )
                }
                emitMessage("Produk berhasil dihapus")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menghapus produk")
                onComplete(false)
            }
        }
    }

    // --- SECTION ACTIONS ---
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
                emitMessage("Section $code berhasil ditambahkan")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menambahkan Section")
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
                emitMessage("Section $code berhasil diperbarui")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal memperbarui Section")
                onComplete(false)
            }
        }
    }

    fun deleteSection(id: Long, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteSection(id)
            res.onSuccess {
                emitMessage("Section berhasil dihapus")
                onComplete(true)
            }.onFailure { err ->
                emitMessage(err.message ?: "Gagal menghapus Section")
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
    fun persistImage(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val path = repository.persistImage(uri)
            onResult(path)
        }
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

    fun getProductHistory(productId: Long): StateFlow<List<SectionHistoryEntity>> {
        return repository.getHistoryForProduct(productId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun emitMessage(msg: String) {
        viewModelScope.launch {
            _userMessage.emit(msg)
        }
    }
}
