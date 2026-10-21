package app.upeer.transport

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class YggdrasilConfigStore(context: Context) {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun save(config: ByteArray) {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val encrypted = cipher.doFinal(config)
    val encoded = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    check(preferences.edit().putString(CONFIG_KEY, encoded).commit()) { "Could not persist encrypted Yggdrasil configuration" }
  }

  fun load(): ByteArray? {
    val encoded = preferences.getString(CONFIG_KEY, null) ?: return null
    val payload = Base64.decode(encoded, Base64.NO_WRAP)
    require(payload.size > IV_LENGTH) { "Encrypted Yggdrasil configuration is truncated" }
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_LENGTH)))
    return cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size))
  }

  fun clear() {
    check(preferences.edit().remove(CONFIG_KEY).commit()) { "Could not remove persisted Yggdrasil configuration" }
  }

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
    generator.init(
      KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build(),
    )
    return generator.generateKey()
  }

  companion object {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "upeer-yggdrasil-config"
    private const val PREFERENCES_NAME = "upeer_yggdrasil_transport"
    private const val CONFIG_KEY = "encrypted_node_config"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
  }
}