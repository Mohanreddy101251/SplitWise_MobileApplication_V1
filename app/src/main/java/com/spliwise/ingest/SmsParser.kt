package com.spliwise.ingest

import com.spliwise.core.Dates
import com.spliwise.core.Money
import com.spliwise.data.Source
import java.time.LocalDate
import kotlin.text.RegexOption.IGNORE_CASE

/** Reads Indian bank alert SMS on the phone. Unknown formats return null, never a guess. */
object SmsParser {
    private val IGNORE = Regex(
        "\\botp\\b|one time password|verification code|will be (debited|credited)|has requested|requested money|" +
            "collect request|\\bis due\\b|due (date|on)|minimum amount|reminder|pre-?approved|loan offer|e-?mandate|" +
            "\\bfailed\\b|\\bdeclined\\b",
        IGNORE_CASE,
    )
    private val DEBIT = Regex("debited|\\bsent\\b|spent|withdrawn|\\bpaid\\b|purchase|\\bdr\\b|transferred to|deducted", IGNORE_CASE)
    private val CREDIT = Regex("credited|received|deposited|\\bcr\\b|refund", IGNORE_CASE)
    private const val NUM = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    private val AMT_VERB = Regex("(?:debited|credited|deducted)\\s+(?:by|for|with)?\\s*(?:rs\\.?|inr|₹)?\\s*$NUM", IGNORE_CASE)
    private val AMT = Regex("(?:rs\\.?|inr|₹)\\s*$NUM", IGNORE_CASE)
    private val ACCT = Regex(
        "(?:a/?c|acct|account|card)(?:\\s*(?:no\\.?|number|ending(?:\\s+with)?))?\\s*[:\\-]?\\s*[x*.]*\\s*(\\d{3,6})\\b",
        IGNORE_CASE,
    )
    private val REF = Regex(
        "(?:ref(?:erence)?\\.?\\s*(?:no\\.?|number|id)?|rrn|utr|upi(?:\\s*ref(?:\\s*no)?)?)\\s*[:.\\-/#]?\\s*(\\d{9,16})",
        IGNORE_CASE,
    )
    private const val END = "\\s*(?:\\bref\\b|\\bon\\b|\\bvia\\b|\\(|,|\\.\\s|\\.$|\\n|$)"
    private val DEBIT_M = listOf(
        Regex("trf to\\s+(.+?)\\s+ref", IGNORE_CASE),
        Regex("\\bto\\b\\s*:?\\s*(?:vpa\\s+)?([^\\n]+?)$END", IGNORE_CASE),
        Regex("\\bat\\b\\s+(.+?)$END", IGNORE_CASE),
        Regex("upi/(?:dr/|p2[am]/)?\\d+/([^/\\n]+)", IGNORE_CASE),
        Regex("info:?\\s*(.+?)(?:\\.\\s|$)", IGNORE_CASE),
    )
    private val CREDIT_M = listOf(
        Regex("(?:transfer from|received from|\\bfrom)\\s+(?:vpa\\s+)?(.+?)$END", IGNORE_CASE),
        Regex("\\bby\\s+(?:vpa\\s+)?(.+?)$END", IGNORE_CASE),
        Regex("upi/(?:cr/|p2[am]/)?\\d+/([^/\\n]+)", IGNORE_CASE),
    )
    private val BAD_START = Regex(
        "^(your|ur|a/?c|ac\\b|acct|account|card|block|report|dispute|call|sms|avoid|rs|inr|₹|\\d|the bank|hdfc bank a|" +
            "us\\b|you\\b|customer)",
        IGNORE_CASE,
    )
    private val MINI_MARK = Regex("mini ?st|last \\d+ (txn|transaction)|recent transactions", IGNORE_CASE)
    private val MINI_ROW = Regex(
        "(\\d{1,2}[\\-/ ]?(?:[A-Za-z]{3}|\\d{1,2})(?:[\\-/ ]?\\d{2,4}(?![\\d.,]))?)\\s*[:,\\-]?\\s*(?:rs\\.?|inr)?\\s*(?<![\\d.,])$NUM\\s*\\(?\\s*(dr|cr|debit|credit)\\b",
        IGNORE_CASE,
    )

    fun isMiniStatement(body: String) = MINI_MARK.containsMatchIn(body)

    fun parse(sender: String, body: String, ts: Long, allowBodyOnly: Boolean = false): ParsedTxn? {
        val bank = Banks.detect(sender, body, allowBodyOnly) ?: return null
        if (IGNORE.containsMatchIn(body) || isMiniStatement(body)) return null
        val d = DEBIT.find(body)
        val c = CREDIT.find(body)
        val isCredit = when {
            d == null && c == null -> return null
            d == null -> true
            c == null -> false
            else -> c.range.first < d.range.first
        }
        val amount = amount(body) ?: return null
        if (amount <= 0) return null
        val merchant = merchant(body, isCredit)
            ?: if (Regex("atm|cash wdl", IGNORE_CASE).containsMatchIn(body)) "ATM" else "Unknown"
        return ParsedTxn(
            amount = amount, isCredit = isCredit, merchant = merchant,
            account = ACCT.find(body)?.groupValues?.get(1)?.takeLast(4),
            refNo = REF.find(body)?.groupValues?.get(1),
            ts = ts, bank = bank.id, source = Source.SMS, card = isCreditCard(body),
        )
    }

    private val CARD = Regex("credit card|avl\\.? ?lmt|available limit|credit limit|avl limit", IGNORE_CASE)
    private val DEBIT_CARD = Regex("debit card", IGNORE_CASE)

    /** Credit-card SMS mention the card or its limit; debit-card SMS say "debit card". */
    fun isCreditCard(body: String) = CARD.containsMatchIn(body) && !DEBIT_CARD.containsMatchIn(body)

    /** Reply to a mini-statement request: one row per transaction (date-only). */
    fun parseMini(sender: String, body: String, allowBodyOnly: Boolean = false, today: LocalDate = LocalDate.now()): List<ParsedTxn> {
        val bank = Banks.detect(sender, body, allowBodyOnly) ?: return emptyList()
        if (!isMiniStatement(body)) return emptyList()
        return MINI_ROW.findAll(body).mapNotNull { m ->
            val ts = Dates.parseLoose(m.groupValues[1], today) ?: return@mapNotNull null
            val amt = Money.parse(m.groupValues[2]) ?: return@mapNotNull null
            ParsedTxn(amt, m.groupValues[3].startsWith("c", true), "Mini statement", null, null, ts, bank.id, Source.MINI, dateOnly = true)
        }.toList()
    }

    fun amount(body: String): Long? {
        AMT_VERB.find(body)?.let { return Money.parse(it.groupValues[1]) }
        for (m in AMT.findAll(body)) {
            val before = body.substring(maxOf(0, m.range.first - 25), m.range.first).lowercase()
            if ("bal" in before || "limit" in before || "avl" in before) continue
            return Money.parse(m.groupValues[1])
        }
        return null
    }

    fun merchant(body: String, isCredit: Boolean): String? {
        for (r in if (isCredit) CREDIT_M else DEBIT_M) {
            for (m in r.findAll(body)) {
                val c = clean(m.groupValues[1])
                if (c.length >= 2 && !BAD_START.containsMatchIn(c)) return c
            }
        }
        return null
    }

    private fun clean(s: String) = s.trim().trimEnd('.', ',', ':', ';', '-').replace(Regex("\\s+"), " ").take(40).trim()
}
