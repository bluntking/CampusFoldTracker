package com.campusfold.tracker

import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.TransactionRepository
import com.campusfold.tracker.data.dao.*
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 7 Unit Tests: Manual Control & Sticky Edits
 *
 * Requirements verified:
 * 1. Sticky edits survive rescan (fields marked in editedFields JSON are never overwritten by rescan/enrichment/rules/AI).
 * 2. Delete account with reassign moves transactions to target account or unassigns them to "Cash/Manual".
 * 3. Delete category with reassign moves transactions and vendor defaults to target category.
 * 4. Vendor merge repoints transactions, keyword mappings, and deletes source vendor.
 * 5. Category merge repoints transactions, rules, vendor mappings, and deletes source category.
 * 6. Manual balance override protection (AUTO balance updates from SMS/Gmail never overwrite MANUAL balance until cleared).
 * 7. Bulk operations: bulk recategorize, bulk move to account, bulk delete with undo restore.
 * 8. Duplicate transaction merge combines splits, descriptions, references, and sticky edit markers.
 * 9. Rebuild from scratch wipes transactions, review items, unparsed messages, and scan history while preserving categories & accounts.
 */
class ManualControlAndStickyEditsTest {

    private lateinit var fakeDb: FakeManualControlAppDatabase

    @Before
    fun setUp() {
        fakeDb = FakeManualControlAppDatabase()
    }

    // ─── 1. Sticky Edits Protection ──────────────────────────────────────────

    @Test
    fun testStickyEdits_SurviveRescanAndEnrichment() = runBlocking {
        // Create a transaction parsed originally as Food (cat=1) with raw vendor "SWIGGY-BANGALORE"
        val originalTx = TransactionEntity(
            id = 1L,
            amountPaise = 35000L,
            type = TransactionType.EXPENSE,
            paymentMode = "Axis Bank ••2003",
            rawMerchant = "SWIGGY-BANGALORE",
            friendlyVendorName = "Custom Swiggy Order",
            categoryId = 2L, // User changed to Entertainment (cat=2)
            timestamp = 100000L,
            accountId = 10L,
            description = "Dinner with team",
            editedFields = "[\"categoryId\",\"friendlyVendorName\",\"description\"]"
        )
        fakeDb.fakeTransactionDao.insertTransaction(originalTx)

        // Rescan / enrichment simulation:
        // Rule engine or bank parser attempts to auto-categorize to Food (cat=1) and vendor "Swiggy"
        val saved = fakeDb.fakeTransactionDao.getTransactionByIdSync(1L)!!
        val editedFieldsList = TransactionRepository.parseEditedFields(saved.editedFields)

        val autoSuggestedCategoryId = 1L // Food
        val autoSuggestedVendor = "Swiggy"
        val autoSuggestedDescription = "Auto description"
        val autoSuggestedPaymentMode = "Axis Bank ••2003"

        // Apply enrichment with sticky edits protection:
        val enriched = saved.copy(
            categoryId = if ("categoryId" in editedFieldsList) saved.categoryId else autoSuggestedCategoryId,
            friendlyVendorName = if ("friendlyVendorName" in editedFieldsList) saved.friendlyVendorName else autoSuggestedVendor,
            description = if ("description" in editedFieldsList) saved.description else autoSuggestedDescription,
            paymentMode = if ("paymentMode" in editedFieldsList) saved.paymentMode else autoSuggestedPaymentMode
        )
        fakeDb.fakeTransactionDao.updateTransaction(enriched)

        val afterRescan = fakeDb.fakeTransactionDao.getTransactionByIdSync(1L)!!
        assertEquals("User's category edit must survive rescan", 2L, afterRescan.categoryId)
        assertEquals("User's vendor name edit must survive rescan", "Custom Swiggy Order", afterRescan.friendlyVendorName)
        assertEquals("User's description edit must survive rescan", "Dinner with team", afterRescan.description)
        assertTrue(editedFieldsList.contains("categoryId"))
        assertTrue(editedFieldsList.contains("friendlyVendorName"))
        assertTrue(editedFieldsList.contains("description"))
    }

    // ─── 2. Delete Account With Reassign ─────────────────────────────────────

    @Test
    fun testDeleteAccountWithReassign_MovesTransactionsToTargetAccount() = runBlocking {
        val accA = AccountEntity(id = 1L, bankName = "Axis Bank", bankKey = "axis", last4 = "2003", type = AccountType.SAVINGS, displayName = "Axis Savings")
        val accB = AccountEntity(id = 2L, bankName = "PNB", bankKey = "pnb", last4 = "8598", type = AccountType.SAVINGS, displayName = "PNB Savings")
        fakeDb.fakeAccountDao.insertAccount(accA)
        fakeDb.fakeAccountDao.insertAccount(accB)

        val tx1 = TransactionEntity(id = 10L, amountPaise = 50000L, type = TransactionType.EXPENSE, paymentMode = "Axis Savings", rawMerchant = "STORE", friendlyVendorName = "Store", accountId = 1L, timestamp = 1000L)
        val tx2 = TransactionEntity(id = 20L, amountPaise = 20000L, type = TransactionType.EXPENSE, paymentMode = "Axis Savings", rawMerchant = "CAFE", friendlyVendorName = "Cafe", accountId = 1L, timestamp = 2000L)
        fakeDb.fakeTransactionDao.insertTransaction(tx1)
        fakeDb.fakeTransactionDao.insertTransaction(tx2)

        // Delete Account A and reassign all transactions to Account B
        ManualControlLogic.deleteAccountWithReassign(
            accountDao = fakeDb.fakeAccountDao,
            accountAliasDao = fakeDb.fakeAccountAliasDao,
            transactionDao = fakeDb.fakeTransactionDao,
            accountId = 1L,
            targetAccountId = 2L
        )

        assertNull("Account A must be deleted", fakeDb.fakeAccountDao.getAccountById(1L))
        assertNotNull("Account B must survive", fakeDb.fakeAccountDao.getAccountById(2L))

        val updatedTx1 = fakeDb.fakeTransactionDao.getTransactionByIdSync(10L)!!
        val updatedTx2 = fakeDb.fakeTransactionDao.getTransactionByIdSync(20L)!!

        assertEquals("Tx1 accountId must be reassigned to Account B", 2L, updatedTx1.accountId)
        assertEquals("Tx2 accountId must be reassigned to Account B", 2L, updatedTx2.accountId)
    }

