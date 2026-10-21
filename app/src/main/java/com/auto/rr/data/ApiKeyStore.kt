package com.auto.rr.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the user's own Twelve Data API key.
 *
 * - The key never lives in source or in the APK; each user types in their own.
 * - It is encrypted with an AES-256-GCM key that lives in the Android Keystore (non-exportable),
 *   so the value in SharedPreferences is useless if the file is copied off the device.
 * - [key] is observable so the REST client, WebSocket and UI react the moment it changes.
 */
object ApiKeyStore {

    private const val PREFS = "auto_rr.secure"
    private const val PREF_KEY = "api_key_enc"
    private const val KS_PROVIDER = "AndroidKeyStore"
    private const val KS_ALIAS = "auto_rr_api_key_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private val _key = MutableStateFlow<String?>(null)
    val key: StateFlow<String?> = _key.asStateFlow()

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = p
            _key.value = readDecrypted(p)
        }
    }

    /** Current key, or null if the user has not entered one yet. Safe to call from any thread. */
    fun current(): String? = _key.value

    fun hasKey(): Boolean = !_key.value.isNullOrBlank()

    /** Returns true if the key was persisted. */
    fun save(raw: String): Boolean {
        val p = prefs ?: return false
        val clean = sanitize(raw)
        if (clean.isEmpty()) return false
        return try {
            p.edit().putString(PREF_KEY, encrypt(clean)).apply()
            _key.value = clean
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun clear() {
        prefs?.edit()?.remove(PREF_KEY)?.apply()
        _key.value = null
    }

    /** Trim whitespace/newlines/quotes users often paste along with the key. */
    fun sanitize(raw: String): String = raw.trim().trim('"', '\'').filter { !it.isWhitespace() }

    /** Shows only the tail, e.g. "••••••••a1b2". */
    fun masked(k: String?): String {
        if (k.isNullOrEmpty()) return ""
        val tail = k.takeLast(4)
        return "\u2022".repeat(8) + tail
    }

    // ---- crypto ----

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KS_PROVIDER).apply { load(null) }
        (ks.getKey(KS_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS_PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(
                KS_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = c.iv
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun readDecrypted(p: SharedPreferences): String? {
        val stored = p.getString(PREF_KEY, null) ?: return null
        return try {
            val parts = stored.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(c.doFinal(ct), Charsets.UTF_8).ifBlank { null }
        } catch (_: Throwable) {
            // Keystore key lost (device restore, security reset): treat as "no key", user re-enters.
            p.edit().remove(PREF_KEY).apply()
            null
        }
    }
}
