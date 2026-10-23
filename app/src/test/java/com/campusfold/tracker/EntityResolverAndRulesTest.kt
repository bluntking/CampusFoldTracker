package com.campusfold.tracker

import com.campusfold.tracker.ai.AiFallbackService
import com.campusfold.tracker.ai.AiParsedTransaction
import com.campusfold.tracker.data.dao.*
import com.campusfold.tracker.data.entity.*
import com.campusfold.tracker.data.resolver.AccountNormalizer
import com.campusfold.tracker.data.resolver.DefaultEntityResolver
import com.campusfold.tracker.data.resolver.VendorResolver
import com.campusfold.tracker.parser.model.Direction
import com.campusfold.tracker.parser.model.MessageSource
import com.campusfold.tracker.parser.model.RawMessage
import com.campusfold.tracker.rules.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EntityResolverAndRulesTest {

    private lateinit var fakeAccountDao: FakeAccountDaoImpl
    private lateinit var fakeAccountAliasDao: FakeAccountAliasDaoImpl
    private lateinit var fakeVendorDao: FakeVendorDaoImpl
    private lateinit var fakeCategoryDao: FakeCategoryDaoImpl
    private lateinit var fakeRuleDao: FakeRuleDaoImpl
    private lateinit var fakeReviewItemDao: FakeReviewItemDaoImpl
    private lateinit var fakeAiCacheDao: FakeAiCacheDaoImpl

    @Before
    fun setUp() {
        fakeAccountDao = FakeAccountDaoImpl()
        fakeAccountAliasDao = FakeAccountAliasDaoImpl()
        fakeVendorDao = FakeVendorDaoImpl()
        fakeCategoryDao = FakeCategoryDaoImpl()
        fakeRuleDao = FakeRuleDaoImpl()
        fakeReviewItemDao = FakeReviewItemDaoImpl()
        fakeAiCacheDao = FakeAiCacheDaoImpl()
    }

    // ─── 1. Account & Bank Normalization Tests ─────────────────────────────────

    @Test
    fun testAccountNormalizer_BankNames() {
        val (axisKey, axisName) = AccountNormalizer.normalizeBank("AXIS BANK")
        assertEquals("axis", axisKey)
        assertEquals("Axis Bank", axisName)

        val (axisKey2, _) = AccountNormalizer.normalizeBank("Axis Bank Ltd")
        assertEquals("axis", axisKey2)

        val (hdfcKey, hdfcName) = AccountNormalizer.normalizeBank("HDFC Bank")
        assertEquals("hdfc", hdfcKey)
        assertEquals("HDFC Bank", hdfcName)

        val (sbiKey, sbiName) = AccountNormalizer.normalizeBank("State Bank of India")
        assertEquals("sbi", sbiKey)
        assertEquals("State Bank of India", sbiName)

        val (iciciKey, iciciName) = AccountNormalizer.normalizeBank("ICICI Bank")
        assertEquals("icici", iciciKey)
        assertEquals("ICICI Bank", iciciName)

        val (pnbKey, pnbName) = AccountNormalizer.normalizeBank("Punjab National Bank")
        assertEquals("pnb", pnbKey)
        assertEquals("Punjab National Bank", pnbName)

        val (pnbKey2, _) = AccountNormalizer.normalizeBank("PNB")
        assertEquals("pnb", pnbKey2)
    }

    @Test
    fun testAccountNormalizer_Last4() {
        assertEquals("2003", AccountNormalizer.normalizeLast4("XX2003"))
        assertEquals("2003", AccountNormalizer.normalizeLast4("x2003"))
        assertEquals("2003", AccountNormalizer.normalizeLast4("2003"))
        assertEquals("3456", AccountNormalizer.normalizeLast4("A/C 123456"))
        assertNull(AccountNormalizer.normalizeLast4(null))
        assertNull(AccountNormalizer.normalizeLast4(""))
        assertNull(AccountNormalizer.normalizeLast4("XX"))
    }

    @Test
    fun testAccountNormalizer_InferType() {
        assertEquals(AccountType.CREDIT_CARD, AccountNormalizer.inferAccountType("PNB Credit Card 8598"))
        assertEquals(AccountType.CREDIT_CARD, AccountNormalizer.inferAccountType("Visa Card XX1234"))
        assertEquals(AccountType.WALLET, AccountNormalizer.inferAccountType("Paytm Wallet balance"))
        assertEquals(AccountType.SAVINGS, AccountNormalizer.inferAccountType("Axis Savings A/c 2003"))
        assertEquals(AccountType.SAVINGS, AccountNormalizer.inferAccountType("A/C XX5476 debited"))
        assertEquals(AccountType.SAVINGS, AccountNormalizer.inferAccountType(null))
    }

    @Test
    fun testAccountNormalizer_FormatDisplayName() {
        assertEquals("Axis Bank ••2003", AccountNormalizer.formatDisplayName("Axis Bank", "2003"))
        assertEquals("Axis Bank", AccountNormalizer.formatDisplayName("Axis Bank", null))
        assertEquals("Unassigned Account", AccountNormalizer.formatDisplayName("", null))
    }

    // ─── 2. Vendor Cleaning & Whole-Word Matching Tests ────────────────────────

    @Test
    fun testVendorResolver_CleanMerchantString() {
        val cleanedUpi = VendorResolver.cleanMerchantString("UPI-SWIGGY-12345@icici")
        assertEquals("Swiggy", cleanedUpi)

        val cleanedPos = VendorResolver.cleanMerchantString("POS/STARBUCKS COFFEE/Ref 987654")
        assertEquals("Starbucks Coffee", cleanedPos)

        val cleanedAtm = VendorResolver.cleanMerchantString("ATM/CASH WDL")
        assertEquals("Cash Wdl", cleanedAtm)

        val cleanedVpa = VendorResolver.cleanMerchantString("paytmqr2810@paytm")
        assertEquals("Paytmqr", cleanedVpa)
    }

    @Test
    fun testVendorResolver_WholeWordMatching_RejectsSubstring() {
        val existingVendors = listOf(
            VendorEntity(id = 1L, displayName = "Bar", normalizedKey = "bar", categoryId = null)
        )

        // Substring inside word MUST NOT match
        assertNull(VendorResolver.findMatchingVendor("CITIBANK", "CITIBANK", existingVendors))
        assertNull(VendorResolver.findMatchingVendor("STARBUCKS", "STARBUCKS", existingVendors))
        assertNull(VendorResolver.findMatchingVendor("BARREL", "BARREL", existingVendors))

        // Whole word matches MUST match
        val matchWholeWord = VendorResolver.findMatchingVendor("The Bar Lounge", "The Bar Lounge", existingVendors)
        assertNotNull(matchWholeWord)
        assertEquals("Bar", matchWholeWord?.displayName)

        val matchExact = VendorResolver.findMatchingVendor("Bar", "Bar", existingVendors)
        assertNotNull(matchExact)
        assertEquals("Bar", matchExact?.displayName)
    }

    // ─── 3. DefaultEntityResolver: Precedence, Aliases & Unassigned ───────────

    @Test
    fun testDefaultEntityResolver_PrecedenceOrder() = runBlocking {
        // Setup categories
        val foodCat = CategoryEntity(id = 10L, name = "Food & Dining", color = "#10B981", budgetPaise = null)
        val leisureCat = CategoryEntity(id = 20L, name = "Leisure", color = "#8B5CF6", budgetPaise = null)
        fakeCategoryDao.insertCategory(foodCat)
        fakeCategoryDao.insertCategory(leisureCat)

        // Setup user rule: IF MERCHANT EQUALS "Swiggy" THEN SET CATEGORY "Leisure"
        val ruleCondition = RuleCondition(
            field = TransactionField.MERCHANT,
            operator = ConditionOperator.EQUALS,
            value = "Swiggy"
        )
        val ruleAction = RuleAction(
            field = TransactionField.CATEGORY,
            actionType = ActionType.SET,
            value = "Leisure"
        )
        val userRule = RuleEntity(
            id = 1L,
            conditionsJson = RuleEngine.serializeConditions(listOf(ruleCondition)),
            actionsJson = RuleEngine.serializeActions(listOf(ruleAction)),
            priority = 10,
            enabled = true,
            source = RuleSource.USER
        )
        fakeRuleDao.insertRule(userRule)

        val resolver = DefaultEntityResolver(
            accountDao = fakeAccountDao,
            accountAliasDao = fakeAccountAliasDao,
            vendorDao = fakeVendorDao,
            categoryDao = fakeCategoryDao,
            ruleDao = fakeRuleDao,
            reviewItemDao = fakeReviewItemDao
        )

        // Step 1: User rule must win over keyword heuristic (Swiggy is normally Food & Dining, but user rule says Leisure)
        val res = resolver.resolve(
            bankName = "Axis Bank",
            bankKey = "axis",
            accountLast4 = "2003",
            instrumentType = "SAVINGS",
            merchantRaw = "Swiggy",
            direction = Direction.DEBIT,
            transactionType = TransactionType.EXPENSE,
            amountPaise = 25000L
        )

        assertEquals("Leisure", fakeCategoryDao.getCategoryById(res.categoryId!!)?.name)
    }

    @Test
    fun testDefaultEntityResolver_CategoryCreationSetsBudgetPaiseNull() = runBlocking {
        val resolver = DefaultEntityResolver(
            accountDao = fakeAccountDao,
            accountAliasDao = fakeAccountAliasDao,
            vendorDao = fakeVendorDao,
            categoryDao = fakeCategoryDao,
            ruleDao = fakeRuleDao,
            reviewItemDao = fakeReviewItemDao
        )

        // Resolving an unknown merchant with keyword heuristic e.g. "Swiggy" -> creates Food & Dining
        val res = resolver.resolve(
            bankName = "Axis Bank",
            bankKey = "axis",
            accountLast4 = "2003",
            instrumentType = "SAVINGS",
            merchantRaw = "Swiggy Bangalore",
            direction = Direction.DEBIT,
            transactionType = TransactionType.EXPENSE,
            amountPaise = 30000L
        )

        assertNotNull(res.categoryId)
        val createdCat = fakeCategoryDao.getCategoryById(res.categoryId!!)
        assertNotNull(createdCat)
        assertEquals("Food & Dining", createdCat?.name)
        // CRITICAL REQUIREMENT: Automatic category creation must set budgetPaise = null
        assertNull(createdCat?.budgetPaise)
    }

    @Test
    fun testDefaultEntityResolver_AccountAliasAndUnassigned() = runBlocking {
        // Create an account with a different primary last4 (e.g. 9999)
        val acc = AccountEntity(
            id = 50L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "9999",
            type = AccountType.SAVINGS,
            displayName = "Axis Bank ••9999",
            balancePaise = 100000L,
            balanceAsOf = 1000L,
            balanceSource = BalanceSource.AUTO
        )
        fakeAccountDao.insertAccount(acc)

        // Create an alias pointing to account 50L for last4 2003
        val alias = AccountAliasEntity(
            accountId = 50L,
            bankKey = "axis",
            last4 = "2003"
        )
        fakeAccountAliasDao.insertAlias(alias)

        val resolver = DefaultEntityResolver(
            accountDao = fakeAccountDao,
            accountAliasDao = fakeAccountAliasDao,
            vendorDao = fakeVendorDao,
            categoryDao = fakeCategoryDao,
            ruleDao = fakeRuleDao,
            reviewItemDao = fakeReviewItemDao
        )

        // Case A: Resolving by alias
        val resAlias = resolver.resolve(
            bankName = "Axis Bank",
            bankKey = "axis",
            accountLast4 = "xx2003",
            instrumentType = "SAVINGS",
            merchantRaw = "Flipkart",
            direction = Direction.DEBIT,
            transactionType = TransactionType.EXPENSE
        )
        assertEquals(50L, resAlias.account.id)

        // Case B: No identifier -> Fallback to Unassigned account & queue review item
        val resUnassigned = resolver.resolve(
            bankName = null,
            bankKey = null,
            accountLast4 = null,
            instrumentType = null,
            merchantRaw = "Unknown Shop",
            direction = Direction.DEBIT,
            transactionType = TransactionType.EXPENSE
        )
        assertEquals(AccountType.UNASSIGNED, resUnassigned.account.type)
        assertTrue(resUnassigned.needsReview)
        assertTrue(fakeReviewItemDao.items.isNotEmpty())
    }

    // ─── 4. RuleEngine: Conditions, Actions, AND/OR, and editedFields ──────────

    @Test
    fun testRuleEngine_ConditionEvaluationAndChaining() {
        val tx = TransactionEntity(
            id = 1L,
            amountPaise = 75000L,
            type = TransactionType.EXPENSE,
            rawMerchant = "Swiggy Order 123",
            friendlyVendorName = "Swiggy",
            timestamp = 1700000000000L,
            myActualSharePaise = 75000L,
            rawSms = "A/C XX2003 debited by INR 750.00 at Swiggy"
        )

        // Rule 1: AMOUNT GREATER_THAN 500 AND MERCHANT CONTAINS "Swiggy"
        val cond1 = RuleCondition(TransactionField.AMOUNT, ConditionOperator.GREATER_THAN, "500.00", LogicalOperator.AND)
        val cond2 = RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Swiggy", LogicalOperator.AND)
        val action1 = RuleAction(TransactionField.CATEGORY, ActionType.SET, "Food & Dining")

        val rule1 = RuleEntity(
            id = 1L,
            conditionsJson = RuleEngine.serializeConditions(listOf(cond1, cond2)),
            actionsJson = RuleEngine.serializeActions(listOf(action1)),
            priority = 0,
            enabled = true,
            source = RuleSource.USER
        )

        val eval1 = RuleEngine.evaluateRules(tx, tx.rawSms, null, listOf(rule1))
        assertTrue(eval1.appliedRuleIds.contains(1L))
        assertEquals("Food & Dining", eval1.targetCategoryName)

        // Rule 2: Amount condition fails with AND
        val condFailing = RuleCondition(TransactionField.AMOUNT, ConditionOperator.LESS_THAN, "500.00", LogicalOperator.AND)
        val rule2 = RuleEntity(
            id = 2L,
            conditionsJson = RuleEngine.serializeConditions(listOf(condFailing, cond2)),
            actionsJson = RuleEngine.serializeActions(listOf(action1)),
            priority = 0,
            enabled = true,
            source = RuleSource.USER
        )
        val eval2 = RuleEngine.evaluateRules(tx, tx.rawSms, null, listOf(rule2))
        assertFalse(eval2.appliedRuleIds.contains(2L))

        // Rule 3: Amount condition fails with OR -> still matches because cond2 passes!
        val condFailingOr = RuleCondition(TransactionField.AMOUNT, ConditionOperator.LESS_THAN, "500.00", LogicalOperator.OR)
        val rule3 = RuleEntity(
            id = 3L,
            conditionsJson = RuleEngine.serializeConditions(listOf(condFailingOr, cond2)),
            actionsJson = RuleEngine.serializeActions(listOf(action1)),
            priority = 0,
            enabled = true,
            source = RuleSource.USER
        )
        val eval3 = RuleEngine.evaluateRules(tx, tx.rawSms, null, listOf(rule3))
        assertTrue(eval3.appliedRuleIds.contains(3L))
    }

    @Test
    fun testRuleEngine_BlockAction() {
        val tx = TransactionEntity(
            id = 1L,
            amountPaise = 10000L,
            type = TransactionType.EXPENSE,
            rawMerchant = "Spam Promotion",
            friendlyVendorName = "Spam Promotion",
            timestamp = 1000L,
            myActualSharePaise = 10000L
        )

        val blockRule = RuleEntity(
            id = 99L,
            conditionsJson = RuleEngine.serializeConditions(listOf(
                RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Spam")
            )),
            actionsJson = RuleEngine.serializeActions(listOf(
                RuleAction(TransactionField.MERCHANT, ActionType.BLOCK)
            )),
            priority = 10,
            enabled = true,
            source = RuleSource.USER
        )

        val eval = RuleEngine.evaluateRules(tx, null, null, listOf(blockRule))
        assertTrue(eval.isBlocked)
        assertEquals(99L, eval.blockingRuleId)
    }

    @Test
    fun testRuleEngine_EditedFieldsProtection() {
        // User manually edited categoryId, so it is in editedFields
        val tx = TransactionEntity(
            id = 1L,
            amountPaise = 20000L,
            type = TransactionType.EXPENSE,
            rawMerchant = "Swiggy",
            friendlyVendorName = "Swiggy",
            timestamp = 1000L,
            myActualSharePaise = 20000L,
            editedFields = "[\"categoryId\"]",
            categoryId = 999L // User chose this manually
        )

        val rule = RuleEntity(
            id = 1L,
            conditionsJson = RuleEngine.serializeConditions(listOf(
                RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Swiggy")
            )),
            actionsJson = RuleEngine.serializeActions(listOf(
                RuleAction(TransactionField.CATEGORY, ActionType.SET, "Food & Dining")
            )),
            priority = 0,
            enabled = true,
            source = RuleSource.USER
        )

        val eval = RuleEngine.evaluateRules(tx, null, null, listOf(rule))
        // targetCategoryName must be null because categoryId is protected!
        assertNull(eval.targetCategoryName)
    }

    // ─── 5. AI Fallback JSON Validation Tests ──────────────────────────────────

    @Test
    fun testAiFallbackService_JsonSchemaValidation() {
        val aiService = AiFallbackService(fakeAiCacheDao)

        // Valid JSON
        val validJson = """
            {
                "amount": 450.50,
                "type": "EXPENSE",
                "merchant": "Campus Canteen",
                "bankName": "Axis Bank",
                "accountLast4": "2003"
            }
        """.trimIndent()
        val parsed = aiService.validateAndParseTransactionJson(validJson)
        assertNotNull(parsed)
        assertEquals(450.50, parsed!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Campus Canteen", parsed.merchant)
        assertEquals("Axis Bank", parsed.bankName)
        assertEquals("2003", parsed.accountLast4)
        assertEquals(0.70f, parsed.confidence, 0.001f) // Capped at 0.70

        // Discard: amount <= 0
        val invalidAmountJson = """
            {
                "amount": -50.0,
                "type": "EXPENSE",
                "merchant": "Campus Canteen"
            }
        """.trimIndent()
        assertNull(aiService.validateAndParseTransactionJson(invalidAmountJson))

        // Discard: blank merchant
        val blankMerchantJson = """
            {
                "amount": 100.0,
                "type": "EXPENSE",
                "merchant": ""
            }
        """.trimIndent()
        assertNull(aiService.validateAndParseTransactionJson(blankMerchantJson))

        // Last4 sanitization: non-digit last4 stripped
        val badLast4Json = """
            {
                "amount": 150.0,
                "type": "EXPENSE",
                "merchant": "Tea Stall",
                "accountLast4": "ABCD"
            }
        """.trimIndent()
        val parsedBadLast4 = aiService.validateAndParseTransactionJson(badLast4Json)
        assertNotNull(parsedBadLast4)
        assertNull(parsedBadLast4!!.accountLast4)
    }

    @Test
    fun testAiFallbackService_OfflineAndNoModelGracefulHandling() {
        val aiService = AiFallbackService(fakeAiCacheDao)
        // With no local model active in test environment, isAiAvailable returns false
        assertFalse(aiService.isAiAvailable())

        // Extracting category from empty/invalid JSON returns null gracefully without exception
        assertNull(aiService.extractCategoryFromJson("not json"))
        assertNull(aiService.validateAndParseTransactionJson("bad input"))
    }
}

// ─── Test Fake DAOs ──────────────────────────────────────────────────────────

class FakeAccountDaoImpl : AccountDao {
    val accounts = mutableMapOf<Long, AccountEntity>()
    private var nextId = 1L

    override fun getAllAccounts(): Flow<List<AccountEntity>> = flowOf(accounts.values.filter { !it.isArchived })
    override fun getAllAccountsIncludingArchived(): Flow<List<AccountEntity>> = flowOf(accounts.values.toList())
    override suspend fun getAllAccountsSync(): List<AccountEntity> = accounts.values.toList()
    override suspend fun getActiveAccountsSync(): List<AccountEntity> = accounts.values.filter { !it.isArchived }
    override suspend fun getAccountById(id: Long): AccountEntity? = accounts[id]
    override suspend fun findByBankKeyAndLast4(bankKey: String, last4: String): AccountEntity? =
        accounts.values.find { it.bankKey.equals(bankKey, ignoreCase = true) && it.last4 == last4 }
    override suspend fun getAccountCount(): Int = accounts.values.count { !it.isArchived }

    override suspend fun insertAccount(account: AccountEntity): Long {
        val id = if (account.id == 0L) nextId++ else account.id
        accounts[id] = account.copy(id = id)
        return id
    }

    override suspend fun insertAccounts(accountsList: List<AccountEntity>): List<Long> =
        accountsList.map { insertAccount(it) }

    override suspend fun updateAccount(account: AccountEntity) { accounts[account.id] = account }
    override suspend fun deleteAccount(account: AccountEntity) { accounts.remove(account.id) }
    override suspend fun deleteAccountById(id: Long) { accounts.remove(id) }
    override suspend fun setArchived(id: Long, isArchived: Boolean) {
        accounts[id]?.let { accounts[id] = it.copy(isArchived = isArchived) }
    }
    override suspend fun updateBalance(id: Long, balancePaise: Long, balanceAsOf: Long, source: BalanceSource) {
        accounts[id]?.let {
            accounts[id] = it.copy(balancePaise = balancePaise, balanceAsOf = balanceAsOf, balanceSource = source)
        }
    }
}

class FakeAccountAliasDaoImpl : AccountAliasDao {
    val aliases = mutableListOf<AccountAliasEntity>()

    override fun getAliasesForAccount(accountId: Long): Flow<List<AccountAliasEntity>> =
        flowOf(aliases.filter { it.accountId == accountId })

    override suspend fun getAliasesForAccountSync(accountId: Long): List<AccountAliasEntity> =
        aliases.filter { it.accountId == accountId }

    override suspend fun findAccountIdByAlias(bankKey: String, last4: String): Long? =
        aliases.find { it.bankKey.equals(bankKey, ignoreCase = true) && it.last4.equals(last4, ignoreCase = true) }?.accountId

    override suspend fun getAllAliasesSync(): List<AccountAliasEntity> = aliases.toList()

    override suspend fun insertAlias(alias: AccountAliasEntity) {
        aliases.removeAll { it.accountId == alias.accountId && it.bankKey.equals(alias.bankKey, ignoreCase = true) && it.last4.equals(alias.last4, ignoreCase = true) }
        aliases.add(alias)
    }

    override suspend fun insertAliases(aliasesList: List<AccountAliasEntity>) {
        aliasesList.forEach { insertAlias(it) }
    }

    override suspend fun deleteAlias(alias: AccountAliasEntity) {
        aliases.remove(alias)
    }

    override suspend fun deleteAliasesForAccount(accountId: Long) {
        aliases.removeAll { it.accountId == accountId }
    }

    override suspend fun reassignAliases(oldAccountId: Long, newAccountId: Long) {
        for (i in aliases.indices) {
            if (aliases[i].accountId == oldAccountId) {
                aliases[i] = aliases[i].copy(accountId = newAccountId)
            }
        }
    }

    override suspend fun deleteAliasByBankKeyAndLast4(bankKey: String, last4: String) {
        aliases.removeAll { it.bankKey.equals(bankKey, ignoreCase = true) && it.last4.equals(last4, ignoreCase = true) }
    }
}

class FakeVendorDaoImpl : VendorDao {
    val vendors = mutableMapOf<Long, VendorEntity>()
    private var nextId = 1L

    override fun getAllVendors(): Flow<List<VendorEntity>> = flowOf(vendors.values.toList())
    override suspend fun getAllVendorsList(): List<VendorEntity> = vendors.values.toList()
    override suspend fun getVendorById(id: Long): VendorEntity? = vendors[id]
    override suspend fun findByNormalizedKey(key: String): VendorEntity? =
        vendors.values.find { it.normalizedKey.equals(key, ignoreCase = true) }

    override suspend fun insertVendor(vendor: VendorEntity): Long {
        val id = if (vendor.id == 0L) nextId++ else vendor.id
        vendors[id] = vendor.copy(id = id)
        return id
    }

    override suspend fun insertVendors(vendorsList: List<VendorEntity>): List<Long> =
        vendorsList.map { insertVendor(it) }

    override suspend fun updateVendor(vendor: VendorEntity) { vendors[vendor.id] = vendor }
    override suspend fun deleteVendor(vendor: VendorEntity) { vendors.remove(vendor.id) }
    override suspend fun deleteVendorById(id: Long) { vendors.remove(id) }
}

class FakeCategoryDaoImpl : CategoryDao {
    val categories = mutableMapOf<Long, CategoryEntity>()
    private var nextId = 1L

    override fun getAllCategories(): Flow<List<CategoryEntity>> = flowOf(categories.values.toList())
    override suspend fun getAllCategoriesList(): List<CategoryEntity> = categories.values.toList()
    override suspend fun getCategoryById(id: Long): CategoryEntity? = categories[id]
    override suspend fun getCategoryByName(name: String): CategoryEntity? =
        categories.values.find { it.name.equals(name, ignoreCase = true) }

    override suspend fun insertCategory(category: CategoryEntity): Long {
        val id = if (category.id == 0L) nextId++ else category.id
        categories[id] = category.copy(id = id)
        return id
    }

    override suspend fun insertCategories(categoriesList: List<CategoryEntity>): List<Long> =
        categoriesList.map { insertCategory(it) }

    override suspend fun updateCategory(category: CategoryEntity) { categories[category.id] = category }
    override suspend fun deleteCategory(category: CategoryEntity) { categories.remove(category.id) }
    override suspend fun deleteCategoryById(id: Long) { categories.remove(id) }
}

class FakeRuleDaoImpl : RuleDao {
    val rules = mutableMapOf<Long, RuleEntity>()
    private var nextId = 1L

    override fun getAllRules(): Flow<List<RuleEntity>> = flowOf(rules.values.sortedByDescending { it.priority })
    override suspend fun getAllRulesSync(): List<RuleEntity> = rules.values.sortedByDescending { it.priority }
    override suspend fun getActiveRulesSync(): List<RuleEntity> =
        rules.values.filter { it.enabled }.sortedByDescending { it.priority }

    override suspend fun getRuleById(id: Long): RuleEntity? = rules[id]

    override suspend fun insertRule(rule: RuleEntity): Long {
        val id = if (rule.id == 0L) nextId++ else rule.id
        rules[id] = rule.copy(id = id)
        return id
    }

    override suspend fun insertRules(rulesList: List<RuleEntity>): List<Long> =
        rulesList.map { insertRule(it) }

    override suspend fun updateRule(rule: RuleEntity) { rules[rule.id] = rule }
    override suspend fun deleteRule(rule: RuleEntity) { rules.remove(rule.id) }
    override suspend fun deleteRuleById(id: Long) { rules.remove(id) }
}

class FakeReviewItemDaoImpl : ReviewItemDao {
    val items = mutableMapOf<Long, ReviewItemEntity>()
    private var nextId = 1L

    override fun getPendingReviewItems(): Flow<List<ReviewItemEntity>> =
        flowOf(items.values.filter { it.status == ReviewStatus.PENDING })

    override fun getAllReviewItems(): Flow<List<ReviewItemEntity>> = flowOf(items.values.toList())
    override suspend fun getAllReviewItemsSync(): List<ReviewItemEntity> = items.values.toList()
    override suspend fun getReviewItemById(id: Long): ReviewItemEntity? = items[id]

    override suspend fun insertReviewItem(item: ReviewItemEntity): Long {
        val id = if (item.id == 0L) nextId++ else item.id
        items[id] = item.copy(id = id)
        return id
    }

    override suspend fun insertReviewItems(itemsList: List<ReviewItemEntity>): List<Long> =
        itemsList.map { insertReviewItem(it) }

    override suspend fun updateReviewItem(item: ReviewItemEntity) { items[item.id] = item }
    override suspend fun updateStatus(id: Long, status: ReviewStatus) {
        items[id]?.let { items[id] = it.copy(status = status) }
    }
    override suspend fun deleteReviewItem(item: ReviewItemEntity) { items.remove(item.id) }
    override suspend fun deleteReviewItemById(id: Long) { items.remove(id) }
}

class FakeAiCacheDaoImpl : AiCacheDao {
    val cache = mutableMapOf<String, AiCacheEntity>()

    override suspend fun getByInputHash(inputHash: String): AiCacheEntity? = cache[inputHash]
    override suspend fun getByInputHashAndTask(inputHash: String, task: String): AiCacheEntity? =
        cache.values.find { it.inputHash == inputHash && it.task == task }
    override suspend fun insertCache(cacheEntity: AiCacheEntity) { cache[cacheEntity.inputHash] = cacheEntity }
    override suspend fun clearAll() { cache.clear() }
}
