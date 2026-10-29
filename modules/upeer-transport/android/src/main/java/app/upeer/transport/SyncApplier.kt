package app.upeer.transport

import android.util.Log
import org.json.JSONObject

internal class SyncApplier(private val state: ProtocolStateStore) {
  fun apply(record: SyncEventRecord): SyncApplyOutcome {
    val outcome = project(record)
    when (outcome) {
      SyncApplyOutcome.APPLIED -> Log.i(TAG, "Evento ${record.kind} aplicado en el almacén local")
      SyncApplyOutcome.UNCHANGED -> Log.i(TAG, "Evento ${record.kind} sin cambios que aplicar")
      SyncApplyOutcome.UNSUPPORTED -> Log.i(TAG, "Evento ${record.kind} sin proyección local todavía")
      SyncApplyOutcome.REJECTED -> Log.w(TAG, "Evento ${record.kind} rechazado por contenido inválido")
    }
    return outcome
  }

  private fun project(record: SyncEventRecord): SyncApplyOutcome {
    val payload = record.payload
    if (record.kind == SyncEventKinds.MESSAGE_UPSERT) return applyMessage(payload)
    val applied = when (record.kind) {
      SyncEventKinds.MESSAGE_STATUS -> applyStatus(payload)
      SyncEventKinds.MESSAGE_EDIT -> applyEdit(payload)
      SyncEventKinds.MESSAGE_DELETE -> applyDelete(payload)
      SyncEventKinds.CHAT_CLEAR -> applyChatClear(payload)
      SyncEventKinds.REACTION_SET -> applyReaction(payload, record.occurredAt)
      SyncEventKinds.GROUP_UPSERT -> state.groups.upsert(payload)
      SyncEventKinds.GROUP_DELETE -> deleteGroup(payload.optString("groupId"))
      SyncEventKinds.CONTACT_PROFILE -> applyContactProfile(payload)
      SyncEventKinds.SETTINGS_UPDATE -> applySettings(payload, record.occurredAt)
      else -> return SyncApplyOutcome.UNSUPPORTED
    }
    return if (applied) SyncApplyOutcome.APPLIED else SyncApplyOutcome.REJECTED
  }

  fun applyAll(records: List<SyncEventRecord>): Int {
    var applied = 0
    for (record in records) {
      if (state.syncEvents.applied(record.eventId)) continue
      val outcome = try {
        apply(record)
      } catch (cause: Exception) {
        Log.w(TAG, "No se pudo aplicar el evento ${record.eventId}, se reintentará: ${cause.message ?: cause.javaClass.simpleName}")
        continue
      }
      if (outcome == SyncApplyOutcome.REJECTED) {
        Log.w(TAG, "Evento ${record.eventId} descartado por contenido inválido")
        state.syncEvents.markApplied(record.eventId)
        continue
      }
      state.syncEvents.markApplied(record.eventId)
      applied += 1
    }
    return applied
  }

  fun repairMissingMessages(limit: Int = REPAIR_BATCH): Int {
    var repaired = 0
    for (record in state.syncEvents.inboundMessages(limit)) {
      val outcome = try {
        apply(record)
      } catch (cause: Exception) {
        Log.w(TAG, "No se pudo recuperar el evento ${record.eventId}: ${cause.message ?: cause.javaClass.simpleName}")
        continue
      }
      if (outcome == SyncApplyOutcome.APPLIED) repaired += 1
    }
    return repaired
  }

  private fun deleteGroup(groupId: String): Boolean {
    state.groupKeys.forget(groupId)
    return state.groups.delete(groupId)
  }

  private fun applyMessage(payload: JSONObject): SyncApplyOutcome {
    val message = SyncHistoryPayload.local(payload) ?: return SyncApplyOutcome.REJECTED
    val id = message.optString("id")
    val content = message.optString("content")
    if (state.messages.find(id) != null) return SyncApplyOutcome.UNCHANGED
    state.messages.save(message)
    if (SyncHistoryPayload.isAttachment(content)) state.syncEvents.markUnsupported(UNSUPPORTED_ATTACHMENT)
    return SyncApplyOutcome.APPLIED
  }

  private fun applyStatus(payload: JSONObject): Boolean {
    val id = payload.optString("id")
    val status = payload.optString("status")
    if (id.isBlank() || status !in STATUSES) return false
    val existing = state.messages.find(id) ?: return false
    if (existing.optString("status") == status) return true
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
    if (existing.optInt("version", 0) > version) return true
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
    if (existing.optBoolean("deleted")) return true
    return state.messages.deleteLocally(id, existing.optString("upeerId"))
  }

  private fun applyChatClear(payload: JSONObject): Boolean {
    val upeerId = payload.optString("upeerId").lowercase()
    if (!SyncPackets.validChatId(upeerId)) return false
    return state.messages.deleteConversation(upeerId)
  }

  private fun applyReaction(payload: JSONObject, occurredAt: Long): Boolean {
    val fields = SyncReaction.parse(payload) ?: return false
    val applied = state.messages.applySyncedReaction(
      fields.messageId,
      fields.actorUpeerId,
      fields.emoji,
      fields.remove,
      occurredAt,
    )
    if (!applied) {
      val known = state.messages.find(fields.messageId) != null
      val motivo = if (known) "reacción ya registrada para ${fields.actorUpeerId.take(10)}" else "mensaje ${fields.messageId} ausente"
      Log.w(TAG, "Reacción ${fields.emoji} no aplicada: $motivo")
    }
    return applied
  }

  private fun applySettings(payload: JSONObject, occurredAt: Long): Boolean {
    val entity = SyncSettings.entity(payload) ?: return false
    val block = payload.optString("block")
    if (SyncSettings.isRemoteBlock(block)) return true
    if (!state.claimSettingsUpdate(entity, occurredAt)) return false
    if (SyncSettings.isBooleanBlock(block)) {
      val patch = SyncSettings.parse(payload) ?: return false
      val changed = state.applySyncSettings(entity, patch.values, occurredAt)
      Log.i(TAG, "Ajustes de $entity sincronizados: ${if (changed) "aplicados" else "ya estaban igual"}")
      return true
    }
    val retention = SyncSettings.parseRetention(payload) ?: return false
    val changed = state.applyRetentionSync(retention, occurredAt)
    Log.i(TAG, "Plazo de ${retention.chatId} sincronizado: ${if (changed) "aplicado" else "ya estaba igual"}")
    return true
  }

  private fun applyContactProfile(payload: JSONObject): Boolean {
    val upeerId = payload.optString("upeerId").lowercase()
    if (upeerId.length != 64) return false
    val name = payload.optString("name").trim()
    val avatar = ProfileFields.avatar(payload.optString("avatar"))
    val learnedKey = state.rememberContactKey(upeerId, payload.optString("publicKey"))
    if (name.isEmpty() && avatar.isEmpty()) return learnedKey
    return state.rememberContactProfile(upeerId, name, avatar) || learnedKey
  }

  private companion object {
    const val TAG = "UPeerTransport"
    const val MAX_CONTENT = 50_000
    const val REPAIR_BATCH = 200
    const val UNSUPPORTED_ATTACHMENT = "ATTACHMENT"
    val STATUSES = setOf("pending", "sent", "vaulted", "delivered", "read", "failed")
  }
}
