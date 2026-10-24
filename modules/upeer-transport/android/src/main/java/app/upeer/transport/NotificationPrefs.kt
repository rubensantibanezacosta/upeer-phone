package app.upeer.transport

import org.json.JSONObject

internal data class NotificationPrefs(
  val messages: Boolean = true,
  val requests: Boolean = true,
  val sound: Boolean = true,
) {
  fun allows(fromKnownContact: Boolean): Boolean = if (fromKnownContact) messages else requests

  fun toJSON(): String = JSONObject()
    .put("messages", messages)
    .put("requests", requests)
    .put("sound", sound)
    .toString()

  companion object {
    val DEFAULT = NotificationPrefs()

    fun parse(raw: String): NotificationPrefs {
      if (raw.isBlank()) return DEFAULT
      return try {
        val json = JSONObject(raw)
        NotificationPrefs(
          messages = json.optBoolean("messages", true),
          requests = json.optBoolean("requests", true),
          sound = json.optBoolean("sound", true),
        )
      } catch (cause: Exception) {
        NotificationPrefs.DEFAULT
      }
    }
  }
}
