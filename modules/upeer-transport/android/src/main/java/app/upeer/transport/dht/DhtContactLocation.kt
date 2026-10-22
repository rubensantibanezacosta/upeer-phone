package app.upeer.transport.dht

import org.json.JSONArray
import org.json.JSONObject

internal object DhtContactLocation {
  data class Verified(
    val blockJSON: String,
    val block: JSONObject,
    val certificateJSON: String,
    val deviceId: String,
    val addresses: List<String>,
    val dhtSeq: Long,
    val expiresAt: Long,
  )

  fun select(
    value: Any?,
    expectedUpeerId: String,
    publicKeyHex: String,
    registryJSON: String,
    nowMillis: Long,
    verify: (String, String, String, Long) -> Boolean,
  ): Verified? = candidates(value)
    .take(MAX_CANDIDATES)
    .mapNotNull { candidate ->
      verifyOne(candidate, expectedUpeerId, publicKeyHex, registryJSON, nowMillis, verify)
    }
    .maxWithOrNull(compareBy<Verified> { it.dhtSeq }.thenBy { it.expiresAt })

  fun verifyOne(
    blockJSON: String,
    expectedUpeerId: String,
    publicKeyHex: String,
    registryJSON: String,
    nowMillis: Long,
    verify: (String, String, String, Long) -> Boolean,
  ): Verified? {
    if (!DhtLocation.isHex(expectedUpeerId, 32) || !DhtLocation.isHex(publicKeyHex, 32)) return null
    val account = expectedUpeerId.lowercase()
    val decoded = runCatching { DhtLocation.validateStructure(blockJSON, publicKeyHex, nowMillis) }.getOrNull() ?: return null
    val certificate = decoded.deviceCertificate ?: return null
    if (decoded.deviceId.isEmpty()) return null
    if (!certificate.optString("deviceId").equals(decoded.deviceId, ignoreCase = true)) return null
    if (!certificate.optString("accountUpeerId").equals(account, ignoreCase = true)) return null
    val admitted = runCatching { verify(blockJSON, publicKeyHex, registryJSON, nowMillis) }.getOrElse { false }
    if (!admitted) return null
    return Verified(
      blockJSON = blockJSON,
      block = decoded.block,
      certificateJSON = certificate.toString(),
      deviceId = decoded.deviceId,
      addresses = decoded.addresses,
      dhtSeq = decoded.dhtSeq,
      expiresAt = decoded.expiresAt,
    )
  }

  private fun candidates(value: Any?): List<String> = when (value) {
    is JSONObject -> listOf(value.toString())
    is JSONArray -> (0 until value.length()).mapNotNull { index ->
      when (val item = value.opt(index)) {
        is JSONObject -> item.toString()
        is String -> item
        else -> null
      }
    }
    is String -> {
      val decoded = runCatching { JSONObject(value) }.getOrNull()
      if (decoded != null) listOf(decoded.toString()) else emptyList()
    }
    else -> emptyList()
  }

  private const val MAX_CANDIDATES = 16
}
