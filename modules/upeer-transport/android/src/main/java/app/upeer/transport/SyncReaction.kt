package app.upeer.transport

import org.json.JSONObject

internal data class SyncReactionFields(
  val messageId: String,
  val actorUpeerId: String,
  val emoji: String,
  val remove: Boolean,
)

internal object SyncReaction {
  private const val ACTOR_LENGTH = 64

  fun payload(messageId: String, actorUpeerId: String, emoji: String, remove: Boolean): JSONObject =
    JSONObject()
      .put("messageId", messageId)
      .put("upeerId", actorUpeerId.lowercase())
      .put("emoji", emoji)
      .put("remove", remove)

  fun parse(payload: JSONObject): SyncReactionFields? {
    val messageId = payload.optString("messageId")
    val emoji = payload.optString("emoji")
    val actor = payload.optString("upeerId").lowercase()
    if (messageId.isBlank() || emoji.isBlank() || actor.length != ACTOR_LENGTH) return null
    return SyncReactionFields(messageId, actor, emoji, payload.optBoolean("remove"))
  }
}
