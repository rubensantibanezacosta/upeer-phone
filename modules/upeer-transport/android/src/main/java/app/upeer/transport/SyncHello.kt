package app.upeer.transport

import org.json.JSONObject

internal data class SyncDeviceBinding(
  val deviceId: String,
  val devicePublicKey: String,
  val certificateJSON: String,
  val signedPreKey: JSONObject,
)

internal object SyncHello {
  fun binding(payload: JSONObject): SyncDeviceBinding? {
    val deviceId = payload.optString("deviceId").lowercase()
    if (!hex(deviceId, 64)) return null
    val certificate = payload.optJSONObject("deviceCertificate") ?: return null
    if (certificate.optString("deviceId").lowercase() != deviceId) return null
    val devicePublicKey = certificate.optString("devicePublicKey").lowercase()
    if (!hex(devicePublicKey, 64)) return null
    val signedPreKey = payload.optJSONObject("deviceSignedPreKey") ?: return null
    if (!SignedPreKeyFields.valid(signedPreKey)) return null
    return SyncDeviceBinding(
      deviceId = deviceId,
      devicePublicKey = devicePublicKey,
      certificateJSON = certificate.toString(),
      signedPreKey = JSONObject()
        .put("spkPub", signedPreKey.optString("spkPub").lowercase())
        .put("spkSig", signedPreKey.optString("spkSig").lowercase())
        .put("spkId", signedPreKey.optLong("spkId")),
    )
  }

  fun timestampIsFresh(timestamp: Long, nowMillis: Long, toleranceMillis: Long = MAX_SKEW_MS): Boolean {
    if (timestamp <= 0) return false
    val delta = nowMillis - timestamp
    return delta <= toleranceMillis && delta >= -toleranceMillis
  }

  private fun hex(value: String, length: Int): Boolean =
    value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }

  private const val MAX_SKEW_MS = 5 * 60_000L
}
