package app.upeer.transport

import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal object MessageSearch {
  const val MAX_RESULTS = 500
  private const val SYSTEM_PREFIX = "__SYS__|"

  fun normalize(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
      .replace(COMBINING_MARKS, "")
      .lowercase(Locale.ROOT)

  fun searchableText(content: String): String? {
    val raw = content.trim()
    if (raw.isEmpty()) return null
    if (raw.startsWith('{') && raw.endsWith('}')) return null
    if (!raw.startsWith(SYSTEM_PREFIX)) return raw
    val text = raw.removePrefix(SYSTEM_PREFIX).trim()
    return text.ifEmpty { null }
  }

  fun matches(query: String, limit: Int, offset: Int, messages: JSONArray): List<JSONObject> {
    val needle = normalize(query.trim())
    if (needle.isEmpty()) return emptyList()
    val found = mutableListOf<JSONObject>()
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      if (message.optBoolean("deleted")) continue
      val id = message.optString("id")
      val chatUpeerId = message.optString("upeerId")
      val timestamp = message.optLong("timestamp", 0L)
      if (id.isBlank() || chatUpeerId.isBlank() || timestamp <= 0) continue
      val text = searchableText(message.optString("content")) ?: continue
      if (!normalize(text).contains(needle)) continue
      found.add(
        JSONObject()
          .put("id", id)
          .put("chatUpeerId", chatUpeerId)
          .put("content", message.optString("content"))
          .put("timestamp", timestamp)
          .put("isMine", message.optBoolean("isMine")),
      )
    }
    return found
      .sortedWith(compareByDescending<JSONObject> { it.optLong("timestamp", 0L) }.thenByDescending { it.optString("id") })
      .drop(offset.coerceAtLeast(0))
      .take(limit.coerceIn(1, MAX_RESULTS))
  }

  private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
