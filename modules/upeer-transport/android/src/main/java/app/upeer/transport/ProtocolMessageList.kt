package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal class ProtocolMessageList(
  private val stateProvider: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun owner(messageID: String): String? = synchronized(lock) {
    ProtocolMessageState.find(messages(), messageID)?.optString("upeerId")?.ifEmpty { null }
  }

  fun find(messageID: String): JSONObject? = synchronized(lock) {
    ProtocolMessageState.find(messages(), messageID)?.let(::copy)
  }

  fun conversation(messageID: String): String? = owner(messageID)

  fun save(message: JSONObject) {
    synchronized(lock) {
      put(ProtocolMessageState.upsert(messages(), copy(message)))
      persist()
    }
  }

  fun updateIncomingContent(messageID: String, senderUpeerId: String, content: String, version: Int): JSONObject? =
    synchronized(lock) {
      val messages = messages()
      val updated = ProtocolMessageState.updateIncomingContent(messages, messageID, senderUpeerId, content, version)
        ?: return@synchronized null
      persist()
      updated
    }

  fun updateLocalContent(messageID: String, upeerId: String, content: String, version: Int): Boolean =
    synchronized(lock) {
      val messages = messages()
      val updated = ProtocolMessageState.updateLocalContent(messages, messageID, upeerId, content, version)
        ?: return@synchronized false
      persist()
      updated.optInt("version", 0) == version
    }

  fun markStatus(messageID: String, status: String) {
    synchronized(lock) {
      val message = ProtocolMessageState.find(messages(), messageID) ?: return@synchronized
      message.put("status", status)
      persist()
    }
  }

  fun ack(messageID: String, senderUpeerId: String, status: String): Boolean = synchronized(lock) {
    val message = ProtocolMessageState.find(messages(), messageID) ?: return@synchronized false
    if (!ReplayPolicy.validAck(message.optString("upeerId"), senderUpeerId, message.optBoolean("isMine"), status)) {
      return@synchronized false
    }
    message.put("status", status)
    persist()
    true
  }

  fun applyReaction(messageID: String, actorUpeerId: String, emoji: String, remove: Boolean): Boolean =
    react(messageID, actorUpeerId, emoji, remove)
  fun reactLocally(
    messageID: String,
    conversationUpeerId: String,
    selfUpeerId: String,
    emoji: String,
    remove: Boolean,
  ): Boolean = synchronized(lock) {
    val message = ProtocolMessageState.find(messages(), messageID) ?: return@synchronized false
    if (!message.optString("upeerId").equals(conversationUpeerId, ignoreCase = true)) return@synchronized false
    if (!ChatReactions.apply(message, selfUpeerId, emoji, remove)) return@synchronized false
    persist()
    true
  }

  private fun react(messageID: String, actorUpeerId: String, emoji: String, remove: Boolean): Boolean =
    synchronized(lock) {
      val message = ProtocolMessageState.find(messages(), messageID) ?: return@synchronized false
      if (!message.optString("upeerId").equals(actorUpeerId, ignoreCase = true)) return@synchronized false
      if (!ChatReactions.apply(message, actorUpeerId, emoji, remove)) return@synchronized false
      persist()
      true
    }

  fun delete(messageID: String, senderUpeerId: String): Boolean = synchronized(lock) {
    val message = ProtocolMessageState.find(messages(), messageID) ?: return@synchronized false
    if (message.optBoolean("deleted")) return@synchronized false
    if (!ChatDeletes.deletable(message, senderUpeerId)) return@synchronized false
    ProtocolMessageState.markDeleted(message)
    persist()
    true
  }

  fun deleteLocally(messageID: String, upeerId: String): Boolean = synchronized(lock) {
    val message = ProtocolMessageState.find(messages(), messageID) ?: return@synchronized false
    if (!message.optString("upeerId").equals(upeerId, ignoreCase = true)) return@synchronized false
    if (message.optBoolean("deleted")) return@synchronized false
    ProtocolMessageState.markDeleted(message)
    persist()
    true
  }

  fun deleteConversation(upeerId: String): Boolean = synchronized(lock) {
    val current = messages()
    val remaining = ProtocolMessageState.withoutConversation(current, upeerId)
    if (remaining.length() == current.length()) return@synchronized false
    put(remaining)
    persist()
    true
  }

  fun historyPage(afterTimestamp: Long, afterId: String, limit: Int): List<JSONObject> = synchronized(lock) {
    if (limit <= 0) return@synchronized emptyList()
    collectAfter(afterTimestamp, afterId).take(limit)
  }

  fun historyPending(afterTimestamp: Long, afterId: String): Int = synchronized(lock) {
    collectAfter(afterTimestamp, afterId).size
  }

  private fun collectAfter(afterTimestamp: Long, afterId: String): List<JSONObject> {
    val current = messages()
    val found = mutableListOf<JSONObject>()
    for (index in 0 until current.length()) {
      val message = current.optJSONObject(index) ?: continue
      val timestamp = message.optLong("timestamp", 0L)
      val id = message.optString("id")
      if (timestamp <= 0 || id.isBlank()) continue
      if (timestamp < afterTimestamp) continue
      if (timestamp == afterTimestamp && afterId.isNotBlank() && id <= afterId) continue
      found.add(JSONObject(message.toString()))
    }
    found.sortWith(compareBy({ it.optLong("timestamp", 0L) }, { it.optString("id") }))
    return found
  }

  fun markRead(upeerId: String): List<String> = synchronized(lock) {
    val current = messages()
    val transitioned = mutableListOf<String>()
    for (index in 0 until current.length()) {
      val message = current.optJSONObject(index) ?: continue
      if (message.optString("upeerId") == upeerId && !message.optBoolean("isMine") && message.optString("status") == "delivered") {
        message.put("status", "read")
        transitioned.add(message.optString("id"))
      }
    }
    if (transitioned.isNotEmpty()) persist()
    transitioned
  }

  fun snapshot(upeerId: String, limit: Int): String = synchronized(lock) {
    ProtocolMessageState.snapshot(messages(), upeerId, limit)
  }

  fun searchJSON(query: String, limit: Int, offset: Int): String = synchronized(lock) {
    JSONArray(MessageSearch.matches(query, limit, offset, messages())).toString()
  }

  private fun messages(): JSONArray = stateProvider().optJSONArray("messages") ?: JSONArray()

  private fun put(next: JSONArray) {
    stateProvider().put("messages", next)
  }

  private fun copy(value: JSONObject): JSONObject = JSONObject(value.toString())
}
