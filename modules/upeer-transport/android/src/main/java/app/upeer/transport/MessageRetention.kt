package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal object MessageRetention {
  data class Preset(val id: String, val label: String, val short: String, val ms: Long)

  const val OFF = "off"
  private const val HOUR = 60L * 60L * 1000L

  val presets: List<Preset> = listOf(
    Preset(OFF, "Desactivados", "Desactivados", 0L),
    Preset("24h", "24 horas", "24 h", 24L * HOUR),
    Preset("7d", "7 días", "7 días", 7L * 24L * HOUR),
    Preset("90d", "90 días", "90 días", 90L * 24L * HOUR),
  )

  private val byId = presets.associateBy { it.id }

  fun resolve(value: String?): String = if (value != null && byId.containsKey(value)) value else OFF

  fun durationMs(value: String?): Long = byId[resolve(value)]?.ms ?: 0L

  fun label(value: String?): String = byId[resolve(value)]?.short ?: "Desactivados"

  fun notice(value: String?): String? {
    val preset = byId[resolve(value)] ?: return null
    if (preset.ms <= 0L) return null
    return "Los mensajes nuevos se borran a los ${preset.short}."
  }

  fun sanitize(map: Map<String, String>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for ((chatId, value) in map) {
      if (chatId.isBlank() || !byId.containsKey(value)) continue
      if (value == OFF) continue
      result[chatId] = value
    }
    return result
  }

  fun parse(raw: JSONObject?): Map<String, String> {
    if (raw == null) return emptyMap()
    val stored = LinkedHashMap<String, String>()
    for (chatId in raw.keys()) {
      val value = raw.optString(chatId, "")
      if (value.isNotEmpty()) stored[chatId] = value
    }
    return sanitize(stored)
  }

  fun toJson(map: Map<String, String>): JSONObject {
    val json = JSONObject()
    for ((chatId, value) in sanitize(map)) json.put(chatId, value)
    return json
  }

  fun expiresAt(timestamp: Long, durationMs: Long): Long? {
    if (timestamp <= 0L || durationMs <= 0L) return null
    return timestamp + durationMs
  }

  fun isExpired(expiresAt: Long, now: Long): Boolean = expiresAt > 0L && expiresAt <= now

  fun stamp(messages: JSONArray, message: JSONObject, retentionId: String?, now: Long) {
    val existing = ProtocolMessageState.find(messages, message.optString("id"))
    if (existing != null) {
      val kept = existing.optLong("expiresAt", 0L)
      if (kept > 0L) message.put("expiresAt", kept) else message.remove("expiresAt")
      return
    }
    val expires = expiresAt(message.optLong("timestamp", 0L), durationMs(retentionId))
    if (expires != null) message.put("expiresAt", expires) else message.remove("expiresAt")
  }

  fun withoutExpired(messages: JSONArray, now: Long): JSONArray? {
    var removed = false
    val kept = JSONArray()
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      if (isExpired(message.optLong("expiresAt", 0L), now)) {
        removed = true
        continue
      }
      kept.put(message)
    }
    return if (removed) kept else null
  }
}
