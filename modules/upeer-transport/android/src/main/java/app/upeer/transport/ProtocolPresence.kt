package app.upeer.transport

import org.json.JSONObject

internal class ProtocolPresence(
  private val state: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun touch(upeerId: String, atMillis: Long, nowMillis: Long): Boolean {
    if (!validId(upeerId) || atMillis <= 0) return false
    val at = atMillis.coerceAtMost(nowMillis)
    return synchronized(lock) {
      val peers = state().optJSONObject("peers") ?: return@synchronized false
      val peer = peers.optJSONObject(upeerId) ?: return@synchronized false
      if (at <= peer.optLong("lastActive", 0L)) return@synchronized false
      peer.put("lastActive", at)
      state().put("peers", peers)
      persist()
      true
    }
  }

  fun targets(sinceMillis: Long, limit: Int): List<String> {
    val peers = state().optJSONObject("peers") ?: return emptyList()
    val candidates = mutableListOf<Pair<String, Long>>()
    val keys = peers.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      if (!validId(key)) continue
      val peer = peers.optJSONObject(key) ?: continue
      if (peer.optString("address").isBlank()) continue
      if (peer.optString("publicKey").length != 64) continue
      val seen = peer.optLong("lastSeen", 0L)
      if (seen >= sinceMillis) candidates.add(key to seen)
    }
    return candidates.sortedByDescending { it.second }.take(limit).map { it.first }
  }

  private fun validId(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
