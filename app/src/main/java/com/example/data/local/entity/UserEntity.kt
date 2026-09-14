package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val fullName: String,
    val email: String,
    val role: String, // "ADMIN" or "KARYAWAN"
    val assignedDepartmentIds: String = "", // Comma-separated IDs: "1,2,3"
    val assignedDepartmentNames: String = "", // Comma-separated: "Tools,Hardware"
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
