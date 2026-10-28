package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal class ProtocolRetention(
  private val stateProvider: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun map(): Map<String, String> = synchronized(lock) {
    MessageRetention.parse(stateProvider().optJSONObject(KEY))
  }

  fun id(chatId: String): String = if (chatId.isBlank()) MessageRetention.OFF else map()[chatId] ?: MessageRetention.OFF

  fun set(map: Map<String, String>) {
    synchronized(lock) {
      stateProvider().put(KEY, MessageRetention.toJson(map))
      persist()
    }
  }

  fun statusJSON(): String {
    val presets = MessageRetention.presets.map { preset ->
      JSONObject()
        .put("id", preset.id)
        .put("label", preset.label)
        .put("short", preset.short)
        .put("ms", preset.ms)
    }
    val chats = JSONObject()
    for ((chatId, value) in map()) chats.put(chatId, value)
    return JSONObject()
      .put("map", chats)
      .put("presets", JSONArray(presets))
      .toString()
  }

  private companion object {
    const val KEY = "retention"
  }
}
