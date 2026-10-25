package app.upeer.transport

import org.json.JSONObject

internal object SyncHistoryPayload {
  private val statuses = setOf("pending", "sent", "vaulted", "delivered", "read", "failed")

  fun desktop(message: JSONObject): JSONObject {
    val payload = JSONObject()
      .put("id", message.optString("id"))
      .put("chatUpeerId", message.optString("upeerId"))
      .put("isMine", message.optBoolean("isMine"))
      .put("message", message.optString("content"))
      .put("status", message.optString("status", "sent"))
      .put("version", message.optInt("version", 0))
      .put("isDeleted", message.optBoolean("deleted"))
      .put("isEdited", message.optBoolean("isEdited"))
      .put("timestamp", message.optLong("timestamp", 0L))
    if (message.has("replyTo")) payload.put("replyTo", message.optString("replyTo"))
    return payload
  }

  fun local(payload: JSONObject): JSONObject? {
    val id = payload.optString("id")
    if (id.isBlank()) return null
    val upeerId = payload.optString("chatUpeerId").ifBlank { payload.optString("upeerId") }.lowercase()
    if (upeerId.length != 64) return null
    val timestamp = payload.optLong("timestamp", 0L)
    if (timestamp <= 0) return null
    val message = JSONObject()
      .put("id", id)
      .put("upeerId", upeerId)
      .put("isMine", payload.optBoolean("isMine"))
      .put("content", payload.optString("content").ifBlank { payload.optString("message") })
      .put("status", statuses.firstOrNull { it == payload.optString("status") } ?: "sent")
      .put("timestamp", timestamp)
      .put("version", payload.optInt("version", 0))
    if (payload.optBoolean("isDeleted") || payload.optBoolean("deleted")) message.put("deleted", true)
    if (payload.optBoolean("isEdited")) message.put("isEdited", true)
    payload.optString("replyTo").takeIf { it.isNotBlank() }?.let { message.put("replyTo", it) }
    payload.optLong("receivedAt", 0L).takeIf { it > 0 }?.let { message.put("receivedAt", it) }
    return message
  }

  fun isAttachment(content: String): Boolean {
    val trimmed = content.trimStart()
    if (!trimmed.startsWith("{")) return false
    val parsed = runCatching { JSONObject(trimmed) }.getOrNull() ?: return false
    return parsed.optString("type") == "file" || parsed.optString("type") == "image"
  }

  fun cursor(message: JSONObject): String? {
    val timestamp = message.optLong("timestamp", 0L)
    val id = message.optString("id")
    if (timestamp <= 0 || id.isBlank()) return null
    return "$timestamp:$id"
  }

  fun parseCursor(value: Any?): Pair<Long, String> {
    if (value !is String) return 0L to ""
    val separator = value.indexOf(':')
    if (separator <= 0) return 0L to ""
    val timestamp = value.substring(0, separator).toLongOrNull() ?: return 0L to ""
    if (timestamp < 0) return 0L to ""
    return timestamp to value.substring(separator + 1)
  }

  fun eventId(deviceId: String, message: JSONObject): String? {
    val id = message.optString("id")
    if (id.isBlank()) return null
    val eventId = "history-$deviceId-$id"
    return eventId.takeIf(SyncPackets::validEventId)
  }
}
