package com.campusfold.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.campusfold.tracker.data.dao.AccountDao
import com.campusfold.tracker.data.dao.CategoryDao
import com.campusfold.tracker.data.dao.PendingSettlementSuggestionDao
import com.campusfold.tracker.data.dao.PersonDao
import com.campusfold.tracker.data.dao.SplitItemDao
import com.campusfold.tracker.data.dao.TransactionDao
import com.campusfold.tracker.data.dao.VendorMappingDao
import com.campusfold.tracker.data.entity.AccountEntity
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
        PendingSettlementSuggestionEntity::class,
        AccountEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun vendorMappingDao(): VendorMappingDao
    abstract fun transactionDao(): TransactionDao
    abstract fun personDao(): PersonDao
    abstract fun splitItemDao(): SplitItemDao
    abstract fun pendingSettlementSuggestionDao(): PendingSettlementSuggestionDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `accounts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `accountType` TEXT NOT NULL,
                        `last4` TEXT NOT NULL,
                        `creditLimitOrBalance` REAL NOT NULL,
                        `colorHex` TEXT NOT NULL
                    )
                """.trimIndent())

                // Pre-seed the default 6 accounts
                db.execSQL("INSERT OR IGNORE INTO `accounts` (`id`, `name`, `bankName`, `accountType`, `last4`, `creditLimitOrBalance`, `colorHex`) VALUES (1, 'PNB Credit Card 8598', 'PNB', 'CREDIT_CARD', '8598', 59396.93, '#F43F5E')")
                db.execSQL("INSERT OR IGNORE INTO `accounts` (`id`, `name`, `bankName`, `accountType`, `last4`, `creditLimitOrBalance`, `colorHex`) VALUES (2, 'Axis Bank XX2003', 'Axis Bank', 'SAVINGS', '2003', 0.0, '#8B5CF6')")
                db.execSQL("INSERT OR IGNORE INTO `accounts` (`id`, `name`, `bankName`, `accountType`, `last4`, `creditLimitOrBalance`, `colorHex`) VALUES (3, 'Federal (Jupiter) 2548', 'Federal Bank', 'SAVINGS', '2548', 0.0, '#F59E0B')")
                db.execSQL("INSERT OR IGNORE INTO `accounts` (`id`, `name`, `bankName`, `accountType`, `last4`, `creditLimitOrBalance`, `colorHex`) VALUES (4, 'PNB Savings Account 5557', 'PNB', 'SAVINGS', '5557', 0.0, '#06B6D4')")
                db.execSQL("INSERT OR IGNORE INTO `accounts` (`id`, `name`, `bankName`, `accountType`, `last4`, `creditLimitOrBalance`, `colorHex`) VALUES (5, 'Axis Bank Savings Account 5476', 'Axis Bank', 'SAVINGS', '5476', 0.0, '#3B82F6')")
                db.execSQL("INSERT OR IGNORE INTO `accounts` (`id`, `name`, `bankName`, `accountType`, `last4`, `creditLimitOrBalance`, `colorHex`) VALUES (6, 'Axis Bank Credit Card', 'Axis Bank', 'CREDIT_CARD', 'Axis CC', 0.0, '#EC4899')")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `description` TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "campus_fold_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade()
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

                // Pre-seed 6 User Accounts
                db.execSQL("INSERT INTO accounts (id, name, bankName, accountType, last4, creditLimitOrBalance, colorHex) VALUES (1, 'PNB Credit Card 8598', 'PNB', 'CREDIT_CARD', '8598', 59396.93, '#F43F5E')")
                db.execSQL("INSERT INTO accounts (id, name, bankName, accountType, last4, creditLimitOrBalance, colorHex) VALUES (2, 'Axis Bank XX2003', 'Axis Bank', 'SAVINGS', '2003', 0.0, '#8B5CF6')")
                db.execSQL("INSERT INTO accounts (id, name, bankName, accountType, last4, creditLimitOrBalance, colorHex) VALUES (3, 'Federal (Jupiter) 2548', 'Federal Bank', 'SAVINGS', '2548', 0.0, '#F59E0B')")
                db.execSQL("INSERT INTO accounts (id, name, bankName, accountType, last4, creditLimitOrBalance, colorHex) VALUES (4, 'PNB Savings Account 5557', 'PNB', 'SAVINGS', '5557', 0.0, '#06B6D4')")
                db.execSQL("INSERT INTO accounts (id, name, bankName, accountType, last4, creditLimitOrBalance, colorHex) VALUES (5, 'Axis Bank Savings Account 5476', 'Axis Bank', 'SAVINGS', '5476', 0.0, '#3B82F6')")
                db.execSQL("INSERT INTO accounts (id, name, bankName, accountType, last4, creditLimitOrBalance, colorHex) VALUES (6, 'Axis Bank Credit Card', 'Axis Bank', 'CREDIT_CARD', 'Axis CC', 0.0, '#EC4899')")
            }
        }
    }
}
