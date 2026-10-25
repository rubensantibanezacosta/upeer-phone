package app.upeer.transport.vault

import app.upeer.transport.dht.DhtLocation
import org.json.JSONArray
import org.json.JSONObject

internal object VaultAcknowledgement {
  const val MAX_HASHES = 200

  fun build(payloadHashes: List<String>, deviceId: String? = null): JSONObject? {
    if (payloadHashes.isEmpty() || payloadHashes.size > MAX_HASHES) return null
    val hashes = payloadHashes.map { it.trim().lowercase() }
    if (hashes.any { it.isEmpty() || it.length > VaultStoreRequest.MAX_HASH_LENGTH || !isHex(it) }) return null
    val packet = JSONObject()
      .put("type", "VAULT_ACK")
      .put("payloadHashes", JSONArray(hashes))
    val device = deviceId?.lowercase()?.takeIf(String::isNotEmpty)
    if (device != null) {
      if (!DhtLocation.isHex(device, 32)) return null
      packet.put("deviceId", device)
    }
    return packet
  }

  fun parse(packet: JSONObject): List<String>? {
    if (packet.optString("type") != "VAULT_ACK") return null
    val array = packet.optJSONArray("payloadHashes") ?: return null
    if (array.length() == 0 || array.length() > MAX_HASHES) return null
    val device = packet.optString("deviceId")
    if (packet.has("deviceId") && !DhtLocation.isHex(device, 32)) return null
    val hashes = mutableListOf<String>()
    for (index in 0 until array.length()) {
      val hash = array.optString(index)
      if (hash.isEmpty() || hash.length > VaultStoreRequest.MAX_HASH_LENGTH || !isHex(hash)) return null
      hashes.add(hash.lowercase())
    }
    return hashes
  }

  fun releasable(entry: VaultEntry, authenticatedSender: String, deviceId: String?): Boolean {
    val sender = authenticatedSender.lowercase()
    if (entry.recipientSid != sender && entry.recipientSid != VaultStoreRequest.WILDCARD) return false
    val device = deviceId?.lowercase()?.takeIf(String::isNotEmpty)
    return entry.recipientDeviceId == null || entry.recipientDeviceId == device
  }

  private fun isHex(value: String): Boolean =
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
