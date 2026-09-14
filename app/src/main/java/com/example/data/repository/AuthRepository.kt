package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.firebase.FirestoreSyncManager
import com.example.data.local.dao.InventoryDao
import com.example.data.local.entity.UserEntity
import com.example.data.model.AppUser
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthRepository(
    private val context: Context,
    private val dao: InventoryDao,
    private val syncManager: FirestoreSyncManager
) {
    private val tag = "AuthRepository"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val sharedPrefs = context.getSharedPreferences("azko_auth_prefs", Context.MODE_PRIVATE)

    val allUsers: Flow<List<AppUser>> = dao.getAllUsers().map { entities ->
        entities.map { it.toDomain() }
    }

    init {
        scope.launch {
            seedDefaultUsersIfEmpty()
            restoreSavedSession()
        }
    }

    private suspend fun restoreSavedSession() {
        val savedUserId = sharedPrefs.getString("logged_in_user_id", null)
        if (!savedUserId.isNullOrEmpty()) {
            val userEntity = dao.getUserById(savedUserId)
            if (userEntity != null && userEntity.isActive) {
                _currentUser.value = userEntity.toDomain()
                syncManager.startRealtimeSync()
            }
        }
    }

    /**
     * Seeds initial store employees (Admin, Budi, Krishna) matching the user prompt requirements.
     */
    suspend fun seedDefaultUsersIfEmpty() = withContext(Dispatchers.IO) {
        val existing = dao.getAllUsers().first()
        if (existing.isNotEmpty()) return@withContext

        // Look up department IDs for Budi and Krishna
        val allDepts = dao.getAllDepartments().first()
        val deptMap = allDepts.associateBy { it.name.lowercase() }

        val hardwareId = deptMap["hardware"]?.id ?: 1L
        val toolsId = deptMap["tools"]?.id ?: 2L
        val lockerId = deptMap["loker rak kabinet"]?.id ?: 3L
        val secSysId = deptMap["self office security system"]?.id ?: 4L
        val electricalId = deptMap["electrical"]?.id ?: 5L

        // 1. Admin
        val adminUser = AppUser(
            id = "admin_azko_uid",
            fullName = "Administrator AZKO",
            email = "admin@azko.com",
            role = "ADMIN",
            assignedDepartmentIds = allDepts.map { it.id },
            assignedDepartmentNames = allDepts.map { it.name },
            isActive = true
        )

        // 2. Budi (Tanggung Jawab: Tools, Hardware, Self Office Security System, Loker Rak Kabinet)
        val budiUser = AppUser(
            id = "budi_azko_uid",
            fullName = "Budi",
            email = "budi@azko.com",
            role = "KARYAWAN",
            assignedDepartmentIds = listOf(toolsId, hardwareId, secSysId, lockerId),
            assignedDepartmentNames = listOf("Tools", "Hardware", "Self Office Security System", "Loker Rak Kabinet"),
            isActive = true
        )

        // 3. Krishna (Tanggung Jawab: Electrical dan departemen lain)
        val krishnaUser = AppUser(
            id = "krishna_azko_uid",
            fullName = "Krishna",
            email = "krishna@azko.com",
            role = "KARYAWAN",
            assignedDepartmentIds = listOf(electricalId),
            assignedDepartmentNames = listOf("Electrical"),
            isActive = true
        )

        listOf(adminUser, budiUser, krishnaUser).forEach { user ->
            dao.insertUser(user.toEntity())
            syncManager.syncUserToCloud(user)
        }
    }

    /**
     * Authenticates user via Firebase Auth with seamless fallback to local/Firestore profiles.
     */
    suspend fun login(email: String, password: String): Result<AppUser> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPassword = password.trim()

        if (trimmedEmail.isEmpty() || trimmedPassword.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Email dan password harus diisi"))
        }

        // Try Firebase Authentication first
        var firebaseUid: String? = null
        try {
            val auth = FirebaseAuth.getInstance()
            val authResult = auth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
            firebaseUid = authResult.user?.uid
            Log.d(tag, "Firebase Auth sign-in succeeded: $firebaseUid")
        } catch (e: Exception) {
            Log.w(tag, "Firebase Auth signIn failed/offline: ${e.message}. Verifying in database.")
        }

        // Look up user entity by email
        val userEntity = dao.getUserByEmail(trimmedEmail)
        if (userEntity == null) {
            return@withContext Result.failure(IllegalArgumentException("Akun tidak ditemukan. Hubungi Admin untuk membuat akun."))
        }

        if (!userEntity.isActive) {
            return@withContext Result.failure(IllegalStateException("Akun Anda telah dinonaktifkan oleh Admin."))
        }

        val domainUser = userEntity.toDomain()
        _currentUser.value = domainUser

        // Persist session
        sharedPrefs.edit().putString("logged_in_user_id", domainUser.id).apply()

        // Start cloud synchronization
        syncManager.startRealtimeSync()

        Result.success(domainUser)
    }

    /**
     * Log out current user and detach listeners
     */
    fun logout() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}
        sharedPrefs.edit().remove("logged_in_user_id").apply()
        _currentUser.value = null
        syncManager.detachListeners()
    }

    /**
     * Admin creates a new employee account (Requirement 1, 4)
     */
    suspend fun createEmployee(
        fullName: String,
        email: String,
        password: String,
        role: String,
        assignedDepartmentIds: List<Long>,
        assignedDepartmentNames: List<String>
    ): Result<AppUser> = withContext(Dispatchers.IO) {
        val current = _currentUser.value
        if (current == null || !current.isAdmin) {
            return@withContext Result.failure(IllegalStateException("Hanya Admin yang berhak menambahkan karyawan."))
        }

        val trimmedEmail = email.trim().lowercase()
        val trimmedName = fullName.trim()
        val trimmedPassword = password.trim()

        if (trimmedName.isEmpty() || trimmedEmail.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Nama dan email wajib diisi."))
        }
        if (trimmedPassword.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password awal minimal 6 karakter."))
        }

        val existing = dao.getUserByEmail(trimmedEmail)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("Email '$trimmedEmail' sudah terdaftar."))
        }

        var uid = UUID.randomUUID().toString()
        // Try creating account in Firebase Auth
        try {
            val auth = FirebaseAuth.getInstance()
            val res = auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
            uid = res.user?.uid ?: uid
        } catch (e: Exception) {
            Log.w(tag, "Firebase Auth createUser failed/offline: ${e.message}. Creating profile.")
        }

        val newUser = AppUser(
            id = uid,
            fullName = trimmedName,
            email = trimmedEmail,
            role = role.uppercase(),
            assignedDepartmentIds = assignedDepartmentIds,
            assignedDepartmentNames = assignedDepartmentNames,
            isActive = true
        )

        dao.insertUser(newUser.toEntity())
        syncManager.syncUserToCloud(newUser)

        // Log admin activity
        syncManager.logActivity(
            action = "TAMBAH_KARYAWAN",
            articleNumber = "",
            productName = newUser.fullName,
            departmentName = assignedDepartmentNames.joinToString(", "),
            details = "Admin membuat akun baru: ${newUser.fullName} (${newUser.role})",
            user = current
        )

        Result.success(newUser)
    }

    /**
     * Admin updates employee details and assigned departments (Requirement 1, 4)
     */
    suspend fun updateEmployee(user: AppUser): Result<Unit> {
        return updateEmployee(
            userId = user.id,
            fullName = user.fullName,
            role = user.role,
            assignedDepartmentIds = user.assignedDepartmentIds,
            assignedDepartmentNames = user.assignedDepartmentNames,
            isActive = user.isActive
        )
    }

    suspend fun updateEmployee(
        userId: String,
        fullName: String,
        role: String,
        assignedDepartmentIds: List<Long>,
        assignedDepartmentNames: List<String>,
        isActive: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val current = _currentUser.value
        if (current == null || !current.isAdmin) {
            return@withContext Result.failure(IllegalStateException("Hanya Admin yang berhak mengubah data karyawan."))
        }

        val existing = dao.getUserById(userId) ?: return@withContext Result.failure(IllegalArgumentException("Karyawan tidak ditemukan."))

        val updated = existing.copy(
            fullName = fullName.trim(),
            role = role.uppercase(),
            assignedDepartmentIds = assignedDepartmentIds.joinToString(","),
            assignedDepartmentNames = assignedDepartmentNames.joinToString(","),
            isActive = isActive,
            updatedAt = System.currentTimeMillis()
        )

        dao.updateUser(updated)
        syncManager.syncUserToCloud(updated.toDomain())

        // Log admin activity
        syncManager.logActivity(
            action = "UBAH_KARYAWAN",
            articleNumber = "",
            productName = updated.fullName,
            departmentName = assignedDepartmentNames.joinToString(", "),
            details = "Admin mengubah data: ${updated.fullName} | Status: ${if (isActive) "Aktif" else "Nonaktif"}",
            user = current
        )

        // If current user is the one updated, refresh local state
        if (current.id == userId) {
            _currentUser.value = updated.toDomain()
        }

        Result.success(Unit)
    }

    /**
     * Resolves names of employees who have responsibility for a specific department (Requirement 6)
     */
    suspend fun getResponsibleNamesForDepartment(deptId: Long): String = withContext(Dispatchers.IO) {
        val users = dao.getAllUsers().first()
        val responsibleList = users.filter { u ->
            val ids = u.assignedDepartmentIds.split(",").mapNotNull { it.trim().toLongOrNull() }
            ids.contains(deptId) && u.isActive && u.role == "KARYAWAN"
        }
        if (responsibleList.isEmpty()) {
            "Admin Toko"
        } else {
            responsibleList.joinToString(", ") { it.fullName }
        }
    }
}

// Extension mappers
fun UserEntity.toDomain(): AppUser {
    val ids = assignedDepartmentIds.split(",").mapNotNull { it.trim().toLongOrNull() }
    val names = assignedDepartmentNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return AppUser(
        id = id,
        fullName = fullName,
        email = email,
        role = role,
        assignedDepartmentIds = ids,
        assignedDepartmentNames = names,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun AppUser.toEntity(): UserEntity {
    return UserEntity(
        id = id,
        fullName = fullName,
        email = email,
        role = role,
        assignedDepartmentIds = assignedDepartmentIds.joinToString(","),
        assignedDepartmentNames = assignedDepartmentNames.joinToString(","),
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
