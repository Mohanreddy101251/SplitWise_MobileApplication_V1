package com.spliwise.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object Source {
    const val MANUAL = "manual"
    const val VOICE = "voice"
    const val SMS = "sms"
    const val MINI = "mini"
    const val PDF = "pdf"
}

/** One money movement. amount is paise; isCredit=true means money came in. */
@Entity(tableName = "txn", indices = [Index("ts"), Index("refNo"), Index("needsReview")])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val isCredit: Boolean = false,
    val category: String,
    val merchant: String = "",
    val note: String = "",
    val ts: Long,
    val source: String = Source.MANUAL,
    val bank: String? = null,
    val account: String? = null,
    val refNo: String? = null,
    val needsReview: Boolean = false,
    /** true = this movement was on a credit card (account = last 4 digits of the card). */
    @ColumnInfo(defaultValue = "0") val card: Boolean = false,
)

@Entity(tableName = "category")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val budget: Long = 0,
    val sort: Int = 0,
)

/** A statement row the user chose to ignore, so it isn't reported as missing again. */
@Entity(tableName = "ignored")
data class Ignored(@PrimaryKey val key: String)

/** Learned "this merchant is always X" rule. */
@Entity(tableName = "rule")
data class Rule(@PrimaryKey val merchantKey: String, val category: String)

@Entity(tableName = "person", indices = [Index(value = ["name"], unique = true)])
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val upi: String? = null,
)

/** payerId = 0 means I paid. */
@Entity(tableName = "split")
data class Split(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val total: Long,
    val payerId: Long = 0,
    val ts: Long,
)

/** One slot of a split. personId: 0 = me, null = unnamed ("Person 2"). */
@Entity(
    tableName = "share",
    primaryKeys = ["splitId", "idx"],
    foreignKeys = [ForeignKey(entity = Split::class, parentColumns = ["id"], childColumns = ["splitId"], onDelete = ForeignKey.CASCADE)],
)
data class Share(val splitId: Long, val idx: Int, val personId: Long?, val label: String, val amount: Long)

/** amount > 0: the person paid me; amount < 0: I paid them. */
@Entity(tableName = "settlement")
data class Settlement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val amount: Long,
    val ts: Long,
)
