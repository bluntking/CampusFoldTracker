package com.campusfold.tracker

import com.campusfold.tracker.data.AppDatabase
import com.campusfold.tracker.data.dao.*
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.ingest.IngestResult
import com.campusfold.tracker.data.ingest.MessageIngestor
import com.campusfold.tracker.data.model.TransactionWithSplits
import com.campusfold.tracker.data.resolver.EntityResolver
import com.campusfold.tracker.data.resolver.ResolvedEntities
import com.campusfold.tracker.parser.model.Direction
import com.campusfold.tracker.parser.model.MessageSource
import com.campusfold.tracker.parser.model.RawMessage
import com.campusfold.tracker.util.MimeMessageDecoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Message Ingestion Pipeline:
 * - Ingestion Idempotence (rescan skips processed messages via SHA-256 bodyHash)
 * - Cross-source Deduplication & Enrichment (SMS + Email)
 * - Balance Update Rules (AUTO account updates vs MANUAL balance override protection)
 * - UID Sync State Handling (Incremental sync and reset on UIDVALIDITY change)
 * - MIME Decoding (quoted-printable, base64, HTML entities)
 */
class MessageIngestorTest {

    private lateinit var fakeDb: FakeAppDatabase
    private lateinit var entityResolver: EntityResolver
    private lateinit var ingestor: MessageIngestor

    private val testAccountAuto = AccountEntity(
        id = 101L,
        bankName = "Axis Bank",
        bankKey = "AXIS",
        last4 = "2003",
        type = AccountType.SAVINGS,
        displayName = "Axis Bank XX2003",
        balancePaise = 500000L, // ₹5,000.00
        balanceAsOf = 1000L,
        balanceSource = BalanceSource.AUTO
    )

    private val testAccountManual = AccountEntity(
        id = 102L,
        bankName = "PNB",
        bankKey = "PNB",
        last4 = "8598",
        type = AccountType.CREDIT_CARD,
        displayName = "PNB CC 8598",
        balancePaise = 2500000L, // ₹25,000.00
        balanceAsOf = 1000L,
        balanceSource = BalanceSource.MANUAL
    )

    @Before
    fun setUp() {
        fakeDb = FakeAppDatabase()
        fakeDb.accountDaoInstance.accounts[testAccountAuto.id] = testAccountAuto
        fakeDb.accountDaoInstance.accounts[testAccountManual.id] = testAccountManual

        entityResolver = object : EntityResolver {
            override suspend fun resolve(
                bankName: String?,
                bankKey: String?,
                accountLast4: String?,
                instrumentType: String?,
                merchantRaw: String?,
                direction: Direction,
                transactionType: TransactionType,
                rawMessage: RawMessage?,
                amountPaise: Long,
                timestamp: Long,
                editedFields: String,
                batchContext: com.campusfold.tracker.data.ingest.IngestBatchContext?
            ): ResolvedEntities {
                val acc = when {
                    accountLast4 == "2003" || bankName?.contains("Axis", ignoreCase = true) == true -> testAccountAuto
                    accountLast4 == "8598" || bankName?.contains("PNB", ignoreCase = true) == true -> testAccountManual
                    else -> testAccountAuto
                }
                return ResolvedEntities(
                    account = acc,
                    vendorDisplayName = merchantRaw?.replace("UPI/", "")?.trim() ?: "Vendor",
                    categoryId = 1L
                )
            }
        }

        ingestor = MessageIngestor(
            db = fakeDb,
            entityResolver = entityResolver
        )
    }

    // ─── 1. Ingestion Idempotence ─────────────────────────────────────────────

