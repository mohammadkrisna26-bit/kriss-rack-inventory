package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.InventoryDao
import com.example.data.local.entity.ActivityLogEntity
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.entity.SectionHistoryEntity
import com.example.data.local.entity.UserEntity

@Database(
    entities = [
        DepartmentEntity::class,
        SectionEntity::class,
        ProductEntity::class,
        SectionHistoryEntity::class,
        UserEntity::class,
        ActivityLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "azko_rack_inventory.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
