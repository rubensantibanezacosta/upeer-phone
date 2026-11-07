package app.upeer.transport

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class KeystoreStore(
  context: Context,
  preferencesName: String,
  private val keyAlias: String,
  private val valueKey: String,
) {
  private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

  fun save(value: ByteArray) = saveTo(valueKey, value)

  fun load(): ByteArray? = loadFrom(valueKey)

  fun saveTo(key: String, value: ByteArray) {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(key))
    val encrypted = cipher.doFinal(value)
    val encoded = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    check(preferences.edit().putString(key, encoded).commit()) { "Could not persist encrypted value" }
  }

  fun loadFrom(key: String): ByteArray? {
    val encoded = preferences.getString(key, null) ?: return null
    return try {
      val payload = Base64.decode(encoded, Base64.NO_WRAP)
      require(payload.size > IV_LENGTH) { "Encrypted value is truncated" }
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(key), GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_LENGTH)))
      cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size))
    } catch (cause: Exception) {
      Log.w(TAG, "Estado cifrado descartado en $key: el llavero no pudo descifrarlo (${cause.javaClass.simpleName}: ${cause.message}).")
      preferences.edit().remove(key).apply()
      null
    }
  }

  fun storedKeys(): Set<String> = preferences.all.keys.filter { it.startsWith(BLOCK_PREFIX) }.toSet()

  fun clear() {
    val editor = preferences.edit().remove(valueKey)
    for (key in storedKeys()) editor.remove(key)
    check(editor.commit()) { "Could not remove persisted value" }
  }

  /** Borra solo los bloques del estado una vez volcados a SQLite, dejando el resto de claves. */
  fun clearBlocks() {
    val keys = storedKeys()
    if (keys.isEmpty()) return
    val editor = preferences.edit()
    for (key in keys) editor.remove(key)
    check(editor.commit()) { "Could not remove persisted blocks" }
  }

  fun hasBlocks(): Boolean = storedKeys().isNotEmpty()

  private fun keyAliasFor(key: String): String = if (key == valueKey) keyAlias else "$keyAlias-$key"

  private fun getOrCreateKey(key: String): SecretKey {
    val alias = keyAliasFor(key)
    val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
    generator.init(
      KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build(),
    )
    return generator.generateKey()
  }

  internal companion object {
    const val BLOCK_PREFIX = "protocol_state_block_"
    private const val TAG = "UPeerTransport"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128
  }
}
