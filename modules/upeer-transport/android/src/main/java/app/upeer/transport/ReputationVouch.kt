package app.upeer.transport

import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject
import java.security.MessageDigest

internal data class ReputationVouch(
  val id: String,
  val fromId: String,
  val toId: String,
  val type: String,
  val positive: Boolean,
  val timestamp: Long,
  val signature: String,
) {
  fun toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("fromId", fromId)
    .put("toId", toId)
    .put("type", type)
    .put("positive", positive)
    .put("timestamp", timestamp)
    .put("signature", signature)

  companion object {
    const val HANDSHAKE = "handshake"
    const val VAULT_HELPED = "vault_helped"
    const val VAULT_RETRIEVED = "vault_ret"
    const val VAULT_CHUNK = "vault_chunk"
    const val SPAM = "spam"
    const val MALICIOUS = "malicious"
    const val INTEGRITY_FAIL = "integrity_fail"

    const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    const val FUTURE_TOLERANCE_MS = 5L * 60 * 1000
    const val MAX_PER_SENDER_PER_DAY = 200
    const val MAX_PER_SENDER = 10
    const val GOSSIP_MAX_IDS = 500
    const val REQUEST_MAX_IDS = 100
    const val DELIVER_MAX_VOUCHES = 50

    private val WEIGHTS = mapOf(
      HANDSHAKE to 1.0,
      VAULT_HELPED to 2.0,
      VAULT_RETRIEVED to 1.5,
      VAULT_CHUNK to 3.0,
      SPAM to 5.0,
      MALICIOUS to 10.0,
      INTEGRITY_FAIL to 15.0,
    )

    fun weight(type: String): Double = WEIGHTS[type] ?: 0.0

    fun isType(type: String): Boolean = WEIGHTS.containsKey(type)

    fun isPositive(type: String): Boolean = when (type) {
      HANDSHAKE, VAULT_HELPED, VAULT_RETRIEVED, VAULT_CHUNK -> true
      else -> false
    }

    fun idFor(fromId: String, toId: String, type: String, timestamp: Long): String {
      val digest = MessageDigest.getInstance("SHA-256")
      return digest.digest("$fromId|$toId|$type|$timestamp".toByteArray(Charsets.UTF_8)).toHex()
    }

    fun isAccountSubject(value: String): Boolean {
      val trimmed = value.trim()
      if (trimmed.isEmpty()) return false
      if (trimmed.startsWith("grp-", ignoreCase = true)) return false
      val looksLikeAddress = trimmed.contains(':') &&
        trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }
      return !looksLikeAddress
    }

    fun parse(raw: JSONObject?, now: Long): ReputationVouch? {
      if (raw == null) return null
      val id = raw.optString("id").lowercase()
      val fromId = raw.optString("fromId").lowercase()
      val toId = raw.optString("toId").lowercase()
      val type = raw.optString("type").lowercase()
      val signature = raw.optString("signature").lowercase()
      val timestamp = raw.optLong("timestamp", -1L)
      if (!isHexId(id) || !isHexId(fromId) || !isHexId(toId)) return null
      if (!isType(type)) return null
      if (!DhtLocation.isHex(signature, 64)) return null
      if (!isAccountSubject(toId)) return null
      if (timestamp <= 0L) return null
      if (timestamp > now + FUTURE_TOLERANCE_MS) return null
      if (timestamp < now - THIRTY_DAYS_MS) return null
      if (idFor(fromId, toId, type, timestamp) != id) return null
      return ReputationVouch(
        id = id,
        fromId = fromId,
        toId = toId,
        type = type,
        positive = isPositive(type),
        timestamp = timestamp,
        signature = signature,
      )
    }

    private fun isHexId(value: String): Boolean =
      value.length in 32..128 && value.all { it.isDigit() || it in 'a'..'f' }

    private fun ByteArray.toHex(): String {
      val builder = StringBuilder(size * 2)
      for (byte in this) {
        val value = byte.toInt() and 0xFF
        builder.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
      }
      return builder.toString()
    }

    private const val HEX = "0123456789abcdef"
  }
}
