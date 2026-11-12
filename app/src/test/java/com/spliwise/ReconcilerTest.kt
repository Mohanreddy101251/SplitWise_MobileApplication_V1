package com.spliwise

import com.spliwise.classify.Categories
import com.spliwise.core.Dates
import com.spliwise.core.Stats
import com.spliwise.data.Source
import com.spliwise.data.Txn
import com.spliwise.ingest.Banks
import com.spliwise.ingest.Rag
import com.spliwise.ingest.Reconciler
import com.spliwise.ingest.SmsParser
import com.spliwise.ingest.StatementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReconcilerTest {
    private val stmt = """
        HDFC BANK LIMITED
        Statement of account  HDFC Bank
        Account No : XXXXXXXX4321
        Opening Balance 10,000.00
        01/11/26 UPI-KPN FRESH-kpnfresh@ybl-431200000001 01/11/26 250.00 9,750.00
        02/11/26 UPI-SWIGGY-swiggy@ybl-431200000002 02/11/26 300.00 9,450.00
        03/11/26 NEFT CR-SALARY ACME 03/11/26 50,000.00 59,450.00
        04/11/26 UPI-DMART-dmart@ybl-431200000004 04/11/26 1,200.00 58,250.00
        05/11/26 UPI-RAVI-ravi@okaxis-431200000005 05/11/26 80.00 58,170.00
    """.trimIndent()

    private fun day(d: Int) = Dates.noon(LocalDate.of(2026, 11, d))
    private fun app(d: Int, rupees: Long, credit: Boolean = false, ref: String? = null, src: String = Source.SMS, bank: String? = "hdfc") =
        Txn(id = (d * 1000 + rupees), amount = rupees * 100, isCredit = credit, category = "food", ts = day(d) + 3_600_000,
            source = src, bank = bank, account = if (bank != null) "4321" else null, refNo = ref)

    @Test fun readsStatementBankAccountRows() {
        val info = Reconciler.read(stmt, null)
        assertEquals("hdfc", info.bankId)
        assertEquals("4321", info.account)
        assertFalse(info.isCard)
        assertEquals(5, info.rows.size)
        assertEquals(LocalDate.of(2026, 11, 1), info.firstDay)
    }

    @Test fun reportsOnlyMissing() {
        val info = Reconciler.read(stmt, null)
        val records = listOf(
            app(1, 250, ref = "431200000001"),            // matched by reference
            app(2, 300),                                   // matched by amount + day
            app(3, 50000, credit = true),                  // salary matched
            app(4, 1200, src = Source.MANUAL, bank = null) // typed by hand, no bank: still counts
        )
        val r = Reconciler.compare(info, records, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), emptySet())
        assertEquals(5, r.inStatement.size)
        assertEquals(1, r.missing.size)
        assertEquals(8000L, r.missing.single().amount)
        assertEquals(8000L, r.gap)
        assertEquals(Rag.AMBER, r.rag)
        assertEquals(3, r.appTxns.size) // only the HDFC ones count as "in SpliWise for this bank"
    }

    @Test fun oneRecordMatchesOnlyOneRow() {
        val two = "01/11/26 UPI-TEA 100.00 900.00\n01/11/26 UPI-TEA 100.00 800.00"
        val info = Reconciler.read("HDFC Bank\nOpening Balance 1,000.00\n$two", null)
        val r = Reconciler.compare(info, listOf(app(1, 100)), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), emptySet())
        assertEquals(1, r.missing.size)
    }

    @Test fun rangeAndIgnore() {
        val info = Reconciler.read(stmt, null)
        val all = Reconciler.compare(info, emptyList(), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), emptySet())
        assertEquals(3, all.missing.size) // 2nd..4th only
        assertEquals(Rag.RED, all.rag)
        val ignore = setOf(Reconciler.key(all.missing.first { it.isCredit }))
        val r = Reconciler.compare(info, emptyList(), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4), ignore)
        assertEquals(2, r.missing.size)
        assertEquals(1, r.ignoredCount)
    }

    @Test fun ragThresholds() {
        assertEquals(Rag.GREEN, Reconciler.rag(0))
        assertEquals(Rag.AMBER, Reconciler.rag(1))
        assertEquals(Rag.AMBER, Reconciler.rag(100_000))
        assertEquals(Rag.RED, Reconciler.rag(100_001))
    }

    @Test fun creditCardStatement() {
        val cc = """
            HDFC Bank Credit Card Statement
            Card No: 4893 XXXX XXXX 1234
            02/11/2026 AMAZON PAY INDIA 1,299.00
            05/11/2026 PAYMENT RECEIVED - THANK YOU 5,000.00 Cr
            06/11/2026 REFUND MYNTRA 499.00 Cr
        """.trimIndent()
        val info = Reconciler.read(cc, null)
        assertTrue(info.isCard)
        assertEquals("1234", info.account)
        assertEquals(listOf(false, true, true), info.rows.map { it.isCredit })
        assertTrue(info.rows.all { it.card })
    }

    @Test fun creditCardSms() {
        val pay = SmsParser.parse("VM-HDFCBK", "Payment of Rs.5,000.00 has been credited to your HDFC Bank Credit Card XX1234 on 05-11-26", 0L)!!
        assertTrue(pay.card); assertTrue(pay.isCredit); assertEquals("1234", pay.account)
        val spend = SmsParser.parse("VM-HDFCBK", "Rs.1,299.00 spent on HDFC Bank Card XX1234 at AMAZON on 2026-11-02. Avl Lmt: Rs.95,000", 0L)!!
        assertTrue(spend.card); assertFalse(spend.isCredit)
        val debit = SmsParser.parse("VM-HDFCBK", "Rs.500 spent on HDFC Bank Debit Card XX9999 at DMART on 2026-11-02", 0L)!!
        assertFalse(debit.card)
    }

    @Test fun cardTransfersNotCountedAsIncomeOrSpend() {
        val list = listOf(
            Txn(amount = 500000, isCredit = true, category = Categories.CARDPAY, ts = day(5)),
            Txn(amount = 100000, isCredit = true, category = Categories.INCOME, ts = day(5)),
            Txn(amount = 20000, category = "food", ts = day(5)),
            Txn(amount = 500000, category = Categories.CARDPAY, ts = day(5)),
        )
        assertEquals(100000L, Stats.income(list))
        assertEquals(20000L, Stats.spent(list))
        assertTrue(Stats.byCategory(list).none { it.first == Categories.CARDPAY })
    }

    @Test fun bankDetection() {
        assertEquals("sbi", Banks.detectInText("STATE BANK OF INDIA ... state bank of india ... HDFC BANK ref"))
        assertNull(Banks.detectInText("some random pdf"))
        assertEquals("canara", Banks.detectInText("Canara Bank statement"))
    }

    @Test fun homeCategory() {
        assertEquals(Categories.HOME, com.spliwise.classify.Categorizer().classify("PEPPERFRY", false).category)
        assertEquals(Categories.CARDPAY, com.spliwise.classify.Categorizer().classify("CRED CLUB", false).category)
        assertEquals(Categories.HOME, (com.spliwise.voice.CommandParser.parse("home 500") as com.spliwise.voice.CommandParser.Expense).category)
    }
}