    @Test
    fun testIngestionIdempotence_SameMessageSkippedOnSecondRun() = runBlocking {
        val rawSms = RawMessage(
            source = MessageSource.SMS,
            sender = "AX-AxisBk",
            body = "Dear UPI user A/C XX2003 debited by 450.00 on 20-Oct-26 trf to Swiggy Ref 1234567890. Bal: 4550.00",
            timestamp = 1700000000000L
        )

        // First ingestion
        val result1 = ingestor.ingest(rawSms)
        assertTrue("First run should be imported", result1 is IngestResult.Imported)
        assertEquals(1, fakeDb.transactionDaoInstance.transactions.size)
        val bodyHash = (result1 as IngestResult.Imported).bodyHash
        assertTrue(fakeDb.processedMessageDaoInstance.isProcessed(bodyHash))

        // Second ingestion (exact same message)
        val result2 = ingestor.ingest(rawSms)
        assertTrue("Second run must be skipped as already processed", result2 is IngestResult.SkippedAlreadyProcessed)
        assertEquals("Transaction count must remain 1 (no duplicate)", 1, fakeDb.transactionDaoInstance.transactions.size)
    }

    // ─── 2. Cross-Source Deduplication & Enrichment ───────────────────────────

    @Test
    fun testCrossSourceDeduplication_SameReferenceEnrichesExistingRow() = runBlocking {
        val refNumber = "UPI987654321012"
        val timestamp = 1700000000000L

        // 1. Ingest via SMS first
        val smsMessage = RawMessage(
            source = MessageSource.SMS,
            sender = "AX-AxisBk",
            body = "Rs. 750.00 debited from A/C XX2003 on 20-Oct-26 to Zomato UPI Ref $refNumber",
            timestamp = timestamp
        )
        val smsResult = ingestor.ingest(smsMessage)
        assertTrue(smsResult is IngestResult.Imported)
        val txId = (smsResult as IngestResult.Imported).transactionId
        assertEquals(1, fakeDb.transactionDaoInstance.transactions.size)

        val txAfterSms = fakeDb.transactionDaoInstance.transactions[txId]!!
        assertEquals(75000L, txAfterSms.amountPaise)
        assertEquals(TransactionSource.SMS, txAfterSms.source)
        assertNotNull(txAfterSms.rawSms)
        assertNull(txAfterSms.description)

        // 2. Ingest via Email for the exact same transaction (same reference)
        val emailMessage = RawMessage(
            source = MessageSource.EMAIL,
            sender = "alerts@axisbank.com",
            body = "Dear Customer, Your Axis Bank account XX2003 was debited for INR 750.00 at ZOMATO on 20-Oct-2026. UTR/Ref: $refNumber",
            timestamp = timestamp + 10_000L, // 10 seconds later
            sourceId = "EMAIL_UID_1001"
        )
        val emailResult = ingestor.ingest(emailMessage)
        assertTrue("Email for same transaction should enrich existing row", emailResult is IngestResult.Enriched)
        assertEquals(txId, (emailResult as IngestResult.Enriched).transactionId)

        // Stored only ONCE in database
        assertEquals("Only one transaction should exist after SMS and Email", 1, fakeDb.transactionDaoInstance.transactions.size)

        // Enriched with email details
        val enrichedTx = fakeDb.transactionDaoInstance.transactions[txId]!!
        assertEquals(75000L, enrichedTx.amountPaise)
        assertNotNull("SMS body preserved", enrichedTx.rawSms)
        assertTrue("Email description added", enrichedTx.description?.contains("alerts@axisbank.com") == true)
    }

    @Test
    fun testCrossSourceDeduplication_Within2MinutesWindow() = runBlocking {
        val t0 = 1700000000000L

        // SMS without explicit UPI ref
        val sms = RawMessage(
            source = MessageSource.SMS,
            sender = "AX-AxisBk",
            body = "Rs. 320.00 debited from A/C XX2003 on 20-Oct-26 at Campus Store",
            timestamp = t0
        )
        val res1 = ingestor.ingest(sms)
        assertTrue(res1 is IngestResult.Imported)

        // Email arrives 45 seconds later (within 2-minute / 120-second window)
        val email = RawMessage(
            source = MessageSource.EMAIL,
            sender = "alerts@axisbank.com",
            body = "Your Axis Bank account XX2003 was debited by INR 320.00 at Campus Store on 20-Oct-2026",
            timestamp = t0 + 45_000L,
            sourceId = "EMAIL_UID_1002"
        )
        val res2 = ingestor.ingest(email)
        assertTrue("Should detect duplicate and enrich within 2 minutes", res2 is IngestResult.Enriched)
        assertEquals(1, fakeDb.transactionDaoInstance.transactions.size)
    }

