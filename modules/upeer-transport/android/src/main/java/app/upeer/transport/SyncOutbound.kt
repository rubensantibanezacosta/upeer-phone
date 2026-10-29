package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject
import java.util.UUID

internal class SyncOutbound(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
  private val channel: SyncChannel,
  private val signOrigin: (SyncEventRecord) -> SyncOriginSignature?,
  private val send: (Bridge, String, String) -> Boolean,
  private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  fun publish(activeBridge: Bridge?, kind: String, payload: JSONObject): Boolean {
    val draft = SyncEventDraft(
      eventId = UUID.randomUUID().toString(),
      kind = kind,
      occurredAt = nowMillis(),
      payload = payload,
      direction = OUTBOUND,
    )
    val queued = state.syncEvents.record(draft, nowMillis()) ?: run {
      Log.w(TAG, "No se pudo encolar el evento $kind")
      return false
    }
    val origin = signOrigin(queued) ?: run {
      Log.w(TAG, "El dispositivo no pudo firmar el origen de ${queued.eventId.take(12)}")
      return false
    }
    if (!attachOrigin(queued, origin)) return false
    val record = queued.copy(
      originDeviceId = origin.deviceId,
      originSignature = origin.signature,
      originCertificate = origin.certificate,
    )
    if (activeBridge != null && deliver(activeBridge, record)) {
      Log.i(TAG, "Evento $kind publicado a los dispositivos propios")
      return true
    }
    Log.i(TAG, "Evento $kind en cola: se enviará al alcanzar un dispositivo propio")
    return false
  }

  fun flush(activeBridge: Bridge): Int {
    var sent = 0
    for (peer in state.syncPeers.list()) {
      if (peer.address.isBlank()) continue
      for (record in pendingFor(peer.deviceId)) {
        if (deliverTo(activeBridge, peer, record)) sent += 1
      }
    }
    if (sent > 0) Log.i(TAG, "Eventos propios reenviados: $sent")
    return sent
  }

  fun hasPending(): Boolean = state.syncPeers.list().any { peer ->
    peer.address.isNotBlank() && pendingFor(peer.deviceId).isNotEmpty()
  }

  private fun pendingFor(deviceId: String): List<SyncEventRecord> {
    val ack = state.syncEvents.ackFor(deviceId)
    return state.syncEvents.eventsAfter(ack, BATCH).filter { it.direction == OUTBOUND }
  }

  private fun deliver(activeBridge: Bridge, record: SyncEventRecord): Boolean {
    var sent = false
    for (peer in state.syncPeers.list()) {
      if (peer.address.isBlank()) continue
      if (deliverTo(activeBridge, peer, record)) sent = true
    }
    return sent
  }

  private fun deliverTo(activeBridge: Bridge, peer: SyncPeerRecord, record: SyncEventRecord): Boolean {
    val device = identity.currentDevice() ?: return false
    if (record.originSignature == null) return false
    val event = SyncOutboundEvent.event(record) ?: return false
    val envelope = SyncOutboundEvent.envelope(device.deviceId, device.certificate, peer.deviceId, event)
    val frame = channel.sealEnvelope(
      activeBridge,
      device,
      peer,
      envelope,
      SyncPackets.plainEventJson(event),
    ) ?: run {
      Log.w(TAG, "No se pudo sellar ${record.kind} para ${peer.deviceId.take(12)}")
      return false
    }
    if (send(activeBridge, peer.address, frame)) return true
    Log.w(TAG, "No se pudo enviar ${record.kind} a ${peer.deviceId.take(12)}")
    return false
  }

  private fun attachOrigin(record: SyncEventRecord, origin: SyncOriginSignature): Boolean =
    state.syncEvents.attachOrigin(record.eventId, origin)

  private companion object {
    const val TAG = "UPeerTransport"
    const val OUTBOUND = "outbound"
    const val BATCH = 100
  }
}
