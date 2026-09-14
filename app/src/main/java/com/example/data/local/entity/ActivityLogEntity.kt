package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val action: String,
    val articleNumber: String = "",
    val productName: String = "",
    val departmentName: String = "",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