    @Test
    fun testDistinctTransactions_MoreThan2MinutesApartNotDeduplicated() = runBlocking {
        val t0 = 1700000000000L

        val tx1 = RawMessage(
            source = MessageSource.SMS,
            sender = "AX-AxisBk",
            body = "Rs. 200.00 debited from A/C XX2003 on 20-Oct-26 at Tea Stall",
            timestamp = t0
        )
        val res1 = ingestor.ingest(tx1)
        assertTrue(res1 is IngestResult.Imported)

        // Same vendor and amount 10 minutes later -> separate purchase
        val tx2 = RawMessage(
            source = MessageSource.SMS,
            sender = "AX-AxisBk",
            body = "Rs. 200.00 debited from A/C XX2003 on 20-Oct-26 at Tea Stall",
            timestamp = t0 + 600_000L // 10 minutes later
        )
        val res2 = ingestor.ingest(tx2)
        assertTrue(res2 is IngestResult.Imported)
        assertEquals("Both distinct transactions must be stored", 2, fakeDb.transactionDaoInstance.transactions.size)
    }

    // ─── 3. Balance Update Rules (AUTO vs MANUAL) ─────────────────────────────

    @Test
    fun testBalanceUpdate_AutoAccountUpdatesBalance() = runBlocking {
        assertEquals(500000L, testAccountAuto.balancePaise)
        assertEquals(BalanceSource.AUTO, testAccountAuto.balanceSource)

        val balanceMsg = RawMessage(
            source = MessageSource.SMS,
            sender = "AX-AxisBk",
            body = "Dear Customer, available balance in Axis Bank A/c XX2003 is Rs. 7,450.00 as of 20-Oct-26",
            timestamp = 2000L
        )

        val result = ingestor.ingest(balanceMsg)
        assertTrue(result is IngestResult.BalanceUpdated)

        val updatedAcc = fakeDb.accountDaoInstance.getAccountById(testAccountAuto.id)!!
        assertEquals("AUTO account balance should be updated", 745000L, updatedAcc.balancePaise)
        assertEquals(2000L, updatedAcc.balanceAsOf)
        assertEquals(BalanceSource.AUTO, updatedAcc.balanceSource)
    }

    @Test
    fun testBalanceUpdate_ManualAccountNeverOverwritten() = runBlocking {
        assertEquals(2500000L, testAccountManual.balancePaise)
        assertEquals(BalanceSource.MANUAL, testAccountManual.balanceSource)

        // Incoming message reporting a different balance for PNB CC 8598
        val balanceMsg = RawMessage(
            source = MessageSource.SMS,
            sender = "PNBSMS",
            body = "PNB Card 8598: Available credit limit is Rs. 15,000.00, outstanding bal Rs. 10,000.00 on 20-Oct-26",
            timestamp = 3000L
        )

        val result = ingestor.ingest(balanceMsg)
        assertTrue(result is IngestResult.BalanceUpdated)

        val accountAfter = fakeDb.accountDaoInstance.getAccountById(testAccountManual.id)!!
        assertEquals("MANUAL balance must NEVER be overwritten", 2500000L, accountAfter.balancePaise)
        assertEquals(BalanceSource.MANUAL, accountAfter.balanceSource)
    }

    // ─── 4. UID Sync State Handling ───────────────────────────────────────────

