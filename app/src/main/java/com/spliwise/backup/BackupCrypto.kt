package com.spliwise.backup

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup file format "SWB1": magic(4) | salt(16) | iv(12) | AES-256-GCM ciphertext+tag.
 * The key comes from the user's backup password via PBKDF2 (200,000 rounds), so the file can be
 * opened after a reinstall on any phone, but only with that password. The password itself is never stored.
 */
object BackupCrypto {
    private val MAGIC = "SWB1".toByteArray()
    private const val ITERATIONS = 200_000
    const val MIN_PASSWORD = 6

    class NotABackup : Exception("This file is not a SpliWise backup")
    class WrongPassword : Exception("Wrong backup password")

    fun newSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password, salt, ITERATIONS, 256)).encoded

    fun seal(plain: ByteArray, key: ByteArray, salt: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + c.doFinal(plain)
    }

    fun isBackup(file: ByteArray) = file.size > 48 && file.copyOfRange(0, 4).contentEquals(MAGIC)

    /** Decrypts with the password typed by the user. Throws [NotABackup] or [WrongPassword]. */
    fun open(file: ByteArray, password: CharArray): ByteArray {
        if (!isBackup(file)) throw NotABackup()
        val salt = file.copyOfRange(4, 20)
        val iv = file.copyOfRange(20, 32)
        val key = deriveKey(password, salt)
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            return c.doFinal(file, 32, file.size - 32)
        } catch (e: AEADBadTagException) {
            throw WrongPassword()
        } finally {
            key.fill(0)
        }
    }
}
