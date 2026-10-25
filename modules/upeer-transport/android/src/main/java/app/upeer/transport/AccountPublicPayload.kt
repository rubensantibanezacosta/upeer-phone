package app.upeer.transport

import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject

internal object AccountPublicPayload {
  const val MODE = "device"

  fun encode(accountUpeerId: String, accountPublicKey: String): String? {
    if (!validPair(accountUpeerId, accountPublicKey)) return null
    return JSONObject()
      .put("mode", MODE)
      .put("accountUpeerId", accountUpeerId.lowercase())
      .put("accountPublicKey", accountPublicKey.lowercase())
      .toString()
  }

  fun decode(raw: String): AccountPublicIdentity? {
    val payload = try {
      JSONObject(raw)
    } catch (cause: Exception) {
      return null
    }
    if (payload.optString("mode") != MODE) return null
    val accountUpeerId = payload.optString("accountUpeerId").lowercase()
    val accountPublicKey = payload.optString("accountPublicKey").lowercase()
    if (!validPair(accountUpeerId, accountPublicKey)) return null
    return AccountPublicIdentity(accountUpeerId, accountPublicKey)
  }

  private fun validPair(accountUpeerId: String, accountPublicKey: String): Boolean =
    DhtLocation.isHex(accountUpeerId, 32) && DhtLocation.isHex(accountPublicKey, 32)
}
