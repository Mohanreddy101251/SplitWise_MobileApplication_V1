package com.spliwise.classify

/**
 * Rule-based, fully on-device categoriser. Order:
 * 1. what the user taught it (learned merchant rules), 2. bundled merchant dictionary,
 * 3. "looks like a person" heuristic -> Friend transfers, 4. unknown -> ask the user.
 */
class Categorizer(private val learned: Map<String, String> = emptyMap()) {
    data class Guess(val category: String, val confident: Boolean)

    fun classify(merchant: String, isCredit: Boolean): Guess {
        val k = key(merchant)
        if (k.isEmpty() || k == "unknown") return Guess(if (isCredit) Categories.INCOME else Categories.UNKNOWN, false)
        learned[k]?.let { return Guess(it, true) }
        learned.entries.firstOrNull { (mk, _) -> mk.length >= 4 && (k.startsWith(mk) || mk.startsWith(k)) }
            ?.let { return Guess(it.value, true) }
        dictionary(k)?.let { return Guess(it, true) }
        val person = looksLikePerson(merchant)
        return when {
            person -> Guess(Categories.FRIENDS, isCredit || isPersonalVpa(merchant))
            isCredit -> Guess(Categories.INCOME, false)
            else -> Guess(Categories.UNKNOWN, false)
        }
    }

    /** Up to [n] category ids to offer as quick buttons for an unknown transaction. */
    fun suggestions(merchant: String, isCredit: Boolean, n: Int = 3): List<String> {
        val first = classify(merchant, isCredit).category
        val pool = if (isCredit) listOf(Categories.INCOME, Categories.FRIENDS, "invest")
        else listOf(Categories.FRIENDS, "food", "grocery", "shopping", "travel")
        return (listOf(first) + pool).filter { it != Categories.UNKNOWN }.distinct().take(n)
    }

    companion object {
        fun key(merchant: String): String =
            merchant.lowercase().replace(Regex("[^a-z0-9@]+"), " ").replace(Regex("\\s+"), " ").trim()

        private val words = Categories.merchantDict

        fun dictionary(k: String): String? {
            val padded = " ${k.replace('@', ' ')} "
            for ((kw, cat) in words) {
                if (padded.contains(" $kw ")) return cat
                if (kw.length >= 5 && k.contains(kw)) return cat
            }
            return null
        }

        private val BUSINESS = Regex(
            "\\b(store|stores|mart|shop|shoppe|enterprises?|traders|trading|foods?|ltd|limited|pvt|private|" +
                "services?|petroleum|fuels?|medicals?|pharma|hotel|restaurant|cafe|bakery|agency|agencies|centre|" +
                "center|hospital|clinic|motors|super|bazaa?r|india|technologies|tech|solutions|industries|" +
                "associates|company|corporation|llp|paytmqr|bharatpe|merchant|payments?|retail|fresh|kitchen|" +
                "sweets|stall|collection|online)\\b"
        )
        private val PERSONAL_PSP = setOf(
            "okaxis", "oksbi", "okhdfcbank", "okicici", "ybl", "ibl", "axl", "paytm", "upi", "apl", "yapl",
            "sbi", "hdfcbank", "icici", "axisbank", "kotak", "cnrb", "fbl", "idfcbank", "ptyes", "ptsbi", "pthdfc",
        )

        fun isPersonalVpa(m: String): Boolean {
            if ('@' !in m) return false
            val handle = m.substringBefore('@').lowercase()
            val psp = m.substringAfter('@').lowercase().trim()
            if (Regex("^\\d{10}$").matches(handle)) return true
            if (Regex("^(q|paytmqr|bharatpe|gpay-)\\w*\\d").containsMatchIn(handle)) return false
            return psp in PERSONAL_PSP && !BUSINESS.containsMatchIn(handle)
        }

        fun looksLikePerson(m: String): Boolean {
            if ('@' in m) return isPersonalVpa(m)
            val lower = m.lowercase().trim()
            if (BUSINESS.containsMatchIn(lower)) return false
            val w = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
            return w.size in 2..4 && w.all { it.length > 1 && it.all(Char::isLetter) || it.length == 1 && it[0].isLetter() }
        }
    }
}
