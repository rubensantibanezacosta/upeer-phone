package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal object ChatReactions {
  const val MAX_EMOJI_LENGTH = 16
  const val MAX_DISTINCT = 20
  const val MAX_ACTORS_PER_EMOJI = 64

  fun validEmoji(emoji: String): Boolean = emoji.isNotBlank() && emoji.length <= MAX_EMOJI_LENGTH

  fun data(messageID: String, emoji: String, remove: Boolean, nowMillis: Long): JSONObject = JSONObject()
    .put("type", "CHAT_REACTION")
    .put("msgId", messageID)
    .put("emoji", emoji)
    .put("remove", remove)
    .put("timestamp", nowMillis)

  fun messageID(packet: JSONObject): String? {
    val messageID = packet.optString("msgId", packet.optString("id"))
    return if (ReplayPolicy.validMessageID(messageID)) messageID else null
  }

  fun apply(message: JSONObject, actorUpeerId: String, emoji: String, remove: Boolean): Boolean {
    if (!validEmoji(emoji) || actorUpeerId.isBlank()) return false
    val reactions = JSONObject(message.optJSONObject("reactions")?.toString() ?: "{}")
    if (remove) {
      val actors = reactions.optJSONArray(emoji) ?: return false
      if (!removeActor(actors, actorUpeerId)) return false
      if (actors.length() == 0) reactions.remove(emoji) else reactions.put(emoji, actors)
      store(message, reactions)
      return true
    }
    if (reactions.length() >= MAX_DISTINCT && !reactions.has(emoji)) return false
    val actors = reactions.optJSONArray(emoji) ?: JSONArray()
    if (containsActor(actors, actorUpeerId) || actors.length() >= MAX_ACTORS_PER_EMOJI) return false
    actors.put(actorUpeerId)
    reactions.put(emoji, actors)
    store(message, reactions)
    return true
  }

  private fun store(message: JSONObject, reactions: JSONObject) {
    if (reactions.length() == 0) message.remove("reactions") else message.put("reactions", reactions)
  }

  private fun containsActor(actors: JSONArray, actorUpeerId: String): Boolean {
    for (index in 0 until actors.length()) {
      if (actors.optString(index).equals(actorUpeerId, ignoreCase = true)) return true
    }
    return false
  }

  private fun removeActor(actors: JSONArray, actorUpeerId: String): Boolean {
    val kept = JSONArray()
    var removed = false
    for (index in 0 until actors.length()) {
      val value = actors.optString(index)
      if (value.equals(actorUpeerId, ignoreCase = true)) removed = true else kept.put(value)
    }
    if (!removed) return false
    while (actors.length() > 0) actors.remove(actors.length() - 1)
    for (index in 0 until kept.length()) actors.put(kept.optString(index))
    return true
  }
}
