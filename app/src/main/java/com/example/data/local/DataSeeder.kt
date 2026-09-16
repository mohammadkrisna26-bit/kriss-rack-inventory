package com.example.data.local

import com.example.data.local.dao.InventoryDao
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SectionEntity
import kotlinx.coroutines.flow.first

object DataSeeder {
    suspend fun seedIfEmpty(dao: InventoryDao) {
        val existingDepts = dao.getAllDepartments().first()
        if (existingDepts.isNotEmpty()) return

        // 1. Seed Departments
        val hardwareId = dao.insertDepartment(
            DepartmentEntity(name = "Hardware", description = "Perlengkapan baut, mur, kunci, perkakas bengkel dan konstruksi")
        )
        val toolsId = dao.insertDepartment(
            DepartmentEntity(name = "Tools", description = "Peralatan mekanik tangan, power tools, dan perkakas teknis")
        )
        val lockerId = dao.insertDepartment(
            DepartmentEntity(name = "Loker Rak Kabinet", description = "Display rak besi, loker karyawan, lemari arsip display")
        )
        val secId = dao.insertDepartment(
            DepartmentEntity(name = "Self Office Security System", description = "Sistem keamanan mandiri, CCTV, brankas, dan alarm toko")
        )
        val electricalId = dao.insertDepartment(
            DepartmentEntity(name = "Electrical", description = "Peralatan elektronik, lampu, audio TWS, dan aksesoris listrik")
        )

        // 2. Seed Sections (Komuditi)
        val h01Id = dao.insertSection(
            SectionEntity(
                code = "H-01",
                name = "Komuditi H-01",
                address = "Rak Hardware A1",
                departmentId = hardwareId,
                description = "Rak utama baris depan hardware perkakas"
            )
        )
        val h02Id = dao.insertSection(
            SectionEntity(
                code = "H-02",
                name = "Komuditi H-02",
                address = "Rak Hardware A2",
                departmentId = hardwareId,
                description = "Rak display tengah hardware"
            )
        )
        val t01Id = dao.insertSection(
            SectionEntity(
                code = "T-01",
                name = "Komuditi T-01",
                address = "Rak Tools A1",
                departmentId = toolsId,
                description = "Display obeng & perkakas tangan ringan"
            )
        )
        val t03Id = dao.insertSection(
            SectionEntity(
                code = "T-03",
                name = "Komuditi T-03",
                address = "Rak Tools B1",
                departmentId = toolsId,
                description = "Display perkakas paket set & tool box"
            )
        )
        val lk01Id = dao.insertSection(
            SectionEntity(
                code = "LK-01",
                name = "Komuditi LK-01",
                address = "Loker Display Utama",
                departmentId = lockerId,
                description = "Area display unit loker 4-6 pintu"
            )
        )
        val sec01Id = dao.insertSection(
            SectionEntity(
                code = "SEC-01",
                name = "Komuditi SEC-01",
                address = "Display CCTV & Alarm A1",
                departmentId = secId,
                description = "Gondola display kamera pintar & sistem akses"
            )
        )
        val e03Id = dao.insertSection(
            SectionEntity(
                code = "E-03",
                name = "Komuditi E-03",
                address = "Wall Bay 2",
                departmentId = electricalId,
                description = "Dinding display audio nirkabel & earphone TWS"
            )
        )

        // 3. Seed Products
        dao.insertProduct(
            ProductEntity(
                articleNumber = "10000001",
                name = "Kunci Inggris Krisbow 10 inch",
                departmentId = hardwareId,
                sectionId = h01Id,
                stockQuantity = 15,
                description = "Kunci Inggris ukuran 10 inch presisi tinggi bahan chrome vanadium tahan karat.",
                isActive = true
            )
        )
        dao.insertProduct(
            ProductEntity(
                articleNumber = "10000002",
                name = "Tang Kombinasi Krisbow 8 inch",
                departmentId = hardwareId,
                sectionId = h02Id,
                stockQuantity = 20,
                description = "Tang kombinasi heavy duty dengan pegangan ergonomis karet anti slip.",
                isActive = true
            )
        )
        dao.insertProduct(
            ProductEntity(
                articleNumber = "20000001",
                name = "Obeng Plus Krisbow PH2x100mm",
                departmentId = toolsId,
                sectionId = t01Id,
                stockQuantity = 35,
                description = "Obeng plus magnetik ujung presisi untuk pekerjaan mekanik & elektrik.",
                isActive = true
            )
        )
        dao.insertProduct(
            ProductEntity(
                articleNumber = "23456789",
                name = "Obeng Krisbow Set 6 pcs",
                departmentId = toolsId,
                sectionId = t03Id,
                stockQuantity = 12,
                description = "Satu set obeng presisi bolak-balik isi 6 pcs serbaguna.",
                isActive = true
            )
        )
        dao.insertProduct(
            ProductEntity(
                articleNumber = "30000001",
                name = "Loker Besi 4 Pintu Abu-abu",
                departmentId = lockerId,
                sectionId = lk01Id,
                stockQuantity = 4,
                description = "Loker kantor penyimpanan arsip dan barang material pelat baja tebal.",
                isActive = true
            )
        )
        dao.insertProduct(
            ProductEntity(
                articleNumber = "40000001",
                name = "Smart CCTV WiFi PTZ 360",
                departmentId = secId,
                sectionId = sec01Id,
                stockQuantity = 8,
                description = "Kamera keamanan outdoor/indoor night vision deteksi gerakan pintar AI.",
                isActive = true
            )
        )
        dao.insertProduct(
            ProductEntity(
                articleNumber = "190351",
                name = "TWS",
                departmentId = electricalId,
                sectionId = e03Id,
                stockQuantity = 25,
                description = "True Wireless Stereo Bluetooth Earbuds dengan peredam bising aktif.",
                isActive = true
            )
        )
    }
}
