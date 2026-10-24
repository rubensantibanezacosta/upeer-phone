package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal object ProtocolConversations {
  fun build(messages: JSONArray, peers: JSONObject, typingPeers: Set<String> = emptySet()): String {
    val latest = LinkedHashMap<String, JSONObject>()
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      val upeerId = message.optString("upeerId")
      if (upeerId.isBlank()) continue
      val current = latest[upeerId]
      if (current == null || message.optLong("timestamp") >= current.optLong("timestamp")) latest[upeerId] = message
    }
    val result = latest.map { (upeerId, message) ->
      var unread = 0
      for (index in 0 until messages.length()) {
        val candidate = messages.optJSONObject(index) ?: continue
        if (candidate.optString("upeerId") == upeerId &&
          !candidate.optBoolean("isMine") &&
          candidate.optString("status") == "delivered"
        ) unread++
      }
      JSONObject()
        .put("upeerId", upeerId)
        .put("alias", peers.optJSONObject(upeerId)?.optString("alias").orEmpty())
        .put("lastMessage", message.optString("content"))
        .put("lastStatus", message.optString("status"))
        .put("lastTimestamp", message.optLong("timestamp"))
        .put("lastIsMine", message.optBoolean("isMine"))
        .put("unread", unread)
        .put("typing", typingPeers.contains(upeerId))
        .put("lastSeen", peers.optJSONObject(upeerId)?.optLong("lastSeen", 0L) ?: 0L)
    }.sortedByDescending { it.optLong("lastTimestamp") }
    return JSONArray(result).toString()
  }
}
