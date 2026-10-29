package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal object ProtocolConversations {
  fun build(
    messages: JSONArray,
    peers: JSONObject,
    typingPeers: Map<String, String> = emptyMap(),
    groups: Map<String, JSONObject> = emptyMap(),
  ): String {
    val latest = LinkedHashMap<String, JSONObject>()
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      val upeerId = message.optString("upeerId")
      if (upeerId.isBlank()) continue
      val current = latest[upeerId]
      if (current == null || message.optLong("timestamp") >= current.optLong("timestamp")) latest[upeerId] = message
    }
    val latestReaction = LinkedHashMap<String, Pair<JSONObject, JSONObject>>()
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      val upeerId = message.optString("upeerId")
      if (upeerId.isBlank()) continue
      val reaction = ChatReactions.latest(message) ?: continue
      val at = reaction.optLong("at", 0L)
      if (at <= 0L) continue
      val current = latestReaction[upeerId]
      if (current == null || at >= current.first.optLong("at", 0L)) latestReaction[upeerId] = reaction to message
    }
    val result = latest.map { (upeerId, message) ->
      val group = groups[upeerId]
      var unread = 0
      for (index in 0 until messages.length()) {
        val candidate = messages.optJSONObject(index) ?: continue
        if (candidate.optString("upeerId") == upeerId &&
          !candidate.optBoolean("isMine") &&
          !candidate.optBoolean("deleted") &&
          candidate.optString("status") == "delivered"
        ) unread++
      }
      val reacted = latestReaction[upeerId]
      val reactionNewer = reacted != null && reacted.first.optLong("at", 0L) > message.optLong("timestamp", 0L)
      val entry = JSONObject()
        .put("upeerId", upeerId)
        .put("alias", group?.optString("name") ?: peers.optJSONObject(upeerId)?.optString("alias").orEmpty())
        .put("avatar", group?.optString("avatar") ?: peers.optJSONObject(upeerId)?.optString("avatar").orEmpty())
        .put("group", group != null)
        .put("members", group?.optJSONArray("members")?.length() ?: 0)
        .put("lastMessage", message.optString("content"))
        .put("lastStatus", message.optString("status"))
        .put("lastTimestamp", if (reactionNewer) reacted.first.optLong("at", 0L) else message.optLong("timestamp"))
        .put("lastIsMine", message.optBoolean("isMine"))
        .put("unread", unread)
        .put("typing", typingPeers.containsKey(upeerId))
        .put("activity", typingPeers[upeerId] ?: "")
        .put("lastSeen", peers.optJSONObject(upeerId)?.optLong("lastSeen", 0L) ?: 0L)
        .put("lastActive", peers.optJSONObject(upeerId)?.optLong("lastActive", 0L) ?: 0L)
      if (reactionNewer) {
        entry.put(
          "lastReaction",
          JSONObject()
            .put("emoji", reacted.first.optString("emoji"))
            .put("actorUpeerId", reacted.first.optString("actorUpeerId"))
            .put("timestamp", reacted.first.optLong("at", 0L))
            .put("message", reacted.second.optString("content"))
            .put("messageIsMine", reacted.second.optBoolean("isMine")),
        )
      }
      entry
    }.toMutableList()
    for ((groupId, group) in groups) {
      if (latest.containsKey(groupId)) continue
      result.add(
        JSONObject()
          .put("upeerId", groupId)
          .put("alias", group.optString("name"))
          .put("avatar", group.optString("avatar"))
          .put("group", true)
          .put("members", group.optJSONArray("members")?.length() ?: 0)
          .put("lastMessage", "")
          .put("lastStatus", "")
          .put("lastTimestamp", group.optLong("updatedAt", group.optLong("createdAt", 0L)))
          .put("lastIsMine", false)
          .put("unread", 0)
          .put("typing", false)
          .put("activity", "")
          .put("lastSeen", 0L)
          .put("lastActive", 0L),
      )
    }
    return JSONArray(result.sortedByDescending { it.optLong("lastTimestamp") }).toString()
  }
}