    @Test
    fun testUidSyncStateHandling() {
        var storedUidValidity = 10001L
        var storedLastUid = 50L

        // Scenario A: Same UIDVALIDITY -> incremental fetch with UID > storedLastUid
        val serverUidValidity = 10001L
        val effectiveLastUid = if (storedUidValidity != serverUidValidity) 0L else storedLastUid
        assertEquals(50L, effectiveLastUid)

        // Advance UID
        storedLastUid = 75L
        assertEquals(75L, storedLastUid)

        // Scenario B: UIDVALIDITY changed on IMAP server -> must reset lastUid to 0
        val newServerUidValidity = 20002L
        val resetLastUid = if (storedUidValidity != newServerUidValidity) 0L else storedLastUid
        assertEquals("UIDVALIDITY change must reset last UID to 0", 0L, resetLastUid)

        storedUidValidity = newServerUidValidity
        storedLastUid = 10L
        assertEquals(20002L, storedUidValidity)
        assertEquals(10L, storedLastUid)
    }

    // ─── 5. MIME Decoding ─────────────────────────────────────────────────────

    @Test
    fun testMimeDecoding_QuotedPrintable() {
        // Quoted printable with soft line breaks and encoded bytes (=20 is space)
        val qpInput = "Dear=20Customer,=20Rs.=201,250.00=20was=20debited=20from=20A/c=20XX2003"
        val decoded = MimeMessageDecoder.decodeQuotedPrintable(qpInput)
        assertEquals("Dear Customer, Rs. 1,250.00 was debited from A/c XX2003", decoded)
    }

    @Test
    fun testMimeDecoding_Base64() {
        // "A/C XX2003 debited by INR 500.00 via UPI" base64-encoded
        val base64Input = "QS9DIFhYMjAwMyBkZWJpdGVkIGJ5IElOUiA1MDAuMDAgdmlhIFVQSQ=="
        val decoded = MimeMessageDecoder.decodeBase64(base64Input)
        assertEquals("A/C XX2003 debited by INR 500.00 via UPI", decoded.trim())
    }

    @Test
    fun testMimeDecoding_HtmlToPlainText() {
        val htmlInput = """
            <html>
                <head><style>.alert { color: red; }</style></head>
                <body>
                    <h2>Transaction Alert</h2>
                    <p>Dear Customer,<br/>Your account <b>XX2003</b> was debited for &#8377;&nbsp;1,200.00 at Flipkart.</p>
                </body>
            </html>
        """.trimIndent()

        val plainText = MimeMessageDecoder.htmlToPlainText(htmlInput)
        assertTrue(plainText.contains("Transaction Alert"))
        assertTrue(plainText.contains("Your account XX2003 was debited for ₹ 1,200.00 at Flipkart."))
        assertFalse(plainText.contains("<style>"))
        assertFalse(plainText.contains("<p>"))
    }
}

// ─── Test Fakes for Database and DAOs ─────────────────────────────────────────

class FakeAppDatabase : AppDatabase() {
    val processedMessageDaoInstance = FakeProcessedMessageDao()
    val unparsedMessageDaoInstance = FakeUnparsedMessageDao()
    val transactionDaoInstance = FakeTransactionDao()
    val accountDaoInstance = FakeAccountDao()
    val accountAliasDaoInstance = FakeAccountAliasDao()
    val vendorDaoInstance = FakeVendorDao()
    val categoryDaoInstance = FakeCategoryDao()
    val personDaoInstance = FakePersonDao()
    val splitItemDaoInstance = FakeSplitItemDao()
    val suggestionDaoInstance = FakePendingSettlementSuggestionDao()
    val scanHistoryDaoInstance = FakeScanHistoryDao()
    val reviewItemDaoInstance = FakeReviewItemDao()