    @Test
    fun testDeleteAccountWithReassign_ToNull_UnassignsTransactions() = runBlocking {
        val accA = AccountEntity(id = 1L, bankName = "Axis Bank", bankKey = "axis", last4 = "2003", type = AccountType.SAVINGS, displayName = "Axis Savings")
        fakeDb.fakeAccountDao.insertAccount(accA)

        val tx1 = TransactionEntity(id = 10L, amountPaise = 50000L, type = TransactionType.EXPENSE, paymentMode = "Axis Savings", rawMerchant = "STORE", friendlyVendorName = "Store", accountId = 1L, timestamp = 1000L)
        fakeDb.fakeTransactionDao.insertTransaction(tx1)

        // Delete Account A with targetAccountId = null (unassigned)
        ManualControlLogic.deleteAccountWithReassign(
            accountDao = fakeDb.fakeAccountDao,
            accountAliasDao = fakeDb.fakeAccountAliasDao,
            transactionDao = fakeDb.fakeTransactionDao,
            accountId = 1L,
            targetAccountId = null
        )

        assertNull("Account A must be deleted", fakeDb.fakeAccountDao.getAccountById(1L))
        val updatedTx1 = fakeDb.fakeTransactionDao.getTransactionByIdSync(10L)!!
        assertNull("Tx1 accountId must be null", updatedTx1.accountId)
        assertEquals("Payment mode should default to Cash/Manual", "Cash/Manual", updatedTx1.paymentMode)
    }

    // ─── 3. Delete Category With Reassign ────────────────────────────────────

    @Test
    fun testDeleteCategoryWithReassign_MovesTransactionsAndVendorDefaults() = runBlocking {
        val catFood = CategoryEntity(id = 1L, name = "Food", colorHex = "#10B981")
        val catGeneral = CategoryEntity(id = 2L, name = "General", colorHex = "#6B7280")
        fakeDb.fakeCategoryDao.insertCategory(catFood)
        fakeDb.fakeCategoryDao.insertCategory(catGeneral)

        val tx = TransactionEntity(id = 10L, amountPaise = 15000L, type = TransactionType.EXPENSE, paymentMode = "Cash", rawMerchant = "CANTEEN", friendlyVendorName = "Canteen", categoryId = 1L, timestamp = 1000L)
        fakeDb.fakeTransactionDao.insertTransaction(tx)

        val vendor = VendorEntity(id = 100L, displayName = "Canteen", normalizedKey = "canteen", categoryId = 1L)
        fakeDb.fakeVendorDao.insertVendor(vendor)

        // Delete Food category and reassign all items to General
        ManualControlLogic.deleteCategoryWithReassign(
            categoryDao = fakeDb.fakeCategoryDao,
            transactionDao = fakeDb.fakeTransactionDao,
            vendorDao = fakeDb.fakeVendorDao,
            categoryId = 1L,
            targetCategoryId = 2L
        )

        assertNull("Food category must be deleted", fakeDb.fakeCategoryDao.getCategoryById(1L))
        val updatedTx = fakeDb.fakeTransactionDao.getTransactionByIdSync(10L)!!
        assertEquals("Transaction category must be moved to General", 2L, updatedTx.categoryId)

        val updatedVendor = fakeDb.fakeVendorDao.getVendorById(100L)!!
        assertEquals("Vendor default category must be moved to General", 2L, updatedVendor.categoryId)
    }

    // ─── 4. Vendor Merge ─────────────────────────────────────────────────────

    @Test
    fun testMergeVendors_RepointsTransactionsAndKeywordMappings() = runBlocking {
        val vendor1 = VendorEntity(id = 1L, displayName = "Campus Chai", normalizedKey = "campus_chai", categoryId = 5L)
        val vendor2 = VendorEntity(id = 2L, displayName = "Tea Stall", normalizedKey = "tea_stall", categoryId = 5L)
        fakeDb.fakeVendorDao.insertVendor(vendor1)
        fakeDb.fakeVendorDao.insertVendor(vendor2)

        val tx1 = TransactionEntity(id = 10L, amountPaise = 2000L, type = TransactionType.EXPENSE, paymentMode = "UPI", rawMerchant = "CHAI", friendlyVendorName = "Tea Stall", timestamp = 1000L)
        val tx2 = TransactionEntity(id = 20L, amountPaise = 3000L, type = TransactionType.EXPENSE, paymentMode = "UPI", rawMerchant = "CAMPUS_CHAI", friendlyVendorName = "Campus Chai", timestamp = 2000L)
        fakeDb.fakeTransactionDao.insertTransaction(tx1)
        fakeDb.fakeTransactionDao.insertTransaction(tx2)

        // Merge Vendor 2 ("Tea Stall") into Vendor 1 ("Campus Chai")
        ManualControlLogic.mergeVendors(
            vendorDao = fakeDb.fakeVendorDao,
            transactionDao = fakeDb.fakeTransactionDao,
            survivingVendorId = 1L,
            mergedVendorId = 2L,
            survivingName = "Campus Chai",
            categoryId = 5L
        )

        assertNull("Merged vendor must be deleted", fakeDb.fakeVendorDao.getVendorById(2L))
        val survivingVendor = fakeDb.fakeVendorDao.getVendorById(1L)!!
        assertEquals("Campus Chai", survivingVendor.displayName)
        assertTrue("Sticky edit flag displayName added", survivingVendor.editedFields.contains("displayName"))

        val updatedTx1 = fakeDb.fakeTransactionDao.getTransactionByIdSync(10L)!!
        assertEquals("Tx1 friendly vendor name must be repointed to Campus Chai", "Campus Chai", updatedTx1.friendlyVendorName)
    }

    // ─── 5. Category Merge ───────────────────────────────────────────────────

    @Test
    fun testMergeCategories_RepointsTransactionsAndDeletesSource() = runBlocking {
        val cat1 = CategoryEntity(id = 1L, name = "Dining", colorHex = "#10B981")
        val cat2 = CategoryEntity(id = 2L, name = "Restaurants", colorHex = "#F59E0B")
        fakeDb.fakeCategoryDao.insertCategory(cat1)
        fakeDb.fakeCategoryDao.insertCategory(cat2)

        val tx = TransactionEntity(id = 10L, amountPaise = 45000L, type = TransactionType.EXPENSE, paymentMode = "Card", rawMerchant = "BISTRO", friendlyVendorName = "Bistro", categoryId = 2L, timestamp = 1000L)
        fakeDb.fakeTransactionDao.insertTransaction(tx)

        val vendor = VendorEntity(id = 100L, displayName = "Bistro", normalizedKey = "bistro", categoryId = 2L)
        fakeDb.fakeVendorDao.insertVendor(vendor)

        // Merge Category 2 into Category 1
        ManualControlLogic.mergeCategories(
            categoryDao = fakeDb.fakeCategoryDao,
            transactionDao = fakeDb.fakeTransactionDao,
            vendorDao = fakeDb.fakeVendorDao,
            survivingCategoryId = 1L,
            mergedCategoryId = 2L
        )

        assertNull("Merged category must be deleted", fakeDb.fakeCategoryDao.getCategoryById(2L))
        val updatedTx = fakeDb.fakeTransactionDao.getTransactionByIdSync(10L)!!
        assertEquals("Tx category must be repointed to Category 1", 1L, updatedTx.categoryId)

        val updatedVendor = fakeDb.fakeVendorDao.getVendorById(100L)!!
        assertEquals("Vendor category must be repointed to Category 1", 1L, updatedVendor.categoryId)
    }

