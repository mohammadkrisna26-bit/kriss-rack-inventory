package com.example.data.local.model

import androidx.room.Embedded
import com.example.data.local.entity.ProductEntity

data class ProductWithLocation(
    @Embedded
    val product: ProductEntity,
    val departmentName: String,
    val sectionCode: String,
    val sectionName: String,
    val sectionAddress: String
) {
    val komuditiCode: String get() = sectionCode
    val komuditiName: String get() = sectionName
    val komuditiAddress: String get() = sectionAddress
}
