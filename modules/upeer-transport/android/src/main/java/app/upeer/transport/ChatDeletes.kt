package app.upeer.transport

import org.json.JSONObject

internal object ChatDeletes {
  fun data(messageID: String, nowMillis: Long): JSONObject = JSONObject()
    .put("type", "CHAT_DELETE")
    .put("msgId", messageID)
    .put("timestamp", nowMillis)

  fun messageID(packet: JSONObject): String? {
    val messageID = packet.optString("msgId", packet.optString("id"))
    return if (ReplayPolicy.validMessageID(messageID)) messageID else null
  }

  fun deletable(message: JSONObject, senderUpeerId: String): Boolean =
    message.optString("upeerId").equals(senderUpeerId, ignoreCase = true) && !message.optBoolean("isMine")
}