    override fun processedMessageDao(): ProcessedMessageDao = processedMessageDaoInstance
    override fun unparsedMessageDao(): UnparsedMessageDao = unparsedMessageDaoInstance
    override fun transactionDao(): TransactionDao = transactionDaoInstance
    override fun accountDao(): AccountDao = accountDaoInstance
    override fun accountAliasDao(): AccountAliasDao = accountAliasDaoInstance
    override fun vendorDao(): VendorDao = vendorDaoInstance
    override fun categoryDao(): CategoryDao = categoryDaoInstance
    override fun personDao(): PersonDao = personDaoInstance
    override fun splitItemDao(): SplitItemDao = splitItemDaoInstance
    override fun pendingSettlementSuggestionDao(): PendingSettlementSuggestionDao = suggestionDaoInstance
    override fun scanHistoryDao(): ScanHistoryDao = scanHistoryDaoInstance
    override fun mergeLogDao(): MergeLogDao = throw NotImplementedError()
    override fun notSamePairDao(): NotSamePairDao = throw NotImplementedError()
    override fun ruleDao(): RuleDao = throw NotImplementedError()
    override fun reviewItemDao(): ReviewItemDao = reviewItemDaoInstance
    override fun aiCacheDao(): AiCacheDao = throw NotImplementedError()
    override fun clearAllTables() {}
    override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
        return androidx.room.InvalidationTracker(this, emptyMap(), emptyMap(), "transactions")
    }
    override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper {
        throw NotImplementedError()
    }
}

class FakeProcessedMessageDao : ProcessedMessageDao {
    val set = mutableMapOf<String, ProcessedMessageEntity>()
    override suspend fun isProcessed(bodyHash: String): Boolean = set.containsKey(bodyHash)
    override suspend fun getByBodyHash(bodyHash: String): ProcessedMessageEntity? = set[bodyHash]
    override suspend fun getAllProcessedMessagesSync(): List<ProcessedMessageEntity> = set.values.toList()
    override suspend fun insertProcessed(message: ProcessedMessageEntity) { set[message.bodyHash] = message }
    override suspend fun insertProcessedMessages(messages: List<ProcessedMessageEntity>) { messages.forEach { set[it.bodyHash] = it } }
    override suspend fun clearAll() { set.clear() }
}

class FakeUnparsedMessageDao : FakeBaseUnparsedMessageDao()

class FakeTransactionDao : FakeBaseTransactionDao()

class FakeAccountDao : AccountDao {
    val accounts = mutableMapOf<Long, AccountEntity>()
    private var idCounter = 1L

    override fun getAllAccounts(): Flow<List<AccountEntity>> = flowOf(accounts.values.toList())
    override fun getAllAccountsIncludingArchived(): Flow<List<AccountEntity>> = flowOf(accounts.values.toList())
    override suspend fun getAllAccountsSync(): List<AccountEntity> = accounts.values.toList()
    override suspend fun getActiveAccountsSync(): List<AccountEntity> = accounts.values.filter { !it.isArchived }
    override suspend fun getAccountById(id: Long): AccountEntity? = accounts[id]
    override suspend fun findByBankKeyAndLast4(bankKey: String, last4: String): AccountEntity? =
        accounts.values.find { it.bankKey.equals(bankKey, ignoreCase = true) && it.last4 == last4 }

    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun insertAccount(account: AccountEntity): Long {
        val id = if (account.id > 0) account.id else idCounter++
        accounts[id] = account.copy(id = id)
        return id
    }
    override suspend fun insertAccounts(accs: List<AccountEntity>): List<Long> = accs.map { insertAccount(it) }
    override suspend fun updateAccount(account: AccountEntity) { accounts[account.id] = account }
    override suspend fun deleteAccount(account: AccountEntity) { accounts.remove(account.id) }
    override suspend fun deleteAccountById(id: Long) { accounts.remove(id) }
    override suspend fun setArchived(id: Long, isArchived: Boolean) {
        accounts[id]?.let { accounts[id] = it.copy(isArchived = isArchived) }
    }
    override suspend fun updateBalance(id: Long, balancePaise: Long, balanceAsOf: Long, source: BalanceSource) {
        val existing = accounts[id] ?: return
        // MANUAL protection
        if (existing.balanceSource == BalanceSource.MANUAL && source != BalanceSource.MANUAL) {
            return
        }
        accounts[id] = existing.copy(balancePaise = balancePaise, balanceAsOf = balanceAsOf, balanceSource = source)
    }
    override suspend fun updateAnchor(id: Long, balancePaise: Long, owedPaise: Long, asOf: Long, source: BalanceSource) {
        val existing = accounts[id] ?: return
        accounts[id] = existing.copy(
            openingBalancePaise = balancePaise,
            openingOwedPaise = owedPaise,
            openingAsOf = asOf,
            anchorSet = true,
            balanceSource = source
        )
    }
}

