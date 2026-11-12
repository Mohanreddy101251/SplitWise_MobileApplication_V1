package com.spliwise.ingest

/**
 * Data-driven bank definitions (no per-bank classes needed). The request number/text are only
 * defaults: they are editable in Settings, because banks change them.
 */
data class Bank(
    val id: String,
    val name: String,
    val senderHints: List<String>,
    val bodyHints: List<String>,
    val requestNumber: String,
    val requestText: String,
    val requestNote: String,
)

data class ParsedTxn(
    val amount: Long,
    val isCredit: Boolean,
    val merchant: String,
    val account: String?,
    val refNo: String?,
    val ts: Long,
    val bank: String,
    val source: String,
    val dateOnly: Boolean = false,
    val card: Boolean = false,
)

object Banks {
    val all = listOf(
        Bank(
            id = "sbi", name = "SBI",
            senderHints = listOf("SBIUPI", "SBIINB", "SBIPSG", "CBSSBI", "ATMSBI", "SBIBNK", "SBYONO", "SBICRD", "SBISMS"),
            bodyHints = listOf("-sbi", "state bank"),
            requestNumber = "09223866666", requestText = "MSTMT",
            requestNote = "SBI Quick mini statement. One-time: SMS “REG <account no>” to 09223488888 from this number.",
        ),
        Bank(
            id = "hdfc", name = "HDFC Bank",
            senderHints = listOf("HDFCBK", "HDFCBN", "HDFCCC"),
            bodyHints = listOf("hdfc bank"),
            requestNumber = "7308080808", requestText = "TXN",
            requestNote = "HDFC SMS banking (last few txns). One-time: SMS “REGISTER <last 4 of Cust ID> <last 4 of A/c>” to 7308080808. Keyword from third-party guides; confirm on hdfcbank.com.",
        ),
        Bank(
            id = "canara", name = "Canara Bank",
            senderHints = listOf("CANBNK", "CANARA", "CNRBNK"),
            bodyHints = listOf("canara bank"),
            requestNumber = "5676728", requestText = "MINI <account no>",
            requestNote = "Canara SMS banking (last 5 txns). Replace <account no>. Sources disagree on this; the reliable route is a missed call to 09015734734. Confirm with Canara.",
        ),
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id }

    private val FULL_NAMES = mapOf("sbi" to "state bank of india", "hdfc" to "hdfc bank", "canara" to "canara bank")

    /** Bank whose full name appears most in a statement (narrations can mention other banks once or twice). */
    fun detectInText(text: String): String? {
        val l = text.lowercase()
        val counts = FULL_NAMES.mapValues { (_, n) -> Regex(Regex.escape(n)).findAll(l).count() }.filterValues { it > 0 }
        val best = counts.maxByOrNull { it.value } ?: return null
        return best.key.takeIf { counts.values.count { it == best.value } == 1 }
    }

    fun detect(sender: String, body: String, allowBodyOnly: Boolean): Bank? {
        val s = sender.uppercase()
        all.firstOrNull { b -> b.senderHints.any { s.contains(it) } }?.let { return it }
        if (!allowBodyOnly) return null
        val l = body.lowercase()
        return all.firstOrNull { b -> b.bodyHints.any { l.contains(it) } }
    }
}
