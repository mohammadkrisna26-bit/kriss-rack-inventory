package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = DepartmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["articleNumber"]),
        Index(value = ["departmentId"]),
        Index(value = ["sectionId"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val articleNumber: String,
    val name: String,
    val departmentId: Long,
    val sectionId: Long,
    val stockQuantity: Int = 0,
    val imageUri: String? = null,
    val imageUri2: String? = null,
    val imageUri3: String? = null,
    val description: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val allImageUris: List<String>
        get() = listOfNotNull(imageUri, imageUri2, imageUri3).filter { it.isNotBlank() }

    val primaryImageUri: String?
        get() = allImageUris.firstOrNull()

    val hasPhoto: Boolean
        get() = allImageUris.isNotEmpty()
}