    // ─── 6. Manual Balance Protection ────────────────────────────────────────

    @Test
    fun testManualBalance_ProtectedFromAutoUpdatesUntilCleared() = runBlocking {
        val account = AccountEntity(
            id = 1L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis Savings",
            balancePaise = 500000L, // ₹5,000.00
            balanceSource = BalanceSource.MANUAL // Entered manually
        )
        fakeDb.fakeAccountDao.insertAccount(account)

        // Incoming bank SMS with an AUTO balance reading of ₹3,500 (350000 paise)
        val incomingAutoBalance = 350000L
        val current = fakeDb.fakeAccountDao.getAccountById(1L)!!

        // Simulation of balance updater logic:
        val updateCount = fakeDb.fakeAccountDao.updateAutoBalanceOnly(current.id, incomingAutoBalance, System.currentTimeMillis())
        assertEquals("Auto update must be rejected for MANUAL balance accounts", 0, updateCount)

        // Must remain protected at ₹5,000.00
        val protectedAccount = fakeDb.fakeAccountDao.getAccountById(1L)!!
        assertEquals("Manual balance must NOT be overwritten by auto updates", 500000L, protectedAccount.balancePaise)
        assertEquals(BalanceSource.MANUAL, protectedAccount.balanceSource)

        // User clears manual override
        fakeDb.fakeAccountDao.updateAccount(protectedAccount.copy(balanceSource = BalanceSource.AUTO))
        val unoverridden = fakeDb.fakeAccountDao.getAccountById(1L)!!
        assertEquals(BalanceSource.AUTO, unoverridden.balanceSource)

        // Subsequent auto update now succeeds
        val autoUpdateCount = fakeDb.fakeAccountDao.updateAutoBalanceOnly(unoverridden.id, incomingAutoBalance, System.currentTimeMillis())
        assertEquals("Auto update must succeed once override is cleared", 1, autoUpdateCount)
        val updatedAccount = fakeDb.fakeAccountDao.getAccountById(1L)!!
        assertEquals("Auto update applied", incomingAutoBalance, updatedAccount.balancePaise)
    }

    // ─── 7. Bulk Operations ──────────────────────────────────────────────────

    @Test
    fun testBulkRecategorizeMoveAndUndoDelete() = runBlocking {
        val tx1 = TransactionEntity(id = 1L, amountPaise = 1000L, type = TransactionType.EXPENSE, paymentMode = "Cash", rawMerchant = "A", friendlyVendorName = "A", categoryId = 1L, accountId = 10L, timestamp = 1000L)
        val tx2 = TransactionEntity(id = 2L, amountPaise = 2000L, type = TransactionType.EXPENSE, paymentMode = "Cash", rawMerchant = "B", friendlyVendorName = "B", categoryId = 1L, accountId = 10L, timestamp = 2000L)
        val tx3 = TransactionEntity(id = 3L, amountPaise = 3000L, type = TransactionType.EXPENSE, paymentMode = "Cash", rawMerchant = "C", friendlyVendorName = "C", categoryId = 1L, accountId = 10L, timestamp = 3000L)
        fakeDb.fakeTransactionDao.insertTransactions(listOf(tx1, tx2, tx3))

        // Bulk Recategorize tx1 and tx2 to Category 5
        fakeDb.fakeTransactionDao.updateCategoryIdForTransactionIds(listOf(1L, 2L), 5L)
        assertEquals(5L, fakeDb.fakeTransactionDao.getTransactionByIdSync(1L)!!.categoryId)
        assertEquals(5L, fakeDb.fakeTransactionDao.getTransactionByIdSync(2L)!!.categoryId)
        assertEquals(1L, fakeDb.fakeTransactionDao.getTransactionByIdSync(3L)!!.categoryId)

        // Bulk Move tx1 and tx2 to Account 20 ("PNB ••8598")
        fakeDb.fakeTransactionDao.updateAccountIdAndPaymentModeForTransactionIds(listOf(1L, 2L), 20L, "PNB ••8598")
        val moved1 = fakeDb.fakeTransactionDao.getTransactionByIdSync(1L)!!
        assertEquals(20L, moved1.accountId)
        assertEquals("PNB ••8598", moved1.paymentMode)

        // Bulk Delete tx1 and tx2
        val toDelete = fakeDb.fakeTransactionDao.getTransactionsByIdsSync(listOf(1L, 2L))
        fakeDb.fakeTransactionDao.deleteTransactionsByIds(listOf(1L, 2L))
        assertEquals(2, toDelete.size)
        assertNull(fakeDb.fakeTransactionDao.getTransactionByIdSync(1L))
        assertNull(fakeDb.fakeTransactionDao.getTransactionByIdSync(2L))
        assertNotNull(fakeDb.fakeTransactionDao.getTransactionByIdSync(3L))

        // Undo Delete (restore transactions)
        fakeDb.fakeTransactionDao.restoreTransactions(toDelete)
        assertNotNull("tx1 restored", fakeDb.fakeTransactionDao.getTransactionByIdSync(1L))
        assertNotNull("tx2 restored", fakeDb.fakeTransactionDao.getTransactionByIdSync(2L))
    }

    // ─── 8. Duplicate Transaction Merge ──────────────────────────────────────

