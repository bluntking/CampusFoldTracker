package com.campusfold.tracker

import com.campusfold.tracker.data.entity.AccountEntity
import com.campusfold.tracker.data.entity.AccountType
import com.campusfold.tracker.data.entity.TransactionEntity
import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.parser.MessageExtractor
import com.campusfold.tracker.parser.model.*
import com.campusfold.tracker.util.CampusSmsParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MessageExtractor covering:
 * - 8+ Indian banks (SBI, HDFC, ICICI, Axis, PNB, Kotak, Federal, BOB, IndusInd, Canara)
 * - All transaction types: DEBIT, CREDIT SPEND, UPI, ATM, EMI, REFUND, CC_BILL_TRANSFER, OWN_TRANSFER
 * - Non-transaction classifications: BALANCE_ONLY alerts, OTP (ignored), PROMO (ignored), MANDATE/DUE reminders (ignored)
 * - Specialized formats: PNB Card Format 1, Axis Account Format 2, Axis HTML Email
 * - Confidence level validations
 */
class MessageExtractorTest {

    private val sampleKnownAccounts = listOf(
        AccountEntity(
            id = 1L,
            bankName = "Axis Bank",
            bankKey = "axis",
            last4 = "2003",
            type = AccountType.SAVINGS,
            displayName = "Axis Savings 2003",
            color = "#990033",
            balancePaise = 5000000L
        ),
        AccountEntity(
            id = 2L,
            bankName = "PNB",
            bankKey = "pnb",
            last4 = "8598",
            type = AccountType.CREDIT_CARD,
            displayName = "PNB CC 8598",
            color = "#003366",
            creditLimitPaise = 6000000L
        ),
        AccountEntity(
            id = 3L,
            bankName = "SBI",
            bankKey = "sbi",
            last4 = "5557",
            type = AccountType.SAVINGS,
            displayName = "SBI Savings 5557",
            color = "#1A5276",
            balancePaise = 2500000L
        ),
        AccountEntity(
            id = 4L,
            bankName = "Federal Bank",
            bankKey = "federal",
            last4 = "2548",
            type = AccountType.SAVINGS,
            displayName = "Federal (Jupiter) 2548",
            color = "#0E6655",
            balancePaise = 1500000L
        )
    )

