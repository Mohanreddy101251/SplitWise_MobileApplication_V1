package com.spliwise

import com.spliwise.core.Money
import com.spliwise.split.Ledger
import com.spliwise.split.ME
import com.spliwise.split.SplitEngine
import com.spliwise.split.SplitRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class SplitAndMoneyTest {
    @Test fun equalAlwaysSumsExactly() {
        val r = Random(7)
        repeat(5000) {
            val total = r.nextLong(1, 10_000_000)
            val n = r.nextInt(1, 25)
            val parts = SplitEngine.equal(total, n)
            assertEquals(total, parts.sum())
            assertEquals(true, parts.max() - parts.min() <= 1)
        }
    }

    @Test fun weightedAlwaysSumsExactly() {
        val r = Random(11)
        repeat(3000) {
            val total = r.nextLong(1, 5_000_000)
            val w = List(r.nextInt(1, 8)) { r.nextDouble(0.1, 5.0) }
            assertEquals(total, SplitEngine.weighted(total, w).sum())
        }
        assertEquals(listOf(5000L, 2500L, 2500L), SplitEngine.weighted(10000, listOf(2.0, 1.0, 1.0)))
        assertEquals(listOf(3334L, 3333L, 3333L), SplitEngine.weighted(10000, listOf(33.34, 33.33, 33.33)))
    }

    @Test fun validation() {
        assertNull(SplitEngine.checkExact(10000, listOf(5000, 5000)))
        assertNotNull(SplitEngine.checkExact(10000, listOf(5000, 4000)))
        assertNull(SplitEngine.checkPercent(listOf(50.0, 25.0, 25.0)))
        assertNotNull(SplitEngine.checkPercent(listOf(50.0, 25.0)))
    }

    @Test fun ledger() {
        // I paid 300 for me + Ravi(1) + Raju(2); Ravi paid 200 for me + Ravi; Raju paid me back 50.
        val splits = listOf(
            SplitRow(ME, listOf(ME to 10000L, 1L to 10000L, 2L to 10000L)),
            SplitRow(1L, listOf(ME to 10000L, 1L to 10000L)),
            SplitRow(ME, listOf(null to 5000L, ME to 5000L)), // unnamed slot ignored
        )
        val b = Ledger.balances(splits, listOf(2L to 5000L))
        assertEquals(mapOf(2L to 5000L), b) // Ravi: +100 - 100 = 0 (dropped), Raju: 100 - 50
        val pays = Ledger.simplify(mapOf(1L to -3000L, 2L to -2000L, 3L to 5000L))
        assertEquals(5000L, pays.sumOf { it.amount })
        assertEquals(2, pays.size)
    }

    @Test fun moneyFormat() {
        assertEquals("₹1,23,456.78", Money.format(12345678))
        assertEquals("₹999", Money.format(99900))
        assertEquals("₹1,000", Money.format(100000))
        assertEquals("-₹50.05", Money.format(-5005))
        assertEquals("₹1,00,00,000", Money.format(10_000_000_00))
        assertEquals(12345L, Money.parse("123.45"))
        assertEquals(123450L, Money.parse("1,234.5"))
        assertNull(Money.parse("abc"))
    }
}