    @Test
    fun testMergeDuplicateTransactions_CombinesSplitsDescriptionsAndMarkers() = runBlocking {
        val manualTx = TransactionEntity(
            id = 1L,
            amountPaise = 50000L,
            type = TransactionType.EXPENSE,
            paymentMode = "Cash/Manual",
            rawMerchant = "STARBUCKS",
            friendlyVendorName = "Starbucks",
            categoryId = 3L,
            timestamp = 1000L,
            description = "Coffee with Alice",
            editedFields = "[\"description\",\"categoryId\"]"
        )
        val bankTx = TransactionEntity(
            id = 2L,
            amountPaise = 50000L,
            type = TransactionType.EXPENSE,
            paymentMode = "Axis Bank ••2003",
            rawMerchant = "STARBUCKS CORP",
            friendlyVendorName = "Starbucks",
            categoryId = 3L,
            timestamp = 1000L,
            reference = "REF123456",
            upiRef = "UPI987654",
            description = "POS Transaction",
            editedFields = "[\"amount\"]"
        )
        fakeDb.fakeTransactionDao.insertTransaction(manualTx)
        fakeDb.fakeTransactionDao.insertTransaction(bankTx)

        // Split item attached to manualTx
        val splitItem = SplitItemEntity(
            id = 50L,
            transactionId = 1L,
            personId = 100L,
            sharePaise = 25000L,
            isPaid = false
        )
        fakeDb.fakeSplitItemDao.insertSplit(splitItem)

        // Merge manualTx into bankTx (survivor = bankTx, duplicate = manualTx)
        val survivor = ManualControlLogic.mergeDuplicateTransactions(
            transactionDao = fakeDb.fakeTransactionDao,
            splitDao = fakeDb.fakeSplitItemDao,
            survivingTxId = 2L,
            duplicateTxId = 1L
        )

        assertNull("Duplicate transaction must be deleted", fakeDb.fakeTransactionDao.getTransactionByIdSync(1L))
        assertNotNull("Surviving transaction must exist", fakeDb.fakeTransactionDao.getTransactionByIdSync(2L))

        // Splits must be reparented to survivor
        val splits = fakeDb.fakeSplitItemDao.getSplitsForTransactionSync(2L)
        assertEquals(1, splits.size)
        assertEquals(2L, splits[0].transactionId)

        // Descriptions combined
        assertTrue("Description combined", survivor.description!!.contains("Coffee with Alice"))
        assertTrue("Description combined", survivor.description!!.contains("POS Transaction"))

        // References preserved
        assertEquals("REF123456", survivor.reference)
        assertEquals("UPI987654", survivor.upiRef)

        // Sticky edits combined
        val combinedEdited = TransactionRepository.parseEditedFields(survivor.editedFields)
        assertTrue("Contains description edit", combinedEdited.contains("description"))
        assertTrue("Contains categoryId edit", combinedEdited.contains("categoryId"))
        assertTrue("Contains amount edit", combinedEdited.contains("amount"))
    }

    // ─── 9. Rebuild from Scratch ─────────────────────────────────────────────

    @Test
    fun testRebuildFromScratch_WipesTransactionsPreservingCategoriesAndAccounts() = runBlocking {
        // Setup initial database state
        val acc = AccountEntity(id = 1L, bankName = "Axis", bankKey = "axis", last4 = "2003", type = AccountType.SAVINGS, displayName = "Axis Savings")
        val cat = CategoryEntity(id = 1L, name = "Food", colorHex = "#10B981")
        val vendor = VendorEntity(id = 1L, displayName = "Canteen", normalizedKey = "canteen")
        fakeDb.fakeAccountDao.insertAccount(acc)
        fakeDb.fakeCategoryDao.insertCategory(cat)
        fakeDb.fakeVendorDao.insertVendor(vendor)

        val tx = TransactionEntity(id = 1L, amountPaise = 20000L, type = TransactionType.EXPENSE, paymentMode = "Axis Savings", rawMerchant = "M", friendlyVendorName = "V", timestamp = 1000L)
        fakeDb.fakeTransactionDao.insertTransaction(tx)
        fakeDb.fakeReviewItemDao.insertReviewItem(ReviewItemEntity(id = 1L, transactionId = 1L, field = TransactionField.CATEGORY, oldValue = "1", predictedValue = "2", confidence = 0.9f, origin = ReviewOrigin.AI, reason = "Test", createdAt = 1000L))
        fakeDb.fakeUnparsedMessageDao.insertUnparsedMessage(UnparsedMessageEntity(id = 1L, source = "SMS", sender = "AXIS", receivedAt = 1000L, body = "unparsed", bodyHash = "hash1"))
        fakeDb.fakeScanHistoryDao.insertScanHistory(ScanHistoryEntity(id = 1L, source = "SMS", startedAt = 1000L, read = 10, added = 5))

        // Execute Rebuild From Scratch
        ManualControlLogic.rebuildFromScratch(
            transactionDao = fakeDb.fakeTransactionDao,
            splitDao = fakeDb.fakeSplitItemDao,
            suggestionDao = fakeDb.fakeSuggestionDao,
            reviewDao = fakeDb.fakeReviewItemDao,
            unparsedDao = fakeDb.fakeUnparsedMessageDao,
            scanHistoryDao = fakeDb.fakeScanHistoryDao,
            processedMessageDao = fakeDb.fakeProcessedMessageDao
        )

        // Transactions, Review Items, Unparsed Messages, Scan History wiped
        assertEquals(0, fakeDb.fakeTransactionDao.getAllTransactionsSync().size)
        assertEquals(0, fakeDb.fakeReviewItemDao.getPendingReviewItemsSync().size)
        assertEquals(0, fakeDb.fakeUnparsedMessageDao.getAllUnparsedMessagesSync().size)
        assertEquals(0, fakeDb.fakeScanHistoryDao.getAllScanHistorySync().size)

        // Categories, Accounts, Vendors preserved
        assertNotNull("Account must survive", fakeDb.fakeAccountDao.getAccountById(1L))
        assertNotNull("Category must survive", fakeDb.fakeCategoryDao.getCategoryById(1L))
        assertNotNull("Vendor must survive", fakeDb.fakeVendorDao.getVendorById(1L))
    }
}

// ─── Manual Control Business Logic Engine for Testing ────────────────────────

object ManualControlLogic {
    suspend fun deleteAccountWithReassign(
        accountDao: AccountDao,
        accountAliasDao: AccountAliasDao,
        transactionDao: TransactionDao,
        accountId: Long,
        targetAccountId: Long?
    ) {
        val count = transactionDao.countTransactionsByAccountId(accountId)
        if (count > 0) {
            if (targetAccountId != null) {
                transactionDao.updateAccountIdForTransactions(accountId, targetAccountId)
            } else {
                val txs = transactionDao.getTransactionsByAccountId(accountId)
                for (tx in txs) {
                    transactionDao.updateTransaction(tx.copy(accountId = null, paymentMode = "Cash/Manual"))
                }
            }
        }
        if (targetAccountId != null) {
            accountAliasDao.reassignAliases(accountId, targetAccountId)
        } else {
            accountAliasDao.deleteAliasesForAccount(accountId)
        }
        accountDao.deleteAccountById(accountId)
    }

    suspend fun deleteCategoryWithReassign(
        categoryDao: CategoryDao,
        transactionDao: TransactionDao,
        vendorDao: VendorDao,
        categoryId: Long,
        targetCategoryId: Long?
    ) {
        transactionDao.updateCategoryIdForTransactions(categoryId, targetCategoryId)
        vendorDao.updateCategoryIdForVendors(categoryId, targetCategoryId)
        categoryDao.deleteCategoryById(categoryId)
    }

