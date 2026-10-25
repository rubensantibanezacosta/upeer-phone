package app.upeer.transport.vault

import app.upeer.transport.dht.DhtLocation
import org.json.JSONArray
import org.json.JSONObject

internal object VaultQueryDelivery {
  const val PAGE_SIZE = 120

  fun buildQuery(
    requesterSid: String,
    requesterDeviceId: String? = null,
    payloadHash: String? = null,
  ): JSONObject {
    val requester = requesterSid.lowercase()
    require(DhtLocation.isHex(requester, 32)) { "El solicitante no es un uPeer ID válido" }
    val packet = JSONObject()
      .put("type", "VAULT_QUERY")
      .put("requesterSid", requester)
    val device = requesterDeviceId?.lowercase()?.takeIf(String::isNotEmpty)
    if (device != null) {
      require(DhtLocation.isHex(device, 32)) { "El dispositivo del solicitante no es válido" }
      packet.put("requesterDeviceId", device)
    }
    val hash = payloadHash?.takeIf(String::isNotEmpty)
    if (hash != null) packet.put("payloadHash", hash)
    return packet
  }

  fun deliver(
    entries: List<VaultEntry>,
    authenticatedSender: String,
    requesterSid: String,
    requesterDeviceId: String? = null,
    payloadHash: String? = null,
    afterId: Long = 0L,
    nowMillis: Long,
  ): JSONObject? {
    val requester = requesterSid.lowercase()
    if (!DhtLocation.isHex(requester, 32)) return null
    if (authenticatedSender.lowercase() != requester) return null
    val device = requesterDeviceId?.lowercase()?.takeIf(String::isNotEmpty)
    val visible = entries
      .filter { it.expiresAt > nowMillis }
      .filter { it.recipientSid == requester || it.recipientSid == VaultStoreRequest.WILDCARD }
      .filter { it.recipientDeviceId == null || it.recipientDeviceId == device }
      .filter { payloadHash == null || it.payloadHash == payloadHash.lowercase() }
      .sortedBy { it.id }
    if (payloadHash != null) {
      val single = visible.firstOrNull() ?: return null
      return packet(listOf(single), false)
    }
    if (visible.isEmpty()) return null
    val page = visible.filter { it.id > afterId }.take(PAGE_SIZE)
    if (page.isEmpty()) return null
    val hasMore = visible.count { it.id > afterId } > PAGE_SIZE
    return packet(page, hasMore)
  }

  fun parseDelivery(packet: JSONObject): List<VaultEntry>? {
    if (packet.optString("type") != "VAULT_DELIVERY") return null
    val entries = packet.optJSONArray("entries") ?: return null
    if (entries.length() > PAGE_SIZE) return null
    val result = mutableListOf<VaultEntry>()
    for (index in 0 until entries.length()) {
      val entry = entries.optJSONObject(index) ?: return null
      val sender = entry.optString("senderSid")
      val hash = entry.optString("payloadHash")
      val data = entry.optString("data")
      if (!DhtLocation.isHex(sender, 32)) return null
      if (hash.isEmpty() || hash.length > VaultStoreRequest.MAX_HASH_LENGTH) return null
      if (data.isEmpty() || data.length > MAX_DELIVERY_DATA) return null
      result.add(
        VaultEntry(
          id = entry.optLong("id", 0L),
          payloadHash = hash.lowercase(),
          recipientSid = "",
          recipientDeviceId = null,
          senderSid = sender.lowercase(),
          priority = VaultStoreEnvelope.PRIORITY_MESSAGE,
          data = data.lowercase(),
          expiresAt = entry.optLong("expiresAt", 0L),
        ),
      )
    }
    return result
  }

  private fun packet(entries: List<VaultEntry>, hasMore: Boolean): JSONObject {
    val array = JSONArray()
    for (entry in entries) {
      array.put(
        JSONObject()
          .put("id", entry.id)
          .put("senderSid", entry.senderSid)
          .put("payloadHash", entry.payloadHash)
          .put("data", entry.data),
      )
    }
    return JSONObject()
      .put("type", "VAULT_DELIVERY")
      .put("entries", array)
      .put("hasMore", hasMore)
  }

  private const val MAX_DELIVERY_DATA = 20_000_000
}
