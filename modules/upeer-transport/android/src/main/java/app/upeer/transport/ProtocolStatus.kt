package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal object ProtocolStatus {
  fun json(state: JSONObject): String {
    val messages = state.optJSONArray("messages") ?: JSONArray()
    var unread = 0
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      if (!message.optBoolean("isMine") && message.optString("status") == "delivered") unread++
    }
    return JSONObject()
      .put("peers", state.optJSONObject("peers")?.length() ?: 0)
      .put("sessions", state.optJSONObject("sessions")?.length() ?: 0)
      .put("messages", messages.length())
      .put("unread", unread)
      .toString()
  }
}
