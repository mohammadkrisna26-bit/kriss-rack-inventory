package com.example.data.model

data class ActivityLogItem(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val action: String = "", // "TAMBAH_PRODUK", "EDIT_PRODUK", "PINDAH_SECTION", "HAPUS_PRODUK", "KELOLA_USER"
    val articleNumber: String = "",
    val productName: String = "",
    val departmentName: String = "",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
