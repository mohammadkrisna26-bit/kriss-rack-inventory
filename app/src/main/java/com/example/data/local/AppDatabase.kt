package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.InventoryDao
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SectionEntity

import android.util.Log
import java.io.File

@Database(
    entities = [
        DepartmentEntity::class,
        SectionEntity::class,
        ProductEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    companion object {
        private const val TAG = "AppDatabase"
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN imageUri2 TEXT")
                db.execSQL("ALTER TABLE products ADD COLUMN imageUri3 TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop unique index on sections.code and recreate as non-unique
                db.execSQL("DROP INDEX IF EXISTS index_sections_code")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sections_code ON sections (code)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sections_address ON sections (address)")
            }
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_products_articleNumber")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_articleNumber ON products (articleNumber)")
                db.execSQL("DROP TABLE IF EXISTS section_histories")
                db.execSQL("DROP INDEX IF EXISTS index_sections_code")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sections_code ON sections (code)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sections_address ON sections (address)")
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateToVersion5(db)
            }
        }

        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateToVersion5(db)
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateToVersion5(db)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop unique index on articleNumber and recreate as standard non-unique index
                db.execSQL("DROP INDEX IF EXISTS index_products_articleNumber")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_articleNumber ON products (articleNumber)")
                // Remove section_histories table
                db.execSQL("DROP TABLE IF EXISTS section_histories")
            }
        }

        private fun migrateToVersion5(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS activity_logs")
            db.execSQL("DROP TABLE IF EXISTS users")
            db.execSQL("DROP TABLE IF EXISTS section_histories")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS products_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    articleNumber TEXT NOT NULL,
                    name TEXT NOT NULL,
                    departmentId INTEGER NOT NULL,
                    sectionId INTEGER NOT NULL,
                    stockQuantity INTEGER NOT NULL,
                    imageUri TEXT,
                    imageUri2 TEXT,
                    imageUri3 TEXT,
                    description TEXT NOT NULL,
                    isActive INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY (departmentId) REFERENCES departments (id) ON DELETE RESTRICT,
                    FOREIGN KEY (sectionId) REFERENCES sections (id) ON DELETE RESTRICT
                )
                """.trimIndent()
            )

            // Safely copy existing product columns
            db.execSQL(
                """
                INSERT OR IGNORE INTO products_new (id, articleNumber, name, departmentId, sectionId, stockQuantity, imageUri, description, isActive, createdAt, updatedAt)
                SELECT id, articleNumber, name, departmentId, sectionId, stockQuantity, imageUri, description, isActive, createdAt, updatedAt
                FROM products
                """.trimIndent()
            )

            db.execSQL("DROP TABLE IF EXISTS products")
            db.execSQL("ALTER TABLE products_new RENAME TO products")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_articleNumber ON products (articleNumber)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_departmentId ON products (departmentId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_sectionId ON products (sectionId)")
        }

        /**
         * Creates an automatic local snapshot backup of the SQLite database file
         * before Room touches or migrates it, ensuring zero data loss during APK updates.
         */
        private fun performPreMigrationAutoBackup(context: Context) {
            try {
                val dbFile = context.getDatabasePath("azko_rack_inventory.db")
                if (dbFile.exists() && dbFile.length() > 0) {
                    val backupDir = File(context.filesDir, "db_safety_backups").apply { if (!exists()) mkdirs() }
                    val backupFile = File(backupDir, "azko_auto_backup_latest.db")
                    dbFile.copyTo(backupFile, overwrite = true)
                    Log.i(TAG, "Pre-migration auto database snapshot created successfully (${dbFile.length()} bytes)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating pre-migration auto backup: ${e.message}")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                performPreMigrationAutoBackup(context.applicationContext)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "azko_rack_inventory.db"
                )
                    .addMigrations(MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5, MIGRATION_4_5, MIGRATION_4_6, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

