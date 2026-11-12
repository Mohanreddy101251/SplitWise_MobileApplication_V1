package com.spliwise

import com.spliwise.classify.Categorizer
import com.spliwise.core.Dates
import com.spliwise.data.Source
import com.spliwise.data.Txn
import com.spliwise.ingest.Deduper
import com.spliwise.ingest.ParsedTxn
import com.spliwise.ingest.SmsParser
import com.spliwise.ingest.StatementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Message formats modelled on real Indian bank alerts (numbers and names are made up). */
class BankParsingTest {
    private val ts = 1_700_000_000_000L
    private fun p(sender: String, body: String) = SmsParser.parse(sender, body, ts)

    @Test fun hdfcUpiDebit() {
        val t = p("VM-HDFCBK", "Sent Rs.250.00\nFrom HDFC Bank A/C *4321\nTo KPN FRESH\nOn 12/11/26\nRef 431298765432\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808")!!
        assertEquals(25000L, t.amount); assertFalse(t.isCredit)
        assertEquals("KPN FRESH", t.merchant); assertEquals("4321", t.account); assertEquals("431298765432", t.refNo)
        assertEquals("hdfc", t.bank)
    }

    @Test fun hdfcCardSpend() {
        val t = p("AD-HDFCBK-S", "Spent Rs.1,500.00 On HDFC Bank Card 9876 At SHELL PETROL PUMP On 2026-11-12:10:22:33. Not You? To Block+Reissue Call 18002586161")!!
        assertEquals(150000L, t.amount); assertEquals("SHELL PETROL PUMP", t.merchant); assertEquals("9876", t.account)
    }

    @Test fun hdfcCredit() {
        val t = p("JD-HDFCBK", "Rs. 5000.00 credited to HDFC Bank A/c XX4321 on 12-11-26 by VPA ravi.kumar@okaxis (UPI Ref No 431212345678)")!!
        assertTrue(t.isCredit); assertEquals(500000L, t.amount); assertEquals("ravi.kumar@okaxis", t.merchant)
    }

    @Test fun sbiUpiDebit() {
        val t = p("JM-SBIUPI", "Dear UPI user A/C X1234 debited by 250.0 on date 12Nov26 trf to KPN FRESH Refno 431234567890. If not u? call 1800111109. -SBI")!!
        assertEquals(25000L, t.amount); assertEquals("KPN FRESH", t.merchant); assertEquals("1234", t.account); assertEquals("431234567890", t.refNo)
        assertEquals("sbi", t.bank)
    }

    @Test fun sbiCredit() {
        val t = p("BZ-SBIINB", "Dear SBI User, your A/c X1234-credited by Rs.500 on 12Nov26 transfer from RAVI KUMAR Ref No 431299990000 -SBI")!!
        assertTrue(t.isCredit); assertEquals(50000L, t.amount); assertEquals("RAVI KUMAR", t.merchant)
    }

    @Test fun canaraDebit() {
        val t = p("CP-CANBNK", "Rs.320.00 paid thru A/C XX5678 on 12-11-26 08:30:45 to HP PAY PETROL BUNK, UPI Ref 431255556666. If not done, SMS BLOCKUPI to 9901771222.-Canara Bank")!!
        assertEquals(32000L, t.amount); assertEquals("HP PAY PETROL BUNK", t.merchant); assertEquals("canara", t.bank)
    }

    @Test fun canaraLongFormat() {
        val t = p("VK-CANBNK", "An amount of INR 1,200.00 has been DEBITED to your account XXX5678 on 12/11/2026 towards UPI/DR/431277778888/SWIGGY/YESB. Total Avail.bal INR 10,250.00 - Canara Bank")!!
        assertEquals(120000L, t.amount); assertFalse(t.isCredit); assertEquals("SWIGGY", t.merchant); assertEquals("5678", t.account)
    }

    @Test fun ignoresNoise() {
        assertNull(p("VM-HDFCBK", "123456 is your OTP for txn of Rs.500 at AMAZON. Do not share."))
        assertNull(p("VM-HDFCBK", "Rs.500 will be debited from your a/c on 15-11 for NACH mandate"))
        assertNull(p("VM-HDFCBK", "RAVI has requested money of Rs.200 on Google Pay"))
        assertNull(p("AD-AMAZON", "Rs.500 debited for your Amazon order")) // not a bank sender
        assertNull(p("+919999999999", "Rs.500 debited from A/c X1234 -SBI")) // phone number sender, test mode off
        assertNotNull(SmsParser.parse("+919999999999", "Rs.500 debited from A/c X1234 trf to KPN -SBI", ts, allowBodyOnly = true))
    }

    @Test fun balanceIsNotTheAmount() {
        val t = p("VM-SBIUPI", "Avl Bal Rs 9,999.00. Your A/C X1234 debited by Rs 75.00 trf to CHAI POINT Refno 431200001111 -SBI")!!
        assertEquals(7500L, t.amount)
    }

