package com.example.data.local.model

import androidx.room.Embedded
import com.example.data.local.entity.DepartmentEntity

data class DepartmentWithStats(
    @Embedded
    val department: DepartmentEntity,
    val sectionCount: Int,
    val productCount: Int
)
