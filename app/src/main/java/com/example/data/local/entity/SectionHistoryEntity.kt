package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "section_histories")
data class SectionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val articleNumber: String,
    val productName: String,
    val fromSectionId: Long,
    val fromSectionCode: String,
    val fromSectionAddress: String,
    val toSectionId: Long,
    val toSectionCode: String,
    val toSectionAddress: String,
    val movedAt: Long = System.currentTimeMillis(),
    val notes: String = ""
)