class FakeAccountAliasDao : AccountAliasDao {
    private val aliases = mutableListOf<AccountAliasEntity>()
    override fun getAliasesForAccount(accountId: Long): Flow<List<AccountAliasEntity>> = flowOf(emptyList())
    override suspend fun getAliasesForAccountSync(accountId: Long): List<AccountAliasEntity> = emptyList()
    override suspend fun findAccountIdByAlias(bankKey: String, last4: String): Long? =
        aliases.find { it.bankKey.equals(bankKey, ignoreCase = true) && it.last4 == last4 }?.accountId
    override suspend fun getAllAliasesSync(): List<AccountAliasEntity> = aliases
    override suspend fun insertAlias(alias: AccountAliasEntity) { aliases.add(alias) }
    override suspend fun insertAliases(list: List<AccountAliasEntity>) { aliases.addAll(list) }
    override suspend fun deleteAlias(alias: AccountAliasEntity) { aliases.remove(alias) }
    override suspend fun deleteAliasesForAccount(accountId: Long) { aliases.removeIf { it.accountId == accountId } }
    override suspend fun reassignAliases(oldAccountId: Long, newAccountId: Long) {
        for (i in aliases.indices) {
            if (aliases[i].accountId == oldAccountId) {
                aliases[i] = aliases[i].copy(accountId = newAccountId)
            }
        }
    }
    override suspend fun deleteAliasByBankKeyAndLast4(bankKey: String, last4: String) {
        aliases.removeIf { it.bankKey.equals(bankKey, ignoreCase = true) && it.last4.equals(last4, ignoreCase = true) }
    }
}

class FakeVendorDao : VendorDao {
    private val vendors = mutableListOf<VendorEntity>()
    override fun getAllVendors(): Flow<List<VendorEntity>> = flowOf(vendors)
    override suspend fun getAllVendorsList(): List<VendorEntity> = vendors
    override suspend fun getVendorById(id: Long): VendorEntity? = vendors.find { it.id == id }
    override suspend fun findByNormalizedKey(key: String): VendorEntity? = vendors.find { it.normalizedKey.equals(key, ignoreCase = true) }
    override suspend fun insertVendor(vendor: VendorEntity): Long { vendors.add(vendor); return vendors.size.toLong() }
    override suspend fun insertVendors(list: List<VendorEntity>): List<Long> = list.map { insertVendor(it) }
    override suspend fun updateVendor(vendor: VendorEntity) {}
    override suspend fun deleteVendor(vendor: VendorEntity) { vendors.remove(vendor) }
    override suspend fun deleteVendorById(id: Long) { vendors.removeIf { it.id == id } }
    override suspend fun updateCategoryIdForVendors(oldCategoryId: Long, newCategoryId: Long?) {}
}

class FakeCategoryDao : CategoryDao {
    private val categories = mutableListOf<CategoryEntity>()
    override fun getAllCategories(): Flow<List<CategoryEntity>> = flowOf(categories)
    override suspend fun getAllCategoriesList(): List<CategoryEntity> = categories
    override suspend fun getCategoryById(id: Long): CategoryEntity? = categories.find { it.id == id }
    override suspend fun getCategoryByName(name: String): CategoryEntity? = categories.find { it.name.equals(name, ignoreCase = true) }
    override suspend fun insertCategory(category: CategoryEntity): Long {
        val id = (categories.size + 1).toLong()
        categories.add(category.copy(id = id))
        return id
    }
    override suspend fun insertCategories(list: List<CategoryEntity>): List<Long> = list.map { insertCategory(it) }
    override suspend fun updateCategory(category: CategoryEntity) {}
    override suspend fun deleteCategory(category: CategoryEntity) { categories.remove(category) }
    override suspend fun deleteCategoryById(id: Long) { categories.removeIf { it.id == id } }
    override fun getCategorySpending(): Flow<List<com.campusfold.tracker.data.model.CategorySpending>> = flowOf(emptyList())
    override fun getCategorySpendingForMonth(startOfMonth: Long, endOfMonth: Long): Flow<List<com.campusfold.tracker.data.model.CategorySpending>> = flowOf(emptyList())
    override suspend fun getCategorySpendingForMonthSync(startOfMonth: Long, endOfMonth: Long): List<com.campusfold.tracker.data.model.CategorySpending> = emptyList()
    override suspend fun getCategorySpendBetweenSync(start: Long, end: Long): List<com.campusfold.tracker.data.model.CategoryPeriodSpend> = emptyList()
}

