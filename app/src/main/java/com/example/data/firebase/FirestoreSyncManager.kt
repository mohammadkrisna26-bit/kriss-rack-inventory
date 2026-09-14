package com.example.data.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.dao.InventoryDao
import com.example.data.local.entity.ActivityLogEntity
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.UserEntity
import com.example.data.model.ActivityLogItem
import com.example.data.model.AppUser
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class CloudConnectionStatus {
    CONNECTED,
    SYNCING,
    OFFLINE,
    NOT_CONFIGURED
}

class FirestoreSyncManager(
    private val context: Context,
    private val dao: InventoryDao
) {
    private val tag = "FirestoreSyncManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    private var firestore: FirebaseFirestore? = null
    private var storage: FirebaseStorage? = null

    private val listeners = mutableListOf<ListenerRegistration>()

    private val _connectionStatus = MutableStateFlow(CloudConnectionStatus.OFFLINE)
    val connectionStatus: StateFlow<CloudConnectionStatus> = _connectionStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    init {
        initializeFirebase()
    }

    fun initializeFirebase(): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firestore = FirebaseFirestore.getInstance()
                storage = FirebaseStorage.getInstance()
                _connectionStatus.value = CloudConnectionStatus.CONNECTED
                Log.d(tag, "Firebase initialized successfully.")
                true
            } else {
                _connectionStatus.value = CloudConnectionStatus.NOT_CONFIGURED
                Log.w(tag, "FirebaseApp is not configured.")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Firebase init failed: ${e.message}")
            _connectionStatus.value = CloudConnectionStatus.OFFLINE
            false
        }
    }

    /**
     * Starts listening to Firestore collections in real-time.
     * When any employee makes changes on another device, Room is updated instantly,
     * triggering reactive Jetpack Compose flows on all screens without re-entry.
     */
    fun startRealtimeSync() {
        val db = firestore ?: run {
            if (!initializeFirebase()) return
            firestore ?: return
        }

        // Clean any existing listeners
        detachListeners()

        try {
            _connectionStatus.value = CloudConnectionStatus.SYNCING

            // 1. Listen to DEPARTMENTS
            val deptListener = db.collection("departments")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(tag, "Listen departments failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshots?.let { querySnapshot ->
                        scope.launch {
                            for (doc in querySnapshot.documents) {
                                val id = doc.getLong("id") ?: continue
                                val name = doc.getString("name") ?: ""
                                val description = doc.getString("description") ?: ""
                                val existing = dao.getDepartmentById(id)
                                if (existing == null) {
                                    dao.insertDepartment(DepartmentEntity(id = id, name = name, description = description))
                                } else if (existing.name != name || existing.description != description) {
                                    dao.updateDepartment(existing.copy(name = name, description = description))
                                }
                            }
                            _lastSyncTimestamp.value = System.currentTimeMillis()
                        }
                    }
                }
            listeners.add(deptListener)

            // 2. Listen to SECTIONS
            val sectionListener = db.collection("sections")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(tag, "Listen sections failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshots?.let { querySnapshot ->
                        scope.launch {
                            for (doc in querySnapshot.documents) {
                                val id = doc.getLong("id") ?: continue
                                val code = doc.getString("code") ?: ""
                                val name = doc.getString("name") ?: ""
                                val address = doc.getString("address") ?: ""
                                val departmentId = doc.getLong("departmentId") ?: continue
                                val description = doc.getString("description") ?: ""
                                val existing = dao.getSectionById(id)
                                if (existing == null) {
                                    dao.insertSection(SectionEntity(id = id, code = code, name = name, address = address, departmentId = departmentId, description = description))
                                } else if (existing.code != code || existing.address != address || existing.departmentId != departmentId) {
                                    dao.updateSection(existing.copy(code = code, name = name, address = address, departmentId = departmentId, description = description))
                                }
                            }
                            _lastSyncTimestamp.value = System.currentTimeMillis()
                        }
                    }
                }
            listeners.add(sectionListener)

            // 3. Listen to PRODUCTS (Core Inventory Real-time Listener)
            val productListener = db.collection("products")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(tag, "Listen products failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshots?.let { querySnapshot ->
                        scope.launch {
                            for (doc in querySnapshot.documents) {
                                val article = doc.getString("articleNumber") ?: doc.id
                                val name = doc.getString("name") ?: continue
                                val deptId = doc.getLong("departmentId") ?: continue
                                val secId = doc.getLong("sectionId") ?: continue
                                val stockQuantity = doc.getLong("stockQuantity")?.toInt() ?: 0
                                val imageUri = doc.getString("imageUri")
                                val description = doc.getString("description") ?: ""
                                val responsiblePerson = doc.getString("responsiblePerson") ?: ""
                                val lastProcessedBy = doc.getString("lastProcessedBy") ?: ""
                                val lastProcessedAt = doc.getLong("lastProcessedAt") ?: System.currentTimeMillis()
                                val isActive = doc.getBoolean("isActive") ?: true
                                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                                val existing = dao.getProductByArticle(article)
                                if (existing == null) {
                                    dao.insertProduct(
                                        ProductEntity(
                                            articleNumber = article,
                                            name = name,
                                            departmentId = deptId,
                                            sectionId = secId,
                                            stockQuantity = stockQuantity,
                                            imageUri = imageUri,
                                            description = description,
                                            responsiblePerson = responsiblePerson,
                                            lastProcessedBy = lastProcessedBy,
                                            lastProcessedAt = lastProcessedAt,
                                            isActive = isActive,
                                            updatedAt = updatedAt
                                        )
                                    )
                                } else if (existing.product.updatedAt < updatedAt) {
                                    // Remote version is newer, update local Room
                                    dao.updateProduct(
                                        existing.product.copy(
                                            name = name,
                                            departmentId = deptId,
                                            sectionId = secId,
                                            stockQuantity = stockQuantity,
                                            imageUri = imageUri ?: existing.product.imageUri,
                                            description = description,
                                            responsiblePerson = responsiblePerson,
                                            lastProcessedBy = lastProcessedBy,
                                            lastProcessedAt = lastProcessedAt,
                                            isActive = isActive,
                                            updatedAt = updatedAt
                                        )
                                    )
                                }
                            }
                            _lastSyncTimestamp.value = System.currentTimeMillis()
                            _connectionStatus.value = CloudConnectionStatus.CONNECTED
                        }
                    }
                }
            listeners.add(productListener)

            // 4. Listen to USERS
            val userListener = db.collection("users")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(tag, "Listen users failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshots?.let { querySnapshot ->
                        scope.launch {
                            for (doc in querySnapshot.documents) {
                                val id = doc.getString("id") ?: doc.id
                                val fullName = doc.getString("fullName") ?: ""
                                val email = doc.getString("email") ?: ""
                                val role = doc.getString("role") ?: "KARYAWAN"
                                val assignedIds = doc.get("assignedDepartmentIds") as? List<*> ?: emptyList<Any>()
                                val assignedNames = doc.get("assignedDepartmentNames") as? List<*> ?: emptyList<Any>()
                                val isActive = doc.getBoolean("isActive") ?: true
                                val userEntity = UserEntity(
                                    id = id,
                                    fullName = fullName,
                                    email = email,
                                    role = role,
                                    assignedDepartmentIds = assignedIds.joinToString(","),
                                    assignedDepartmentNames = assignedNames.joinToString(","),
                                    isActive = isActive
                                )
                                dao.insertUser(userEntity)
                            }
                            _lastSyncTimestamp.value = System.currentTimeMillis()
                        }
                    }
                }
            listeners.add(userListener)

            // 5. Listen to ACTIVITY LOGS
            val logListener = db.collection("activityLogs")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(tag, "Listen activityLogs failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    snapshots?.let { querySnapshot ->
                        scope.launch {
                            for (doc in querySnapshot.documents) {
                                val id = doc.id
                                val userId = doc.getString("userId") ?: ""
                                val userName = doc.getString("userName") ?: ""
                                val userEmail = doc.getString("userEmail") ?: ""
                                val action = doc.getString("action") ?: ""
                                val articleNumber = doc.getString("articleNumber") ?: ""
                                val productName = doc.getString("productName") ?: ""
                                val departmentName = doc.getString("departmentName") ?: ""
                                val details = doc.getString("details") ?: ""
                                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                                dao.insertActivityLog(
                                    ActivityLogEntity(
                                        id = id,
                                        userId = userId,
                                        userName = userName,
                                        userEmail = userEmail,
                                        action = action,
                                        articleNumber = articleNumber,
                                        productName = productName,
                                        departmentName = departmentName,
                                        details = details,
                                        timestamp = timestamp
                                    )
                                )
                            }
                        }
                    }
                }
            listeners.add(logListener)

            _connectionStatus.value = CloudConnectionStatus.CONNECTED
        } catch (e: Exception) {
            Log.e(tag, "startRealtimeSync exception: ${e.message}")
            _connectionStatus.value = CloudConnectionStatus.OFFLINE
        }
    }

    /**
     * Stop and detach all listeners to prevent memory leaks (Requirement 15, 22)
     */
    fun detachListeners() {
        for (listener in listeners) {
            try {
                listener.remove()
            } catch (_: Exception) {}
        }
        listeners.clear()
    }

    /**
     * Syncs a single product upsert to Firestore cloud database
     */
    suspend fun syncProductToCloud(product: ProductEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val docData = hashMapOf(
                "id" to product.id,
                "articleNumber" to product.articleNumber,
                "name" to product.name,
                "departmentId" to product.departmentId,
                "sectionId" to product.sectionId,
                "stockQuantity" to product.stockQuantity,
                "imageUri" to (product.imageUri ?: ""),
                "description" to product.description,
                "responsiblePerson" to product.responsiblePerson,
                "lastProcessedBy" to product.lastProcessedBy,
                "lastProcessedAt" to product.lastProcessedAt,
                "isActive" to product.isActive,
                "updatedAt" to product.updatedAt
            )
            db.collection("products").document(product.articleNumber)
                .set(docData, SetOptions.merge())
                .await()
            _connectionStatus.value = CloudConnectionStatus.CONNECTED
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync product to Firestore: ${e.message}")
        }
    }

    /**
     * Syncs updated stock quantity to Firestore
     */
    suspend fun syncProductStockToCloud(
        articleNumber: String,
        newStock: Int,
        processedBy: String,
        updatedAt: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val updateData = hashMapOf<String, Any>(
                "stockQuantity" to newStock,
                "lastProcessedBy" to processedBy,
                "lastProcessedAt" to updatedAt,
                "updatedAt" to updatedAt
            )
            db.collection("products").document(articleNumber)
                .set(updateData, SetOptions.merge())
                .await()
            _connectionStatus.value = CloudConnectionStatus.CONNECTED
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync stock to Firestore: ${e.message}")
        }
    }

    /**
     * Syncs product deletion to Firestore cloud database
     */
    suspend fun syncProductDeleteToCloud(articleNumber: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("products").document(articleNumber).delete().await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete product on Firestore: ${e.message}")
        }
    }

    /**
     * Syncs department upsert to Firestore cloud database
     */
    suspend fun syncDepartmentToCloud(dept: DepartmentEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val docData = hashMapOf(
                "id" to dept.id,
                "name" to dept.name,
                "description" to dept.description
            )
            db.collection("departments").document(dept.id.toString())
                .set(docData, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync dept: ${e.message}")
        }
    }

    /**
     * Syncs section upsert to Firestore cloud database
     */
    suspend fun syncSectionToCloud(section: SectionEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val docData = hashMapOf(
                "id" to section.id,
                "code" to section.code,
                "name" to section.name,
                "address" to section.address,
                "departmentId" to section.departmentId,
                "description" to section.description
            )
            db.collection("sections").document(section.id.toString())
                .set(docData, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync section: ${e.message}")
        }
    }

    /**
     * Syncs user profile to Firestore cloud database
     */
    suspend fun syncUserToCloud(user: AppUser) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val docData = hashMapOf(
                "id" to user.id,
                "fullName" to user.fullName,
                "email" to user.email,
                "role" to user.role,
                "assignedDepartmentIds" to user.assignedDepartmentIds,
                "assignedDepartmentNames" to user.assignedDepartmentNames,
                "isActive" to user.isActive,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("users").document(user.id)
                .set(docData, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync user to Firestore: ${e.message}")
        }
    }

    /**
     * Records an audit log entry in Cloud Firestore & Room (Requirement 8)
     */
    suspend fun logActivity(
        action: String,
        articleNumber: String,
        productName: String,
        departmentName: String,
        details: String,
        user: AppUser
    ) = withContext(Dispatchers.IO) {
        val logId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 1. Save to Room immediately
        dao.insertActivityLog(
            ActivityLogEntity(
                id = logId,
                userId = user.id,
                userName = user.fullName,
                userEmail = user.email,
                action = action,
                articleNumber = articleNumber,
                productName = productName,
                departmentName = departmentName,
                details = details,
                timestamp = now
            )
        )

        // 2. Write to Firestore activityLogs collection
        val db = firestore ?: return@withContext
        try {
            val logData = hashMapOf(
                "id" to logId,
                "userId" to user.id,
                "userName" to user.fullName,
                "userEmail" to user.email,
                "action" to action,
                "articleNumber" to articleNumber,
                "productName" to productName,
                "departmentName" to departmentName,
                "details" to details,
                "timestamp" to now
            )
            db.collection("activityLogs").document(logId).set(logData).await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to push audit log to Firestore: ${e.message}")
        }
    }

    /**
     * Optional upload of product image to Firebase Storage (Requirement 17)
     */
    suspend fun uploadImageToStorage(localFileUri: Uri, articleNumber: String): String? = withContext(Dispatchers.IO) {
        val st = storage ?: return@withContext null
        try {
            val fileName = "${articleNumber}_${System.currentTimeMillis()}.jpg"
            val ref = st.reference.child("product_images/$fileName")

            // Upload from stream or file
            val inputStream = context.contentResolver.openInputStream(localFileUri) ?: return@withContext null
            ref.putStream(inputStream).await()
            val downloadUrl = ref.downloadUrl.await()
            downloadUrl.toString()
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload image to Firebase Storage: ${e.message}")
            null
        }
    }
}
