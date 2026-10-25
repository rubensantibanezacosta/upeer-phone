package app.upeer.transport

import android.util.Log
import org.json.JSONObject

internal class SyncApplier(private val state: ProtocolStateStore) {
  fun apply(record: SyncEventRecord): Boolean {
    val payload = record.payload
    val applied = when (record.kind) {
      SyncEventKinds.MESSAGE_UPSERT -> applyMessage(payload)
      SyncEventKinds.MESSAGE_STATUS -> applyStatus(payload)
      SyncEventKinds.MESSAGE_EDIT -> applyEdit(payload)
      SyncEventKinds.MESSAGE_DELETE -> applyDelete(payload)
      SyncEventKinds.CHAT_CLEAR -> applyChatClear(payload)
      SyncEventKinds.REACTION_SET -> applyReaction(payload)
      SyncEventKinds.CONTACT_PROFILE -> applyContactProfile(payload)
      else -> {
        Log.i(TAG, "Evento ${record.kind} sin equivalente local todavía")
        false
      }
    }
    if (applied) Log.i(TAG, "Evento ${record.kind} aplicado en el almacén local")
    return applied
  }

  fun applyAll(records: List<SyncEventRecord>): Int {
    var applied = 0
    for (record in records) {
      if (state.syncEvents.applied(record.eventId)) continue
      runCatching { apply(record) }.onFailure { cause ->
        Log.w(TAG, "No se pudo aplicar el evento ${record.eventId}: ${cause.message ?: cause.javaClass.simpleName}")
      }
      state.syncEvents.markApplied(record.eventId)
      applied += 1
    }
    return applied
  }

  private fun applyMessage(payload: JSONObject): Boolean {
    val message = SyncHistoryPayload.local(payload) ?: return false
    if (state.messages.find(message.optString("id")) != null) return false
    state.messages.save(message)
    return true
  }

  private fun applyStatus(payload: JSONObject): Boolean {
    val id = payload.optString("id")
    val status = payload.optString("status")
    if (id.isBlank() || status !in STATUSES) return false
    val existing = state.messages.find(id) ?: return false
    if (existing.optString("status") == status) return false
    state.messages.markStatus(id, status)
    return true
  }

  private fun applyEdit(payload: JSONObject): Boolean {
    val id = payload.optString("id")
    val content = payload.optString("message")
    if (id.isBlank() || content.isBlank()) return false
    if (content.length > MAX_CONTENT) return false
    val version = payload.optInt("version", 1)
    val existing = state.messages.find(id) ?: return false
    if (existing.optInt("version", 0) > version) return false
    val upeerId = existing.optString("upeerId")
    return if (existing.optBoolean("isMine")) {
      state.messages.updateLocalContent(id, upeerId, content, version)
    } else {
      state.messages.updateIncomingContent(id, upeerId, content, version) != null
    }
  }

  private fun applyDelete(payload: JSONObject): Boolean {
    val id = payload.optString("id")
    if (id.isBlank()) return false
    val existing = state.messages.find(id) ?: return false
    if (existing.optBoolean("deleted")) return false
    return state.messages.deleteLocally(id, existing.optString("upeerId"))
  }

  private fun applyChatClear(payload: JSONObject): Boolean {
    val upeerId = payload.optString("upeerId").lowercase()
    if (upeerId.length != 64) return false
    return state.messages.deleteConversation(upeerId)
  }

  private fun applyReaction(payload: JSONObject): Boolean {
    val id = payload.optString("id")
    val emoji = payload.optString("emoji")
    val actor = payload.optString("actorUpeerId").lowercase()
    if (id.isBlank() || emoji.isBlank() || actor.length != 64) return false
    return state.messages.applyReaction(id, actor, emoji, payload.optBoolean("remove"))
  }

  private fun applyContactProfile(payload: JSONObject): Boolean {
    val upeerId = payload.optString("upeerId").lowercase()
    if (upeerId.length != 64) return false
    val name = payload.optString("name").trim()
    val avatar = ProfileFields.avatar(payload.optString("avatar"))
    if (name.isEmpty() && avatar.isEmpty()) return false
    return state.rememberContactProfile(upeerId, name, avatar)
  }

  private companion object {
    const val TAG = "UPeerTransport"
    const val MAX_CONTENT = 50_000
    val STATUSES = setOf("pending", "sent", "vaulted", "delivered", "read", "failed")
  }
}
