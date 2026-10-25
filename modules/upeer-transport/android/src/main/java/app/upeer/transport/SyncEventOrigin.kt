package app.upeer.transport

import org.json.JSONObject
import app.upeer.transportbridge.transportbridge.CryptoIdentity
import app.upeer.transportbridge.transportbridge.Transportbridge

internal data class SyncOriginSignature(
  val deviceId: String,
  val signature: String,
  val certificate: JSONObject,
)

internal class SyncEventOrigin(
  private val sign: (JSONObject) -> String?,
  private val verify: (JSONObject, String, String) -> Boolean,
  private val verifyCertificate: (JSONObject, String, Long) -> Boolean,
) {
  fun sign(record: SyncEventRecord, deviceId: String, certificate: JSONObject): SyncOriginSignature? {
    if (!hex(deviceId, 64)) return null
    if (certificate.optString("deviceId").lowercase() != deviceId) return null
    val payload = payload(record, deviceId)
    val signature = sign(payload)?.lowercase() ?: return null
    if (!hex(signature, 128)) return null
    return SyncOriginSignature(deviceId, signature, certificate)
  }

  fun verify(record: SyncEventRecord, accountUpeerId: String, nowMillis: Long): Boolean {
    val deviceId = record.originDeviceId ?: return false
    if (!hex(deviceId, 64)) return false
    val signature = record.originSignature ?: return false
    if (!hex(signature, 128)) return false
    val certificate = record.originCertificate ?: return false
    if (certificate.optString("deviceId").lowercase() != deviceId) return false
    if (!verifyCertificate(certificate, accountUpeerId, referenceTime(record, certificate, nowMillis))) return false
    val devicePublicKey = certificate.optString("devicePublicKey").lowercase()
    if (!hex(devicePublicKey, 64)) return false
    return verify(payload(record, deviceId), signature, devicePublicKey)
  }

  private fun payload(record: SyncEventRecord, deviceId: String): JSONObject = JSONObject()
    .put("eventId", record.eventId)
    .put("kind", record.kind)
    .put("occurredAt", record.occurredAt)
    .put("sequence", record.sequence)
    .put("originDeviceId", deviceId)
    .put("payload", record.payload)

  private fun referenceTime(record: SyncEventRecord, certificate: JSONObject, nowMillis: Long): Long =
    maxOf(record.occurredAt, certificate.optLong("issuedAt", 0L)).coerceAtMost(nowMillis)

  private fun hex(value: String, length: Int): Boolean =
    value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }

  companion object {
    fun fromBridge(crypto: CryptoIdentity): SyncEventOrigin = SyncEventOrigin(
      sign = { payload ->
        try {
          crypto.signCanonicalJSON(payload.toString())?.lowercase()
        } catch (_: Exception) {
          null
        }
      },
      verify = { payload, signature, publicKey ->
        try {
          Transportbridge.verifyCanonicalJSON(payload.toString(), signature, publicKey)
        } catch (_: Exception) {
          false
        }
      },
      verifyCertificate = { certificate, account, now ->
        try {
          Transportbridge.verifyDeviceCertificate(certificate.toString(), account, now)
        } catch (_: Exception) {
          false
        }
      },
    )
  }
}
