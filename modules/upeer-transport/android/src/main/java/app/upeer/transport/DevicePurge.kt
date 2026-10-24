package app.upeer.transport

import org.json.JSONObject

internal object DevicePurge {
  private val DEVICE_FIELDS = listOf("devicePublicKey", "deviceId", "deviceCertificate", "deviceSignedPreKey")

  fun apply(state: JSONObject, deviceId: String): Boolean {
    val target = deviceId.trim().lowercase()
    if (target.length != 64) return false
    val peers = state.optJSONObject("peers") ?: return false
    val sessions = state.optJSONObject("sessions")
    var changed = false
    val affected = mutableListOf<String>()
    for (upeerId in peerIds(peers)) {
      val peer = peers.optJSONObject(upeerId) ?: continue
      if (!peer.optString("deviceId").equals(target, ignoreCase = true)) continue
      DEVICE_FIELDS.forEach(peer::remove)
      affected.add(upeerId)
      changed = true
    }
    affected.forEach { upeerId ->
      if (sessions?.remove(upeerId) != null) changed = true
    }
    return changed
  }

  private fun peerIds(peers: JSONObject): List<String> {
    val keys = peers.keys()
    val result = mutableListOf<String>()
    while (keys.hasNext()) result.add(keys.next())
    return result
  }
}
