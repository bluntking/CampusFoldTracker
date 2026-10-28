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
    version = 6,
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Accounts: openingBalancePaise, openingOwedPaise, openingAsOf, anchorSet
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `openingBalancePaise` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `openingOwedPaise` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `openingAsOf` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `anchorSet` INTEGER NOT NULL DEFAULT 0")

                // Convert existing MANUAL balances to anchors
                db.execSQL("""
                    UPDATE `accounts`
                    SET `openingBalancePaise` = CASE WHEN `type` != 'CREDIT_CARD' THEN `balancePaise` ELSE 0 END,
                        `openingOwedPaise` = CASE WHEN `type` = 'CREDIT_CARD' THEN `balancePaise` ELSE 0 END,
                        `openingAsOf` = `balanceAsOf`,
                        `anchorSet` = CASE WHEN `balanceSource` = 'MANUAL' THEN 1 ELSE 0 END
                    WHERE `balanceSource` = 'MANUAL'
                """.trimIndent())

                // 2. Split Items: direction
                db.execSQL("ALTER TABLE `split_items` ADD COLUMN `direction` TEXT NOT NULL DEFAULT 'OWED_TO_ME'")

                // 3. Transactions: paidByPersonId
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `paidByPersonId` INTEGER DEFAULT NULL REFERENCES `persons`(`id`) ON UPDATE CASCADE ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_paidByPersonId` ON `transactions` (`paidByPersonId`)")

                // 4. Pending Settlement Suggestions: direction
                db.execSQL("ALTER TABLE `pending_settlement_suggestions` ADD COLUMN `direction` TEXT NOT NULL DEFAULT 'OWED_TO_ME'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add 4 new columns to transactions
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `linkedExpenseId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `linkedIncomeId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `reimbursedOffsetPaise` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `previousSharePaise` INTEGER DEFAULT NULL")

                // 2. Migrate existing linked rows from description tags
                val cursor = db.query("SELECT id, description, rawMerchant, friendlyVendorName, type FROM transactions WHERE description LIKE '%[LINKED_%' OR (type = 'SPLIT_SETTLEMENT' AND friendlyVendorName LIKE 'Reimbursement for %')")
                val linkedToExpenseRegex = Regex("""\[LINKED_TO_EXPENSE:(\d+)\|OFFSET:(\d+)\|PREV_SHARE:(\d+)\]""")
                val linkedIncomeRegex = Regex("""\[LINKED_INCOME:(\d+)\|OFFSET:(\d+)\]""")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val desc = cursor.getString(1) ?: ""
                    val rawMerchant = cursor.getString(2) ?: ""
                    val friendlyVendor = cursor.getString(3) ?: ""
                    val txType = cursor.getString(4) ?: ""

                    var linkedExpenseId: Long? = null
                    var linkedIncomeId: Long? = null
                    var offsetPaise: Long? = null
                    var prevSharePaise: Long? = null
                    var newDesc = desc

                    val toExpMatch = linkedToExpenseRegex.find(desc)
                    if (toExpMatch != null) {
                        linkedExpenseId = toExpMatch.groupValues[1].toLongOrNull()
                        offsetPaise = toExpMatch.groupValues[2].toLongOrNull()
                        prevSharePaise = toExpMatch.groupValues[3].toLongOrNull()
                        newDesc = newDesc.replace(toExpMatch.value, "").trim()
                    }

                    val incMatch = linkedIncomeRegex.find(desc)
                    if (incMatch != null) {
                        linkedIncomeId = incMatch.groupValues[1].toLongOrNull()
                        if (offsetPaise == null) {
                            offsetPaise = incMatch.groupValues[2].toLongOrNull()
                        }
                        newDesc = newDesc.replace(incMatch.value, "").trim()
                    }

                    val newFriendlyVendor = if (friendlyVendor.startsWith("Reimbursement for ") && rawMerchant.isNotBlank()) {
                        rawMerchant
                    } else {
                        friendlyVendor
                    }
                    val newType = if (txType == "SPLIT_SETTLEMENT" && (toExpMatch != null || friendlyVendor.startsWith("Reimbursement for "))) {
                        "INCOME"
                    } else {
                        txType
                    }

                    db.execSQL(
                        """
                        UPDATE transactions 
                        SET linkedExpenseId = ?,
                            linkedIncomeId = ?,
                            reimbursedOffsetPaise = ?,
                            previousSharePaise = ?,
                            description = ?,
                            friendlyVendorName = ?,
                            type = ?
                        WHERE id = ?
                        """.trimIndent(),
                        arrayOf<Any?>(
                            linkedExpenseId,
                            linkedIncomeId,
                            offsetPaise,
                            prevSharePaise,
                            if (newDesc.isBlank()) null else newDesc,
                            newFriendlyVendor,
                            newType,
                            id
                        )
                    )
                }
                cursor.close()

                // 3. Clean up any wrongly saved future-debit mandate notices
                db.execSQL("""
                    DELETE FROM transactions 
                    WHERE (rawSms LIKE '%will be debited%' OR rawSms LIKE '%upcoming mandate%')
                      AND rawSms NOT LIKE '%was debited%'
                      AND rawSms NOT LIKE '%has been debited%'
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "campus_fold_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
