package app.upeer.transport

import org.json.JSONObject

internal object OutboxFallback {
  const val KIND_CHAT = "CHAT"
  const val KIND_UPDATE = "CHAT_UPDATE"

  fun deservesVault(entry: JSONObject): Boolean {
    if (entry.optString("kind", KIND_CHAT) == KIND_UPDATE) return false
    if (entry.optString("id").isEmpty()) return false
    return entry.optString("upeerId").isNotEmpty()
  }

  fun outOfResends(entry: JSONObject): Boolean =
    entry.has("sentAt") && !OutboxRetryPolicy.canResend(entry.optInt("sends", 0))
}
