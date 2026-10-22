package com.campusfold.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.campusfold.tracker.data.dao.CategoryDao
import com.campusfold.tracker.data.dao.PendingSettlementSuggestionDao
import com.campusfold.tracker.data.dao.PersonDao
import com.campusfold.tracker.data.dao.SplitItemDao
import com.campusfold.tracker.data.dao.TransactionDao
import com.campusfold.tracker.data.dao.VendorMappingDao
import com.campusfold.tracker.data.entity.CategoryEntity
import com.campusfold.tracker.data.entity.PendingSettlementSuggestionEntity
import com.campusfold.tracker.data.entity.PersonEntity
import com.campusfold.tracker.data.entity.SplitItemEntity
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.VendorMappingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CategoryEntity::class,
        VendorMappingEntity::class,
        TransactionEntity::class,
        PersonEntity::class,
        SplitItemEntity::class,
        PendingSettlementSuggestionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun vendorMappingDao(): VendorMappingDao
    abstract fun transactionDao(): TransactionDao
    abstract fun personDao(): PersonDao
    abstract fun splitItemDao(): SplitItemDao
    abstract fun pendingSettlementSuggestionDao(): PendingSettlementSuggestionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "campus_fold_tracker.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Pre-seed 6 Default Categories
                db.execSQL("INSERT INTO categories (id, name, colorHex, monthlyBudget) VALUES (1, 'Food', '#10B981', 5000.0)")
                db.execSQL("INSERT INTO categories (id, name, colorHex, monthlyBudget) VALUES (2, 'Chai & Smoke', '#F59E0B', 2000.0)")
                db.execSQL("INSERT INTO categories (id, name, colorHex, monthlyBudget) VALUES (3, 'Beverages', '#06B6D4', 1500.0)")
                db.execSQL("INSERT INTO categories (id, name, colorHex, monthlyBudget) VALUES (4, 'Lifestyle', '#8B5CF6', 3000.0)")
                db.execSQL("INSERT INTO categories (id, name, colorHex, monthlyBudget) VALUES (5, 'Utilities', '#3B82F6', 1500.0)")
                db.execSQL("INSERT INTO categories (id, name, colorHex, monthlyBudget) VALUES (6, 'Clothing', '#EC4899', 2500.0)")

                // Pre-seed 6 Campus Vendor Mappings
                db.execSQL("INSERT INTO vendor_mappings (id, rawSmsKeyword, friendlyName, categoryId) VALUES (1, 'Sekar store', 'Akka shop', 2)")
                db.execSQL("INSERT INTO vendor_mappings (id, rawSmsKeyword, friendlyName, categoryId) VALUES (2, 'KARTHIK', 'Bistro', 1)")
                db.execSQL("INSERT INTO vendor_mappings (id, rawSmsKeyword, friendlyName, categoryId) VALUES (3, 'CHAATTERBOX', 'Chaaterbox', 1)")
                db.execSQL("INSERT INTO vendor_mappings (id, rawSmsKeyword, friendlyName, categoryId) VALUES (4, 'Mohan', 'Chennai beverage', 3)")
                db.execSQL("INSERT INTO vendor_mappings (id, rawSmsKeyword, friendlyName, categoryId) VALUES (5, 'HungerBox', 'Hungerbox', 1)")
                db.execSQL("INSERT INTO vendor_mappings (id, rawSmsKeyword, friendlyName, categoryId) VALUES (6, 'DD STORE', 'Utility store', 5)")
            }
        }
    }
}
