package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.repository.InventoryRepository
import com.example.util.BackupManager
import com.example.util.ExcelExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: InventoryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = InventoryRepository(db.inventoryDao(), context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFullBackupAndRestoreCycleWithImages() {
        runBlocking {
            val dao = db.inventoryDao()

        // 1. Masukkan data Departemen dan Komuditi
        val deptId = dao.insertDepartment(DepartmentEntity(name = "Tools", description = "Perkakas"))
        val secId = dao.insertSection(
            SectionEntity(
                departmentId = deptId,
                code = "T-01",
                name = "Power Tools",
                address = "Rak Tools Utama Blok A",
                description = "Rak display power tools"
            )
        )

        // Buat file gambar produk buatan di folder internal app
        val imagesDir = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }
        val testImageFile = File(imagesDir, "prod_test_article101.jpg")
        val fakeImageData = "FAKE_JPEG_IMAGE_BINARY_DATA_TEST_12345".toByteArray()
        testImageFile.writeBytes(fakeImageData)
        assertTrue("Test image file must exist before backup", testImageFile.exists())

        // Masukkan data produk baru beserta fotonya
        val productId = dao.insertProduct(
            ProductEntity(
                articleNumber = "1000101",
                name = "Bor Listrik Impact Cordless 20V",
                departmentId = deptId,
                sectionId = secId,
                stockQuantity = 42,
                imageUri = testImageFile.absolutePath,
                description = "Bor cordless 20V brushless garansi 2 tahun"
            )
        )
        assertTrue("Product inserted successfully", productId > 0)

        // Verifikasi produk sebelum backup
        val initialProducts = dao.getAllProductsWithLocation().first()
        assertEquals(1, initialProducts.size)
        val initialProduct = initialProducts[0]
        assertEquals("1000101", initialProduct.product.articleNumber)
        assertEquals(42, initialProduct.product.stockQuantity)
        assertEquals("T-01", initialProduct.sectionCode)
        assertEquals("Rak Tools Utama Blok A", initialProduct.sectionAddress)
        assertEquals("Tools", initialProduct.departmentName)

        // 2. Lakukan "Backup Data" (ZIP format containing manifest + actual image binary)
        val backupZipFile = repository.createZipBackup()
        assertNotNull(backupZipFile)
        assertTrue("Backup ZIP file exists", backupZipFile.exists())
        assertTrue("Backup ZIP file has content", backupZipFile.length() > 0)

        // 3. Hapus data produk dari aplikasi atau lakukan reset (simulasi penghapusan)
        dao.deleteProduct(initialProduct.product)
        testImageFile.delete() // hapus file gambar dari storage
        assertFalse("Image file must be gone after delete", testImageFile.exists())

        val emptyProducts = dao.getAllProductsWithLocation().first()
        assertEquals("Product count should be 0 after delete", 0, emptyProducts.size)

        // 4. Lakukan "Restore Data" dari file backup yang baru dibuat
        val restoreStream = FileInputStream(backupZipFile)
        val restoreResult = repository.restoreBackup(restoreStream)
        assertTrue("Restore operation must succeed", restoreResult.isSuccess)
        val resultSummary = restoreResult.getOrThrow()
        assertEquals(1, resultSummary.productsRestored)
        assertTrue("Image must be restored", resultSummary.imagesRestored >= 1)

        // 5. Pastikan verifikasi mandatory:
        // - Data produk kembali utuh
        val restoredProducts = dao.getAllProductsWithLocation().first()
        assertEquals(1, restoredProducts.size)
        val restoredProduct = restoredProducts[0]

        // - Nomor artikel dan nama produk kembali sesuai
        assertEquals("1000101", restoredProduct.product.articleNumber)
        assertEquals("Bor Listrik Impact Cordless 20V", restoredProduct.product.name)

        // - Stok kembali sesuai data sebelumnya
        assertEquals(42, restoredProduct.product.stockQuantity)

        // - Lokasi Komuditi tidak berubah
        assertEquals("T-01", restoredProduct.sectionCode)
        assertEquals("Rak Tools Utama Blok A", restoredProduct.sectionAddress)
        assertEquals("Tools", restoredProduct.departmentName)

        // - Foto produk kembali muncul dan dapat dibuka
        val restoredImageUri = restoredProduct.product.imageUri
        assertNotNull("Restored image URI must not be null", restoredImageUri)
        val restoredFile = File(restoredImageUri!!)
        assertTrue("Restored image file must exist on disk", restoredFile.exists())
        val restoredBytes = restoredFile.readBytes()
        assertArrayEquals("Restored image binary content must match original byte-for-byte", fakeImageData, restoredBytes)

        // Cleanup temporary backup file
        backupZipFile.delete()
        }
    }

    @Test
    fun testExcelExportFunctionality() {
        runBlocking {
            val dao = db.inventoryDao()
            val deptId = dao.insertDepartment(DepartmentEntity(name = "Hardware", description = "Baut"))
            val secId = dao.insertSection(
                SectionEntity(
                    departmentId = deptId,
                    code = "H-01",
                    name = "Hardware Komuditi",
                    address = "Rak Display H1",
                    description = "Komuditi Hardware"
                )
            )
            dao.insertProduct(
                ProductEntity(
                    articleNumber = "2000202",
                    name = "Gembok Baja 60mm",
                    departmentId = deptId,
                    sectionId = secId,
                    stockQuantity = 15,
                    imageUri = null,
                    description = "Gembok baja anti maling"
                )
            )

            val products = dao.getAllProductsWithLocation().first()
            val excelFile = ExcelExporter.exportToExcel(context, products)

            assertNotNull("Excel file must be generated", excelFile)
            assertTrue("Excel file exists on disk", excelFile.exists())
            assertTrue("Excel file is not empty", excelFile.length() > 0)
            assertTrue("File ends with .xlsx", excelFile.name.endsWith(".xlsx"))

            excelFile.delete()
        }
    }
}