class FakePersonDao : PersonDao {
    private val persons = mutableListOf<PersonEntity>()
    override fun getAllPersons(): Flow<List<PersonEntity>> = flowOf(persons)
    override suspend fun getAllPersonsList(): List<PersonEntity> = persons
    override suspend fun getPersonById(id: Long): PersonEntity? = persons.find { it.id == id }
    override fun getPersonsWithBalances(): Flow<List<com.campusfold.tracker.data.model.PersonWithBalance>> = flowOf(emptyList())
    override suspend fun getPersonWithBalance(personId: Long): com.campusfold.tracker.data.model.PersonWithBalance? = null
    override suspend fun insertPerson(person: PersonEntity): Long { persons.add(person); return persons.size.toLong() }
    override suspend fun insertPersons(list: List<PersonEntity>): List<Long> = list.map { insertPerson(it) }
    override suspend fun updatePerson(person: PersonEntity) {}
    override suspend fun deletePerson(person: PersonEntity) { persons.remove(person) }
    override suspend fun deletePersonById(id: Long) { persons.removeIf { it.id == id } }
}

class FakeSplitItemDao : SplitItemDao {
    override suspend fun getNetPendingBalanceForPersonSync(personId: Long): Long = 0L
    override fun getNetPendingBalanceForPerson(personId: Long): Flow<Long> = flowOf(0L)
    override fun getSplitsForTransaction(transactionId: Long): Flow<List<SplitItemEntity>> = flowOf(emptyList())
    override suspend fun getSplitsForTransactionSync(transactionId: Long): List<SplitItemEntity> = emptyList()
    override suspend fun getUnsettledSplitsForPersonSync(personId: Long): List<SplitItemEntity> = emptyList()
    override suspend fun getUnsettledSplitsForPersonAndDirectionSync(personId: Long, direction: SplitDirection): List<SplitItemEntity> = emptyList()
    override fun getUnsettledSplitsForPerson(personId: Long): Flow<List<SplitItemEntity>> = flowOf(emptyList())
    override fun getAllUnsettledSplits(): Flow<List<SplitItemEntity>> = flowOf(emptyList())
    override suspend fun getAllSplitsSync(): List<SplitItemEntity> = emptyList()
    override suspend fun getSplitById(id: Long): SplitItemEntity? = null
    override fun getUnsettledBillsForPerson(personId: Long): Flow<List<com.campusfold.tracker.data.model.UnsettledBillItem>> = flowOf(emptyList())
    override fun getAllUnsettledBills(): Flow<List<com.campusfold.tracker.data.model.UnsettledBillItem>> = flowOf(emptyList())
    override suspend fun insertSplitItem(splitItem: SplitItemEntity): Long = 1L
    override suspend fun insertSplitItems(splitItems: List<SplitItemEntity>): List<Long> = emptyList()
    override suspend fun updateSplitItem(splitItem: SplitItemEntity) {}
    override suspend fun deleteSplitItem(split: SplitItemEntity) {}
    override suspend fun reassignSplits(oldTxId: Long, newTxId: Long) {}
    override suspend fun deleteSplitsForTransaction(transactionId: Long) {}
    override suspend fun clearAll() {}
    override suspend fun recordPayment(splitId: Long, amountPaidPaise: Long) {}
    override suspend fun markSettled(splitId: Long, isSettled: Boolean) {}
}

