package com.spliwise.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    @Insert suspend fun insert(t: Txn): Long
    @Update suspend fun update(t: Txn)
    @Delete suspend fun delete(t: Txn)
    @Query("SELECT * FROM txn WHERE id = :id") suspend fun get(id: Long): Txn?
    @Query("SELECT * FROM txn WHERE ts BETWEEN :from AND :to ORDER BY ts DESC") fun range(from: Long, to: Long): Flow<List<Txn>>
    @Query("SELECT * FROM txn WHERE ts BETWEEN :from AND :to") suspend fun rangeOnce(from: Long, to: Long): List<Txn>
    @Query("SELECT * FROM txn ORDER BY ts DESC LIMIT :n") fun recent(n: Int): Flow<List<Txn>>
    @Query("SELECT * FROM txn WHERE needsReview = 1 ORDER BY ts DESC") fun review(): Flow<List<Txn>>
    @Query("SELECT COUNT(*) FROM txn WHERE needsReview = 1") fun reviewCount(): Flow<Int>
    @Query("UPDATE txn SET category = :cat, needsReview = 0 WHERE merchant = :merchant AND needsReview = 1")
    suspend fun recategorizeMerchant(merchant: String, cat: String)
    @Query("SELECT * FROM txn ORDER BY ts") suspend fun all(): List<Txn>
    @Query("SELECT MAX(ts) FROM txn") suspend fun lastTs(): Long?
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<Txn>)
}

@Dao
interface IgnoredDao {
    @Query("SELECT `key` FROM ignored") suspend fun keys(): List<String>
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun add(i: Ignored)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY sort") fun all(): Flow<List<Category>>
    @Query("SELECT * FROM category") suspend fun list(): List<Category>
    @Upsert suspend fun upsert(c: Category)
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun insertIgnore(list: List<Category>)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rule") suspend fun all(): List<Rule>
    @Upsert suspend fun upsert(r: Rule)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM person ORDER BY name COLLATE NOCASE") fun all(): Flow<List<Person>>
    @Query("SELECT * FROM person") suspend fun list(): List<Person>
    @Insert suspend fun insert(p: Person): Long
    @Update suspend fun update(p: Person)
    @Delete suspend fun delete(p: Person)
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<Person>)
}

@Dao
abstract class SplitDao {
    @Insert abstract suspend fun insert(s: Split): Long
    @Insert abstract suspend fun insertShares(s: List<Share>)

    @Transaction
    open suspend fun create(s: Split, shares: List<Share>): Long {
        val id = insert(s)
        insertShares(shares.mapIndexed { i, sh -> sh.copy(splitId = id, idx = i) })
        return id
    }

    @Update abstract suspend fun updateSplit(s: Split)
    @Query("SELECT ts FROM split WHERE id = :id") abstract suspend fun ts(id: Long): Long?
    @Query("DELETE FROM share WHERE splitId = :id") abstract suspend fun deleteShares(id: Long)

    /** Edit an existing split in place (same id, new amount/people). */
    @Transaction
    open suspend fun replace(s: Split, shares: List<Share>) {
        updateSplit(s)
        deleteShares(s.id)
        insertShares(shares.mapIndexed { i, sh -> sh.copy(splitId = s.id, idx = i) })
    }

    @Query("DELETE FROM split WHERE id = :id") abstract suspend fun delete(id: Long)
    @Query("SELECT * FROM split ORDER BY ts DESC") abstract fun splits(): Flow<List<Split>>
    @Query("SELECT * FROM share") abstract fun shares(): Flow<List<Share>>
    @Query("SELECT * FROM settlement ORDER BY ts DESC") abstract fun settlements(): Flow<List<Settlement>>
    @Insert abstract suspend fun settle(s: Settlement)

    // one-shot reads/writes used by backup & restore
    @Query("SELECT * FROM split") abstract suspend fun splitsOnce(): List<Split>
    @Query("SELECT * FROM share") abstract suspend fun sharesOnce(): List<Share>
    @Query("SELECT * FROM settlement") abstract suspend fun settlementsOnce(): List<Settlement>
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) abstract suspend fun insertSplits(list: List<Split>)
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) abstract suspend fun insertSharesReplace(list: List<Share>)
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) abstract suspend fun insertSettlements(list: List<Settlement>)
}
