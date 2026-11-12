package com.spliwise.voice

import com.spliwise.classify.Categories
import com.spliwise.core.Money
import com.spliwise.split.SplitEngine
import java.math.BigDecimal

/**
 * Turns a spoken/typed sentence into a command. Pure rules, no ML, nothing leaves the phone.
 *   "grocery 100 rupees"                -> Expense(grocery, 100)
 *   "split 100 to 3"                    -> Split 33.34 / 33.33 / 33.33
 *   "split 300 with mohan ravi raju"    -> Split 100 each
 *   "mohan ravi raju split them 100 each" -> Split total 300
 */
object CommandParser {
    sealed interface Result
    data class Expense(val category: String, val amount: Long, val note: String) : Result
    data class Split(
        val title: String, val total: Long, val names: List<String>,
        val includeMe: Boolean, val unnamed: Int, val shares: List<Long>,
    ) : Result {
        /** Slot labels in the same order as [shares]: Me first, then names, then unnamed people. */
        val labels: List<String>
            get() {
                val head = (if (includeMe) listOf("Me") else emptyList()) + names
                return head + List(unnamed) { "Person ${head.size + it + 1}" }
            }
    }
    data class Error(val message: String) : Result

    fun parse(input: String): Result {
        val t = normalize(input)
        if (t.isBlank()) return Error("I didn't catch that. Try “grocery 100” or “split 300 with Ravi and Raju”.")
        return if (SPLIT_WORD.containsMatchIn(t)) parseSplit(t) else parseExpense(t)
    }

    // ---------- expense ----------
    private fun parseExpense(t: String): Result {
        val m = AMOUNT.find(t) ?: return Error("I couldn't hear an amount. Say e.g. “petrol 500”.")
        val amount = Money.parse(m.value) ?: 0
        if (amount <= 0) return Error("The amount must be more than zero.")
        val (cat, kw) = findCategory(t)
            ?: return Error("Which category is ${Money.format(amount)} for? Say e.g. “food ${m.value}”.")
        val catName = Categories.defaults.first { it.id == cat }.name.lowercase()
        val rest = t.removeRange(m.range).replace(Regex("\\b${Regex.escape(kw)}\\b"), " ")
            .split(' ').filter { it.isNotBlank() && it !in FILLER && it.any(Char::isLetter) }
        val generic = kw == cat || kw.startsWith(catName.take(4)) || catName.startsWith(kw)
        val words = (if (generic) emptyList() else listOf(kw)) + rest
        return Expense(cat, amount, words.joinToString(" ").replaceFirstChar { it.uppercase() })
    }

    // ---------- split ----------
    private fun parseSplit(t: String): Result {
        val eachM = EACH.find(t)
        val countM = COUNT.find(t)
        val countGroup = countM?.let { it.groups[1] ?: it.groups[2] }
        val count = countGroup?.value?.toIntOrNull()
        val usedStarts = setOfNotNull(eachM?.groups?.get(1)?.range?.first, countGroup?.range?.first)
        val total = AMOUNT.findAll(t).firstOrNull { it.range.first !in usedStarts }?.let { Money.parse(it.value) }
        val each = eachM?.let { Money.parse(it.groupValues[1]) }
        var me = ME_WORD.containsMatchIn(t)
        val title = findCategory(t)?.second?.replaceFirstChar { it.uppercase() } ?: "Split"

        val names = t.split(' ')
            .filter { w -> w.length >= 2 && w.all(Char::isLetter) && w !in STOP && w !in KEYWORD_WORDS && !isNumWord(w) }
            .distinct().map { it.replaceFirstChar(Char::uppercase) }

        if (count != null && names.isNotEmpty()) {
            val people = names.size + if (me) 1 else 0
            if (count != people) {
                if (!me && count == names.size + 1) me = true
                else return Error(
                    "You said $count people but named ${names.size}${if (me) " + you" else ""} " +
                        "(${names.joinToString()}). Say the names again or the right count."
                )
            }
        }
        val n = count ?: (names.size + if (me) 1 else 0)
        if (n <= 0) return Error("Split between how many? Say “split 300 to 3” or name the people.")
        if (n == 1 && names.isEmpty()) return Error("A split needs at least 2 people.")
        if (n > 50) return Error("That's $n people — did you mean an amount? Say “split 300 to 3”.")
        val unnamed = n - names.size - if (me) 1 else 0

        val shares: List<Long>
        val tot: Long
        if (each != null) {
            tot = each * n
            if (total != null && total != tot)
                return Error("${Money.format(each)} × $n = ${Money.format(tot)}, but you said ${Money.format(total)}.")
            shares = List(n) { each }
        } else {
            if (total == null) return Error("How much should I split? Say e.g. “split 300 with Ravi and Raju”.")
            tot = total
            shares = SplitEngine.equal(total, n)
        }
        if (tot <= 0) return Error("The amount must be more than zero.")
        return Split(title, tot, names, me, unnamed, shares)
    }

