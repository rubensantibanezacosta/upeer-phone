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
    val start = maxOf(0, next.length() - MAX_MESSAGES)
    for (index in start until next.length()) trimmed.put(next.get(index))
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

  private fun applyContent(message: JSONObject, content: String, version: Int, editedAt: Long): JSONObject? {
    if (version <= 0 || version <= message.optInt("version", 0)) return null
    message
      .put("content", content)
      .put("version", version)
      .put("isEdited", true)
      .put("editedAt", editedAt)
    return JSONObject(message.toString())
  }

  private const val MAX_MESSAGES = 500
}