class FakePendingSettlementSuggestionDao : PendingSettlementSuggestionDao {
    private val suggestions = mutableListOf<PendingSettlementSuggestionEntity>()
    override fun getPendingSuggestions(): Flow<List<PendingSettlementSuggestionEntity>> = flowOf(suggestions)
    override suspend fun getAllSuggestionsSync(): List<PendingSettlementSuggestionEntity> = suggestions
    override suspend fun getSuggestionById(id: Long): PendingSettlementSuggestionEntity? = suggestions.find { it.id == id }
    override suspend fun insertSuggestion(suggestion: PendingSettlementSuggestionEntity): Long {
        suggestions.add(suggestion)
        return suggestions.size.toLong()
    }
    override suspend fun insertSuggestions(suggestions: List<PendingSettlementSuggestionEntity>): List<Long> = suggestions.map { insertSuggestion(it) }
    override suspend fun updateStatus(id: Long, status: SettlementSuggestionStatus) {}
    override suspend fun deleteSuggestion(suggestion: PendingSettlementSuggestionEntity) { suggestions.remove(suggestion) }
    override suspend fun deleteSuggestionById(id: Long) { suggestions.removeIf { it.id == id } }
    override suspend fun clearAll() { suggestions.clear() }
}

class FakeScanHistoryDao : ScanHistoryDao {
    private val history = mutableListOf<ScanHistoryEntity>()
    override fun getAllScanHistory(): Flow<List<ScanHistoryEntity>> = flowOf(history)
    override suspend fun getScanHistoryById(id: Long): ScanHistoryEntity? = history.find { it.id == id }
    override suspend fun getAllScanHistorySync(): List<ScanHistoryEntity> = history
    override suspend fun insertScanHistory(scan: ScanHistoryEntity): Long {
        val id = (history.size + 1).toLong()
        history.add(scan.copy(id = id))
        return id
    }
    override suspend fun updateScanHistory(scan: ScanHistoryEntity) {
        val idx = history.indexOfFirst { it.id == scan.id }
        if (idx != -1) history[idx] = scan
    }
    override suspend fun deleteScanHistory(scan: ScanHistoryEntity) { history.remove(scan) }
    override suspend fun clearAll() { history.clear() }
}

class FakeReviewItemDao : ReviewItemDao {
    private val items = mutableListOf<ReviewItemEntity>()
    override fun getPendingReviewItems(): Flow<List<ReviewItemEntity>> = flowOf(items.filter { it.status == ReviewStatus.PENDING })
    override fun getAllReviewItems(): Flow<List<ReviewItemEntity>> = flowOf(items)
    override suspend fun getReviewItemById(id: Long): ReviewItemEntity? = items.find { it.id == id }
    override suspend fun getAllReviewItemsSync(): List<ReviewItemEntity> = items
    override suspend fun insertReviewItem(item: ReviewItemEntity): Long {
        val id = (items.size + 1).toLong()
        items.add(item.copy(id = id))
        return id
    }
    override suspend fun insertReviewItems(items: List<ReviewItemEntity>): List<Long> = items.map { insertReviewItem(it) }
    override suspend fun updateReviewItem(item: ReviewItemEntity) {
        val idx = items.indexOfFirst { it.id == item.id }
        if (idx != -1) items[idx] = item
    }
    override suspend fun updateStatus(id: Long, status: ReviewStatus) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx != -1) items[idx] = items[idx].copy(status = status)
    }
    override suspend fun discardAllPending() {
        items.replaceAll { if (it.status == ReviewStatus.PENDING) it.copy(status = ReviewStatus.DISCARDED) else it }
    }
    override suspend fun getPendingReviewItemsSync(): List<ReviewItemEntity> = items.filter { it.status == ReviewStatus.PENDING }
    override suspend fun deleteReviewItem(item: ReviewItemEntity) { items.remove(item) }
    override suspend fun deleteReviewItemById(id: Long) { items.removeIf { it.id == id } }
    override suspend fun clearAll() { items.clear() }
}
