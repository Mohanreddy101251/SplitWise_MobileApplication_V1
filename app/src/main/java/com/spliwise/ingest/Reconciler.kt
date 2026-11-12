package com.spliwise.ingest

import com.spliwise.core.Dates
import com.spliwise.data.Source
import com.spliwise.data.Txn
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class Rag { GREEN, AMBER, RED }

/** What one uploaded statement contains (read on the phone; nothing is stored). */
data class StatementInfo(
    val bankId: String?,
    val account: String?,
    val isCard: Boolean,
    val rows: List<ParsedTxn>,
) {
    val firstDay: LocalDate? get() = rows.minOfOrNull { it.ts }?.let(Dates::localDate)
    val lastDay: LocalDate? get() = rows.maxOfOrNull { it.ts }?.let(Dates::localDate)
}

/** Gap between a statement and SpliWise's records, for the chosen date range. Lists only what is missing. */
data class GapReport(
    val info: StatementInfo,
    val inStatement: List<ParsedTxn>,
    val appTxns: List<Txn>,
    val missing: List<ParsedTxn>,
    val ignoredCount: Int,
) {
    val stmtOut get() = inStatement.filter { !it.isCredit }.sumOf { it.amount }
    val stmtIn get() = inStatement.filter { it.isCredit }.sumOf { it.amount }
    val appOut get() = appTxns.filter { !it.isCredit }.sumOf { it.amount }
    val appIn get() = appTxns.filter { it.isCredit }.sumOf { it.amount }
    val missingOut get() = missing.filter { !it.isCredit }.sumOf { it.amount }
    val missingIn get() = missing.filter { it.isCredit }.sumOf { it.amount }
    /** Money not recorded in SpliWise (debits + credits). Drives the RED/AMBER/GREEN colour. */
    val gap get() = missingOut + missingIn
    val rag get() = Reconciler.rag(gap)
    val bySource: Map<String, Int> get() = appTxns.groupingBy { it.source }.eachCount()
}

object Reconciler {
    /** GREEN: nothing missing · AMBER: up to ₹1,000 missing · RED: more than ₹1,000 missing. */
    const val AMBER_MAX = 100_000L

    fun rag(gapPaise: Long) = when {
        gapPaise <= 0L -> Rag.GREEN
        gapPaise <= AMBER_MAX -> Rag.AMBER
        else -> Rag.RED
    }

    private val ACCOUNT = Regex(
        "(?:account\\s*(?:no|number)|a/c\\s*(?:no|number)?|card\\s*(?:no|number))\\.?\\s*[:\\-]?\\s*([x*\\d][x*\\d -]{3,24})",
        RegexOption.IGNORE_CASE,
    )
    private val CARD_STATEMENT = Regex("credit card", RegexOption.IGNORE_CASE)

    /** Reads statement text: which bank, which account/card, and every row. [bank] overrides auto-detection. */
    fun read(text: String, bank: String?): StatementInfo {
        val bankId = bank ?: Banks.detectInText(text)
        val isCard = CARD_STATEMENT.containsMatchIn(text)
        val account = ACCOUNT.find(text)?.groupValues?.get(1)?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 }
        val rows = StatementParser.parse(text, bankId ?: "unknown", isCard).map { it.copy(account = account, card = isCard) }
        return StatementInfo(bankId, account, isCard, rows)
    }

    /** Stable id of a statement row, used for "Ignore". */
    fun key(r: ParsedTxn) =
        "${r.bank}|${r.account ?: ""}|${Dates.localDate(r.ts)}|${r.amount}|${if (r.isCredit) "C" else "D"}|${r.refNo ?: ""}"

    /**
     * Matches each statement row in [from]..[to] to at most one SpliWise record of the same bank
     * (or a manual/voice entry with no bank): same reference no., else same amount + direction within ±1 day.
     */
    fun compare(info: StatementInfo, app: List<Txn>, from: LocalDate, to: LocalDate, ignored: Set<String>): GapReport {
        val rows = info.rows.filter { Dates.localDate(it.ts) in from..to }
        fun sameSource(t: Txn) = t.bank == info.bankId &&
            (info.account == null || t.account == null || t.account == info.account)
        val inRangeApp = app.filter { sameSource(it) && Dates.localDate(it.ts) in from..to }
        val pool = app.filter {
            (sameSource(it) || it.bank == null) && Dates.localDate(it.ts) in from.minusDays(1)..to.plusDays(1)
        }.toMutableList()

        val missing = mutableListOf<ParsedTxn>()
        var ignoredCount = 0
        for (r in rows) {
            val day = Dates.localDate(r.ts)
            val match = r.refNo?.let { ref -> pool.firstOrNull { it.refNo == ref } }
                ?: pool.filter { it.amount == r.amount && it.isCredit == r.isCredit }
                    .map { it to abs(ChronoUnit.DAYS.between(day, Dates.localDate(it.ts))) }
                    .filter { it.second <= 1 }.minByOrNull { it.second }?.first
            when {
                match != null -> pool.remove(match)
                key(r) in ignored -> ignoredCount++
                else -> missing += r
            }
        }
        return GapReport(info, rows, inRangeApp, missing, ignoredCount)
    }

    fun sourceLabel(s: String) = when (s) {
        Source.SMS -> "SMS"; Source.MANUAL -> "Manual"; Source.VOICE -> "Voice"; Source.PDF -> "Statement"; Source.MINI -> "Mini-stmt"
        else -> s
    }
}
