package app.upeer.transport

import org.json.JSONObject

internal class InstalledCertificateCheck(val certificate: JSONObject?, val failure: String?)

internal object InstalledCertificate {
  fun check(
    certificateJSON: String,
    accountUpeerId: String,
    deviceId: String,
    nowMillis: Long,
  ): InstalledCertificateCheck {
    val certificate = try {
      JSONObject(certificateJSON)
    } catch (cause: Exception) {
      return InstalledCertificateCheck(null, "no es JSON válido")
    }
    if (!certificate.optString("accountUpeerId").equals(accountUpeerId, ignoreCase = true)) {
      return InstalledCertificateCheck(null, "pertenece a otra cuenta")
    }
    if (!certificate.optString("deviceId").equals(deviceId, ignoreCase = true)) {
      return InstalledCertificateCheck(null, "no corresponde a la clave del dispositivo")
    }
    val expiresAt = certificate.optLong("expiresAt")
    if (expiresAt <= 0L) {
      return InstalledCertificateCheck(null, "no declara caducidad")
    }
    if (expiresAt <= nowMillis) {
      return InstalledCertificateCheck(null, "está caducado")
    }
    return InstalledCertificateCheck(certificate, null)
  }
}
