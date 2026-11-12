package com.spliwise.ingest

import android.content.Context
import com.spliwise.core.Crypto

/** Per-profile bank import settings. No passwords are stored anywhere in the app. */
class BankSettings(ctx: Context, phone: String) {
    private val p = ctx.getSharedPreferences("bank_${Crypto.sha(phone)}", Context.MODE_PRIVATE)

    init {
        // Older versions saved statement passwords / Gmail state: wipe them for good.
        val old = p.all.keys.filter { it.endsWith("_pw") || it.endsWith("_mail") || it == "gmail" || "_ms_" in it }
        if (old.isNotEmpty()) p.edit().apply { old.forEach { remove(it) } }.commit()
    }

    fun enabled(b: Bank) = p.getBoolean("${b.id}_on", false)
    fun setEnabled(b: Bank, on: Boolean) = p.edit().putBoolean("${b.id}_on", on).apply()
    fun requestNumber(b: Bank) = p.getString("${b.id}_num", null) ?: b.requestNumber
    fun requestText(b: Bank) = p.getString("${b.id}_txt", null) ?: b.requestText
    fun setRequest(b: Bank, number: String, text: String) =
        p.edit().putString("${b.id}_num", number.trim()).putString("${b.id}_txt", text.trim()).apply()

    var allowBodyOnly: Boolean
        get() = p.getBoolean("body_only", false)
        set(v) = p.edit().putBoolean("body_only", v).apply()
    var lastRun: String
        get() = p.getString("last_run", "Not run yet") ?: ""
        set(v) = p.edit().putString("last_run", v).apply()

    /** Last time the hourly SMS sync read the inbox. */
    var lastSmsScan: Long
        get() = p.getLong("sms_scan", 0L)
        set(v) = p.edit().putLong("sms_scan", v).apply()

    val enabledBanks get() = Banks.all.filter { enabled(it) }
}
