package app.upeer.transport

import org.json.JSONObject

internal data class SyncPeerRecord(
  val deviceId: String,
  val address: String,
  val devicePublicKey: String,
  val certificateJSON: String,
  val signedPreKey: JSONObject,
  val lastSeen: Long,
) {
  fun toJson(): JSONObject = JSONObject()
    .put("address", address)
    .put("devicePublicKey", devicePublicKey)
    .put("certificate", certificateJSON)
    .put("signedPreKey", signedPreKey)
    .put("lastSeen", lastSeen)

  companion object {
    fun from(deviceId: String, json: JSONObject): SyncPeerRecord? {
      if (deviceId.length != 64) return null
      val signedPreKey = json.optJSONObject("signedPreKey") ?: return null
      if (!SignedPreKeyFields.valid(signedPreKey)) return null
      return SyncPeerRecord(
        deviceId = deviceId,
        address = json.optString("address"),
        devicePublicKey = json.optString("devicePublicKey").lowercase(),
        certificateJSON = json.optString("certificate"),
        signedPreKey = signedPreKey,
        lastSeen = json.optLong("lastSeen", 0L),
      )
    }
  }
}

internal class SyncDevicePeers(
  private val state: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun remember(binding: SyncDeviceBinding, address: String, nowMillis: Long): Boolean {
    if (binding.deviceId.length != 64) return false
    return synchronized(lock) {
      val sync = bucket()
      val peers = sync.optJSONObject("devices") ?: JSONObject()
      val existing = peers.optJSONObject(binding.deviceId) ?: JSONObject()
      val record = SyncPeerRecord(
        deviceId = binding.deviceId,
        address = address.ifBlank { existing.optString("address") },
        devicePublicKey = binding.devicePublicKey,
        certificateJSON = binding.certificateJSON,
        signedPreKey = if (SignedPreKeyFields.isNewer(existing.optJSONObject("signedPreKey"), binding.signedPreKey)) {
          binding.signedPreKey
        } else {
          existing.optJSONObject("signedPreKey") ?: binding.signedPreKey
        },
        lastSeen = nowMillis,
      )
      peers.put(binding.deviceId, record.toJson())
      sync.put("devices", peers)
      state().put("sync", sync)
      persist()
      true
    }
  }

  fun list(): List<SyncPeerRecord> = synchronized(lock) {
    val peers = bucket().optJSONObject("devices") ?: return@synchronized emptyList()
    val records = mutableListOf<SyncPeerRecord>()
    val keys = peers.keys()
    while (keys.hasNext()) {
      val deviceId = keys.next()
      SyncPeerRecord.from(deviceId, peers.optJSONObject(deviceId) ?: continue)?.let { records.add(it) }
    }
    records.sortedByDescending { it.lastSeen }
  }

  fun forget(deviceId: String): Boolean = synchronized(lock) {
    val sync = bucket()
    val peers = sync.optJSONObject("devices") ?: return@synchronized false
    if (!peers.has(deviceId)) return@synchronized false
    peers.remove(deviceId)
    sync.put("devices", peers)
    state().put("sync", sync)
    persist()
    true
  }

  fun relayTargets(senderDeviceId: String, originDeviceId: String?): List<SyncPeerRecord> =
    list().filter { peer ->
      peer.address.isNotBlank() &&
        peer.deviceId != senderDeviceId.lowercase() &&
        peer.deviceId != originDeviceId?.lowercase()
    }

  private fun bucket(): JSONObject {
    val sync = state().optJSONObject("sync") ?: JSONObject()
    if (sync.optJSONObject("devices") == null) sync.put("devices", JSONObject())
    return sync
  }
}
