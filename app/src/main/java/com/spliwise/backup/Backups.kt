package com.spliwise.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.spliwise.core.Crypto
import com.spliwise.data.Repo
import com.spliwise.ingest.BankSettings
import java.time.LocalDate

/**
 * Per-profile backup settings. The backup password is never stored: only a random salt and the
 * derived key, itself encrypted by the Android Keystore, so daily backups can run unattended.
 */
class BackupSettings(ctx: Context, phone: String) {
    private val p = ctx.getSharedPreferences("backup_${Crypto.sha(phone)}", Context.MODE_PRIVATE)

    val isOn get() = p.contains("salt") && p.contains("key")
    var lastBackup: Long
        get() = p.getLong("last", 0L)
        set(v) = p.edit().putLong("last", v).apply()
    var lastResult: String
        get() = p.getString("result", "") ?: ""
        set(v) = p.edit().putString("result", v).apply()

    /** Call off the main thread (key derivation takes a moment). */
    fun setPassword(password: CharArray) {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password, salt)
        p.edit().putString("salt", Crypto.b64(salt)).putString("key", Crypto.encrypt(key)).commit()
        key.fill(0); password.fill(' ')
    }

    fun turnOff() = p.edit().remove("salt").remove("key").apply()

    internal fun keyAndSalt(): Pair<ByteArray, ByteArray>? = runCatching {
        Crypto.decrypt(p.getString("key", null)!!) to Crypto.unb64(p.getString("salt", null)!!)
    }.getOrNull()
}

object Backups {
    const val FOLDER = "SpliWise"
    private const val KEEP = 7
    private const val DAY = 24 * 3_600_000L

    fun due(b: BackupSettings) = b.isOn && System.currentTimeMillis() - b.lastBackup > 20 * 3_600_000L

    private fun prefix(phone: String) = "SpliWise-backup-${phone.takeLast(4)}-"

    /**
     * Writes today's encrypted backup to Downloads/SpliWise (survives uninstall) and keeps the last 7.
     * Returns a short status line.
     */
    suspend fun run(ctx: Context, repo: Repo, phone: String, name: String, bank: BankSettings, b: BackupSettings): String {
        if (Build.VERSION.SDK_INT < 29) return "Backup needs Android 10 or newer".also { b.lastResult = it }
        val (key, salt) = b.keyAndSalt() ?: return "Backup password missing — set it again in Settings".also { b.lastResult = it }
        val json = BackupJson.encode(repo.snapshot(phone, name, bank.export())).toByteArray()
        val sealed = BackupCrypto.seal(json, key, salt)
        key.fill(0)
        val file = "${prefix(phone)}${LocalDate.now()}.swb"
        write(ctx, file, sealed)
        prune(ctx, phone)
        b.lastBackup = System.currentTimeMillis()
        return "Saved ${file} (${sealed.size / 1024 + 1} KB) in Downloads/$FOLDER".also { b.lastResult = it }
    }

    private fun write(ctx: Context, name: String, bytes: ByteArray) {
        val r = ctx.contentResolver
        val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val existing = r.query(
            coll, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, "Download/$FOLDER/"), null,
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(coll, c.getLong(0)) else null }
        val uri = existing ?: r.insert(coll, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/$FOLDER")
        }) ?: error("Couldn't create the backup file")
        r.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
    }

    /** Keeps the newest [KEEP] backups this install created; older ones are deleted. */
    private fun prune(ctx: Context, phone: String) {
        val r = ctx.contentResolver
        val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val old = mutableListOf<Uri>()
        r.query(
            coll, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf("${prefix(phone)}%", "Download/$FOLDER/"), "${MediaStore.MediaColumns.DISPLAY_NAME} DESC",
        )?.use { c -> var i = 0; while (c.moveToNext()) { if (i++ >= KEEP) old += ContentUris.withAppendedId(coll, c.getLong(0)) } }
        old.forEach { runCatching { r.delete(it, null, null) } }
    }

    fun readUri(ctx: Context, uri: Uri): ByteArray =
        ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    /** Opens a backup file with the password the user typed. Throws BackupCrypto.WrongPassword / NotABackup. */
    fun decode(file: ByteArray, password: CharArray): Snapshot =
        BackupJson.decode(String(BackupCrypto.open(file, password)))

    /**
     * After a restore: resume SMS import from one day before the last saved transaction,
     * so everything that happened while the app was gone is picked up (duplicates are merged).
     */
    fun resumePoint(s: Snapshot): Long = (s.lastTxnTs ?: System.currentTimeMillis()) - DAY
}
