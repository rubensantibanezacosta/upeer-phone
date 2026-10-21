package app.upeer.transport

import android.content.Context

internal class IdentityVault(context: Context) {
  private val store = KeystoreStore(
    context = context,
    preferencesName = "upeer_yggdrasil_identity",
    keyAlias = "upeer-yggdrasil-identity",
    valueKey = "encrypted_identity",
  )

  fun save(secretKey: ByteArray) = store.save(secretKey)

  fun load(): ByteArray? = store.load()

  fun clear() = store.clear()
}

