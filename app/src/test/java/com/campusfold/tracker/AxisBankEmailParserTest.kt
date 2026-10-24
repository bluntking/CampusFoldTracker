package com.campusfold.tracker

import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.parser.MessageExtractor
import com.campusfold.tracker.parser.model.Direction
import com.campusfold.tracker.parser.model.ExtractionResult
import com.campusfold.tracker.parser.model.MessageSource
import com.campusfold.tracker.parser.model.RawMessage
import com.campusfold.tracker.util.AxisBankEmailParser
import org.junit.Assert.*
import org.junit.Test

class AxisBankEmailParserTest {

    @Test
    fun testAxisBankDebitNotificationSanitized() {
        val subject = "INR 350.00 was debited from your A/c no. XX9876."
        val body = """
            22-10-2026
            Dear Alex Morgan,
            Here's the summary of your transaction:

            Amount Debited:
            INR 350.00

            Account Number:
            XX9876

            Date & Time:
            22-10-26, 20:36:32 IST

            Transaction Info:
            UPI/P2M/999112233445/METRO SUPERMARKET
        """.trimIndent()

        val parsed = AxisBankEmailParser.parse(subject, body)
        assertNotNull(parsed)
        val tx = parsed!!.transaction

        assertEquals(350.0, tx.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Axis Bank XX9876", tx.paymentMode)
        assertEquals("METRO SUPERMARKET", tx.rawMerchant)
        assertEquals("999112233445", tx.upiRef)
        assertFalse(parsed.isCredit)
        assertTrue(parsed.isUpi)
    }

    @Test
    fun testAxisBankCreditNotificationSanitized() {
        val subject = "INR 150.00 was credited to your A/c."
        val body = """
            21-10-2026
            Dear Alex Morgan,
            Here's the summary of your transaction:

            Amount Credited:
            INR 150.00

            Account Number:
            XX9876

            Date & Time:
            21-10-26, 13:46:29 IST

            Transaction Info:
            UPI/P2A/888112233445/JORDAN LEE/HDFC/Sent
        """.trimIndent()

        val parsed = AxisBankEmailParser.parse(subject, body)
        assertNotNull(parsed)
        val tx = parsed!!.transaction

        assertEquals(150.0, tx.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Axis Bank XX9876", tx.paymentMode)
        assertEquals("JORDAN LEE", tx.rawMerchant)
        assertEquals("888112233445", tx.upiRef)
        assertTrue(parsed.isCredit)
        assertTrue(parsed.isUpi)
    }

    @Test
    fun testAxisBankHtmlEmailParsingSanitized() {
        val subject = "INR 350.00 was debited from your A/c no. XX9876."
        val htmlBody = """
            <html>
            <body>
            <table>
            <tr><td>Amount Debited:</td><td>INR 350.00</td></tr>
            <tr><td>Account Number:</td><td>XX9876</td></tr>
            <tr><td>Date &amp; Time:</td><td>22-10-26, 20:36:32 IST</td></tr>
            <tr><td>Transaction Info:</td><td>UPI/P2M/999112233445/METRO SUPERMARKET</td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val parsed = AxisBankEmailParser.parse(subject, htmlBody)
        assertNotNull(parsed)
        val tx = parsed!!.transaction

        assertEquals(350.0, tx.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("METRO SUPERMARKET", tx.rawMerchant)
        assertEquals("999112233445", tx.upiRef)
    }

    @Test
    fun testQuotedPrintableAndMimeHeaderSanitized() {
        val encodedSubject = "=?UTF-8?Q?INR_350.00_was_debited_from_your_A/c_no._XX9876.?="
        val qpBody = "Amount Debited: INR=20350.00\r\nAccount Number: XX9876\r\nDate & Time: 22-10-26, 20:36:32 IST\r\nTransaction Info: UPI/P2M/999112233445/METRO=20SUPERMARKET"

        val parsed = AxisBankEmailParser.parse(encodedSubject, qpBody)
        assertNotNull(parsed)
        val tx = parsed!!.transaction

        assertEquals(350.0, tx.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("METRO SUPERMARKET", tx.rawMerchant)
        assertEquals("999112233445", tx.upiRef)
    }

    @Test
    fun testAxisBankEmailWithOtpDisclaimerNotIgnored() {
        val emailBodyWithFooter = """
            Dear Alex Morgan,
            Amount Debited: INR 500.00
            Account Number: XX9876
            Date & Time: 23-10-26, 11:20:10 IST
            Transaction Info: UPI/P2M/777112233445/CITY CAFE

            Please do not share your Internet Banking details, such as user ID/password or your Credit/Debit Card number/CVV/OTP with anyone through telephone, SMS or email.
            Warm Regards,
            Axis Bank
        """.trimIndent()

        val raw = RawMessage(
            source = MessageSource.EMAIL,
            sender = "alerts@axis.bank.in",
            body = emailBodyWithFooter,
            subject = "INR 500.00 was debited from your A/c no. XX9876."
        )

        val result = MessageExtractor.extract(raw)
        assertTrue("Financial email with security disclaimer must not be ignored as OTP", result is ExtractionResult.Transaction)
        val tx = (result as ExtractionResult.Transaction).transaction
        assertEquals(50000L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals("CITY CAFE", tx.counterpartyName)
        assertEquals("777112233445", tx.reference)
    }
}
