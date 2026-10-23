package com.campusfold.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.campusfold.tracker.data.dao.*
import com.campusfold.tracker.data.entity.*

@Database(
    entities = [
        CategoryEntity::class,
        VendorEntity::class,
        TransactionEntity::class,
        PersonEntity::class,
        SplitItemEntity::class,
        PendingSettlementSuggestionEntity::class,
        AccountEntity::class,
        AccountAliasEntity::class,
        MergeLogEntity::class,
        NotSamePairEntity::class,
        RuleEntity::class,
        ReviewItemEntity::class,
        UnparsedMessageEntity::class,
        ProcessedMessageEntity::class,
        AiCacheEntity::class,
        ScanHistoryEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun vendorDao(): VendorDao
    fun vendorMappingDao(): VendorDao = vendorDao()
    abstract fun transactionDao(): TransactionDao
    abstract fun personDao(): PersonDao
    abstract fun splitItemDao(): SplitItemDao
    abstract fun pendingSettlementSuggestionDao(): PendingSettlementSuggestionDao
    abstract fun accountDao(): AccountDao
    abstract fun accountAliasDao(): AccountAliasDao
    abstract fun mergeLogDao(): MergeLogDao
    abstract fun notSamePairDao(): NotSamePairDao
    abstract fun ruleDao(): RuleDao
    abstract fun reviewItemDao(): ReviewItemDao
    abstract fun unparsedMessageDao(): UnparsedMessageDao
    abstract fun processedMessageDao(): ProcessedMessageDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun scanHistoryDao(): ScanHistoryDao

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
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `description` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Disable foreign key constraints temporarily while altering/recreating tables
                db.execSQL("PRAGMA foreign_keys=OFF;")

                // 1. Categories (colorHex -> color, monthlyBudget -> budgetPaise, editedFields)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `categories_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `color` TEXT NOT NULL,
                        `budgetPaise` INTEGER,
                        `editedFields` TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `categories_new` (`id`, `name`, `color`, `budgetPaise`, `editedFields`)
                    SELECT 
                        `id`, 
                        `name`, 
                        `colorHex`, 
                        CASE WHEN `monthlyBudget` > 0 THEN CAST(ROUND(`monthlyBudget` * 100.0) AS INTEGER) ELSE NULL END,
                        '[]'
                    FROM `categories`
                """.trimIndent())

                db.execSQL("DROP TABLE `categories`")
                db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")

                // 2. Accounts (new fields: bankKey, type, displayName, color, balancePaise, balanceAsOf, balanceSource, creditLimitPaise, isArchived, editedFields)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `accounts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `bankKey` TEXT NOT NULL,
                        `last4` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `color` TEXT NOT NULL,
                        `balancePaise` INTEGER NOT NULL,
                        `balanceAsOf` INTEGER NOT NULL,
                        `balanceSource` TEXT NOT NULL,
                        `creditLimitPaise` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `editedFields` TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `accounts_new` (
                        `id`, `bankName`, `bankKey`, `last4`, `type`, `displayName`, `color`,
                        `balancePaise`, `balanceAsOf`, `balanceSource`, `creditLimitPaise`, `isArchived`, `editedFields`
                    )
                    SELECT
                        `id`,
                        `bankName`,
                        UPPER(TRIM(`bankName`)),
                        `last4`,
                        CASE 
                            WHEN `accountType` = 'CREDIT_CARD' THEN 'CREDIT_CARD'
                            WHEN `accountType` = 'SAVINGS' THEN 'SAVINGS'
                            WHEN `accountType` = 'WALLET' THEN 'WALLET'
                            ELSE 'UNASSIGNED'
                        END,
                        `name`,
                        `colorHex`,
                        CASE 
                            WHEN `accountType` != 'CREDIT_CARD' THEN CAST(ROUND(`creditLimitOrBalance` * 100.0) AS INTEGER)
                            ELSE 0
                        END,
                        strftime('%s', 'now') * 1000,
                        'MANUAL',
                        CASE 
                            WHEN `accountType` = 'CREDIT_CARD' THEN CAST(ROUND(`creditLimitOrBalance` * 100.0) AS INTEGER)
                            ELSE 0
                        END,
                        0,
                        '[]'
                    FROM `accounts`
                """.trimIndent())

                db.execSQL("DROP TABLE `accounts`")
                db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_bankKey` ON `accounts` (`bankKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_last4` ON `accounts` (`last4`)")

                // 3. Account Aliases
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `account_aliases` (
                        `accountId` INTEGER NOT NULL,
                        `bankKey` TEXT NOT NULL,
                        `last4` TEXT NOT NULL,
                        PRIMARY KEY(`accountId`, `bankKey`, `last4`),
                        FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_aliases_bankKey_last4` ON `account_aliases` (`bankKey`, `last4`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_account_aliases_accountId` ON `account_aliases` (`accountId`)")

                // Seed initial aliases from existing accounts
                db.execSQL("""
                    INSERT OR IGNORE INTO `account_aliases` (`accountId`, `bankKey`, `last4`)
                    SELECT `id`, `bankKey`, `last4` FROM `accounts` WHERE `last4` != ''
                """.trimIndent())

                // 4. Merge Log
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `merge_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `survivingAccountId` INTEGER NOT NULL,
                        `mergedAccountSnapshot` TEXT NOT NULL,
                        `movedTransactionIds` TEXT NOT NULL,
                        `mode` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `undoneAt` INTEGER
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_merge_log_survivingAccountId` ON `merge_log` (`survivingAccountId`)")

                // 5. Not Same Pairs
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `not_same_pairs` (
                        `accountIdA` INTEGER NOT NULL,
                        `accountIdB` INTEGER NOT NULL,
                        PRIMARY KEY(`accountIdA`, `accountIdB`),
                        FOREIGN KEY(`accountIdA`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`accountIdB`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_not_same_pairs_accountIdA` ON `not_same_pairs` (`accountIdA`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_not_same_pairs_accountIdB` ON `not_same_pairs` (`accountIdB`)")

                // 6. Vendors (migrated from vendor_mappings)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `vendors` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `normalizedKey` TEXT NOT NULL,
                        `categoryId` INTEGER,
                        `editedFields` TEXT NOT NULL,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE CASCADE ON DELETE SET_NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vendors_normalizedKey` ON `vendors` (`normalizedKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vendors_categoryId` ON `vendors` (`categoryId`)")

                db.execSQL("""
                    INSERT INTO `vendors` (`id`, `displayName`, `normalizedKey`, `categoryId`, `editedFields`)
                    SELECT `id`, `friendlyName`, LOWER(TRIM(`rawSmsKeyword`)), `categoryId`, '[]'
                    FROM `vendor_mappings`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `vendor_mappings`")

                // 7. Rules
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conditionsJson` TEXT NOT NULL,
                        `actionsJson` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `source` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rules_priority` ON `rules` (`priority`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rules_enabled` ON `rules` (`enabled`)")

                // 8. Review Items
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `review_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `origin` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_items_status` ON `review_items` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_items_createdAt` ON `review_items` (`createdAt`)")

                // 9. Unparsed Messages
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `unparsed_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `source` TEXT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `receivedAt` INTEGER NOT NULL,
                        `body` TEXT NOT NULL,
                        `bodyHash` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_unparsed_messages_bodyHash` ON `unparsed_messages` (`bodyHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_unparsed_messages_receivedAt` ON `unparsed_messages` (`receivedAt`)")

                // 10. Processed Messages
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `processed_messages` (
                        `bodyHash` TEXT PRIMARY KEY NOT NULL,
                        `source` TEXT NOT NULL,
                        `sourceId` TEXT,
                        `outcome` TEXT NOT NULL,
                        `processedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_processed_messages_processedAt` ON `processed_messages` (`processedAt`)")

                // 11. AI Cache
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_cache` (
                        `inputHash` TEXT PRIMARY KEY NOT NULL,
                        `task` TEXT NOT NULL,
                        `resultJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_cache_task` ON `ai_cache` (`task`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_cache_createdAt` ON `ai_cache` (`createdAt`)")

                // 12. Scan History
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `scan_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `source` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `finishedAt` INTEGER,
                        `read` INTEGER NOT NULL,
                        `added` INTEGER NOT NULL,
                        `duplicatesSkipped` INTEGER NOT NULL,
                        `sentToReview` INTEGER NOT NULL,
                        `resolvedByAi` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_history_startedAt` ON `scan_history` (`startedAt`)")

                // 13. Transactions (amountPaise, myActualSharePaise, accountId, reference, source, confidence, rawMessageHash, editedFields)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amountPaise` INTEGER NOT NULL,
                        `myActualSharePaise` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `paymentMode` TEXT NOT NULL,
                        `rawMerchant` TEXT NOT NULL,
                        `friendlyVendorName` TEXT NOT NULL,
                        `categoryId` INTEGER,
                        `timestamp` INTEGER NOT NULL,
                        `upiRef` TEXT,
                        `rawSms` TEXT,
                        `description` TEXT,
                        `accountId` INTEGER,
                        `reference` TEXT,
                        `source` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `rawMessageHash` TEXT,
                        `editedFields` TEXT NOT NULL,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE CASCADE ON DELETE SET_NULL,
                        FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE CASCADE ON DELETE SET_NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `transactions_new` (
                        `id`, `amountPaise`, `myActualSharePaise`, `type`, `paymentMode`,
                        `rawMerchant`, `friendlyVendorName`, `categoryId`, `timestamp`,
                        `upiRef`, `rawSms`, `description`, `accountId`, `reference`,
                        `source`, `confidence`, `rawMessageHash`, `editedFields`
                    )
                    SELECT 
                        t.`id`,
                        CAST(ROUND(t.`amount` * 100.0) AS INTEGER),
                        CAST(ROUND(t.`myActualShare` * 100.0) AS INTEGER),
                        t.`type`,
                        t.`paymentMode`,
                        t.`rawMerchant`,
                        t.`friendlyVendorName`,
                        t.`categoryId`,
                        t.`timestamp`,
                        t.`upiRef`,
                        t.`rawSms`,
                        t.`description`,
                        (
                            SELECT a.`id` FROM `accounts` a
                            WHERE (a.`last4` != '' AND t.`paymentMode` LIKE '%' || a.`last4` || '%')
                               OR LOWER(t.`paymentMode`) LIKE '%' || LOWER(a.`displayName`) || '%'
                            LIMIT 1
                        ),
                        NULL,
                        CASE WHEN t.`rawSms` IS NOT NULL AND t.`rawSms` != '' THEN 'SMS' ELSE 'MANUAL' END,
                        1.0,
                        NULL,
                        '[]'
                    FROM `transactions` t
                """.trimIndent())

                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_upiRef` ON `transactions` (`upiRef`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_reference` ON `transactions` (`reference`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")

                // 14. Split Items (amountOwedPaise, amountPaidSoFarPaise)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `split_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `personId` INTEGER NOT NULL,
                        `amountOwedPaise` INTEGER NOT NULL,
                        `amountPaidSoFarPaise` INTEGER NOT NULL,
                        `isSettled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
                        FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `split_items_new` (`id`, `transactionId`, `personId`, `amountOwedPaise`, `amountPaidSoFarPaise`, `isSettled`, `createdAt`)
                    SELECT 
                        `id`, 
                        `transactionId`, 
                        `personId`, 
                        CAST(ROUND(`amountOwed` * 100.0) AS INTEGER), 
                        CAST(ROUND(`amountPaidSoFar` * 100.0) AS INTEGER), 
                        `isSettled`, 
                        `createdAt`
                    FROM `split_items`
                """.trimIndent())

                db.execSQL("DROP TABLE `split_items`")
                db.execSQL("ALTER TABLE `split_items_new` RENAME TO `split_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_items_transactionId` ON `split_items` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_items_personId` ON `split_items` (`personId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_items_isSettled` ON `split_items` (`isSettled`)")

                // 15. Pending Settlement Suggestions (amountPaise)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pending_settlement_suggestions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER,
                        `suggestedPersonId` INTEGER NOT NULL,
                        `detectedSenderName` TEXT NOT NULL,
                        `amountPaise` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
                        FOREIGN KEY(`suggestedPersonId`) REFERENCES `persons`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `pending_settlement_suggestions_new` (`id`, `transactionId`, `suggestedPersonId`, `detectedSenderName`, `amountPaise`, `status`)
                    SELECT 
                        `id`, 
                        `transactionId`, 
                        `suggestedPersonId`, 
                        `detectedSenderName`, 
                        CAST(ROUND(`amount` * 100.0) AS INTEGER), 
                        `status`
                    FROM `pending_settlement_suggestions`
                """.trimIndent())

                db.execSQL("DROP TABLE `pending_settlement_suggestions`")
                db.execSQL("ALTER TABLE `pending_settlement_suggestions_new` RENAME TO `pending_settlement_suggestions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_settlement_suggestions_transactionId` ON `pending_settlement_suggestions` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_settlement_suggestions_suggestedPersonId` ON `pending_settlement_suggestions` (`suggestedPersonId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_settlement_suggestions_status` ON `pending_settlement_suggestions` (`status`)")

                // Re-enable foreign keys
                db.execSQL("PRAGMA foreign_keys=ON;")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "campus_fold_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
                // Fresh install starts completely empty - no pre-seeded personal or test data
            }
        }
    }
}
