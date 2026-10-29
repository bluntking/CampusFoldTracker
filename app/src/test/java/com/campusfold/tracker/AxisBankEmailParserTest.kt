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

    @Test
    fun testAxisBankCreditNotificationRealPdfStructure() {
        val subject = "INR 20.00 was credited to your A/c."
        val fullBody = """
            Alex Morgan <fakeuser@gmail.com>INR 20.00 was credited to your A/c.1 messageAxis Bank Alerts <alerts@axis.bank.in> 21 October 2026 at 13:46To: FAKEUSER@gmail.com
            21-10-2026Dear Alex Morgan,Here's the summary of your transaction:Amount Credited:INR 20.00Account Number:XX9876Date & Time:21-10-26, 13:46:29 ISTTransaction Info:UPI/P2A/218606473101/JORDAN LEE/HDFC/Sent
            Gmail - INR 20.00 was credited to your A/c. https://mail.google.com/mail/u/1/?ik=a070fc7d84&view=pt&search=al...
            1 of 222-10-2026, 19:56

            Call us at:18001035577(Toll Free)18604195555(Charges Applicable)Feel free to connect with us for any clarification.Always open to help you.Regards,Axis Bank Ltd.Footerimg****This is a system generated email and does not require signature. ****E003598117_09_2024Reach us at: 
            CHAT WEB Support Mobile app INTERNET BANKING WHATSAPP BRANCH LOCATORCopyright Axis Bank Ltd. All rights reserved. Terms & Conditions apply.Please do not share your Internet Banking details, such as user ID/password or your Credit/Debit Card number/CVV/OTPwith anyone, either over phone or through email.RBI never deals with individuals for Savings Account, Current Account, Credit Card, Debit Card, etc. Don't be victim to suchoffers coming to you on phone or email in the name of RBI.Do not click on Links from unknown/unsecure Sources that seek your confidential information.This email is confidential. It may also be legally privileged. If you are not the addressee, you may not copy, forward,disclose or use any part of it. Internet communications cannot be guaranteed to be timely, secure, error or virus-free.The sender does not accept liability for any errors or omissions. We maintain strict security standards and procedures toprevent unauthorised access to information about you. Know more >>
            Gmail - INR 20.00 was credited to your A/c. https://mail.google.com/mail/u/1/?ik=a070fc7d84&view=pt&search=al...
            2 of 222-10-2026, 19:56
        """.trimIndent()

        val parsed = AxisBankEmailParser.parse(subject, fullBody)
        assertNotNull(parsed)
        val tx = parsed!!.transaction
        assertEquals(20.0, tx.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("JORDAN LEE", tx.rawMerchant)
        assertEquals("218606473101", tx.upiRef)
        assertTrue(parsed.isCredit)
    }

    @Test
    fun testAxisBankDebitNotificationRealPdfStructure() {
        val subject = "INR 120.00 was debited from your A/c no. XX9876."
        val fullBody = """
            Alex Morgan <fakeuser@gmail.com>INR 120.00 was debited from your A/c no. XX9876.1 messagealerts@axis.bank.in <alerts@axis.bank.in> 22 October 2026 at 20:36To: FAKEUSER@gmail.com
            22-10-2026Dear Alex Morgan,Here's the summary of your transaction:Amount Debited:INR 120.00Account Number:XX9876Date & Time:22-10-26, 20:36:32 ISTTransaction Info:UPI/P2M/666113532290/DD STORE
            Gmail - INR 120.00 was debited from your A/c no. XX9876. https://mail.google.com/mail/u/1/?ik=a070fc7d84&view=pt&search=al...
            1 of 223-10-2026, 00:03

            Call us at:18001035577(Toll Free)18604195555(Charges Applicable)To block UPI:SMS BLOCKUPI <Customer ID> to +919951860002from your registered mobile number.If this transaction was not initiated by you:Always open to help you.Regards,Axis Bank Ltd.Footerimg ****This is a system generated communication and does not require signature. ****E003598217_09_2024Reach us at: 
            CHAT WEB Support Mobile app INTERNET BANKING WHATSAPP BRANCH LOCATORCopyright Axis Bank Ltd. All rights reserved. Terms & Conditions apply.Please do not share your Internet Banking details, such as user ID/password or your Credit/Debit Card number/CVV/OTPwith anyone, either over phone or through email.RBI never deals with individuals for Savings Account, Current Account, Credit Card, Debit Card, etc. Don't be victim to suchoffers coming to you on phone or email in the name of RBI.Do not click on Links from unknown/unsecure Sources that seek your confidential information.This email is confidential. It may also be legally privileged. If you are not the addressee, you may not copy, forward,disclose or use any part of it. Internet communications cannot be guaranteed to be timely, secure, error or virus-free.The sender does not accept liability for any errors or omissions. We maintain strict security standards and procedures toprevent unauthorised access to information about you. Know more >>
            Gmail - INR 120.00 was debited from your A/c no. XX9876. https://mail.google.com/mail/u/1/?ik=a070fc7d84&view=pt&search=al...
            2 of 223-10-2026, 00:03
        """.trimIndent()

        val parsed = AxisBankEmailParser.parse(subject, fullBody)
        assertNotNull(parsed)
        val tx = parsed!!.transaction
        assertEquals(120.0, tx.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals("DD STORE", tx.rawMerchant)
        assertEquals("666113532290", tx.upiRef)
        assertFalse(parsed.isCredit)
    }

    @Test
    fun testAxisBankCreditEmailWithDebitBoilerplateInFooter() {
        val subject = "INR 20.00 was credited to your A/c."
        val bodyWithDebitDisclaimer = """
            Dear Alex Morgan,
            Here's the summary of your transaction:
            Amount Credited:
            INR 20.00
            Account Number:
            XX9876
            Date & Time:
            21-10-26, 13:46:29 IST
            Transaction Info:
            UPI/P2A/218606473101/JORDAN LEE/HDFC/Sent

            Call us at: 18001035577 (Toll Free)
            Feel free to connect with us for any clarification.
            Always open to help you.
            Regards, Axis Bank Ltd.
            ****This is a system generated email and does not require signature. ****
            Please do not share your Internet Banking details, such as user ID/password or your Credit/Debit Card number/CVV/OTP with anyone, either over phone or through email.
            RBI never deals with individuals for Savings Account, Current Account, Credit Card, Debit Card, etc.
            If this transaction was not initiated by you and an amount was debited from your account, please click here to dispute.
        """.trimIndent()

        val parsed = AxisBankEmailParser.parse(subject, bodyWithDebitDisclaimer)
        assertNotNull("Should parse credit email", parsed)
        val tx = parsed!!.transaction
        assertEquals("Primary signal is credit, so type must be INCOME despite boilerplate debit wording", TransactionType.INCOME, tx.type)
        assertTrue(parsed.isCredit)
    }

    @Test
    fun testAxisBankConflictingSignalsSentToReviewQueue() {
        // Labelled field says Amount Debited, but subject says was credited
        val subject = "INR 20.00 was credited to your A/c."
        val body = """
            Dear Alex Morgan,
            Amount Debited:
            INR 20.00
            Account Number:
            XX9876
            Transaction Info: UPI/P2M/111222333444/MERCHANT
        """.trimIndent()

        val raw = RawMessage(
            source = MessageSource.EMAIL,
            sender = "alerts@axis.bank.in",
            body = body,
            subject = subject
        )
        val result = MessageExtractor.extract(raw)
        assertTrue("Conflicting debit/credit signals must be routed to Review/Unparsed, not guessed", result is ExtractionResult.Unparsed)
    }

    @Test
    fun testAxisBankCreditPdfExact() {
        val subject = "INR 66.67 was credited to your A/c."
        val body = """
            29-10-2026Dear Sahil Gupta,Here's the summary of your transaction:Amount Credited:INR 66.67Account Number:XX2003Date & Time:29-10-26, 23:04:25 ISTTransaction Info:UPI/P2A/630274894830/PRATOSH R/HDFC/UPI
            Gmail - INR 66.67 was credited to your A/c. https://mail.google.com/mail/u/1/?ik=a070fc7d84&view=pt&search=al...
            1 of 229-10-2026, 23:40

            Call us at:18001035577(Toll Free)18604195555(Charges Applicable)Feel free to connect with us for any clarification.Always open to help you.Regards,Axis Bank Ltd.Footerimg****This is a system generated email and does not require signature. ****E003598117_09_2024Reach us at: 
            CHAT WEB Support Mobile app INTERNET BANKING WHATSAPP BRANCH LOCATORCopyright Axis Bank Ltd. All rights reserved. Terms & Conditions apply.Please do not share your Internet Banking details, such as user ID/password or your Credit/Debit Card number/CVV/OTPwith anyone, either over phone or through email.RBI never deals with individuals for Savings Account, Current Account, Credit Card, Debit Card, etc. Don't be victim to suchoffers coming to you on phone or email in the name of RBI.Do not click on Links from unknown/unsecure Sources that seek your confidential information.This email is confidential. It may also be legally privileged. If you are not the addressee, you may not copy, forward,disclose or use any part of it. Internet communications cannot be guaranteed to be timely, secure, error or virus-free.The sender does not accept liability for any errors or omissions. We maintain strict security standards and procedures toprevent unauthorised access to information about you. Know more >>
            Gmail - INR 66.67 was credited to your A/c. https://mail.google.com/mail/u/1/?ik=a070fc7d84&view=pt&search=al...
            2 of 229-10-2026, 23:40
        """.trimIndent()

        val parsed = AxisBankEmailParser.parse(subject, body)
        assertNotNull("Should parse Axis credit email from PDF", parsed)
        val tx = parsed!!.transaction
        assertEquals(66.67, tx.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("PRATOSH R", tx.rawMerchant)
        assertEquals("630274894830", tx.upiRef)
        assertEquals("Axis Bank XX2003", tx.paymentMode)
        assertTrue(parsed.isCredit)

        val raw = RawMessage(
            source = MessageSource.EMAIL,
            sender = "Axis Bank Alerts <alerts@axis.bank.in>",
            body = body,
            subject = subject
        )
        val result = MessageExtractor.extract(raw)
        assertTrue("MessageExtractor must extract transaction", result is ExtractionResult.Transaction)
        val extracted = (result as ExtractionResult.Transaction).transaction
        assertEquals(Direction.CREDIT, extracted.direction)
        assertEquals(TransactionType.INCOME, extracted.type)
        assertEquals(6667L, extracted.amountPaise)
        assertEquals("630274894830", extracted.reference)
    }
}
