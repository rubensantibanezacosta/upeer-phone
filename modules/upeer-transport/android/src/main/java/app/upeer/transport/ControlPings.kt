package app.upeer.transport

import org.json.JSONObject

internal object ControlPings {
  fun pong(): JSONObject = JSONObject().put("type", "PONG")

  fun active(nowMillis: Long): JSONObject = JSONObject()
    .put("type", "PING")
    .put("activeAt", nowMillis)
}
