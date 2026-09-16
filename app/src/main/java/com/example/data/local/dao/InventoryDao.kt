package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.model.DepartmentWithStats
import com.example.data.local.model.ProductWithLocation
import com.example.data.local.model.SectionWithStats
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    // --- DEPARTMENTS ---
    @Query("SELECT * FROM departments ORDER BY name ASC")
    fun getAllDepartments(): Flow<List<DepartmentEntity>>

    @Query("""
        SELECT d.*, 
            (SELECT COUNT(*) FROM sections s WHERE s.departmentId = d.id) AS sectionCount,
            (SELECT COUNT(*) FROM products p WHERE p.departmentId = d.id) AS productCount
        FROM departments d
        ORDER BY d.name ASC
    """)
    fun getDepartmentsWithStats(): Flow<List<DepartmentWithStats>>

    @Query("SELECT * FROM departments WHERE id = :id LIMIT 1")
    suspend fun getDepartmentById(id: Long): DepartmentEntity?

    @Query("SELECT * FROM departments WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getDepartmentByName(name: String): DepartmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDepartment(department: DepartmentEntity): Long

    @Update
    suspend fun updateDepartment(department: DepartmentEntity): Int

    @Delete
    suspend fun deleteDepartment(department: DepartmentEntity): Int

    @Query("SELECT COUNT(*) FROM sections WHERE departmentId = :deptId")
    suspend fun countSectionsByDepartment(deptId: Long): Int

    @Query("SELECT COUNT(*) FROM products WHERE departmentId = :deptId")
    suspend fun countProductsByDepartment(deptId: Long): Int

    // --- SECTIONS ---
    @Query("SELECT * FROM sections ORDER BY code ASC")
    fun getAllSections(): Flow<List<SectionEntity>>

    @Query("SELECT * FROM sections WHERE departmentId = :deptId ORDER BY code ASC")
    fun getSectionsByDepartment(deptId: Long): Flow<List<SectionEntity>>

    @Query("""
        SELECT s.*, 
            d.name AS departmentName,
            (SELECT COUNT(*) FROM products p WHERE p.sectionId = s.id) AS productCount
        FROM sections s
        INNER JOIN departments d ON s.departmentId = d.id
        ORDER BY s.code ASC
    """)
    fun getSectionsWithStats(): Flow<List<SectionWithStats>>

    @Query("""
        SELECT s.*, 
            d.name AS departmentName,
            (SELECT COUNT(*) FROM products p WHERE p.sectionId = s.id) AS productCount
        FROM sections s
        INNER JOIN departments d ON s.departmentId = d.id
        ORDER BY s.updatedAt DESC
        LIMIT :limit
    """)
    fun getRecentSectionsWithStats(limit: Int = 5): Flow<List<SectionWithStats>>

    @Query("SELECT * FROM sections WHERE id = :id LIMIT 1")
    suspend fun getSectionById(id: Long): SectionEntity?

    @Query("SELECT * FROM sections WHERE LOWER(code) = LOWER(:code) LIMIT 1")
    suspend fun getSectionByCode(code: String): SectionEntity?

    @Query("SELECT * FROM sections WHERE LOWER(address) = LOWER(:address) LIMIT 1")
    suspend fun getSectionByAddress(address: String): SectionEntity?

    @Query("SELECT * FROM sections WHERE LOWER(code) = LOWER(:code) AND LOWER(address) = LOWER(:address) LIMIT 1")
    suspend fun getSectionByCodeAndAddress(code: String, address: String): SectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSection(section: SectionEntity): Long

    @Update
    suspend fun updateSection(section: SectionEntity): Int

    @Delete
    suspend fun deleteSection(section: SectionEntity): Int

    @Query("SELECT COUNT(*) FROM products WHERE sectionId = :sectionId")
    suspend fun countProductsBySection(sectionId: Long): Int

    // --- PRODUCTS ---
    @Query("""
        SELECT p.*,
            d.name AS departmentName,
            s.code AS sectionCode,
            s.name AS sectionName,
            s.address AS sectionAddress
        FROM products p
        INNER JOIN departments d ON p.departmentId = d.id
        INNER JOIN sections s ON p.sectionId = s.id
        ORDER BY p.updatedAt DESC
    """)
    fun getAllProductsWithLocation(): Flow<List<ProductWithLocation>>

    @Query("""
        SELECT p.*,
            d.name AS departmentName,
            s.code AS sectionCode,
            s.name AS sectionName,
            s.address AS sectionAddress
        FROM products p
        INNER JOIN departments d ON p.departmentId = d.id
        INNER JOIN sections s ON p.sectionId = s.id
        WHERE p.sectionId = :sectionId
        ORDER BY p.updatedAt DESC
    """)
    fun getProductsBySection(sectionId: Long): Flow<List<ProductWithLocation>>

    @Query("""
        SELECT p.*,
            d.name AS departmentName,
            s.code AS sectionCode,
            s.name AS sectionName,
            s.address AS sectionAddress
        FROM products p
        INNER JOIN departments d ON p.departmentId = d.id
        INNER JOIN sections s ON p.sectionId = s.id
        WHERE p.id = :id
        LIMIT 1
    """)
    suspend fun getProductById(id: Long): ProductWithLocation?

    @Query("""
        SELECT p.*,
            d.name AS departmentName,
            s.code AS sectionCode,
            s.name AS sectionName,
            s.address AS sectionAddress
        FROM products p
        INNER JOIN departments d ON p.departmentId = d.id
        INNER JOIN sections s ON p.sectionId = s.id
        WHERE p.articleNumber = :articleNumber
        ORDER BY p.updatedAt DESC
    """)
    suspend fun getAllLocationsForArticle(articleNumber: String): List<ProductWithLocation>

    @Query("""
        SELECT p.*,
            d.name AS departmentName,
            s.code AS sectionCode,
            s.name AS sectionName,
            s.address AS sectionAddress
        FROM products p
        INNER JOIN departments d ON p.departmentId = d.id
        INNER JOIN sections s ON p.sectionId = s.id
        WHERE p.articleNumber = :articleNumber AND p.sectionId = :sectionId
        LIMIT 1
    """)
    suspend fun getProductByArticleAndSection(articleNumber: String, sectionId: Long): ProductWithLocation?

    @Query("""
        SELECT p.*,
            d.name AS departmentName,
            s.code AS sectionCode,
            s.name AS sectionName,
            s.address AS sectionAddress
        FROM products p
        INNER JOIN departments d ON p.departmentId = d.id
        INNER JOIN sections s ON p.sectionId = s.id
        WHERE p.articleNumber = :articleNumber
        LIMIT 1
    """)
    suspend fun getProductByArticle(articleNumber: String): ProductWithLocation?

    @Query("""
        SELECT p.*,
            d.name AS departmentName,
            s.code AS sectionCode,
            s.name AS sectionName,
            s.address AS sectionAddress
        FROM products p
        INNER JOIN departments d ON p.departmentId = d.id
        INNER JOIN sections s ON p.sectionId = s.id
        WHERE p.articleNumber = :articleNumber
        LIMIT 1
    """)
    suspend fun getProductByBarcodeOrArticle(articleNumber: String): ProductWithLocation?

    @Query("""
        SELECT p.*,
            d.name AS departmentName,
            s.code AS sectionCode,
            s.name AS sectionName,
            s.address AS sectionAddress
        FROM products p
        INNER JOIN departments d ON p.departmentId = d.id
        INNER JOIN sections s ON p.sectionId = s.id
        WHERE p.articleNumber LIKE '%' || :query || '%'
           OR p.name LIKE '%' || :query || '%'
           OR s.code LIKE '%' || :query || '%'
           OR s.name LIKE '%' || :query || '%'
           OR s.address LIKE '%' || :query || '%'
           OR d.name LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN p.articleNumber = :query THEN 1
                WHEN p.articleNumber LIKE :query || '%' THEN 2
                WHEN p.name LIKE :query || '%' THEN 3
                ELSE 4
            END,
            p.updatedAt DESC
    """)
    fun searchProducts(query: String): Flow<List<ProductWithLocation>>

    @Query("SELECT COUNT(*) FROM products WHERE sectionId = :sectionId")
    fun observeProductCountInSection(sectionId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity): Int

    @Delete
    suspend fun deleteProduct(product: ProductEntity): Int

    @Query("""
        UPDATE products 
        SET stockQuantity = :newStock, updatedAt = :updatedAt 
        WHERE id = :productId
    """)
    suspend fun updateProductStock(
        productId: Long,
        newStock: Int,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query("""
        UPDATE products 
        SET sectionId = :newSectionId, departmentId = :newDepartmentId, updatedAt = :updatedAt 
        WHERE id = :productId
    """)
    suspend fun updateProductLocation(
        productId: Long,
        newSectionId: Long,
        newDepartmentId: Long,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    // --- DASHBOARD COUNTS ---
    @Query("SELECT COUNT(*) FROM products")
    fun getTotalProductsCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT articleNumber) FROM products")
    fun getTotalArticlesCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(stockQuantity), 0) FROM products")
    fun getTotalStockCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM departments")
    fun getTotalDepartmentsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sections")
    fun getTotalSectionsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE (imageUri IS NULL OR imageUri = '') AND (imageUri2 IS NULL OR imageUri2 = '') AND (imageUri3 IS NULL OR imageUri3 = '')")
    fun getProductsWithoutPhotoCount(): Flow<Int>

    // --- BULK BACKUP & RESTORE ---
    @Query("SELECT * FROM departments ORDER BY id ASC")
    suspend fun getAllDepartmentsList(): List<DepartmentEntity>

    @Query("SELECT * FROM sections ORDER BY id ASC")
    suspend fun getAllSectionsList(): List<SectionEntity>

    @Query("SELECT * FROM products ORDER BY id ASC")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDepartments(departments: List<DepartmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<SectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("DELETE FROM sections")
    suspend fun clearSections()

    @Query("DELETE FROM departments")
    suspend fun clearDepartments()
}
