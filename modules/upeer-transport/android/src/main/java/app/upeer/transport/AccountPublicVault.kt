package app.upeer.transport

import android.content.Context

internal class AccountPublicVault(context: Context) {
  private val store = KeystoreStore(
    context = context,
    preferencesName = "upeer_account_public",
    keyAlias = "upeer-account-public",
    valueKey = "encrypted_account_public",
  )

  fun save(accountUpeerId: String, accountPublicKey: String): Boolean {
    val payload = AccountPublicPayload.encode(accountUpeerId, accountPublicKey) ?: return false
    return try {
      store.save(payload.toByteArray(Charsets.UTF_8))
      true
    } catch (cause: Exception) {
      false
    }
  }

  fun load(): AccountPublicIdentity? = try {
    val raw = store.load() ?: return null
    AccountPublicPayload.decode(String(raw, Charsets.UTF_8))
  } catch (cause: Exception) {
    null
  }

  fun clear() = store.clear()
}

internal data class AccountPublicIdentity(val accountUpeerId: String, val accountPublicKey: String)
