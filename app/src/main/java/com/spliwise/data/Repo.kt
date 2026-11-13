package com.spliwise.data

import androidx.room.withTransaction
import com.spliwise.classify.Categories
import com.spliwise.classify.Categorizer
import com.spliwise.ingest.Deduper
import com.spliwise.ingest.ParsedTxn
import com.spliwise.split.ME
import com.spliwise.voice.CommandParser

data class IngestResult(val added: Int, val merged: Int, val needReview: List<Txn>)
data class Part(val personId: Long?, val label: String, val amount: Long)

/** Single entry point to one profile's data. */
class Repo(val db: SpliDb) {
    private val t = db.txn()
    val categories = db.cat().all()
    val people = db.people().all()
    val splits = db.split().splits()
    val shares = db.split().shares()
    val settlements = db.split().settlements()
    val review = t.review()
    val reviewCount = t.reviewCount()

    fun range(from: Long, to: Long) = t.range(from, to)
    fun recent(n: Int = 60) = t.recent(n)

    /** Makes sure every built-in category exists (safe to call on every start). */
    suspend fun ensureCategories() {
        val have = db.cat().list().associateBy { it.id }
        Categories.defaults.forEachIndexed { i, c ->
            db.cat().upsert(Category(c.id, c.name, c.emoji, c.color, have[c.id]?.budget ?: 0, i)) // keeps budgets
        }
    }

    /** Everything this profile owns, for the encrypted backup file. */
    suspend fun snapshot(phone: String, name: String, banks: Map<String, com.spliwise.backup.BankPref>) = com.spliwise.backup.Snapshot(
        phone = phone, name = name, createdAt = System.currentTimeMillis(),
        txns = t.all(),
        budgets = db.cat().list().filter { it.budget > 0 }.associate { it.id to it.budget },
        rules = db.rule().all(), people = db.people().list(),
        splits = db.split().splitsOnce(), shares = db.split().sharesOnce(), settlements = db.split().settlementsOnce(),
        ignored = db.ignored().keys(), banks = banks,
    )

    /** Loads a backup into this (new, empty) profile in one transaction. */
    suspend fun restore(s: com.spliwise.backup.Snapshot) = db.withTransaction {
        ensureCategories()
        db.cat().list().forEach { c -> s.budgets[c.id]?.let { db.cat().upsert(c.copy(budget = it)) } }
        t.insertAll(s.txns)
        s.rules.forEach { db.rule().upsert(it) }
        db.people().insertAll(s.people)
        db.split().insertSplits(s.splits)
        db.split().insertSharesReplace(s.shares)
        db.split().insertSettlements(s.settlements)
        s.ignored.forEach { db.ignored().add(Ignored(it)) }
    }

    suspend fun lastTxnTs(): Long? = t.lastTs()

    suspend fun ignoredKeys(): Set<String> = db.ignored().keys().toSet()
    suspend fun ignore(key: String) = db.ignored().add(Ignored(key))

    suspend fun categorizer() =Categorizer(db.rule().all().associate { it.merchantKey to it.category })

    suspend fun add(amount: Long, category: String, note: String, ts: Long, source: String, merchant: String = ""): Long =
        t.insert(
            Txn(
                amount = amount, isCredit = category == Categories.INCOME, category = category,
                merchant = merchant, note = note, ts = ts, source = source,
            )
        )

    suspend fun update(x: Txn) = t.update(x)
    suspend fun delete(x: Txn) = t.delete(x)
    suspend fun get(id: Long) = t.get(id)
    suspend fun all() = t.all()

    /** Categorise + de-duplicate bank transactions, then store them. */
    suspend fun ingest(items: List<ParsedTxn>): IngestResult {
        if (items.isEmpty()) return IngestResult(0, 0, emptyList())
        val day = 86_400_000L
        val existing = t.rangeOnce(items.minOf { it.ts } - day, items.maxOf { it.ts } + day).toMutableList()
        val cz = categorizer()
        var added = 0; var merged = 0
        val review = mutableListOf<Txn>()
        for (p in items) {
            val match = Deduper.findMatch(p, existing)
            if (match != null) {
                if (match.bank == null) { // manual/voice entry: attach the bank details to it
                    val upd = match.copy(bank = p.bank, account = p.account, refNo = p.refNo ?: match.refNo,
                        merchant = match.merchant.ifBlank { p.merchant })
                    t.update(upd); existing[existing.indexOf(match)] = upd
                }
                merged++
                continue
            }
            // Money credited back to a credit card (bill paid / refund) only restores the card limit.
            val g = if (p.card && p.isCredit) Categorizer.Guess(Categories.CARDPAY, true) else cz.classify(p.merchant, p.isCredit)
            val x = Txn(
                amount = p.amount, isCredit = p.isCredit, category = g.category, merchant = p.merchant,
                ts = p.ts, source = p.source, bank = p.bank, account = p.account, refNo = p.refNo,
                needsReview = !g.confident, card = p.card,
            )
            val saved = x.copy(id = t.insert(x))
            existing += saved; added++
            if (saved.needsReview) review += saved
        }
        return IngestResult(added, merged, review)
    }

