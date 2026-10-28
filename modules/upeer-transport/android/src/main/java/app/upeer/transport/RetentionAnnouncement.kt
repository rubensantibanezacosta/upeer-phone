package app.upeer.transport

import org.json.JSONObject

internal object RetentionAnnouncement {
  fun parse(packet: JSONObject): String? {
    if (packet.optString("type") != TYPE) return null
    val retention = packet.optString("retention")
    if (retention.isEmpty() || retention != MessageRetention.resolve(retention)) return null
    return retention
  }

  fun data(retention: String): JSONObject? {
    val resolved = MessageRetention.resolve(retention)
    if (resolved != retention) return null
    return JSONObject().put("type", TYPE).put("retention", resolved)
  }

  fun apply(map: Map<String, String>, chatId: String, retention: String): Map<String, String> =
    MessageRetention.sanitize(map + (chatId to retention))

  const val TYPE = "RETENTION_SET"
}
