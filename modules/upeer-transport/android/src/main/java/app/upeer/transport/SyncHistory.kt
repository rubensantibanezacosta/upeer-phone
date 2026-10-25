package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class SyncHistory(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
  private val channel: SyncChannel,
  private val signOrigin: (SyncEventRecord) -> SyncOriginSignature?,
  private val verifyOrigin: (SyncEventRecord) -> Boolean,
  private val send: (Bridge, String, String) -> Boolean,
  private val applyEvents: () -> Int = { 0 },
  private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  private val lastRequest = mutableMapOf<String, Long>()

  fun request(activeBridge: Bridge, peer: SyncPeerRecord) {
    val progress = state.history.progress(peer.deviceId)
    if (progress.done) return
    val now = nowMillis()
    if (now - (lastRequest[peer.deviceId] ?: 0L) < REQUEST_COOLDOWN_MS) return
    lastRequest[peer.deviceId] = now
    Log.i(TAG, "Pidiendo histórico a ${peer.deviceId.take(12)} desde ${progress.cursor ?: "el principio"}")
    sendRequest(activeBridge, peer, progress.cursor)
  }

  fun resetBackoff(peerDeviceId: String) {
    lastRequest.remove(peerDeviceId)
  }

  fun restart(peerDeviceId: String) {
    state.history.reopen(peerDeviceId)
    lastRequest.remove(peerDeviceId)
  }

  fun requestIfInitiator(activeBridge: Bridge, peer: SyncPeerRecord) {
    val myDeviceId = identity.currentDevice()?.deviceId ?: return
    if (myDeviceId > peer.deviceId && state.sessionFor(SyncPackets.contextId(peer.deviceId)) == null) return
    request(activeBridge, peer)
  }

  fun respond(activeBridge: Bridge, peer: SyncPeerRecord, payload: JSONObject) {
    val requestId = payload.optString("requestId")
    if (!SyncPackets.validRequestId(requestId)) {
      Log.w(TAG, "SYNC_HISTORY_REQ sin requestId válido para ${peer.deviceId.take(12)}")
      return
    }
    val device = identity.currentDevice() ?: run {
      Log.w(TAG, "SYNC_HISTORY_REQ sin dispositivo enrolado; no se puede responder")
      return
    }
    val cursor = SyncHistoryPayload.parseCursor(payload.opt("cursor"))
    val limit = payload.optInt("limit", PAGE_SIZE).coerceIn(1, MAX_PAGE)
    val rows = state.messages.historyPage(cursor.first, cursor.second, limit + 1)
    val done = rows.size <= limit
    val page = if (done) rows else rows.take(limit)
    val entries = JSONArray()
    for (message in page) {
      val entry = historyEntry(device.deviceId, message) ?: continue
      entries.put(entry)
    }
    Log.i(TAG, "Histórico para ${peer.deviceId.take(12)}: ${entries.length()} de ${page.size} mensajes (${rows.size} leídos)")
    if (page.isNotEmpty() && entries.length() == 0) {
      Log.w(TAG, "Histórico para ${peer.deviceId.take(12)} sin ningún evento firmado: revisa la clave del dispositivo")
    }
    val metadata = SyncPackets.historyMeta(sender(device), peer.deviceId, SyncPackets.HISTORY_RES, requestId) ?: return
    metadata.put("entries", entries)
    val last = page.lastOrNull()
    val nextCursor = last?.let(SyncHistoryPayload::cursor)
    if (nextCursor == null) metadata.put("nextCursor", JSONObject.NULL) else metadata.put("nextCursor", nextCursor)
    metadata.put("done", done || nextCursor == null)
    val frame = channel.seal(activeBridge, device, peer, metadata) ?: run {
      Log.w(TAG, "No se pudo sellar el histórico para ${peer.deviceId.take(12)}")
      return
    }
    if (!send(activeBridge, peer.address, frame)) Log.w(TAG, "No se pudo enviar SYNC_HISTORY_RES")
  }

  fun apply(activeBridge: Bridge, peer: SyncPeerRecord, payload: JSONObject) {
    val entries = payload.optJSONArray("entries")
    var failed = 0
    if (entries != null && entries.length() <= MAX_PAGE) {
      val now = nowMillis()
      var applied = 0
      for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: continue
        if (applyEntry(entry, now)) applied += 1
        else if (!state.syncEvents.contains(entry.optString("eventId"))) failed += 1
      }
      if (applied > 0) Log.i(TAG, "Histórico de ${peer.deviceId.take(12)}: $applied eventos registrados")
      val projected = applyEvents()
      if (projected > 0) Log.i(TAG, "Histórico de ${peer.deviceId.take(12)}: $projected eventos proyectados")
    }
    val nextCursor = payload.optString("nextCursor").ifBlank { null }
    if (failed > 0) {
      lastRequest.remove(peer.deviceId)
      Log.w(TAG, "Histórico de ${peer.deviceId.take(12)}: $failed eventos rechazados, no avanzo el cursor")
      return
    }
    val done = payload.optBoolean("done", false)
    state.history.save(peer.deviceId, nextCursor, done || nextCursor == null)
    if (!done && nextCursor != null) {
      val refreshed = state.syncPeers.list().firstOrNull { it.deviceId == peer.deviceId } ?: return
      lastRequest[peer.deviceId] = nowMillis()
      sendRequest(activeBridge, refreshed, nextCursor)
    }
  }

  private fun sendRequest(activeBridge: Bridge, peer: SyncPeerRecord, cursor: String?) {
    if (peer.address.isBlank()) return
    val device = identity.currentDevice() ?: return
    val requestId = UUID.randomUUID().toString()
    val metadata = SyncPackets.historyMeta(sender(device), peer.deviceId, SyncPackets.HISTORY_REQ, requestId) ?: return
    if (cursor.isNullOrBlank()) metadata.put("cursor", JSONObject.NULL) else metadata.put("cursor", cursor)
    metadata.put("limit", PAGE_SIZE)
    val frame = channel.seal(activeBridge, device, peer, metadata) ?: run {
      Log.w(TAG, "No se pudo sellar la petición de histórico para ${peer.deviceId.take(12)}")
      return
    }
    if (!send(activeBridge, peer.address, frame)) Log.w(TAG, "No se pudo enviar SYNC_HISTORY_REQ")
  }

  private fun historyEntry(deviceId: String, message: JSONObject): JSONObject? {
    val occurredAt = message.optLong("timestamp", 0L)
    if (occurredAt <= 0) return null
    val eventId = SyncHistoryPayload.eventId(deviceId, message) ?: return null
    val payload = SyncHistoryPayload.desktop(message)
    val record = SyncEventRecord(
      eventId = eventId,
      kind = SyncEventKinds.MESSAGE_UPSERT,
      occurredAt = occurredAt,
      sequence = occurredAt,
      payload = payload,
      direction = "outbound",
      originDeviceId = deviceId,
      originSignature = null,
      originCertificate = null,
    )
    val origin = signOrigin(record) ?: run {
      Log.w(TAG, "No se pudo firmar el mensaje ${message.optString("id")} para el histórico")
      return null
    }
    return JSONObject()
      .put("eventId", eventId)
      .put("kind", record.kind)
      .put("occurredAt", occurredAt)
      .put("sequence", occurredAt)
      .put("payload", payload)
      .put("originDeviceId", origin.deviceId)
      .put("originSignature", origin.signature)
      .put("originCertificate", origin.certificate)
  }

  private fun applyEntry(entry: JSONObject, now: Long): Boolean {
    val eventId = entry.optString("eventId")
    if (!SyncPackets.validEventId(eventId)) {
      Log.w(TAG, "Evento de histórico con id inválido: ${eventId.take(40)}")
      return false
    }
    val kind = entry.optString("kind")
    if (!SyncEventKinds.isValid(kind)) {
      Log.w(TAG, "Evento de histórico $eventId con tipo desconocido: ${kind.take(30)}")
      return false
    }
    if (state.syncEvents.contains(eventId)) return false
    val occurredAt = entry.optLong("occurredAt", 0L)
    val sequence = entry.optLong("sequence", 0L)
    val payload = entry.optJSONObject("payload") ?: run {
      Log.w(TAG, "Evento de histórico $eventId sin payload")
      return false
    }
    if (occurredAt <= 0 || sequence <= 0) return false
    val originDeviceId = entry.optString("originDeviceId").lowercase().ifBlank { null }
    val originSignature = entry.optString("originSignature").lowercase().ifBlank { null }
    val originCertificate = entry.optJSONObject("originCertificate")
    val record = SyncEventRecord(
      eventId = eventId,
      kind = kind,
      occurredAt = occurredAt,
      sequence = sequence,
      payload = payload,
      direction = "inbound",
      originDeviceId = originDeviceId,
      originSignature = originSignature,
      originCertificate = originCertificate,
    )
    if (!verifyOrigin(record)) {
      Log.w(TAG, "Evento de histórico $eventId sin firma de origen válida")
      return false
    }
    val draft = SyncEventDraft(
      eventId = eventId,
      kind = kind,
      occurredAt = occurredAt,
      payload = payload,
      direction = "inbound",
      originDeviceId = originDeviceId,
      originSignature = originSignature,
      originCertificate = originCertificate,
    )
    return state.syncEvents.record(draft, now) != null
  }

  private fun sender(device: DeviceIdentity): SyncSender = SyncSender(device.deviceId, device.certificate)

  private companion object {
    const val TAG = "UPeerTransport"
    const val PAGE_SIZE = 50
    const val MAX_PAGE = 200
    const val REQUEST_COOLDOWN_MS = 30_000L
  }
}
