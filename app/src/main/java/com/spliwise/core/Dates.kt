package com.spliwise.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun localDate(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    fun dayStart(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun startOfDay(ts: Long): Long = dayStart(localDate(ts))
    fun sameDay(a: Long, b: Long) = localDate(a) == localDate(b)
    fun noon(d: LocalDate): Long = dayStart(d) + 12 * 3_600_000L

    fun monthRange(ym: YearMonth): Pair<Long, Long> =
        dayStart(ym.atDay(1)) to dayStart(ym.plusMonths(1).atDay(1)) - 1

    private fun fmt(p: String, year: Int? = null): DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive().appendPattern(p)
        .apply { if (year != null) parseDefaulting(ChronoField.YEAR, year.toLong()) }
        .toFormatter(Locale.ENGLISH)

    private val FULL = listOf(
        "d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "d/M/yy", "d-M-yy", "d.M.yy",
        "d-MMM-yyyy", "d-MMM-yy", "d MMM yyyy", "d MMM yy", "ddMMMyyyy", "ddMMMyy", "d/MMM/yyyy", "yyyy-MM-dd",
    ).map { fmt(it) }

    private val NO_YEAR = listOf("d/M", "d-M", "ddMMM", "d-MMM", "d MMM")

    /** Parses the many date styles banks use; returns noon local time of that day. */
    fun parseLoose(raw: String, today: LocalDate = LocalDate.now()): Long? {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        for (f in FULL) runCatching { return noon(LocalDate.parse(s, f)) }
        for (p in NO_YEAR) runCatching {
            var d = LocalDate.parse(s, fmt(p, today.year))
            if (d.isAfter(today.plusDays(1))) d = d.minusYears(1)
            return noon(d)
        }
        return null
    }
}
