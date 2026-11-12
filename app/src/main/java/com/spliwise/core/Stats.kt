package com.spliwise.core

import com.spliwise.classify.Categories
import com.spliwise.data.Txn
import java.time.YearMonth

/** Pure analytics over a list of transactions. */
object Stats {
    /** Card bill payments / refunds credited to a card just move money: not income, not spend. */
    private fun counts(t: Txn) = t.category !in Categories.TRANSFERS
    fun spent(txns: List<Txn>) = txns.filter { !it.isCredit && counts(it) }.sumOf { it.amount }
    fun income(txns: List<Txn>) = txns.filter { it.isCredit && counts(it) }.sumOf { it.amount }

    /** Debits per category, biggest first. */
    fun byCategory(txns: List<Txn>): List<Pair<String, Long>> =
        txns.filter { !it.isCredit && counts(it) }.groupBy { it.category }
            .map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

    /** Debits for each day of the month (index 0 = 1st). */
    fun byDay(txns: List<Txn>, ym: YearMonth): LongArray {
        val out = LongArray(ym.lengthOfMonth())
        txns.filter { !it.isCredit && counts(it) }.forEach {
            val d = Dates.localDate(it.ts)
            if (YearMonth.from(d) == ym) out[d.dayOfMonth - 1] += it.amount
        }
        return out
    }

    /** Debits per month for [months] months ending at [end]. */
    fun byMonth(txns: List<Txn>, end: YearMonth, months: Int): List<Pair<YearMonth, Long>> {
        val map = txns.filter { !it.isCredit && counts(it) }.groupBy { YearMonth.from(Dates.localDate(it.ts)) }
            .mapValues { (_, v) -> v.sumOf { it.amount } }
        return (months - 1 downTo 0).map { end.minusMonths(it.toLong()) }.map { it to (map[it] ?: 0L) }
    }

    fun topMerchants(txns: List<Txn>, n: Int = 5): List<Pair<String, Long>> =
        txns.filter { !it.isCredit && it.merchant.isNotBlank() && it.merchant != "Unknown" }
            .groupBy { it.merchant.uppercase() }.map { (k, v) -> k to v.sumOf { it.amount } }
            .sortedByDescending { it.second }.take(n)

    fun percent(part: Long, whole: Long): Int = if (whole <= 0) 0 else ((part * 1000 / whole + 5) / 10).toInt()
}
