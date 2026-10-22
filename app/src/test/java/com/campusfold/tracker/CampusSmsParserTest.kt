package com.campusfold.tracker

import com.campusfold.tracker.data.entity.TransactionType
import com.campusfold.tracker.util.CampusSmsParser
import org.junit.Assert.*
import org.junit.Test

class CampusSmsParserTest {

    @Test
    fun testPnbCreditCard8598Debit() {
        val sms = "PNB Credit Card 8598 debited with Rs.100 [CODE:WY9024] at Sekar store on 22-10-2026 14:18 through UPI: 629525888174 Avl limit Rs. 59396.93. -PNB"
        val parsed = CampusSmsParser.parse(sms, "VM-PNBSMS")

        assertNotNull(parsed)
        val tx = parsed!!.transaction
        assertEquals(100.0, tx.amount, 0.001)
        assertEquals("Sekar store", tx.rawMerchant)
        assertEquals("PNB CC 8598", tx.paymentMode)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("629525888174", tx.upiRef)
        assertEquals(59396.93, parsed.extractedCreditLimit!!, 0.001)
    }

    @Test
    fun testPnbCreditCardBillConfirmation() {
        val sms = "Payment received of Rs. 5000.00 for your PNB Credit Card 8598 on 22-10-2026. Avl limit Rs. 59396.93. Thank you for payment."
        val parsed = CampusSmsParser.parse(sms, "VM-PNBSMS")

        assertNotNull(parsed)
        val tx = parsed!!.transaction
        assertEquals(5000.0, tx.amount, 0.001)
        assertEquals("PNB CC 8598", tx.paymentMode)
        // Must be CC_BILL_TRANSFER, never INCOME
        assertEquals(TransactionType.CC_BILL_TRANSFER, tx.type)
        assertEquals(59396.93, parsed.extractedCreditLimit!!, 0.001)
    }

    @Test
    fun testAxisBankXX2003Debit() {
        val sms = "INR 60.00 debited\nA/c no. XX2003\n21-10-26, 17:54:38\nUPI/P2M/666090797851/DD STORE"
        val parsed = CampusSmsParser.parse(sms, "AXISBK")

        assertNotNull(parsed)
        val tx = parsed!!.transaction
        assertEquals(60.0, tx.amount, 0.001)
        assertEquals("Axis Bank XX2003", tx.paymentMode)
        assertEquals("DD STORE", tx.rawMerchant)
        assertEquals("666090797851", tx.upiRef)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun testAxisBankOutboundCcBillPaymentCred() {
        val sms = "INR 2500.00 debited\nA/c no. XX2003\n21-10-26, 18:00:00\nUPI/P2M/999123456789/CRED"
        val parsed = CampusSmsParser.parse(sms, "AXISBK")

        assertNotNull(parsed)
        val tx = parsed!!.transaction
        assertEquals(2500.0, tx.amount, 0.001)
        assertEquals("Axis Bank XX2003", tx.paymentMode)
        assertEquals("CRED", tx.rawMerchant)
        // Strict word boundary rule should auto-classify CRED as CC_BILL_TRANSFER
        assertEquals(TransactionType.CC_BILL_TRANSFER, tx.type)
    }

    @Test
    fun testWordBoundaryNoFalsePositiveOnCredited() {
        // "credited" must not trigger \bCRED\b as CC_BILL_TRANSFER
        val sms = "Your A/c XX2003 is credited with INR 500.00 on 22-10-2026 by Rahul"
        val parsed = CampusSmsParser.parse(sms, "AXISBK")

        assertNotNull(parsed)
        val tx = parsed!!.transaction
        assertEquals(500.0, tx.amount, 0.001)
        // Must be INCOME, not CC_BILL_TRANSFER
        assertEquals(TransactionType.INCOME, tx.type)
    }
}
