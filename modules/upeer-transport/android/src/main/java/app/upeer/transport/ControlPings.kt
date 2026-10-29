package app.upeer.transport

import org.json.JSONObject

internal object ControlPings {
  fun pong(): JSONObject = JSONObject().put("type", "PONG")

  fun active(nowMillis: Long, shareActivity: Boolean): JSONObject {
    val packet = JSONObject().put("type", "PING")
    if (shareActivity) packet.put("activeAt", nowMillis)
    return packet
  }
}
