package com.example.data.local.model

import androidx.room.Embedded
import com.example.data.local.entity.SectionEntity

data class SectionWithStats(
    @Embedded
    val section: SectionEntity,
    val departmentName: String,
    val productCount: Int
) {
    val komuditi: SectionEntity get() = section
}

typealias KomuditiWithStats = SectionWithStats