    // ---------- helpers ----------
    private val SPLIT_WORD = Regex("\\b(split|divide|each|share it|per head)\\b")
    private val AMOUNT = Regex("\\d+(?:\\.\\d{1,2})?")
    private val EACH = Regex("(\\d+(?:\\.\\d{1,2})?)\\s*(?:each|per head|per person|apiece)")
    private val COUNT = Regex(
        "\\b(?:to|between|among|amongst|into|in|by|across)\\s+(\\d+)\\b(?!\\s*(?:each|per|\\.\\d))" +
            "|\\b(\\d+)\\s*(?:people|persons|members|ways|parts|friends|guys|of us)\\b"
    )
    private val ME_WORD = Regex("\\b(me|myself|i|including me)\\b")

    private val KEYWORDS: List<Triple<Regex, String, String>> = Categories.voiceKeywords.entries
        .sortedByDescending { it.key.length }
        .map { Triple(Regex("\\b${Regex.escape(it.key)}\\b"), it.key, it.value) }
    private val KEYWORD_WORDS = Categories.voiceKeywords.keys.flatMap { it.split(' ') }.toSet()

    /** Returns (categoryId, keywordHeard) for the longest keyword found. */
    private fun findCategory(t: String): Pair<String, String>? =
        KEYWORDS.firstOrNull { it.first.containsMatchIn(t) }?.let { it.third to it.second }

    private val FILLER = setOf(
        "for", "on", "to", "the", "a", "an", "add", "added", "spent", "paid", "pay", "of", "in", "at", "i", "my",
        "today", "expense", "log", "please", "and", "with", "from", "rupees", "rupee", "rs", "is", "was", "it",
    )
    private val STOP = FILLER + setOf(
        "split", "divide", "share", "between", "among", "amongst", "into", "by", "across", "them", "each", "people",
        "persons", "person", "members", "ways", "way", "friends", "guys", "us", "me", "myself", "equally", "equal",
        "parts", "part", "bill", "total", "per", "head", "apiece", "we", "all", "our", "plus", "along", "both",
        "everyone", "amount", "so", "okay", "ok", "then", "also", "yesterday", "are", "he", "she", "they", "him",
        "her", "including", "lakh", "thousand", "hundred", "inr",
    )

    private val UNITS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    ).withIndex().associate { it.value to it.index.toLong() }
    private val TENS = mapOf(
        "twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fifty" to 50L,
        "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L,
    )
    private val SCALES = mapOf("thousand" to 1_000L, "lakh" to 100_000L, "lakhs" to 100_000L)
    private fun isNumWord(w: String) = w in UNITS || w in TENS || w in SCALES || w == "hundred"

    fun normalize(s: String): String {
        var t = s.lowercase().replace("₹", " rs ")
        t = t.replace(Regex("(\\d),(\\d)"), "$1$2")
        t = t.replace(Regex("(?<=\\d)(rs|rupees?|/-)"), " ")
        t = t.replace(Regex("[^a-z0-9.@ ]"), " ").replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
        t = t.replace(Regex("\\b(rupees?|rs|inr|bucks)\\b"), " ")
        t = wordsToDigits(t.replace(Regex("\\s+"), " ").trim())
        t = Regex("(\\d+(?:\\.\\d+)?)\\s*(k|thousand|lakhs?|hundred)\\b").replace(t) { m ->
            val mult = when (m.groupValues[2]) { "k", "thousand" -> 1000; "hundred" -> 100; else -> 100_000 }
            BigDecimal(m.groupValues[1]).multiply(BigDecimal(mult)).stripTrailingZeros().toPlainString()
        }
        return t.replace(Regex("\\s+"), " ").trim()
    }

    /** "two thousand five hundred" -> 2500, Indian-style "two fifty" -> 250, "one fifty five" -> 155. */
    fun wordsToDigits(t: String): String {
        if (t.isEmpty()) return t
        val tk = t.split(' ')
        val out = ArrayList<String>()
        var i = 0
        while (i < tk.size) {
            // "15 thousand": leave the scale word for the digit-suffix rule in normalize()
            val afterDigits = out.lastOrNull()?.all(Char::isDigit) == true && (tk[i] in SCALES || tk[i] == "hundred")
            if (!isNumWord(tk[i]) || afterDigits) { out += tk[i]; i++; continue }
            var total = 0L; var cur = 0L; var prevUnit = false
            loop@ while (i < tk.size) {
                val w = tk[i]
                when {
                    w == "and" && i + 1 < tk.size && isNumWord(tk[i + 1]) && (cur > 0 || total > 0) -> {}
                    w in UNITS -> {
                        val v = UNITS.getValue(w)
                        cur = if (prevUnit && cur in 1..9 && v >= 10) cur * 100 + v else cur + v
                        prevUnit = v in 1..9 && cur < 10
                    }
                    w in TENS -> {
                        val v = TENS.getValue(w)
                        cur = if (prevUnit && cur in 1..9) cur * 100 + v else cur + v
                        prevUnit = false
                    }
                    w == "hundred" -> { cur = maxOf(cur, 1) * 100; prevUnit = false }
                    w in SCALES -> { total += maxOf(cur, 1) * SCALES.getValue(w); cur = 0; prevUnit = false }
                    else -> break@loop
                }
                i++
            }
            out += (total + cur).toString()
        }
        return out.joinToString(" ")
    }
}
