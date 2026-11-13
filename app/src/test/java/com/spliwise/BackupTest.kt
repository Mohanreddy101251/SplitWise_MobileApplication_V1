package com.spliwise

import com.spliwise.backup.BackupCrypto
import com.spliwise.backup.BackupJson
import com.spliwise.backup.BankPref
import com.spliwise.backup.Snapshot
import com.spliwise.data.Person
import com.spliwise.data.Rule
import com.spliwise.data.Settlement
import com.spliwise.data.Share
import com.spliwise.data.Source
import com.spliwise.data.Split
import com.spliwise.data.Txn
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val snap = Snapshot(
        phone = "9876543210", name = "Arjun", createdAt = 1_700_000_000_000L,
        txns = listOf(
            Txn(id = 1, amount = 25000, category = "grocery", merchant = "KPN FRESH", ts = 1_700_000_000_000L, source = Source.SMS,
                bank = "hdfc", account = "4321", refNo = "431298765432"),
            Txn(id = 2, amount = 500000, isCredit = true, category = "cardpay", ts = 1_700_100_000_000L, source = Source.SMS,
                bank = "hdfc", account = "1234", card = true),
            Txn(id = 3, amount = 4000, category = "food", note = "Tea \"special\"", ts = 1_700_200_000_000L, source = Source.VOICE),
        ),
        budgets = mapOf("food" to 800000L),
        rules = listOf(Rule("sri lakshmi agencies", "grocery")),
        people = listOf(Person(1, "Ravi", "9000000001", "ravi@okaxis"), Person(2, "Raju")),
        splits = listOf(Split(1, "Dinner", 90000, 0, 1_700_000_000_000L)),
        shares = listOf(Share(1, 0, 0, "Me", 30000), Share(1, 1, 1, "Ravi", 30000), Share(1, 2, null, "Person 3", 30000)),
        settlements = listOf(Settlement(1, 1, 10000, 1_700_300_000_000L)),
        ignored = listOf("hdfc|4321|2026-11-01|8000|D|"),
        banks = mapOf("hdfc" to BankPref(true, "7308080808", "TXN")),
    )

    @Test fun jsonRoundTripKeepsEverything() {
        val back = BackupJson.decode(BackupJson.encode(snap))
        assertEquals(snap, back)
        assertEquals(1_700_200_000_000L, back.lastTxnTs)
    }

    @Test fun encryptedRoundTrip() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey("myBackup#2026".toCharArray(), salt)
        val plain = BackupJson.encode(snap).toByteArray()
        val file = BackupCrypto.seal(plain, key, salt)
        assertTrue(BackupCrypto.isBackup(file))
        assertFalse(String(file).contains("KPN FRESH")) // nothing readable in the file
        assertArrayEquals(plain, BackupCrypto.open(file, "myBackup#2026".toCharArray()))
    }

    @Test(expected = BackupCrypto.WrongPassword::class)
    fun wrongPasswordIsRejected() {
        val salt = BackupCrypto.newSalt()
        val file = BackupCrypto.seal("hello".toByteArray(), BackupCrypto.deriveKey("right-one".toCharArray(), salt), salt)
        BackupCrypto.open(file, "wrong-one".toCharArray())
    }

    @Test(expected = BackupCrypto.NotABackup::class)
    fun randomFileIsRejected() {
        BackupCrypto.open(ByteArray(100) { it.toByte() }, "whatever".toCharArray())
    }

    @Test fun eachBackupUsesFreshIv() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey("abcdef".toCharArray(), salt)
        val a = BackupCrypto.seal("same".toByteArray(), key, salt)
        val b = BackupCrypto.seal("same".toByteArray(), key, salt)
        assertFalse(a.contentEquals(b))
    }
}
