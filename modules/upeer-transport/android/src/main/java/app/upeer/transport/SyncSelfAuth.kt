package app.upeer.transport

import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject

internal data class SyncSelfIdentity(
  val deviceId: String,
  val devicePublicKey: String,
  val certificateJSON: String,
)

internal sealed class SyncSelfAuthResult {
  data class Accepted(val identity: SyncSelfIdentity) : SyncSelfAuthResult()
  data class Rejected(val reason: String) : SyncSelfAuthResult()
}

internal object SyncSelfAuth {
  fun authenticate(
    packet: JSONObject,
    selfUpeerId: String,
    nowMillis: Long,
    verifyCertificate: (String, String, Long) -> Boolean,
    isRevoked: (String) -> Boolean,
  ): SyncSelfAuthResult {
    val self = selfUpeerId.trim().lowercase()
    if (!DhtLocation.isHex(self, 32)) return reject("identidad propia inválida")
    val declaredSender = packet.optString("senderUpeerId").trim().lowercase()
    if (declaredSender != self) return reject("remitente que no es la propia cuenta")
    val deviceId = packet.optString("senderDeviceId").trim().lowercase()
    if (!DhtLocation.isHex(deviceId, 32)) return reject("dispositivo emisor inválido")
    val certificate = packet.optJSONObject("senderDeviceCertificate") ?: return reject("certificado ausente")
    if (!certificate.optString("accountUpeerId").equals(self, ignoreCase = true)) {
      return reject("certificado de otra cuenta")
    }
    if (!certificate.optString("deviceId").equals(deviceId, ignoreCase = true)) {
      return reject("dispositivo que no coincide con el certificado")
    }
    val devicePublicKey = certificate.optString("devicePublicKey").lowercase()
    if (!DhtLocation.isHex(devicePublicKey, 32)) return reject("clave de dispositivo inválida")
    if (packet.optString("signature").length != 128) return reject("firma ausente")
    val referenceTime = maxOf(nowMillis, certificate.optLong("issuedAt", 0L))
    val valid = try {
      verifyCertificate(certificate.toString(), self, referenceTime)
    } catch (_: Exception) {
      false
    }
    if (!valid) return reject("certificado no verificable")
    if (isRevoked(deviceId)) return reject("dispositivo revocado")
    return SyncSelfAuthResult.Accepted(SyncSelfIdentity(deviceId, devicePublicKey, certificate.toString()))
  }

  private fun reject(reason: String) = SyncSelfAuthResult.Rejected(reason)
}