    @Test fun miniStatement() {
        val rows = SmsParser.parseMini(
            "JM-SBIBNK",
            "Mini Statement for A/c XX1234: 10Nov 250.00 Dr, 11Nov 5000.00 Cr, 12Nov 120.50 Dr. Bal 10,000.00 -SBI",
            today = LocalDate.of(2026, 11, 12),
        )
        assertEquals(3, rows.size)
        assertEquals(listOf(25000L, 500000L, 12050L), rows.map { it.amount })
        assertEquals(listOf(false, true, false), rows.map { it.isCredit })
        assertTrue(rows.all { it.dateOnly })
    }

    @Test fun pdfStatementByBalance() {
        val text = """
            Statement of Account
            Opening Balance 10,000.00
            Date Narration Chq/Ref Value Dt Withdrawal Deposit Closing Balance
            10/11/26 UPI-KPN FRESH-kpnfresh@ybl-YESB0YBLUPI-431234567890-PAYMENT 10/11/26 250.00 9,750.00
            11/11/26 NEFT CR-SALARY ACME TECH PVT LTD 11/11/26 50,000.00 59,750.00
            12/11/26 UPI-RAVI KUMAR-ravi@okaxis-431299998888 12/11/26 1,000.00 58,750.00
            continuation text that belongs to the row above
        """.trimIndent()
        val rows = StatementParser.parse(text, "hdfc")
        assertEquals(3, rows.size)
        assertEquals(listOf(25000L, 5000000L, 100000L), rows.map { it.amount })
        assertEquals(listOf(false, true, false), rows.map { it.isCredit })
        assertEquals("KPN FRESH", rows[0].merchant)
        assertEquals("RAVI KUMAR", rows[2].merchant)
        assertEquals("431234567890", rows[0].refNo)
    }

    @Test fun pdfNewestFirstAndSbiNarration() {
        val text = """
            12 Nov 2026 12 Nov 2026 TO TRANSFER-UPI/DR/431211112222/DMART/YESB/dmart@ybl 500.00 4,500.00
            11 Nov 2026 11 Nov 2026 BY TRANSFER-UPI/CR/431233334444/RAJU/SBIN 1,000.00 5,000.00
        """.trimIndent()
        val rows = StatementParser.parse(text, "sbi")
        assertEquals(2, rows.size)
        assertTrue(rows[0].isCredit) // 11 Nov credit (sorted oldest first)
        assertFalse(rows[1].isCredit)
        assertEquals("DMART", rows[1].merchant)
    }

    @Test fun categorizer() {
        val c = Categorizer()
        assertEquals("grocery", c.classify("KPN FRESH", false).category)
        assertEquals("petrol", c.classify("HP PAY PETROL BUNK", false).category)
        assertEquals("petrol", c.classify("SHELL PETROL PUMP", false).category)
        assertEquals("food", c.classify("SWIGGY", false).category)
        assertEquals("travel", c.classify("UBER INDIA", false).category)
        assertEquals("invest", c.classify("ZERODHA BROKING", false).category)
        assertEquals("shopping", c.classify("AMAZON PAY INDIA", false).category)
        assertEquals("salon", c.classify("GREEN TRENDS SALON", false).category)
        val friend = c.classify("ravi.kumar@okaxis", false)
        assertEquals("friends", friend.category); assertTrue(friend.confident)
        val name = c.classify("RAMESH KUMAR", false)
        assertEquals("friends", name.category); assertFalse(name.confident) // could be a small shop: ask
        val unknown = c.classify("XYZ ENTERPRISES", false)
        assertEquals("unknown", unknown.category); assertFalse(unknown.confident)
        assertFalse(Categorizer.isPersonalVpa("q123456789@ybl"))
        // learned rules win
        val learned = Categorizer(mapOf(Categorizer.key("XYZ ENTERPRISES") to "gifts"))
        assertEquals("gifts", learned.classify("XYZ ENTERPRISES", false).category)
    }

    @Test fun dedupe() {
        val base = Txn(id = 1, amount = 25000, category = "grocery", ts = ts, source = Source.SMS, bank = "hdfc", account = "4321", refNo = "431298765432")
        val manual = Txn(id = 2, amount = 9900, category = "food", ts = ts, source = Source.MANUAL)
        val existing = listOf(base, manual)
        // same ref
        assertEquals(1L, Deduper.findMatch(ParsedTxn(25000, false, "KPN", "4321", "431298765432", ts + 3_600_000, "hdfc", Source.PDF, true), existing)?.id)
        // PDF row, same day + amount
        assertEquals(1L, Deduper.findMatch(ParsedTxn(25000, false, "KPN", null, null, Dates.noon(Dates.localDate(ts)), "hdfc", Source.PDF, true), existing)?.id)
        // SMS for something I already typed in by hand 2 minutes earlier
        assertEquals(2L, Deduper.findMatch(ParsedTxn(9900, false, "CAFE", "4321", "999", ts + 120_000, "hdfc", Source.SMS), existing)?.id)
        // different amount: new
        assertNull(Deduper.findMatch(ParsedTxn(25100, false, "KPN", "4321", null, ts, "hdfc", Source.SMS), existing))
        // same amount but 2 hours apart: new
        assertNull(Deduper.findMatch(ParsedTxn(25000, false, "KPN", "4321", null, ts + 7_200_000, "hdfc", Source.SMS), existing))
    }
}
