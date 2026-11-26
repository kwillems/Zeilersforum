package nl.kwillems.zeilersforum.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Bewaart gebruikersnaam en wachtwoord versleuteld met een sleutel uit de
 * Android Keystore. De sleutel kan het toestel niet verlaten.
 */
class CredentialStore(context: Context) {

    data class Credentials(val username: String, val password: String)

    private val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)

    fun load(): Credentials? {
        val user = prefs.getString(KEY_USER, null) ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        return runCatching { Credentials(decrypt(user), decrypt(pass)) }.getOrNull()
    }

    fun save(credentials: Credentials) {
        prefs.edit()
            .putString(KEY_USER, encrypt(credentials.username))
            .putString(KEY_PASS, encrypt(credentials.password))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_LEN))
        return String(cipher.doFinal(data, IV_LEN, data.size - IV_LEN), Charsets.UTF_8)
    }

    private companion object {
        const val ALIAS = "zeilersforum-credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val KEY_USER = "u"
        const val KEY_PASS = "p"
    }
}