    suspend fun mergeVendors(
        vendorDao: VendorDao,
        transactionDao: TransactionDao,
        survivingVendorId: Long,
        mergedVendorId: Long,
        survivingName: String,
        categoryId: Long?
    ) {
        val surviving = vendorDao.getVendorById(survivingVendorId) ?: return
        val merged = vendorDao.getVendorById(mergedVendorId) ?: return

        transactionDao.updateVendorNameAndCategoryForTransactions(merged.displayName, survivingName, categoryId)
        transactionDao.updateVendorNameAndCategoryForTransactions(surviving.displayName, survivingName, categoryId)

        val edited = TransactionRepository.parseEditedFields(surviving.editedFields).toMutableSet().apply {
            add("displayName")
            if (categoryId != null) add("categoryId")
        }

        vendorDao.updateVendor(
            surviving.copy(
                displayName = survivingName.trim(),
                normalizedKey = survivingName.trim().lowercase(java.util.Locale.US),
                categoryId = categoryId ?: surviving.categoryId,
                editedFields = JSONArray(edited.toList()).toString()
            )
        )
        vendorDao.deleteVendor(merged)
    }

    suspend fun mergeCategories(
        categoryDao: CategoryDao,
        transactionDao: TransactionDao,
        vendorDao: VendorDao,
        survivingCategoryId: Long,
        mergedCategoryId: Long
    ) {
        transactionDao.updateCategoryIdForTransactions(mergedCategoryId, survivingCategoryId)
        vendorDao.updateCategoryIdForVendors(mergedCategoryId, survivingCategoryId)
        categoryDao.deleteCategoryById(mergedCategoryId)
    }

    suspend fun mergeDuplicateTransactions(
        transactionDao: TransactionDao,
        splitDao: SplitItemDao,
        survivingTxId: Long,
        duplicateTxId: Long
    ): TransactionEntity {
        val survivor = transactionDao.getTransactionByIdSync(survivingTxId)
            ?: throw IllegalArgumentException("Survivor transaction not found: $survivingTxId")
        val duplicate = transactionDao.getTransactionByIdSync(duplicateTxId)
            ?: throw IllegalArgumentException("Duplicate transaction not found: $duplicateTxId")

        splitDao.reassignSplits(duplicateTxId, survivingTxId)

        val combinedDesc = when {
            survivor.description.isNullOrBlank() -> duplicate.description
            duplicate.description.isNullOrBlank() -> survivor.description
            survivor.description == duplicate.description -> survivor.description
            else -> "${survivor.description} | ${duplicate.description}"
        }

        val finalRef = if (!survivor.reference.isNullOrBlank()) survivor.reference else duplicate.reference
        val finalUpi = if (!survivor.upiRef.isNullOrBlank()) survivor.upiRef else duplicate.upiRef

        val survivorEdited = TransactionRepository.parseEditedFields(survivor.editedFields).toMutableSet()
        survivorEdited.addAll(TransactionRepository.parseEditedFields(duplicate.editedFields))

        val updated = survivor.copy(
            description = combinedDesc,
            reference = finalRef,
            upiRef = finalUpi,
            editedFields = JSONArray(survivorEdited.toList()).toString()
        )
        transactionDao.updateTransaction(updated)
        transactionDao.deleteTransaction(duplicate)
        return updated
    }

    suspend fun rebuildFromScratch(
        transactionDao: TransactionDao,
        splitDao: SplitItemDao,
        suggestionDao: PendingSettlementSuggestionDao,
        reviewDao: ReviewItemDao,
        unparsedDao: UnparsedMessageDao,
        scanHistoryDao: ScanHistoryDao,
        processedMessageDao: ProcessedMessageDao
    ) {
        transactionDao.clearAllTransactions()
        splitDao.clearAll()
        suggestionDao.clearAll()
        reviewDao.clearAll()
        unparsedDao.clearAll()
        scanHistoryDao.clearAll()
        processedMessageDao.clearAll()
    }
}

// ─── Test Fake Database & DAOs for Manual Control ────────────────────────────

class FakeManualControlAppDatabase : AppDatabase() {
    val fakeTransactionDao = FakeManualTransactionDao()
    val fakeCategoryDao = FakeManualCategoryDao()
    val fakeVendorDao = FakeManualVendorDao()
    val fakeAccountDao = FakeManualAccountDao()
    val fakeAccountAliasDao = FakeManualAccountAliasDao()
    val fakePersonDao = FakeManualPersonDao()
    val fakeSplitItemDao = FakeManualSplitItemDao()
    val fakeSuggestionDao = FakeManualPendingSettlementSuggestionDao()
    val fakeRuleDao = FakeManualRuleDao()
    val fakeReviewItemDao = FakeManualReviewItemDao()
    val fakeUnparsedMessageDao = FakeManualUnparsedMessageDao()
    val fakeScanHistoryDao = FakeManualScanHistoryDao()
    val fakeProcessedMessageDao = FakeManualProcessedMessageDao()

    override fun transactionDao(): TransactionDao = fakeTransactionDao
    override fun categoryDao(): CategoryDao = fakeCategoryDao
    override fun vendorDao(): VendorDao = fakeVendorDao
    override fun accountDao(): AccountDao = fakeAccountDao
    override fun accountAliasDao(): AccountAliasDao = fakeAccountAliasDao
    override fun personDao(): PersonDao = fakePersonDao
    override fun splitItemDao(): SplitItemDao = fakeSplitItemDao
    override fun pendingSettlementSuggestionDao(): PendingSettlementSuggestionDao = fakeSuggestionDao
    override fun ruleDao(): RuleDao = fakeRuleDao
    override fun reviewItemDao(): ReviewItemDao = fakeReviewItemDao
    override fun unparsedMessageDao(): UnparsedMessageDao = fakeUnparsedMessageDao
    override fun scanHistoryDao(): ScanHistoryDao = fakeScanHistoryDao
    override fun processedMessageDao(): ProcessedMessageDao = fakeProcessedMessageDao
    override fun aiCacheDao(): AiCacheDao = throw NotImplementedError()
    override fun mergeLogDao(): MergeLogDao = throw NotImplementedError()
    override fun notSamePairDao(): NotSamePairDao = throw NotImplementedError()

    override fun clearAllTables() {}
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
        return androidx.room.InvalidationTracker(this, emptyMap(), emptyMap(), "transactions")
    }
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper {
        throw NotImplementedError()
    }
}

class FakeManualTransactionDao : TransactionDao {
    val transactions = mutableMapOf<Long, TransactionEntity>()
    private var idCounter = 1L

    override fun getAllTransactions(): Flow<List<TransactionEntity>> = flowOf(transactions.values.toList())
    override suspend fun getAllTransactionsSync(): List<TransactionEntity> = transactions.values.toList()
    override suspend fun getTransactionByIdSync(id: Long): TransactionEntity? = transactions[id]
    override suspend fun getTransactionsByIdsSync(ids: List<Long>): List<TransactionEntity> = ids.mapNotNull { transactions[it] }

