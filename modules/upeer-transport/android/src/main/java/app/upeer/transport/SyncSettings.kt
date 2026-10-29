package app.upeer.transport

import org.json.JSONObject

internal data class SyncSettingsPatch(val block: String, val values: Map<String, Boolean>)

internal object SyncSettings {
  const val PRIVACY = "privacy"
  const val NOTIFICATIONS = "notifications"

  private val fields = mapOf(
    PRIVACY to listOf("readReceipts", "shareActivity"),
    NOTIFICATIONS to listOf("messages", "requests", "sound"),
  )

  fun blocks(): Set<String> = fields.keys

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

  fun payload(block: String, values: Map<String, Boolean>): JSONObject? {
    if (block !in fields) return null
    val filtered = values.filterKeys { it in fields.getValue(block) }
    if (filtered.isEmpty()) return null
    val payload = JSONObject().put("block", block)
    filtered.forEach { (field, value) -> payload.put(field, value) }
    return payload
  }
}
