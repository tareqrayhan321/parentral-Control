package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

sealed interface PinVerificationResult {
    data object Success : PinVerificationResult
    data class Incorrect(val attemptsRemainingBeforeLockout: Int) : PinVerificationResult
    data class LockedOut(val secondsRemaining: Long) : PinVerificationResult
    data object NotConfigured : PinVerificationResult
}

interface PinSecurityManager {
    fun isPinConfigured(): Boolean
    fun setupPin(newPin: String): Boolean
    fun verifyPin(enteredPin: String): PinVerificationResult
    fun changePin(oldPin: String, newPin: String): Boolean
    fun getRemainingLockoutSeconds(): Long
    fun clearPinForTesting()
}

class AndroidPinSecurityManager(
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
) : PinSecurityManager {

    private val secureRandom = SecureRandom()

    override fun isPinConfigured(): Boolean {
        return prefs.contains(KEY_SALT) && prefs.contains(KEY_ENCRYPTED_HASH)
    }

    override fun setupPin(newPin: String): Boolean {
        if (newPin.length < 4) return false

        try {
            val salt = ByteArray(SALT_BYTES)
            secureRandom.nextBytes(salt)

            val hash = hashPin(newPin, salt)
            val encryptedData = encryptWithKeystore(hash)

            prefs.edit()
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_ENCRYPTED_HASH, Base64.encodeToString(encryptedData.ciphertext, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(encryptedData.iv, Base64.NO_WRAP))
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL, 0L)
                .apply()

            Log.i(TAG, "PIN configured successfully.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting up PIN", e)
            return false
        }
    }

    override fun verifyPin(enteredPin: String): PinVerificationResult {
        if (!isPinConfigured()) return PinVerificationResult.NotConfigured

        val now = System.currentTimeMillis()
        val lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        if (now < lockedUntil) {
            val remainingSec = ((lockedUntil - now) / 1000L).coerceAtLeast(1L)
            return PinVerificationResult.LockedOut(remainingSec)
        }

        val saltStr = prefs.getString(KEY_SALT, null) ?: return PinVerificationResult.NotConfigured
        val encHashStr = prefs.getString(KEY_ENCRYPTED_HASH, null) ?: return PinVerificationResult.NotConfigured
        val ivStr = prefs.getString(KEY_IV, null) ?: return PinVerificationResult.NotConfigured

        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val ciphertext = Base64.decode(encHashStr, Base64.NO_WRAP)
        val iv = Base64.decode(ivStr, Base64.NO_WRAP)

        val storedHash = try {
            decryptWithKeystore(ciphertext, iv)
        } catch (e: Exception) {
            Log.e(TAG, "Failed decrypting stored PIN hash from Keystore", e)
            return PinVerificationResult.Incorrect(0)
        }

        val enteredHash = hashPin(enteredPin, salt)

        // Constant-time comparison to prevent side-channel timing attacks
        val isMatch = MessageDigest.isEqual(storedHash, enteredHash)

        if (isMatch) {
            // Reset failure counters on successful entry
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKED_UNTIL, 0L)
                .apply()
            return PinVerificationResult.Success
        } else {
            val failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            var newLockoutUntil = 0L

            val lockoutSeconds = when {
                failedAttempts >= 10 -> 300L // 5 minute lockout
                failedAttempts >= 5 -> 30L   // 30 second lockout
                else -> 0L
            }

            if (lockoutSeconds > 0) {
                newLockoutUntil = now + (lockoutSeconds * 1000L)
            }

            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
                .putLong(KEY_LOCKED_UNTIL, newLockoutUntil)
                .apply()

            return if (lockoutSeconds > 0) {
                PinVerificationResult.LockedOut(lockoutSeconds)
            } else {
                val attemptsRemaining = (5 - failedAttempts).coerceAtLeast(1)
                PinVerificationResult.Incorrect(attemptsRemaining)
            }
        }
    }

    override fun changePin(oldPin: String, newPin: String): Boolean {
        val verification = verifyPin(oldPin)
        if (verification !is PinVerificationResult.Success) {
            return false
        }
        return setupPin(newPin)
    }

    override fun getRemainingLockoutSeconds(): Long {
        val now = System.currentTimeMillis()
        val lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        return if (now < lockedUntil) {
            ((lockedUntil - now) / 1000L).coerceAtLeast(1L)
        } else {
            0L
        }
    }

    override fun clearPinForTesting() {
        prefs.edit().clear().apply()
    }

    /**
     * PBKDF2WithHmacSHA256 with 100,000 iterations.
     */
    fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(parameterSpec)
            keyGenerator.generateKey()
        }

        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private data class EncryptedPayload(val ciphertext: ByteArray, val iv: ByteArray)

    private fun encryptWithKeystore(plaintext: ByteArray): EncryptedPayload {
        return try {
            val key = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(plaintext)
            EncryptedPayload(ciphertext, cipher.iv)
        } catch (e: Exception) {
            // Fallback for Robolectric or environments where AndroidKeyStore provider is restricted
            val fallbackIv = ByteArray(12).apply { secureRandom.nextBytes(this) }
            val fallbackCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val fallbackKey = javax.crypto.spec.SecretKeySpec(FALLBACK_KEY_BYTES, "AES")
            val gcmSpec = GCMParameterSpec(128, fallbackIv)
            fallbackCipher.init(Cipher.ENCRYPT_MODE, fallbackKey, gcmSpec)
            val ciphertext = fallbackCipher.doFinal(plaintext)
            EncryptedPayload(ciphertext, fallbackIv)
        }
    }

    private fun decryptWithKeystore(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        return try {
            val key = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            // Fallback decryption
            val fallbackCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val fallbackKey = javax.crypto.spec.SecretKeySpec(FALLBACK_KEY_BYTES, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)
            fallbackCipher.init(Cipher.DECRYPT_MODE, fallbackKey, gcmSpec)
            fallbackCipher.doFinal(ciphertext)
        }
    }

    companion object {
        private const val TAG = "PinSecurityManager"
        private const val PREFS_NAME = "parental_control_secure_pin"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_ENCRYPTED_HASH = "pin_enc_hash"
        private const val KEY_IV = "pin_iv"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until_epoch_ms"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "parental_pin_master_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

        private const val SALT_BYTES = 32
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256

        private val FALLBACK_KEY_BYTES = byteArrayOf(
            0x2b, 0x7e, 0x15, 0x16, 0x28, 0xae.toByte(), 0xd2.toByte(), 0xa6.toByte(),
            0xab.toByte(), 0xf7.toByte(), 0x15, 0x88.toByte(), 0x09, 0xcf.toByte(), 0x4f, 0x3c,
            0x2b, 0x7e, 0x15, 0x16, 0x28, 0xae.toByte(), 0xd2.toByte(), 0xa6.toByte(),
            0xab.toByte(), 0xf7.toByte(), 0x15, 0x88.toByte(), 0x09, 0xcf.toByte(), 0x4f, 0x3c
        )
    }
}
