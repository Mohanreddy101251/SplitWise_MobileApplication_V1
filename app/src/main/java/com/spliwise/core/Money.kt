package com.spliwise.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** All money is stored as paise (Long) so sums are always exact. */
object Money {
    fun format(paise: Long, symbol: Boolean = true, decimals: Boolean = true): String {
        val p = abs(paise)
        val digits = (p / 100).toString()
        val grouped = if (digits.length <= 3) digits else {
            var rest = digits.dropLast(3)
            val parts = ArrayDeque<String>()
            while (rest.length > 2) { parts.addFirst(rest.takeLast(2)); rest = rest.dropLast(2) }
            if (rest.isNotEmpty()) parts.addFirst(rest)
            parts.joinToString(",") + "," + digits.takeLast(3)
        }
        val frac = p % 100
        val body = if (frac == 0L || !decimals) grouped else "$grouped.${frac.toString().padStart(2, '0')}"
        return (if (paise < 0) "-" else "") + (if (symbol) "₹" else "") + body
    }

    /** "1,234.5" -> 123450. Returns null when the text is not a number. */
    fun parse(text: String): Long? {
        val d = text.replace(",", "").trim().toBigDecimalOrNull() ?: return null
        return d.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    fun fromRupees(r: BigDecimal): Long = r.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}