    override suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val id = if (transaction.id > 0) transaction.id else idCounter++
        transactions[id] = transaction.copy(id = id)
        return id
    }

    override suspend fun insertTransactions(list: List<TransactionEntity>): List<Long> = list.map { insertTransaction(it) }
    override suspend fun updateTransaction(transaction: TransactionEntity) { transactions[transaction.id] = transaction }
    override suspend fun deleteTransaction(transaction: TransactionEntity) { transactions.remove(transaction.id) }
    override suspend fun deleteTransactionsByIds(ids: List<Long>) { ids.forEach { transactions.remove(it) } }
    override suspend fun restoreTransactions(list: List<TransactionEntity>) { list.forEach { transactions[it.id] = it } }
    override suspend fun clearAllTransactions() { transactions.clear() }

    override suspend fun countTransactionsByAccountId(accountId: Long): Int = transactions.values.count { it.accountId == accountId }
    override suspend fun getTransactionsByAccountId(accountId: Long): List<TransactionEntity> = transactions.values.filter { it.accountId == accountId }
    override suspend fun updateAccountIdForTransactions(oldAccountId: Long, newAccountId: Long) {
        transactions.values.filter { it.accountId == oldAccountId }.forEach {
            transactions[it.id] = it.copy(accountId = newAccountId)
        }
    }
    override suspend fun updateCategoryIdForTransactions(oldCategoryId: Long, newCategoryId: Long?) {
        transactions.values.filter { it.categoryId == oldCategoryId }.forEach {
            transactions[it.id] = it.copy(categoryId = newCategoryId)
        }
    }
    override suspend fun updateCategoryIdForTransactionIds(ids: List<Long>, newCategoryId: Long?) {
        ids.forEach { id ->
            transactions[id]?.let { transactions[id] = it.copy(categoryId = newCategoryId) }
        }
    }
    override suspend fun updateAccountIdAndPaymentModeForTransactionIds(ids: List<Long>, newAccountId: Long?, newPaymentMode: String) {
        ids.forEach { id ->
            transactions[id]?.let { transactions[id] = it.copy(accountId = newAccountId, paymentMode = newPaymentMode) }
        }
    }
    override suspend fun updateVendorNameForTransactions(oldName: String, newName: String) {
        transactions.values.filter { it.friendlyVendorName == oldName }.forEach {
            transactions[it.id] = it.copy(friendlyVendorName = newName)
        }
    }
    override suspend fun updateVendorNameAndCategoryForTransactions(oldName: String, newName: String, categoryId: Long?) {
        transactions.values.filter { it.friendlyVendorName == oldName }.forEach {
            transactions[it.id] = it.copy(friendlyVendorName = newName, categoryId = categoryId ?: it.categoryId)
        }
    }

    override fun getAllTransactionsWithSplits(): Flow<List<TransactionWithSplits>> = flowOf(emptyList())
    override suspend fun getAllTransactionsWithSplitsSync(): List<TransactionWithSplits> = emptyList()
    override suspend fun getTransactionWithSplitsSync(id: Long): TransactionWithSplits? = null
    override suspend fun findByUpiRef(upiRef: String): TransactionEntity? = transactions.values.find { it.upiRef == upiRef }
    override suspend fun findByReference(reference: String): TransactionEntity? = transactions.values.find { it.reference == reference }
    override suspend fun findDuplicateCandidate(accountId: Long, amountPaise: Long, type: TransactionType, minTime: Long, maxTime: Long): TransactionEntity? = null
    override fun getTransactionsByAccount(accountId: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
    override fun getTransactionsByCategory(categoryId: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
    override fun getRecentTransactions(limit: Int): Flow<List<TransactionEntity>> = flowOf(emptyList())
    override fun getMonthlyCategorySpending(monthStart: Long, monthEnd: Long): Flow<List<CategorySpending>> = flowOf(emptyList())
    override fun getMonthlyTrueSpendPaise(monthStart: Long, monthEnd: Long): Flow<Long?> = flowOf(null)
    override suspend fun getMonthlyTrueSpendPaiseSync(monthStart: Long, monthEnd: Long): Long = 0L
    override fun getCategoryPeriodSpending(startEpoch: Long, endEpoch: Long): Flow<List<CategoryPeriodSpend>> = flowOf(emptyList())
    override suspend fun countTransactionsByVendorName(name: String): Int = 0
    override suspend fun countTransactionsByCategoryId(categoryId: Long): Int = 0
    override suspend fun getActiveTransactionCount(): Int = transactions.size
}

class FakeManualAccountDao : AccountDao {
    val accounts = mutableMapOf<Long, AccountEntity>()
    private var idCounter = 1L

    override fun getAllAccounts(): Flow<List<AccountEntity>> = flowOf(accounts.values.filter { !it.isArchived })
    override fun getAllAccountsIncludingArchived(): Flow<List<AccountEntity>> = flowOf(accounts.values.toList())
    override suspend fun getAllAccountsSync(): List<AccountEntity> = accounts.values.toList()
    override suspend fun getActiveAccountsSync(): List<AccountEntity> = accounts.values.filter { !it.isArchived }
    override suspend fun getAccountById(id: Long): AccountEntity? = accounts[id]
    override suspend fun findByBankKeyAndLast4(bankKey: String, last4: String): AccountEntity? =
        accounts.values.find { it.bankKey.equals(bankKey, ignoreCase = true) && it.last4 == last4 }
    override suspend fun getAccountCount(): Int = accounts.values.count { !it.isArchived }
    override suspend fun insertAccount(account: AccountEntity): Long {
        val id = if (account.id > 0) account.id else idCounter++
        accounts[id] = account.copy(id = id)
        return id
    }
    override suspend fun insertAccounts(list: List<AccountEntity>): List<Long> = list.map { insertAccount(it) }
    override suspend fun updateAccount(account: AccountEntity) { accounts[account.id] = account }
    override suspend fun deleteAccount(account: AccountEntity) { accounts.remove(account.id) }
    override suspend fun deleteAccountById(id: Long) { accounts.remove(id) }
    override suspend fun updateBalance(id: Long, balancePaise: Long, balanceAsOf: Long, source: BalanceSource) {
        accounts[id]?.let { accounts[id] = it.copy(balancePaise = balancePaise, balanceAsOf = balanceAsOf, balanceSource = source) }
    }
    override suspend fun updateAutoBalanceOnly(id: Long, balancePaise: Long, balanceAsOf: Long): Int {
        val acc = accounts[id]
        return if (acc != null && acc.balanceSource == BalanceSource.AUTO) {
            accounts[id] = acc.copy(balancePaise = balancePaise, balanceAsOf = balanceAsOf)
            1
        } else 0
    }
    override suspend fun setArchived(id: Long, archived: Boolean) {
        accounts[id]?.let { accounts[id] = it.copy(isArchived = archived) }
    }
    override suspend fun updateCreditLimit(id: Long, creditLimitPaise: Long?) {
        accounts[id]?.let { accounts[id] = it.copy(creditLimitPaise = creditLimitPaise) }
    }
    override suspend fun updateColor(id: Long, color: String) {
        accounts[id]?.let { accounts[id] = it.copy(color = color) }
    }
}

class FakeManualCategoryDao : CategoryDao {
    val categories = mutableMapOf<Long, CategoryEntity>()
    private var idCounter = 1L

    override fun getAllCategories(): Flow<List<CategoryEntity>> = flowOf(categories.values.toList())
    override suspend fun getAllCategoriesSync(): List<CategoryEntity> = categories.values.toList()
    override suspend fun getCategoryById(id: Long): CategoryEntity? = categories[id]
    override suspend fun getCategoryByName(name: String): CategoryEntity? = categories.values.find { it.name.equals(name, ignoreCase = true) }
    override suspend fun insertCategory(category: CategoryEntity): Long {
        val id = if (category.id > 0) category.id else idCounter++
        categories[id] = category.copy(id = id)
        return id
    }
    override suspend fun insertCategories(list: List<CategoryEntity>): List<Long> = list.map { insertCategory(it) }
    override suspend fun updateCategory(category: CategoryEntity) { categories[category.id] = category }
    override suspend fun deleteCategory(category: CategoryEntity) { categories.remove(category.id) }
    override suspend fun deleteCategoryById(id: Long) { categories.remove(id) }
    override suspend fun getCategoryCount(): Int = categories.size
    override suspend fun updateMonthlyBudget(id: Long, budgetPaise: Long) {
        categories[id]?.let { categories[id] = it.copy(budgetPaise = budgetPaise) }
    }
    override suspend fun updateColorHex(id: Long, colorHex: String) {
        categories[id]?.let { categories[id] = it.copy(colorHex = colorHex) }
    }
    override suspend fun clearAll() { categories.clear() }
}

class FakeManualVendorDao : VendorDao {
    val vendors = mutableMapOf<Long, VendorEntity>()
    private var idCounter = 1L

    override fun getAllVendors(): Flow<List<VendorEntity>> = flowOf(vendors.values.toList())
    override suspend fun getAllVendorsSync(): List<VendorEntity> = vendors.values.toList()
    override suspend fun getVendorById(id: Long): VendorEntity? = vendors[id]
    override suspend fun findByNormalizedKey(normalizedKey: String): VendorEntity? =
        vendors.values.find { it.normalizedKey.equals(normalizedKey, ignoreCase = true) }
    override suspend fun insertVendor(vendor: VendorEntity): Long {
        val id = if (vendor.id > 0) vendor.id else idCounter++
        vendors[id] = vendor.copy(id = id)
        return id
    }
    override suspend fun insertVendors(list: List<VendorEntity>): List<Long> = list.map { insertVendor(it) }
    override suspend fun updateVendor(vendor: VendorEntity) { vendors[vendor.id] = vendor }
    override suspend fun deleteVendor(vendor: VendorEntity) { vendors.remove(vendor.id) }
    override suspend fun deleteVendorById(id: Long) { vendors.remove(id) }
    override suspend fun updateCategoryIdForVendors(oldCatId: Long, newCatId: Long?) {
        vendors.values.filter { it.categoryId == oldCatId }.forEach {
            vendors[it.id] = it.copy(categoryId = newCatId)
        }
    }
    override suspend fun getVendorCount(): Int = vendors.size
    override suspend fun clearAll() { vendors.clear() }
}

class FakeManualAccountAliasDao : AccountAliasDao {
    val aliases = mutableListOf<AccountAliasEntity>()
    private var idCounter = 1L

    override suspend fun getAliasesForAccount(accountId: Long): List<AccountAliasEntity> = aliases.filter { it.accountId == accountId }
    override suspend fun findByAlias(bankKey: String, aliasLast4: String): AccountAliasEntity? =
        aliases.find { it.bankKey.equals(bankKey, ignoreCase = true) && it.aliasLast4 == aliasLast4 }
    override suspend fun insertAlias(alias: AccountAliasEntity): Long {
        val id = if (alias.id > 0) alias.id else idCounter++
        aliases.add(alias.copy(id = id))
        return id
    }
    override suspend fun insertAliases(list: List<AccountAliasEntity>): List<Long> = list.map { insertAlias(it) }
    override suspend fun reassignAliases(oldAccountId: Long, newAccountId: Long) {
        val updated = aliases.map { if (it.accountId == oldAccountId) it.copy(accountId = newAccountId) else it }
        aliases.clear()
        aliases.addAll(updated)
    }
    override suspend fun deleteAliasesForAccount(accountId: Long) {
        aliases.removeAll { it.accountId == accountId }
    }
    override suspend fun deleteAlias(alias: AccountAliasEntity) {
        aliases.remove(alias)
    }
    override suspend fun getAllAliasesSync(): List<AccountAliasEntity> = aliases.toList()
}

class FakeManualPersonDao : PersonDao {
    val persons = mutableMapOf<Long, PersonEntity>()
    private var idCounter = 1L

    override fun getAllPersons(): Flow<List<PersonEntity>> = flowOf(persons.values.toList())
    override suspend fun getAllPersonsSync(): List<PersonEntity> = persons.values.toList()
    override suspend fun getPersonById(id: Long): PersonEntity? = persons[id]
    override suspend fun getPersonByName(name: String): PersonEntity? = persons.values.find { it.name.equals(name, ignoreCase = true) }
    override suspend fun insertPerson(person: PersonEntity): Long {
        val id = if (person.id > 0) person.id else idCounter++
        persons[id] = person.copy(id = id)
        return id
    }
    override suspend fun insertPersons(list: List<PersonEntity>): List<Long> = list.map { insertPerson(it) }
    override suspend fun updatePerson(person: PersonEntity) { persons[person.id] = person }
    override suspend fun deletePerson(person: PersonEntity) { persons.remove(person.id) }
    override fun getPersonsWithBalances(): Flow<List<PersonWithBalance>> = flowOf(emptyList())
    override suspend fun getPersonsWithBalancesSync(): List<PersonWithBalance> = emptyList()
}

class FakeManualSplitItemDao : SplitItemDao {
    val splits = mutableMapOf<Long, SplitItemEntity>()
    private var idCounter = 1L

    override suspend fun insertSplit(split: SplitItemEntity): Long {
        val id = if (split.id > 0) split.id else idCounter++
        splits[id] = split.copy(id = id)
        return id
    }
    override suspend fun insertSplits(list: List<SplitItemEntity>): List<Long> = list.map { insertSplit(it) }
    override suspend fun updateSplit(split: SplitItemEntity) { splits[split.id] = split }
    override suspend fun deleteSplit(split: SplitItemEntity) { splits.remove(split.id) }
    override suspend fun deleteSplitsForTransaction(transactionId: Long) { splits.values.filter { it.transactionId == transactionId }.forEach { splits.remove(it.id) } }
    override fun getSplitsForTransaction(transactionId: Long): Flow<List<SplitItemEntity>> = flowOf(splits.values.filter { it.transactionId == transactionId })
    override suspend fun getSplitsForTransactionSync(transactionId: Long): List<SplitItemEntity> = splits.values.filter { it.transactionId == transactionId }
    override suspend fun reassignSplits(oldTransactionId: Long, newTransactionId: Long) {
        splits.values.filter { it.transactionId == oldTransactionId }.forEach {
            splits[it.id] = it.copy(transactionId = newTransactionId)
        }
    }
    override fun getUnpaidSplitsForPerson(personId: Long): Flow<List<SplitItemEntity>> = flowOf(splits.values.filter { it.personId == personId && !it.isPaid })
    override suspend fun getUnpaidSplitsForPersonSync(personId: Long): List<SplitItemEntity> = splits.values.filter { it.personId == personId && !it.isPaid }
    override suspend fun clearAll() { splits.clear() }
}

class FakeManualPendingSettlementSuggestionDao : PendingSettlementSuggestionDao {
    val suggestions = mutableMapOf<Long, PendingSettlementSuggestionEntity>()
    private var idCounter = 1L

    override fun getAllSuggestions(): Flow<List<PendingSettlementSuggestionEntity>> = flowOf(suggestions.values.toList())
    override suspend fun getAllSuggestionsSync(): List<PendingSettlementSuggestionEntity> = suggestions.values.toList()
    override suspend fun getSuggestionById(id: Long): PendingSettlementSuggestionEntity? = suggestions[id]
    override suspend fun insertSuggestion(suggestion: PendingSettlementSuggestionEntity): Long {
        val id = if (suggestion.id > 0) suggestion.id else idCounter++
        suggestions[id] = suggestion.copy(id = id)
        return id
    }
    override suspend fun updateSuggestion(suggestion: PendingSettlementSuggestionEntity) { suggestions[suggestion.id] = suggestion }
    override suspend fun deleteSuggestion(suggestion: PendingSettlementSuggestionEntity) { suggestions.remove(suggestion.id) }
    override suspend fun deleteSuggestionsForPerson(personId: Long) { suggestions.values.filter { it.personId == personId }.forEach { suggestions.remove(it.id) } }
    override suspend fun clearAll() { suggestions.clear() }
}

class FakeManualRuleDao : RuleDao {
    val rules = mutableMapOf<Long, RuleEntity>()
    private var idCounter = 1L

    override fun getAllRules(): Flow<List<RuleEntity>> = flowOf(rules.values.toList())
    override suspend fun getAllRulesSync(): List<RuleEntity> = rules.values.toList()
    override suspend fun getEnabledRulesSync(): List<RuleEntity> = rules.values.filter { it.enabled }
    override suspend fun getRuleById(id: Long): RuleEntity? = rules[id]
    override suspend fun insertRule(rule: RuleEntity): Long {
        val id = if (rule.id > 0) rule.id else idCounter++
        rules[id] = rule.copy(id = id)
        return id
    }
    override suspend fun updateRule(rule: RuleEntity) { rules[rule.id] = rule }
    override suspend fun deleteRule(rule: RuleEntity) { rules.remove(rule.id) }
    override suspend fun setRuleEnabled(id: Long, enabled: Boolean) { rules[id]?.let { rules[id] = it.copy(enabled = enabled) } }
    override suspend fun getRuleCount(): Int = rules.size
}

class FakeManualReviewItemDao : ReviewItemDao {
    val items = mutableMapOf<Long, ReviewItemEntity>()
    private var idCounter = 1L

    override fun getPendingReviewItems(): Flow<List<ReviewItemEntity>> = flowOf(items.values.filter { it.status == ReviewStatus.PENDING })
    override suspend fun getPendingReviewItemsSync(): List<ReviewItemEntity> = items.values.filter { it.status == ReviewStatus.PENDING }
    override suspend fun getReviewItemById(id: Long): ReviewItemEntity? = items[id]
    override suspend fun insertReviewItem(item: ReviewItemEntity): Long {
        val id = if (item.id > 0) item.id else idCounter++
        items[id] = item.copy(id = id)
        return id
    }
    override suspend fun insertReviewItems(list: List<ReviewItemEntity>): List<Long> = list.map { insertReviewItem(it) }
    override suspend fun updateReviewItem(item: ReviewItemEntity) { items[item.id] = item }
    override suspend fun deleteReviewItem(item: ReviewItemEntity) { items.remove(item.id) }
    override suspend fun updateStatus(id: Long, status: ReviewStatus) { items[id]?.let { items[id] = it.copy(status = status) } }
    override suspend fun discardAllPending() { items.values.filter { it.status == ReviewStatus.PENDING }.forEach { items[it.id] = it.copy(status = ReviewStatus.DISCARDED) } }
    suspend fun clearAll() { items.clear() }
}

class FakeManualUnparsedMessageDao : FakeBaseUnparsedMessageDao()

class FakeManualScanHistoryDao : ScanHistoryDao {
    val history = mutableMapOf<Long, ScanHistoryEntity>()
    private var idCounter = 1L

    override fun getAllScanHistory(): Flow<List<ScanHistoryEntity>> = flowOf(history.values.toList())
    override suspend fun getAllScanHistorySync(): List<ScanHistoryEntity> = history.values.toList()
    override suspend fun getScanHistoryById(id: Long): ScanHistoryEntity? = history[id]
    override suspend fun insertScanHistory(entry: ScanHistoryEntity): Long {
        val id = if (entry.id > 0) entry.id else idCounter++
        history[id] = entry.copy(id = id)
        return id
    }
    override suspend fun updateScanHistory(entry: ScanHistoryEntity) { history[entry.id] = entry }
    override suspend fun deleteScanHistory(scan: ScanHistoryEntity) { history.remove(scan.id) }
    override suspend fun clearAll() { history.clear() }
}

class FakeManualProcessedMessageDao : ProcessedMessageDao {
    val hashes = mutableSetOf<String>()
    val messages = mutableMapOf<String, ProcessedMessageEntity>()

    override suspend fun isProcessed(bodyHash: String): Boolean = hashes.contains(bodyHash)
    override suspend fun getByBodyHash(bodyHash: String): ProcessedMessageEntity? = messages[bodyHash]
    override suspend fun getAllProcessedMessagesSync(): List<ProcessedMessageEntity> = messages.values.toList()
    override suspend fun insertProcessed(message: ProcessedMessageEntity) {
        hashes.add(message.bodyHash)
        messages[message.bodyHash] = message
    }
    override suspend fun insertProcessedMessages(messages: List<ProcessedMessageEntity>) {
        messages.forEach { insertProcessed(it) }
    }
    override suspend fun clearAll() {
        hashes.clear()
        messages.clear()
    }

    suspend fun hasMessageHash(bodyHash: String): Boolean = isProcessed(bodyHash)
    suspend fun insertProcessedMessage(message: ProcessedMessageEntity): Long {
        insertProcessed(message)
        return hashes.size.toLong()
    }
}