    // ─── 1. SBI: Account Debit via UPI ───────────────────────────────────────
    @Test
    fun testSbiAccountDebitUpi() {
        val sms = "Dear SBI User, your A/c ending 5557 has been debited by Rs 1500.00 on 22-10-2026 by transfer to SWIGGY Ref No 123456789012 - SBI"
        val raw = RawMessage(MessageSource.SMS, "SBIINB", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(150000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("5557", tx.accountLast4)
        assertTrue(tx.confidence >= 0.85f)
    }

    // ─── 2. HDFC: Credit Card Spend & Available Limit ─────────────────────────
    @Test
    fun testHdfcCreditCardSpendWithLimit() {
        val sms = "INR 2499.00 spent on your HDFC Bank Credit Card ending 4321 at ZOMATO on 22-Oct-26. Avl limit: INR 75,000.00."
        val raw = RawMessage(MessageSource.SMS, "HDFCBK", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(249900L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("4321", tx.accountLast4)
        assertEquals(7500000L, tx.availableCreditPaise)
        assertEquals("CARD", tx.instrumentType)
        assertTrue(tx.confidence >= 0.85f)
    }

    // ─── 3. ICICI: ATM Cash Withdrawal & Balance ──────────────────────────────
    @Test
    fun testIciciAtmCashWithdrawal() {
        val sms = "Dear Customer, your Acct XX9876 is debited with INR 5,000.00 on 22-Oct-26 towards ATM cash withdrawal at MG Road ATM. Avail Bal: INR 12,400.00."
        val raw = RawMessage(MessageSource.SMS, "ICICIB", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(500000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.ATM, tx.type)
        assertEquals("9876", tx.accountLast4)
        assertEquals(1240000L, tx.balancePaise)
    }

    // ─── 4. Axis Bank: EMI Installment Debit ──────────────────────────────────
    @Test
    fun testAxisEmiPayment() {
        val sms = "INR 3,250.00 debited from A/c XX2003 on 22-10-26 towards SmartEMI installment Ref 998877665544."
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(325000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.EMI, tx.type)
        assertEquals("2003", tx.accountLast4)
    }

    // ─── 5. Kotak: Refund Inflow ──────────────────────────────────────────────
    @Test
    fun testKotakRefundReceived() {
        val sms = "Dear Customer, INR 450.00 has been credited to your Kotak Bank A/c 5678 on 22-10-26 as refund from AMAZON PAY Ref 443322110099."
        val raw = RawMessage(MessageSource.SMS, "KOTAKB", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(45000L, tx.amountPaise)
        assertEquals(Direction.CREDIT, tx.direction)
        assertEquals(TransactionType.REFUND, tx.type)
        assertEquals("5678", tx.accountLast4)
    }

    // ─── 6. PNB: Inbound Credit Card Bill Confirmation ────────────────────────
    @Test
    fun testPnbInboundCcBillConfirmation() {
        val sms = "Payment received of Rs. 5000.00 for your PNB Credit Card 8598 on 22-10-2026. Avl limit Rs. 59396.93. Thank you for payment."
        val raw = RawMessage(MessageSource.SMS, "VM-PNBSMS", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(500000L, tx.amountPaise)
        assertEquals(Direction.CREDIT, tx.direction)
        assertEquals(TransactionType.CC_BILL_TRANSFER, tx.type)
        assertEquals("8598", tx.accountLast4)
        assertEquals(5939693L, tx.availableCreditPaise)
    }

    // ─── 7. Axis: Outbound CC Bill Payment to CRED ───────────────────────────
    @Test
    fun testAxisOutboundCcBillPaymentCred() {
        val sms = "INR 2500.00 debited\nA/c no. XX2003\n21-10-26, 18:00:00\nUPI/P2M/999123456789/CRED"
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(250000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.CC_BILL_TRANSFER, tx.type)
        assertEquals("2003", tx.accountLast4)
        assertEquals("999123456789", tx.reference)
    }

    // ─── 8. Axis: Outbound CC Bill Payment to BILLDESK ───────────────────────
    @Test
    fun testAxisOutboundCcBillPaymentBilldesk() {
        val sms = "INR 3500.00 debited\nA/c no. XX2003\n21-10-26, 19:15:00\nUPI/P2M/888123456789/BILLDESK"
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(350000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.CC_BILL_TRANSFER, tx.type)
    }

    // ─── 9. Federal Bank: Balance-Only Alert ──────────────────────────────────
    @Test
    fun testFederalBankBalanceOnlyAlert() {
        val sms = "Dear Customer, Avail Bal in Federal Bank A/c ending 2548 is INR 45,210.50 as of 22-10-2026. Never share OTP."
        val raw = RawMessage(MessageSource.SMS, "FEDBNK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.BalanceOnly)
        val bal = (result as ExtractionResult.BalanceOnly).balanceUpdate

        assertEquals(4521050L, bal.balancePaise)
        assertEquals("2548", bal.accountLast4)
    }

    // ─── 10. OTP Message: Ignored with no content stored ──────────────────────
    @Test
    fun testOtpMessageIgnored() {
        val sms = "Your OTP for transaction of Rs. 500 at Amazon is 492019. Do not share this OTP with anyone."
        val raw = RawMessage(MessageSource.SMS, "VM-AMZNOTP", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Ignored)
        val reason = (result as ExtractionResult.Ignored).reason
        assertTrue(reason.contains("OTP", ignoreCase = true))
    }

    // ─── 11. Promotional Marketing: Ignored ──────────────────────────────────
    @Test
    fun testPromotionalOfferIgnored() {
        val sms = "Congratulations! You have a pre-approved personal loan offer of Rs. 5,00,000 at special discount interest rate. Apply now!"
        val raw = RawMessage(MessageSource.SMS, "AD-HDFCBK", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Ignored)
        val reason = (result as ExtractionResult.Ignored).reason
        assertTrue(reason.contains("Promotion", ignoreCase = true) || reason.contains("Marketing", ignoreCase = true))
    }

    // ─── 12. Mandate Reminder: Ignored before money moves ─────────────────────
    @Test
    fun testMandateReminderIgnored() {
        val sms = "Dear Customer, e-mandate for Netflix of INR 649.00 will be auto-debited from your A/c XX1234 on 28-10-2026. Ensure sufficient balance."
        val raw = RawMessage(MessageSource.SMS, "SBIINB", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Ignored)
        val reason = (result as ExtractionResult.Ignored).reason
        assertTrue(reason.contains("Mandate", ignoreCase = true) || reason.contains("Reminder", ignoreCase = true))
    }

    // ─── 13. Bill Due Reminder: Ignored ──────────────────────────────────────
    @Test
    fun testBillDueReminderIgnored() {
        val sms = "Dear Customer, Credit Card bill of INR 14,250.00 is due on 28-Oct-26. Pay minimum amount due of INR 1,500.00 to avoid late fee."
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Ignored)
    }

    // ─── 14. Own Transfer: Text naming another user account ──────────────────
    @Test
    fun testOwnTransferViaAccountNaming() {
        val sms = "INR 10,000.00 debited from A/c XX2003 on 22-10-2026 to SBI A/c XX5557 Ref 112233445566."
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(1000000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.OWN_TRANSFER, tx.type)
    }

    // ─── 15. Own Transfer: Explicit Self-Transfer Keyword ─────────────────────
    @Test
    fun testOwnTransferViaSelfTransferKeyword() {
        val sms = "Rs 5,000.00 debited from A/c XX2003 for self transfer on 22-10-2026 Ref 991122334455."
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(500000L, tx.amountPaise)
        assertEquals(TransactionType.OWN_TRANSFER, tx.type)
    }

    // ─── 16. Own Transfer: Batch Opposite Debit & Credit Matching ─────────────
    @Test
    fun testOwnTransferBatchPairing() {
        val baseTime = 1729600000000L
        val txDebit = ExtractedTransaction(
            amountPaise = 750000L,
            direction = Direction.DEBIT,
            type = TransactionType.EXPENSE,
            bankName = "Axis Bank",
            accountLast4 = "2003",
            timestamp = baseTime,
            parserId = "test"
        )
        val txCredit = ExtractedTransaction(
            amountPaise = 750000L,
            direction = Direction.CREDIT,
            type = TransactionType.INCOME,
            bankName = "SBI",
            accountLast4 = "5557",
            timestamp = baseTime + 120000L, // 2 minutes later
            parserId = "test"
        )

        val processed = MessageExtractor.postProcessOwnTransfers(listOf(txDebit, txCredit), sampleKnownAccounts)

        assertEquals(2, processed.size)
        assertEquals(TransactionType.OWN_TRANSFER, processed[0].type)
        assertEquals(TransactionType.OWN_TRANSFER, processed[1].type)
    }

    // ─── 17. Bank of Baroda: Debit Card POS Purchase ─────────────────────────
    @Test
    fun testBankOfBarodaDebitCardSpend() {
        val sms = "Your Baroda A/c XX3456 is debited for Rs 850.00 on 22-10-26 towards POS purchase at RELIANCE RETAIL. Bal: Rs 15200.00."
        val raw = RawMessage(MessageSource.SMS, "BOBTXN", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(85000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("3456", tx.accountLast4)
        assertEquals(1520000L, tx.balancePaise)
    }

    // ─── 18. IndusInd Bank: Debit UPI Spend ───────────────────────────────────
    @Test
    fun testIndusIndDebitUpi() {
        val sms = "Rs. 230.00 debited from IndusInd Bank A/c XX7890 on 22-Oct-26 via UPI to CHAI POINT Ref 778899001122. Avl Bal: Rs 8,300.00."
        val raw = RawMessage(MessageSource.SMS, "INDUSB", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(23000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("7890", tx.accountLast4)
    }

    // ─── 19. Canara Bank: Salary Income Credit ────────────────────────────────
    @Test
    fun testCanaraBankSalaryCredit() {
        val sms = "Your Canara Bank A/c ending 1122 is credited with INR 75,000.00 on 22-10-2026 by SALARY CREDIT - OCT 2026. Avl Bal INR 1,10,000.00."
        val raw = RawMessage(MessageSource.SMS, "CANBNK", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(7500000L, tx.amountPaise)
        assertEquals(Direction.CREDIT, tx.direction)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("1122", tx.accountLast4)
        assertEquals(11000000L, tx.balancePaise)
    }

    // ─── 20. PNB Format 1: Credit Card Debit with CODE and Limit ─────────────
    @Test
    fun testPnbFormat1CardDebit() {
        val sms = "PNB Credit Card 8598 debited with Rs.100 [CODE:WY9024] at Sekar store on 22-10-2026 14:18 through UPI: 629525888174 Avl limit Rs. 59396.93. -PNB"
        val raw = RawMessage(MessageSource.SMS, "VM-PNBSMS", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(10000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Sekar store", tx.merchantRaw)
        assertEquals("629525888174", tx.reference)
        assertEquals(5939693L, tx.availableCreditPaise)
    }

    // ─── 21. Axis Format 2: Multiline UPI Transaction ────────────────────────
    @Test
    fun testAxisFormat2MultilineUpiDebit() {
        val sms = "INR 60.00 debited\nA/c no. XX2003\n21-10-26, 17:54:38\nUPI/P2M/666090797851/DD STORE"
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(6000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("DD STORE", tx.merchantRaw)
        assertEquals("666090797851", tx.reference)
    }

    // ─── 22. Axis Bank: HTML Email Transaction ───────────────────────────────
    @Test
    fun testAxisHtmlEmailParsing() {
        val htmlBody = """
            <html>
            <body>
            <table>
            <tr><td>Amount Debited:</td><td>INR 120.00</td></tr>
            <tr><td>Account Number:</td><td>XX2003</td></tr>
            <tr><td>Date &amp; Time:</td><td>22-10-26, 20:36:32 IST</td></tr>
            <tr><td>Transaction Info:</td><td>UPI/P2M/666113532290/DD STORE</td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()
        val raw = RawMessage(MessageSource.EMAIL, "alerts@axisbank.com", htmlBody)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(12000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("DD STORE", tx.merchantRaw)
        assertEquals("666113532290", tx.reference)
    }

    // ─── 23. Non-Financial Password Reset Alert: Ignored ──────────────────────
    @Test
    fun testPasswordResetNotificationIgnored() {
        val sms = "Dear Customer, your NetBanking password has been changed successfully on 22-10-2026. If not done by you, call bank immediately."
        val raw = RawMessage(MessageSource.SMS, "HDFCBK", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Ignored)
    }

    // ─── 24. ASBA IPO Fund Blocking: Ignored (Not Spent) ─────────────────────
    @Test
    fun testAsbaIpoBlockingIgnored() {
        val sms = "Your ASBA application value of Rs 14,800.00 is blocked in your A/c XX2003 for Swiggy IPO on 22-10-2026."
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)

        assertTrue(result is ExtractionResult.Ignored)
    }

    // ─── 25. Generic Fallback Regex Spend ────────────────────────────────────
    @Test
    fun testGenericRegexFallbackSpend() {
        val sms = "Paid Rs 420.00 at CAFE COFFEE DAY using Card ending 9988 on 22-10-2026"
        val raw = RawMessage(MessageSource.SMS, "ALERT-SENDER", sms)
        val result = MessageExtractor.extract(raw)

        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction

        assertEquals(42000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("9988", tx.accountLast4)
        assertTrue(tx.confidence >= 0.60f)
    }

    @Test
    fun testCheckOwnTransferConditionCRequiresDifferentAccounts() {
        val t0 = 1000000L
        val accountAxis = sampleKnownAccounts.first { it.last4 == "2003" }

        // Previous transaction was an EXPENSE of Rs 20 on Axis ••2003
        val recentExpense = TransactionEntity(
            id = 5,
            amountPaise = 2000L,
            type = TransactionType.EXPENSE,
            paymentMode = accountAxis.displayName,
            accountId = accountAxis.id,
            timestamp = t0
        )

        // Incoming transfer of Rs 20 on the SAME Axis account within 5 minutes (friend paying back)
        val incomingRaw = RawMessage(
            source = MessageSource.EMAIL,
            sender = "alerts@axis.bank.in",
            subject = "INR 20.00 was credited to your A/c.",
            body = "Amount Credited: INR 20.00\nAccount Number: XX2003\nTransaction Info: UPI/P2A/112233/FRIEND/Sent",
            timestamp = t0 + 300_000L
        )

        val result = MessageExtractor.extract(incomingRaw, listOf(accountAxis), listOf(recentExpense))
        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction
        assertEquals("Friend paying back on same account must NOT be swallowed as OWN_TRANSFER", TransactionType.INCOME, tx.type)
    }

    @Test
    fun testUpcomingMandateDebitNoticeIgnored() {
        val sms = "For the upcoming mandate set for 25-10-26, INR 89.00 will be debited from your A/c towards Google Play for UPI Mandate, 660303724524. To stop execution, pause mandate - Axis Bank"
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)
        assertTrue("Upcoming mandate debit notice must be ignored", result is ExtractionResult.Ignored)
        val ignored = result as ExtractionResult.Ignored
        assertEquals("Upcoming mandate debit notice", ignored.reason)
    }

    @Test
    fun testRealUpiMandateDebitProcessedAsTransaction() {
        val sms = "Dear Customer, INR 89.00 was debited from your Axis Bank A/c no. XX2003 on 25-10-26 towards Google Play for UPI Mandate 660303724524. Bal INR 5,200.00 - Axis Bank"
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)
        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction
        assertEquals(8900L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun testNseilIpoCollectRequestIgnored() {
        val sms = "Dear HCLXXXXX6E IPO Jio Platforms Limited : App no ABCD12458 UPI ID user@okaxis for Rs 13428.00 received. Please check for UPI-Mandate collect request for blocking of funds- NSEIL"
        val raw = RawMessage(MessageSource.SMS, "NSEIL", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)
        assertTrue("NSEIL IPO collect request must be ignored", result is ExtractionResult.Ignored)
        val ignored = result as ExtractionResult.Ignored
        assertEquals("ASBA / IPO Fund Blocking", ignored.reason)

        // Also test CampusSmsParser directly
        val campusParsed = CampusSmsParser.parse(sms, "NSEIL", knownAccounts = sampleKnownAccounts)
        assertNull("CampusSmsParser must return null for IPO notices", campusParsed)
    }

    @Test
    fun testInvestmentDebitDetectedAsInvestmentType() {
        val sms = "INR 5000.00 debited\nA/c no. XX2003\n25-10-26, 11:00:00\nUPI/P2M/777123456789/ZERODHA BROKING"
        val raw = RawMessage(MessageSource.SMS, "AXISBK", sms)
        val result = MessageExtractor.extract(raw, sampleKnownAccounts)
        assertTrue(result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction
        assertEquals(500000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals(TransactionType.INVESTMENT, tx.type)
    }
}
