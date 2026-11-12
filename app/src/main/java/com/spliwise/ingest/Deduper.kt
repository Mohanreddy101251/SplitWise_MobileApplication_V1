package com.spliwise.ingest

import com.spliwise.core.Dates
import com.spliwise.data.Source
import com.spliwise.data.Txn
import kotlin.math.abs

/** The same spend can arrive as alert SMS, mini statement, PDF and a manual entry: keep one. */
object Deduper {
    private const val WINDOW = 10 * 60_000L

    fun findMatch(p: ParsedTxn, existing: List<Txn>): Txn? {
        if (p.refNo != null) existing.firstOrNull { it.refNo == p.refNo }?.let { return it }
        return existing.firstOrNull { e ->
            e.amount == p.amount && e.isCredit == p.isCredit &&
                (p.account == null || e.account == null || e.account == p.account) &&
                (p.bank == e.bank || e.bank == null) &&
                if (p.dateOnly || e.source == Source.PDF || e.source == Source.MINI) Dates.sameDay(e.ts, p.ts)
                else abs(e.ts - p.ts) <= WINDOW
        }
    }
}
