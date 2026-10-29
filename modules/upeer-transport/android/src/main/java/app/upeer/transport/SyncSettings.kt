package app.upeer.transport

import org.json.JSONObject

internal data class SyncSettingsPatch(val block: String, val values: Map<String, Boolean>)

internal data class SyncRetentionPatch(val chatId: String, val retention: String)

internal object SyncSettings {
  const val PRIVACY = "privacy"
  const val NOTIFICATIONS = "notifications"
  const val RETENTION = "retention"

  private const val MAX_ID_LENGTH = 200

  private val fields = mapOf(
    PRIVACY to listOf("readReceipts", "shareActivity"),
    NOTIFICATIONS to listOf("messages", "requests", "sound"),
  )

  fun blocks(): Set<String> = fields.keys + RETENTION

  fun isBooleanBlock(block: String): Boolean = fields.containsKey(block)

  fun entity(payload: JSONObject): String? {
    val block = payload.optString("block")
    if (isBooleanBlock(block)) return block
    if (block != RETENTION) return null
    val chatId = chatId(payload) ?: return null
    return "$RETENTION:$chatId"
  }

  fun chatId(payload: JSONObject): String? {
    val candidate = payload.optString("chatId").trim()
    if (candidate.isEmpty() || candidate.length > MAX_ID_LENGTH) return null
    return candidate
  }

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

  fun payload(block: String, values: Map<String, Boolean>): JSONObject? {
    if (block !in fields) return null
    val filtered = values.filterKeys { it in fields.getValue(block) }
    if (filtered.isEmpty()) return null
    val payload = JSONObject().put("block", block)
    filtered.forEach { (field, value) -> payload.put(field, value) }
    return payload
  }
}
