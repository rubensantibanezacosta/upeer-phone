package app.upeer.transport

import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject

internal data class SyncRendezvous(
  val accountUpeerId: String,
  val deviceId: String,
  val address: String,
  val certificateJSON: String,
  val publishedAt: Long,
)

internal object SyncRendezvousRecord {
  const val MAX_ADDRESS_LENGTH = 64

  fun build(
    accountUpeerId: String,
    deviceId: String,
    address: String,
    certificate: JSONObject,
    publishedAt: Long,
  ): JSONObject? {
    val account = accountUpeerId.trim().lowercase()
    val device = deviceId.trim().lowercase()
    if (!DhtLocation.isHex(account, 32) || !DhtLocation.isHex(device, 32)) return null
    if (address.isBlank() || address.length > MAX_ADDRESS_LENGTH) return null
    if (!certificate.optString("deviceId").equals(device, ignoreCase = true)) return null
    if (!certificate.optString("accountUpeerId").equals(account, ignoreCase = true)) return null
    if (publishedAt <= 0) return null
    return JSONObject()
      .put("accountUpeerId", account)
      .put("deviceId", device)
      .put("address", address)
      .put("certificate", certificate)
      .put("publishedAt", publishedAt)
  }

  fun parse(value: Any?, expectedAccount: String, nowMillis: Long, ttlMillis: Long = TTL_MILLIS): SyncRendezvous? {
    val account = expectedAccount.trim().lowercase()
    if (!DhtLocation.isHex(account, 32)) return null
    val objectValue = when (value) {
      is JSONObject -> value
      is String -> runCatching { JSONObject(value) }.getOrNull()
      else -> null
    } ?: return null
    if (!objectValue.optString("accountUpeerId").equals(account, ignoreCase = true)) return null
    val device = objectValue.optString("deviceId").trim().lowercase()
    if (!DhtLocation.isHex(device, 32)) return null
    val address = objectValue.optString("address")
    if (address.isBlank() || address.length > MAX_ADDRESS_LENGTH) return null
    val certificate = objectValue.optJSONObject("certificate") ?: return null
    if (!certificate.optString("deviceId").equals(device, ignoreCase = true)) return null
    if (!certificate.optString("accountUpeerId").equals(account, ignoreCase = true)) return null
    val publishedAt = objectValue.optLong("publishedAt", 0L)
    if (publishedAt <= 0) return null
    if (nowMillis - publishedAt > ttlMillis) return null
    if (publishedAt - nowMillis > CLOCK_TOLERANCE_MILLIS) return null
    return SyncRendezvous(account, device, address, certificate.toString(), publishedAt)
  }

  const val TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L
  private const val CLOCK_TOLERANCE_MILLIS = 10L * 60L * 1000L
}
