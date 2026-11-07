package app.upeer.transport

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra el valor de una columna concreta con la clave del AndroidKeyStore.
 *
 * El escritorio protege toda la base con SQLCipher; en móvil, para no añadir una dependencia
 * nativa nueva, se cifran solo las columnas con material sensible. Se usa una clave propia
 * (`upeer-db-columns`) distinta de la del estado, para poder borrar una sin tocar la otra.
 */
internal class ProtocolCipher(context: Context) {
  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEY_STORE)
    generator.init(
      android.security.keystore.KeyGenParameterSpec.Builder(ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build(),
    )
    return generator.generateKey()
  }

  /** Devuelve `null` para valores nulos; si no, el texto cifrado listo para guardar. */
  fun seal(value: String?): String? {
    if (value == null) return null
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
  }

  fun open(value: String?): String? {
    if (value == null) return null
    return try {
      val payload = Base64.decode(value, Base64.NO_WRAP)
      require(payload.size > IV_LENGTH) { "valor cifrado truncado" }
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_LENGTH)))
      String(cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size)), Charsets.UTF_8)
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo descifrar una columna de la base de datos (${cause.javaClass.simpleName})")
      null
    }
  }

  private companion object {
    const val ALIAS = "upeer-db-columns"
    const val ANDROID_KEY_STORE = "AndroidKeyStore"
    const val KEY_ALGORITHM = "AES"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_LENGTH = 12
    const val TAG_BITS = 128
    const val TAG = "UPeerTransport"
  }
}
