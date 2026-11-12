package com.spliwise.ingest

import com.spliwise.core.Dates
import com.spliwise.core.Money
import com.spliwise.data.Source

/**
 * Bank-agnostic statement reader working on the text extracted from a PDF.
 * Each row = a line starting with a date and ending with amounts; the LAST amount is the balance.
 * Debit vs credit is decided by how the running balance moves, so column layout doesn't matter.
 */
object StatementParser {
    private val DATE_START = Regex("^\\s*(\\d{1,2}[/\\-. ](?:\\d{1,2}|[A-Za-z]{3})[/\\-. ]\\d{2,4})\\b(.*)$")
    private val MONEY = Regex("(?<![\\d.,])(\\d{1,3}(?:,\\d{2,3})+\\.\\d{2}|\\d+\\.\\d{2})(?!\\d)")
    private val OPENING = Regex("opening\\s+balance[^\\d]{0,40}([\\d,]+\\.\\d{2})", RegexOption.IGNORE_CASE)
    private val CR_WORD = Regex("\\b(cr|credit|deposit|by transfer|neft cr|imps cr|upi/cr|salary|interest)\\b", RegexOption.IGNORE_CASE)
    private val REF = Regex("\\b(\\d{10,16})\\b")
    private val SKIP_TOKEN = Regex(
        "^(upi|dr|cr|p2a|p2m|neft|imps|rtgs|pos|ecom|to transfer|by transfer|transfer|trf|payment|pay|to|by|inb|" +
            "mob|ib|[a-z]{4}0[a-z0-9]{6,}|yesb|utib|hdfc|sbin|icic|cnrb)$",
        RegexOption.IGNORE_CASE,
    )

    private val CARD_CR = Regex("\\b(payment received|payment - thank you|thank you|refund|reversal|cashback|credit)\\b", RegexOption.IGNORE_CASE)
    private val SUFFIX_CR = Regex("^\\s*(cr|credit)\\b", RegexOption.IGNORE_CASE)

    /** tail = text after the last amount on the row (where card statements put "Cr"). */
    private class Row(val ts: Long, val desc: StringBuilder, var amounts: List<Long>, var tail: String = "")

    /** card = credit-card statement: one amount per row, credits marked "Cr" or described as payment/refund. */
    fun parse(text: String, bankId: String, card: Boolean = false): List<ParsedTxn> {
        val rows = mutableListOf<Row>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val m = DATE_START.find(line)
            val ts = m?.let { Dates.parseLoose(it.groupValues[1]) }
            if (m != null && ts != null) {
                var rest = m.groupValues[2]
                DATE_START.find(rest)?.let { v -> if (Dates.parseLoose(v.groupValues[1]) != null) rest = v.groupValues[2] } // value date
                val amts = MONEY.findAll(rest).toList()
                val desc = if (amts.isEmpty()) rest else rest.substring(0, amts.first().range.first)
                rows += Row(ts, StringBuilder(desc.trim()), amts.mapNotNull { Money.parse(it.value) },
                    amts.lastOrNull()?.let { rest.substring(it.range.last + 1) } ?: "")
                continue
            }
            val last = rows.lastOrNull() ?: continue
            val amts = MONEY.findAll(line).toList()
            if (last.amounts.isEmpty() && amts.isNotEmpty()) {
                last.desc.append(' ').append(line.substring(0, amts.first().range.first).trim())
                last.amounts = amts.mapNotNull { Money.parse(it.value) }
                last.tail = line.substring(amts.last().range.last + 1)
            } else if (amts.isEmpty() && last.desc.length < 160) {
                last.desc.append(' ').append(line)
            }
        }
        var data = rows.filter { it.amounts.isNotEmpty() }
        if (card) return data.mapNotNull { r ->
            val desc = r.desc.toString()
            val amt = r.amounts.last()
            val isCredit = SUFFIX_CR.containsMatchIn(r.tail) || CARD_CR.containsMatchIn(desc)
            if (amt <= 0) null
            else ParsedTxn(amt, isCredit, merchantFrom(desc), null, REF.find(desc)?.value, r.ts, bankId, Source.PDF, dateOnly = true, card = true)
        }
        if (data.size > 1 && data.first().ts > data.last().ts) data = data.reversed() // newest-first statements

        var prevBal = OPENING.find(text)?.let { Money.parse(it.groupValues[1]) }
        return data.mapNotNull { r ->
            val a = r.amounts
            val desc = r.desc.toString()
            val (amt, isCredit, bal) = when {
                a.size >= 3 -> {
                    val (w, d, b) = a.takeLast(3)
                    when {
                        w > 0 && d == 0L -> Triple(w, false, b)
                        d > 0 && w == 0L -> Triple(d, true, b)
                        else -> Triple(a[a.size - 2], direction(prevBal, a[a.size - 2], b, desc), b)
                    }
                }
                a.size == 2 -> Triple(a[0], direction(prevBal, a[0], a[1], desc), a[1])
                else -> Triple(a[0], CR_WORD.containsMatchIn(desc), null)
            }
            if (bal != null) prevBal = bal
            if (amt <= 0) null
            else ParsedTxn(amt, isCredit, merchantFrom(desc), null, REF.find(desc)?.value, r.ts, bankId, Source.PDF, dateOnly = true)
        }
    }

    private fun direction(prev: Long?, amt: Long, bal: Long, desc: String): Boolean = when {
        prev != null && prev + amt == bal -> true
        prev != null && prev - amt == bal -> false
        else -> CR_WORD.containsMatchIn(desc)
    }

    fun merchantFrom(desc: String): String {
        val tokens = desc.split('/', '-', '|').map { it.trim() }
        val pick = tokens.firstOrNull { t ->
            t.length >= 3 && t.any(Char::isLetter) && '@' !in t && !SKIP_TOKEN.matches(t) && !t.all { it.isDigit() || it == ' ' }
        }
        return (pick ?: desc).replace(Regex("\\s+"), " ").trim().take(40).ifEmpty { "Unknown" }
    }
}
