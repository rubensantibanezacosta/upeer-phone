package app.upeer.transport

import org.json.JSONObject

internal object ControlPings {
  fun pong(): JSONObject = JSONObject().put("type", "PONG")
}
