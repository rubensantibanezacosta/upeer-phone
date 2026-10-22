package app.upeer.transport

import android.util.Log
import app.upeer.transport.dht.DhtLocation
import org.json.JSONArray
import org.json.JSONObject

internal data class DeviceCertificateInfo(
  val accountUpeerId: String,
  val deviceId: String,
  val issuedAt: Long,
  val expiresAt: Long,
  val epoch: Long,
)

internal fun deviceCertificateInfo(certificateJSON: String): DeviceCertificateInfo? {
  if (certificateJSON.isBlank()) return null
  return try {
    val certificate = JSONObject(certificateJSON)
    val account = certificate.optString("accountUpeerId").lowercase()
    val deviceId = certificate.optString("deviceId").lowercase()
    if (!DhtLocation.isHex(account, 32) || !DhtLocation.isHex(deviceId, 32)) return null
    val issuedAt = certificate.optLong("issuedAt")
    val expiresAt = certificate.optLong("expiresAt")
    val epoch = certificate.optLong("epoch")
    if (issuedAt <= 0L || expiresAt <= issuedAt || epoch < 0L) return null
    DeviceCertificateInfo(account, deviceId, issuedAt, expiresAt, epoch)
  } catch (cause: Exception) {
    Log.w(TAG, "El certificado de dispositivo del registro no es válido", cause)
    null
  }
}

internal fun normalizeAccountUpeerId(value: String): String {
  val account = value.trim().lowercase()
  require(DhtLocation.isHex(account, 32)) { "accountUpeerId must be a 32-byte hexadecimal value" }
  return account
}

internal fun revocationJSON(registryJSON: String): String = runCatching {
  JSONObject(registryJSON).optJSONArray("revoked")?.toString() ?: "[]"
}.getOrElse { cause ->
  Log.w(TAG, "El registro de cuenta no contiene revocaciones válidas", cause)
  "[]"
}

internal fun revocationDeviceIds(registryJSON: String): Set<String> =
  revocationIds(revocationJSON(registryJSON))

internal fun revocationDeviceIds(registry: JSONObject): Set<String> =
  revocationIds(registry.optJSONArray("revoked")?.toString() ?: "[]")

internal fun revocationIds(revokedJSON: String): Set<String> {
  val array = runCatching { JSONArray(revokedJSON) }.getOrElse { cause ->
    Log.w(TAG, "La lista de revocaciones del registro no es válida", cause)
    return emptySet()
  }
  val result = mutableSetOf<String>()
  for (index in 0 until array.length()) {
    val deviceId = array.optJSONObject(index)?.optString("deviceId")?.lowercase().orEmpty()
    if (DhtLocation.isHex(deviceId, 32)) result += deviceId
  }
  return result
}

internal fun usableRegistryCertificates(
  certificates: List<String>,
  revoked: Set<String>,
  accountUpeerId: String,
  nowMillis: Long,
): List<String> {
  val seen = mutableSetOf<String>()
  return certificates.mapNotNull { encoded ->
    val certificate = deviceCertificateInfo(encoded) ?: return@mapNotNull null
    if (certificate.accountUpeerId != accountUpeerId) return@mapNotNull null
    if (certificate.deviceId in revoked || certificate.expiresAt <= nowMillis) return@mapNotNull null
    if (!seen.add(certificate.deviceId)) null else encoded
  }
}

internal fun mergeDeviceCertificate(certificates: List<String>, certificateJSON: String, deviceId: String): List<String> =
  certificates.filter { deviceCertificateInfo(it)?.deviceId != deviceId } + certificateJSON

private const val TAG = "UPeerTransport"
