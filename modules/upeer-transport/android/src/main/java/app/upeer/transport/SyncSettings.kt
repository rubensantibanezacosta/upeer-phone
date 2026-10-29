package app.upeer.transport

import org.json.JSONObject

internal data class SyncSettingsPatch(val block: String, val values: Map<String, Boolean>)

internal data class SyncRetentionPatch(val chatId: String, val retention: String)

internal object SyncSettings {
  const val PRIVACY = "privacy"
  const val NOTIFICATIONS = "notifications"
  const val RETENTION = "retention"
  const val CHAT = "chat"
  const val MESSAGE = "message"
  const val LISTS = "lists"

  private const val MAX_ID_LENGTH = 200

  private val fields = mapOf(
    PRIVACY to listOf("readReceipts", "shareActivity"),
    NOTIFICATIONS to listOf("messages", "requests", "sound"),
  )

  private val chatFields = listOf("archived", "muted", "pinned", "unread")

  private val messageFields = listOf("starred", "pinned")

  private val idFields = mapOf(RETENTION to "chatId", CHAT to "chatId", MESSAGE to "messageId", LISTS to "listId")

  fun blocks(): Set<String> = fields.keys + RETENTION + CHAT + MESSAGE + LISTS

  fun isBooleanBlock(block: String): Boolean = fields.containsKey(block)

  fun isRemoteBlock(block: String): Boolean = block == CHAT || block == MESSAGE || block == LISTS

  fun entity(payload: JSONObject): String? {
    val block = payload.optString("block")
    if (isBooleanBlock(block)) return block
    val field = idFields[block] ?: return null
    val id = id(payload, field) ?: return null
    return "$block:$id"
  }

  fun id(payload: JSONObject, field: String): String? {
    val candidate = payload.optString(field).trim()
    if (candidate.isEmpty() || candidate.length > MAX_ID_LENGTH) return null
    return candidate
  }

  fun chatId(payload: JSONObject): String? = id(payload, "chatId")

  fun parse(payload: JSONObject): SyncSettingsPatch? {
    val block = payload.optString("block")
    val allowed = fields[block] ?: return null
    val values = LinkedHashMap<String, Boolean>()
    for (field in allowed) {
      val value = payload.opt(field)
      if (value is Boolean) values[field] = value
    }
    if (values.isEmpty()) return null
    return SyncSettingsPatch(block, values)
  }

  fun parseRetention(payload: JSONObject): SyncRetentionPatch? {
    if (payload.optString("block") != RETENTION) return null
    val chatId = chatId(payload) ?: return null
    return SyncRetentionPatch(chatId, MessageRetention.resolve(payload.optString("retention")))
  }

  fun retentionPayload(chatId: String, retention: String): JSONObject? {
    val id = chatId.trim()
    if (id.isEmpty() || id.length > MAX_ID_LENGTH) return null
    return JSONObject()
      .put("block", RETENTION)
      .put("chatId", id)
      .put("retention", MessageRetention.resolve(retention))
  }

  fun chatPayload(chatId: String, values: Map<String, Any>): JSONObject? {
    val id = chatId.trim()
    if (id.isEmpty() || id.length > MAX_ID_LENGTH) return null
    val payload = JSONObject().put("block", CHAT).put("chatId", id)
    var count = 0
    for (field in chatFields) {
      val value = values[field]
      if (value is Boolean) {
        payload.put(field, value)
        count += 1
      }
    }
    if (count == 0) return null
    return payload
  }

  fun messagePayload(messageId: String, values: Map<String, Boolean>): JSONObject? {
    val id = messageId.trim()
    if (id.isEmpty() || id.length > MAX_ID_LENGTH) return null
    val payload = JSONObject().put("block", MESSAGE).put("messageId", id)
    var count = 0
    for (field in messageFields) {
      val value = values[field]
      if (value != null) {
        payload.put(field, value)
        count += 1
      }
    }
    if (count == 0) return null
    return payload
  }

  fun payload(block: String, values: Map<String, Boolean>): JSONObject? {
    if (block !in fields) return null
    val filtered = values.filterKeys { it in fields.getValue(block) }
    if (filtered.isEmpty()) return null
    val payload = JSONObject().put("block", block)
    filtered.forEach { (field, value) -> payload.put(field, value) }
    return payload
  }
}
