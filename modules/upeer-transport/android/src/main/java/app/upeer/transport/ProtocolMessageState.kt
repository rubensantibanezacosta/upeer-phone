package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal object ProtocolMessageState {
  fun find(messages: JSONArray, messageID: String): JSONObject? {
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      if (message.optString("id") == messageID) return message
    }
    return null
  }

  fun upsert(messages: JSONArray, message: JSONObject): JSONArray {
    val next = JSONArray().put(message)
    val id = message.optString("id")
    for (index in 0 until messages.length()) {
      val existing = messages.optJSONObject(index) ?: continue
      if (existing.optString("id") != id) next.put(existing)
    }
    val trimmed = JSONArray()
    val count = minOf(next.length(), MAX_MESSAGES)
    for (index in 0 until count) trimmed.put(next.get(index))
    return trimmed
  }

  fun updateIncomingContent(
    messages: JSONArray,
    messageID: String,
    senderUpeerId: String,
    content: String,
    version: Int,
    editedAt: Long = System.currentTimeMillis(),
  ): JSONObject? {
    val message = find(messages, messageID) ?: return null
    if (message.optString("upeerId") != senderUpeerId || message.optBoolean("isMine")) return null
    return applyContent(message, content, version, editedAt)
  }

  fun updateLocalContent(
    messages: JSONArray,
    messageID: String,
    upeerId: String,
    content: String,
    version: Int,
    editedAt: Long = System.currentTimeMillis(),
  ): JSONObject? {
    val message = find(messages, messageID) ?: return null
    if (message.optString("upeerId") != upeerId || !message.optBoolean("isMine")) return null
    return applyContent(message, content, version, editedAt)
  }

  fun markDeleted(message: JSONObject, deletedAt: Long = System.currentTimeMillis()): JSONObject {
    message
      .put("content", DELETED_CONTENT)
      .put("deleted", true)
      .put("deletedAt", deletedAt)
    message.remove("replyTo")
    message.remove("reactions")
    message.put("isEdited", false)
    return JSONObject(message.toString())
  }

  fun withoutConversation(messages: JSONArray, upeerId: String): JSONArray {
    val next = JSONArray()
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      if (message.optString("upeerId") == upeerId) continue
      next.put(message)
    }
    return next
  }

  fun snapshot(messages: JSONArray, upeerId: String, limit: Int): String {
    val newest = buildList {
      for (index in 0 until messages.length()) {
        val message = messages.optJSONObject(index) ?: continue
        if (upeerId.isNotBlank() && message.optString("upeerId") != upeerId) continue
        add(JSONObject(message.toString()))
      }
    }
    return JSONArray(newest.take(limit.coerceIn(1, MAX_MESSAGES)).reversed()).toString()
  }

  fun historyBefore(messages: JSONArray, upeerId: String, beforeTimestamp: Long, limit: Int): String {
    val found = mutableListOf<JSONObject>()
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      if (upeerId.isNotBlank() && message.optString("upeerId") != upeerId) continue
      val timestamp = message.optLong("timestamp", 0L)
      if (timestamp <= 0 || timestamp >= beforeTimestamp) continue
      found.add(JSONObject(message.toString()))
    }
    found.sortWith(compareBy({ it.optLong("timestamp", 0L) }, { it.optString("id") }))
    return JSONArray(found.takeLast(limit.coerceIn(1, MAX_MESSAGES)).reversed()).toString()
  }

  private fun applyContent(message: JSONObject, content: String, version: Int, editedAt: Long): JSONObject? {
    if (message.optBoolean("deleted")) return null
    if (version <= 0 || version <= message.optInt("version", 0)) return null
    message
      .put("content", content)
      .put("version", version)
      .put("isEdited", true)
      .put("editedAt", editedAt)
    return JSONObject(message.toString())
  }

  const val DELETED_CONTENT = "Mensaje eliminado"

  private const val MAX_MESSAGES = 500
}