    /** User fixed a category: remember it for this merchant and fix the other pending ones too. */
    suspend fun setCategory(x: Txn, cat: String, learn: Boolean = true) {
        t.update(x.copy(category = cat, needsReview = false, isCredit = if (cat == Categories.INCOME) true else x.isCredit))
        if (learn && x.merchant.isNotBlank() && x.merchant != "Unknown") {
            db.rule().upsert(Rule(Categorizer.key(x.merchant), cat))
            t.recategorizeMerchant(x.merchant, cat)
        }
    }

    suspend fun setBudget(c: Category, paise: Long) = db.cat().upsert(c.copy(budget = paise))

    // ---------- people & splits ----------
    suspend fun addPerson(name: String, phone: String? = null, upi: String? = null): Long =
        db.people().insert(Person(name = name.trim(), phone = phone?.ifBlank { null }, upi = upi?.ifBlank { null }))

    suspend fun updatePerson(p: Person) = db.people().update(p)
    suspend fun deletePerson(p: Person) = db.people().delete(p)

    /** Names in a voice split that aren't saved people yet. */
    suspend fun missingPeople(names: List<String>): List<String> {
        val known = db.people().list().map { it.name.lowercase() }.toSet()
        return names.filter { it.lowercase() !in known }
    }

    suspend fun saveSplit(title: String, total: Long, parts: List<Part>, payerId: Long = ME, ts: Long = System.currentTimeMillis()): Long =
        db.split().create(
            Split(title = title, total = total, payerId = payerId, ts = ts),
            parts.map { Share(0, 0, it.personId, it.label, it.amount) },
        )

    suspend fun saveSplit(cmd: CommandParser.Split, createMissing: Boolean): Long {
        if (createMissing) missingPeople(cmd.names).forEach { addPerson(it) }
        val byName = db.people().list().associateBy { it.name.lowercase() }
        val labels = cmd.labels
        val parts = labels.mapIndexed { i, label ->
            val id = when {
                cmd.includeMe && i == 0 -> ME
                else -> byName[label.lowercase()]?.id
            }
            Part(id, label, cmd.shares[i])
        }
        return saveSplit(cmd.title, cmd.total, parts)
    }

    /**
     * The simple split: equal shares, I paid. labels: "Me" = me, "Person N" = unnamed, anything else = a person
     * (created if new). editId != null updates that split instead of adding one.
     */
    suspend fun saveSimpleSplit(editId: Long?, title: String, total: Long, labels: List<String>): Long {
        val amounts = com.spliwise.split.SplitEngine.equal(total, labels.size)
        val known = db.people().list().associateBy { it.name.lowercase() }.toMutableMap()
        val parts = labels.mapIndexed { i, raw ->
            val label = raw.trim()
            when {
                label.equals("me", true) -> Part(ME, "Me", amounts[i])
                label.isBlank() || Regex("^person \\d+$", RegexOption.IGNORE_CASE).matches(label) ->
                    Part(null, "Person ${i + 1}", amounts[i])
                else -> {
                    val id = known[label.lowercase()]?.id ?: addPerson(label).also { known[label.lowercase()] = Person(it, label) }
                    Part(id, label, amounts[i])
                }
            }
        }
        val shares = parts.map { Share(0, 0, it.personId, it.label, it.amount) }
        return if (editId == null) saveSplit(title, total, parts)
        else {
            val ts = db.split().ts(editId) ?: System.currentTimeMillis() // keep the original date
            db.split().replace(Split(id = editId, title = title, total = total, payerId = ME, ts = ts), shares)
            editId
        }
    }

    suspend fun deleteSplit(id: Long) = db.split().delete(id)
    suspend fun settle(personId: Long, amount: Long) =
        db.split().settle(Settlement(personId = personId, amount = amount, ts = System.currentTimeMillis()))
}
