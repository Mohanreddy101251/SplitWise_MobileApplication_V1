package com.spliwise.auth

import android.content.Context
import com.spliwise.core.Crypto
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val phone: String, val name: String, val pinHash: String, val salt: String, val biometric: Boolean)

/** Local profiles: phone number + PIN (PBKDF2 hash) + optional fingerprint. No server, no OTP. */
class ProfileManager(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun all(): List<Profile> {
        val arr = JSONArray(prefs.getString("list", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Profile(it.getString("phone"), it.getString("name"), it.getString("hash"), it.getString("salt"), it.optBoolean("bio"))
        }
    }

    private fun save(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("phone", it.phone).put("name", it.name).put("hash", it.pinHash).put("salt", it.salt).put("bio", it.biometric))
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    /** Returns an error message, or null when the profile was created. */
    fun create(name: String, rawPhone: String, pin: String, biometric: Boolean): String? {
        val phone = normalizePhone(rawPhone) ?: return "Enter a valid 10-digit mobile number"
        if (name.isBlank()) return "Enter your name"
        if (!Regex("^\\d{4}$").matches(pin)) return "PIN must be 4 digits"
        if (all().any { it.phone == phone }) return "A profile with this number already exists"
        val salt = Crypto.random(16)
        save(all() + Profile(phone, name.trim(), Crypto.b64(Crypto.hashPin(pin, salt)), Crypto.b64(salt), biometric))
        return null
    }

    fun verify(p: Profile, pin: String): Boolean =
        Crypto.same(Crypto.hashPin(pin, Crypto.unb64(p.salt)), Crypto.unb64(p.pinHash))

    fun setBiometric(p: Profile, on: Boolean) = save(all().map { if (it.phone == p.phone) it.copy(biometric = on) else it })

    fun delete(p: Profile) {
        save(all().filter { it.phone != p.phone })
        prefs.edit().remove("key_${p.phone}").apply()
        if (prefs.getString("active", null) == p.phone) prefs.edit().remove("active").apply()
        ctx.deleteDatabase(dbName(p.phone))
        ctx.deleteSharedPreferences("bank_${Crypto.sha(p.phone)}")
    }

    fun setActive(phone: String) = prefs.edit().putString("active", phone).apply()
    fun active(): Profile? = prefs.getString("active", null)?.let { a -> all().firstOrNull { it.phone == a } }

    /** Random 32-byte DB key, stored wrapped by the Keystore key. */
    fun dbKey(phone: String): ByteArray {
        prefs.getString("key_$phone", null)?.let { return Crypto.decrypt(it) }
        val k = Crypto.random(32)
        prefs.edit().putString("key_$phone", Crypto.encrypt(k)).commit()
        return k
    }

    companion object {
        fun dbName(phone: String) = "spli_${Crypto.sha(phone)}.db"
        fun normalizePhone(raw: String): String? {
            var d = raw.filter(Char::isDigit)
            if (d.length == 12 && d.startsWith("91")) d = d.drop(2)
            if (d.length == 11 && d.startsWith("0")) d = d.drop(1)
            return d.takeIf { it.length == 10 && it[0] in '6'..'9' }
        }
        fun mask(phone: String) = "••••••" + phone.takeLast(4)
    }
}
