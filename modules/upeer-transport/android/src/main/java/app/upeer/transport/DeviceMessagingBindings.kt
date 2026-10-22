package app.upeer.transport

import android.util.Log
import app.upeer.transport.crypto.Blake2b
import app.upeer.transport.dht.DhtLocation
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject

internal data class DeviceBinding(
  val devicePublicKey: String,
  val deviceId: String,
  val certificateJSON: String,
  val signedPreKey: JSONObject,
)

internal class DeviceMessagingBindings(
  private val nowMillis: () -> Long = System::currentTimeMillis,
  private val verifyCertificate: (String, String, Long) -> Boolean = { certificate, account, now ->
    Transportbridge.verifyDeviceCertificate(certificate, account, now)
  },
  private val verifySignedPreKey: (String, String, String) -> Boolean = { deviceKey, spkPub, spkSig ->
    Transportbridge.verifySignedPreKey(deviceKey, spkPub, spkSig)
  },
  private val deriveDeviceId: (String) -> String = ::deriveDeviceId,
) {
  fun fieldsFor(device: DeviceIdentity, signedPreKey: JSONObject): JSONObject? {
    if (!validSignedPreKey(signedPreKey)) return null
    return JSONObject()
      .put("devicePublicKey", device.crypto.publicKeyHex().lowercase())
      .put("deviceId", device.deviceId.lowercase())
      .put("deviceCertificate", device.certificate)
      .put("deviceSignedPreKey", publicSignedPreKey(signedPreKey))
  }

  fun handshakeFields(device: DeviceIdentity, signedPreKeys: DeviceSignedPreKeyStore): JSONObject? = try {
    fieldsFor(device, JSONObject(signedPreKeys.currentJSON(device)))
  } catch (cause: Exception) {
    Log.w("UPeerTransport", "No se pudo preparar la SPK de dispositivo para el handshake", cause)
    null
  }

  fun hasFields(source: JSONObject): Boolean =
    source.has("devicePublicKey") ||
      source.has("deviceId") ||
      source.has("deviceCertificate") ||
      source.has("deviceSignedPreKey")

  fun parse(source: JSONObject, accountUpeerId: String): DeviceBinding? {
    val account = accountUpeerId.lowercase()
    if (!DhtLocation.isHex(account, 32)) return null
    val devicePublicKey = source.optString("devicePublicKey").lowercase()
    val deviceId = source.optString("deviceId").lowercase()
    if (!DhtLocation.isHex(devicePublicKey, 32) || !DhtLocation.isHex(deviceId, 32)) return null
    if (!deriveDeviceId(devicePublicKey).equals(deviceId, ignoreCase = true)) return null
    val certificate = source.optJSONObject("deviceCertificate") ?: return null
    if (!certificate.optString("accountUpeerId").equals(account, ignoreCase = true)) return null
    if (!certificate.optString("deviceId").equals(deviceId, ignoreCase = true)) return null
    if (!certificate.optString("devicePublicKey").equals(devicePublicKey, ignoreCase = true)) return null
    val certificateJSON = certificate.toString()
    val certificateValid = runCatching {
      verifyCertificate(certificateJSON, account, nowMillis())
    }.getOrElse { cause ->
      Log.w("UPeerTransport", "No se pudo verificar el certificado de dispositivo del contacto", cause)
      false
    }
    if (!certificateValid) return null
    val signedPreKey = source.optJSONObject("deviceSignedPreKey") ?: return null
    if (!validSignedPreKey(signedPreKey)) return null
    if (!verifySignedPreKey(devicePublicKey, signedPreKey.optString("spkPub"), signedPreKey.optString("spkSig"))) return null
    return DeviceBinding(devicePublicKey, deviceId, certificateJSON, publicSignedPreKey(signedPreKey))
  }

  fun bindingFor(peer: JSONObject, accountUpeerId: String): DeviceBinding? = parse(peer, accountUpeerId)

  fun attach(metadata: JSONObject, packet: JSONObject, accountUpeerId: String): Boolean {
    val binding = parse(packet, accountUpeerId) ?: return false
    metadata
      .put("devicePublicKey", binding.devicePublicKey)
      .put("deviceId", binding.deviceId)
      .put("deviceCertificate", JSONObject(binding.certificateJSON))
      .put("deviceSignedPreKey", binding.signedPreKey)
    return true
  }

  private fun validSignedPreKey(value: JSONObject): Boolean =
    DhtLocation.isHex(value.optString("spkPub"), 32) &&
      DhtLocation.isHex(value.optString("spkSig"), 64) &&
      value.optLong("spkId") > 0

  private fun publicSignedPreKey(value: JSONObject): JSONObject = JSONObject()
    .put("spkPub", value.optString("spkPub").lowercase())
    .put("spkSig", value.optString("spkSig").lowercase())
    .put("spkId", value.optLong("spkId"))
}

private fun deriveDeviceId(publicKeyHex: String): String {
  val publicKey = DhtLocation.decodeHex(publicKeyHex, 32) ?: return ""
  return DhtLocation.hex(Blake2b.hash256(publicKey))
}
