package com.spliwise.split

import kotlin.math.abs
import kotlin.math.min

const val ME = 0L

/** Paise-exact split maths: every result always sums to the total. */
object SplitEngine {
    fun equal(total: Long, n: Int): List<Long> {
        require(n > 0) { "Need at least one person" }
        val base = total / n
        val rem = (total % n).toInt()
        return List(n) { base + if (it < rem) 1 else 0 }
    }

    /** Weighted split (shares or percentages) using the largest-remainder method. */
    fun weighted(total: Long, weights: List<Double>): List<Long> {
        require(weights.isNotEmpty() && weights.all { it >= 0 } && weights.sum() > 0) { "Weights must be positive" }
        val sum = weights.sum()
        val raw = weights.map { total * it / sum }
        val floor = raw.map { it.toLong() }.toMutableList()
        var left = total - floor.sum()
        raw.indices.sortedByDescending { raw[it] - floor[it] }.forEach { if (left > 0) { floor[it]++; left-- } }
        return floor
    }

    /** Returns an error message when exact amounts don't add up, else null. */
    fun checkExact(total: Long, parts: List<Long>): String? {
        val s = parts.sum()
        return when {
            parts.any { it < 0 } -> "Amounts can't be negative"
            s != total -> "Parts add up to ${com.spliwise.core.Money.format(s)}, " +
                "but the bill is ${com.spliwise.core.Money.format(total)}"
            else -> null
        }
    }

    fun checkPercent(pcts: List<Double>): String? =
        if (abs(pcts.sum() - 100.0) > 0.01) "Percentages add up to ${"%.2f".format(pcts.sum())}%, not 100%" else null
}

data class SplitRow(val payerId: Long, val shares: List<Pair<Long?, Long>>) // personId (null = unnamed) -> paise
data class Payment(val from: Long, val to: Long, val amount: Long)

object Ledger {
    /**
     * Net balance per person from MY point of view: positive = they owe me, negative = I owe them.
     * settlements: personId -> amount they paid me (negative if I paid them).
     */
    fun balances(splits: List<SplitRow>, settlements: List<Pair<Long, Long>>): Map<Long, Long> {
        val b = HashMap<Long, Long>()
        for (s in splits) {
            if (s.payerId == ME) {
                s.shares.forEach { (pid, amt) -> if (pid != null && pid != ME) b.merge(pid, amt, Long::plus) }
            } else {
                s.shares.forEach { (pid, amt) -> if (pid == ME) b.merge(s.payerId, -amt, Long::plus) }
            }
        }
        settlements.forEach { (pid, amt) -> b.merge(pid, -amt, Long::plus) }
        return b.filterValues { it != 0L }
    }

    /** Minimum-ish set of payments that settles everyone (greedy, like Splitwise "simplify debts"). */
    fun simplify(net: Map<Long, Long>): List<Payment> {
        val debtors = net.filter { it.value < 0 }.map { it.key to -it.value }.sortedByDescending { it.second }.toMutableList()
        val creditors = net.filter { it.value > 0 }.map { it.key to it.value }.sortedByDescending { it.second }.toMutableList()
        val out = mutableListOf<Payment>()
        var i = 0; var j = 0
        while (i < debtors.size && j < creditors.size) {
            val pay = min(debtors[i].second, creditors[j].second)
            out += Payment(debtors[i].first, creditors[j].first, pay)
            debtors[i] = debtors[i].first to debtors[i].second - pay
            creditors[j] = creditors[j].first to creditors[j].second - pay
            if (debtors[i].second == 0L) i++
            if (creditors[j].second == 0L) j++
        }
        return out
    }
}
