package com.spliwise.backup

import com.spliwise.data.Person
import com.spliwise.data.Rule
import com.spliwise.data.Settlement
import com.spliwise.data.Share
import com.spliwise.data.Split
import com.spliwise.data.Txn
import org.json.JSONArray
import org.json.JSONObject

/** Bank import switches saved with the backup, so a restored profile keeps reading the same banks. */
data class BankPref(val on: Boolean, val number: String, val text: String)

/** Everything one profile owns. Amounts stay in paise. */
data class Snapshot(
    val phone: String,
    val name: String,
    val createdAt: Long,
    val txns: List<Txn>,
    val budgets: Map<String, Long>,
    val rules: List<Rule>,
    val people: List<Person>,
    val splits: List<Split>,
    val shares: List<Share>,
    val settlements: List<Settlement>,
    val ignored: List<String>,
    val banks: Map<String, BankPref>,
) {
    val lastTxnTs: Long? get() = txns.maxOfOrNull { it.ts }
}

object BackupJson {
    private const val VERSION = 1

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)
    private fun Any?.orNull(): Any = this ?: JSONObject.NULL
    private inline fun <T> JSONArray.map(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }

    fun encode(s: Snapshot): String = JSONObject().apply {
        put("app", "SpliWise"); put("version", VERSION)
        put("phone", s.phone); put("name", s.name); put("createdAt", s.createdAt)
        put("txns", JSONArray(s.txns.map { t ->
            JSONObject().put("id", t.id).put("amount", t.amount).put("isCredit", t.isCredit).put("category", t.category)
                .put("merchant", t.merchant).put("note", t.note).put("ts", t.ts).put("source", t.source)
                .put("bank", t.bank.orNull()).put("account", t.account.orNull()).put("refNo", t.refNo.orNull())
                .put("needsReview", t.needsReview).put("card", t.card)
        }))
        put("budgets", JSONObject(s.budgets as Map<*, *>))
        put("rules", JSONArray(s.rules.map { JSONObject().put("k", it.merchantKey).put("c", it.category) }))
        put("people", JSONArray(s.people.map { JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone.orNull()).put("upi", it.upi.orNull()) }))
        put("splits", JSONArray(s.splits.map { JSONObject().put("id", it.id).put("title", it.title).put("total", it.total).put("payerId", it.payerId).put("ts", it.ts) }))
        put("shares", JSONArray(s.shares.map {
            JSONObject().put("splitId", it.splitId).put("idx", it.idx).put("personId", it.personId.orNull()).put("label", it.label).put("amount", it.amount)
        }))
        put("settlements", JSONArray(s.settlements.map { JSONObject().put("id", it.id).put("personId", it.personId).put("amount", it.amount).put("ts", it.ts) }))
        put("ignored", JSONArray(s.ignored))
        put("banks", JSONObject().apply { s.banks.forEach { (id, b) -> put(id, JSONObject().put("on", b.on).put("number", b.number).put("text", b.text)) } })
    }.toString()

    fun decode(json: String): Snapshot {
        val o = JSONObject(json)
        require(o.optString("app") == "SpliWise") { "Not a SpliWise backup" }
        val budgets = o.optJSONObject("budgets")?.let { b -> b.keys().asSequence().associateWith { b.getLong(it) } }.orEmpty()
        val ignored = o.optJSONArray("ignored")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val banks = o.optJSONObject("banks")?.let { b ->
            b.keys().asSequence().associateWith { k -> b.getJSONObject(k).let { BankPref(it.optBoolean("on"), it.optString("number"), it.optString("text")) } }
        }.orEmpty()
        return Snapshot(
            phone = o.getString("phone"), name = o.getString("name"), createdAt = o.optLong("createdAt"),
            txns = o.getJSONArray("txns").map {
                Txn(it.getLong("id"), it.getLong("amount"), it.getBoolean("isCredit"), it.getString("category"),
                    it.optString("merchant"), it.optString("note"), it.getLong("ts"), it.optString("source"),
                    it.str("bank"), it.str("account"), it.str("refNo"), it.optBoolean("needsReview"), it.optBoolean("card"))
            },
            budgets = budgets,
            rules = o.getJSONArray("rules").map { Rule(it.getString("k"), it.getString("c")) },
            people = o.getJSONArray("people").map { Person(it.getLong("id"), it.getString("name"), it.str("phone"), it.str("upi")) },
            splits = o.getJSONArray("splits").map { Split(it.getLong("id"), it.getString("title"), it.getLong("total"), it.getLong("payerId"), it.getLong("ts")) },
            shares = o.getJSONArray("shares").map {
                Share(it.getLong("splitId"), it.getInt("idx"), if (it.isNull("personId")) null else it.getLong("personId"), it.getString("label"), it.getLong("amount"))
            },
            settlements = o.getJSONArray("settlements").map { Settlement(it.getLong("id"), it.getLong("personId"), it.getLong("amount"), it.getLong("ts")) },
            ignored = ignored, banks = banks,
        )
    }
}
