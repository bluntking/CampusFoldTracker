package com.campusfold.tracker

import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.util.AxisBankEmailParser
import org.junit.Assert.*
import org.junit.Test

class AxisBankEmailParserTest {

    @Test
    fun testAxisBankDebitNotificationPdf() {
        val subject = "INR 120.00 was debited from your A/c no. XX2003."
        val body = """
            22-10-2026
            Dear Sahil Gupta,
            Here's the summary of your transaction:

            Amount Debited:
            INR 120.00

            Account Number:
            XX2003

            Date & Time:
            22-10-26, 20:36:32 IST

            Transaction Info:
            UPI/P2M/666113532290/DD STORE
        """.trimIndent()

        val parsed = AxisBankEmailParser.parse(subject, body)
        assertNotNull(parsed)
        val tx = parsed!!.transaction

        assertEquals(120.0, tx.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("Axis Bank XX2003", tx.paymentMode)
        assertEquals("DD STORE", tx.rawMerchant)
        assertEquals("666113532290", tx.upiRef)
        assertFalse(parsed.isCredit)
        assertTrue(parsed.isUpi)
    }

    @Test
    fun testAxisBankCreditNotificationPdf() {
        val subject = "INR 20.00 was credited to your A/c."
        val body = """
            21-10-2026
            Dear Sahil Gupta,
            Here's the summary of your transaction:

            Amount Credited:
            INR 20.00

            Account Number:
            XX2003

            Date & Time:
            21-10-26, 13:46:29 IST

            Transaction Info:
            UPI/P2A/218606473101/KAVYA KOH/KKBK/Sent
        """.trimIndent()

        val parsed = AxisBankEmailParser.parse(subject, body)
        assertNotNull(parsed)
        val tx = parsed!!.transaction

        assertEquals(20.0, tx.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Axis Bank XX2003", tx.paymentMode)
        assertEquals("KAVYA KOH", tx.rawMerchant)
        assertEquals("218606473101", tx.upiRef)
        assertTrue(parsed.isCredit)
        assertTrue(parsed.isUpi)
    }

    @Test
    fun testAxisBankHtmlEmailParsing() {
        val subject = "INR 120.00 was debited from your A/c no. XX2003."
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

        val parsed = AxisBankEmailParser.parse(subject, htmlBody)
        assertNotNull(parsed)
        val tx = parsed!!.transaction

        assertEquals(120.0, tx.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("DD STORE", tx.rawMerchant)
        assertEquals("666113532290", tx.upiRef)
    }

    @Test
    fun testQuotedPrintableAndMimeHeader() {
        val encodedSubject = "=?UTF-8?Q?INR_120.00_was_debited_from_your_A/c_no._XX2003.?="
        val qpBody = "Amount Debited: INR=20120.00\r\nAccount Number: XX2003\r\nDate & Time: 22-10-26, 20:36:32 IST\r\nTransaction Info: UPI/P2M/666113532290/DD=20STORE"

        val parsed = AxisBankEmailParser.parse(encodedSubject, qpBody)
        assertNotNull(parsed)
        val tx = parsed!!.transaction

        assertEquals(120.0, tx.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("DD STORE", tx.rawMerchant)
        assertEquals("666113532290", tx.upiRef)
    }
}
