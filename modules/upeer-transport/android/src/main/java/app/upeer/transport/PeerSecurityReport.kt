package app.upeer.transport

import org.json.JSONObject

internal object PeerSecurityReport {
  fun json(upeerId: String, peer: JSONObject?, myPublicKey: String, hasSession: Boolean): String {
    val publicKey = peer?.optString("publicKey").orEmpty()
    return JSONObject()
      .put("upeerId", upeerId)
      .put("publicKey", publicKey)
      .put("deviceId", peer?.optString("deviceId").orEmpty())
      .put("devicePublicKey", peer?.optString("devicePublicKey").orEmpty())
      .put("hasSession", hasSession)
      .put("lastSeen", peer?.optLong("lastSeen", 0L) ?: 0L)
      .put("safetyNumber", SafetyNumber.compute(myPublicKey, publicKey))
      .toString()
  }
}
