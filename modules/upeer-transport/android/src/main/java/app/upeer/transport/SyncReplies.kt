package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject

internal class SyncReplies(
  private val identity: YggdrasilIdentity,
  private val deviceSignedPreKeys: DeviceSignedPreKeyStore,
  private val deviceBindings: DeviceMessagingBindings,
  private val state: ProtocolStateStore,
  private val policy: InboundPolicy,
  private val channel: SyncChannel,
  private val history: SyncHistory,
  private val verifyOrigin: (SyncEventRecord, String, Long) -> Boolean,
  private val validate: (ByteArray, String, String) -> Unit,
  private val selfAddress: () -> String,
  private val send: (Bridge, String, String) -> Boolean,
  private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  fun handle(
    activeBridge: Bridge,
    payload: ByteArray,
    packet: JSONObject,
    type: String,
    sourceAddress: String,
  ): String {
    val self = identity.current()?.upeerID() ?: throw IllegalStateException("uPeer identity is not available")
    val auth = when (val result = SyncSelfAuth.authenticate(packet, self, nowMillis(), ::verifyCertificate, ::isRevoked)) {
      is SyncSelfAuthResult.Rejected -> {
        Log.w(TAG, "Paquete $type rechazado: ${result.reason}")
        return InboundPolicy.rejected(packet)
      }
      is SyncSelfAuthResult.Accepted -> result.identity
    }
    if (!policy.allow(packet, sourceAddress)) return policy.rateLimited(packet)
    try {
      validate(payload, auth.devicePublicKey, self)
    } catch (cause: Exception) {
      Log.w(TAG, "Firma de $type inválida para el dispositivo ${auth.deviceId.take(12)}: ${message(cause)}")
      return InboundPolicy.rejected(packet)
    }
    val now = nowMillis()
    when (type) {
      SyncPackets.HELLO -> hello(activeBridge, packet, sourceAddress, auth, now)
      SyncPackets.ACK -> acknowledge(packet, auth)
      SyncPackets.ENVELOPE -> envelope(activeBridge, packet, sourceAddress, auth, now)
      SyncPackets.HISTORY_REQ -> historyRequest(activeBridge, packet, sourceAddress, auth, now)
      SyncPackets.HISTORY_RES -> historyResponse(activeBridge, packet, sourceAddress, auth, now)
      else -> Log.i(TAG, "Paquete $type de ${auth.deviceId.take(12)} todavía no se aplica en el móvil")
    }
    return JSONObject()
      .put("type", type)
      .put("senderUpeerId", self)
      .put("deviceId", auth.deviceId)
      .toString()
  }

  private fun hello(
    activeBridge: Bridge,
    packet: JSONObject,
    sourceAddress: String,
    auth: SyncSelfIdentity,
    now: Long,
  ) {
    val binding = SyncHello.binding(packet) ?: run {
      Log.w(TAG, "SYNC_HELLO inválido de ${auth.deviceId.take(12)}")
      return
    }
    if (binding.deviceId != auth.deviceId) {
      Log.w(TAG, "SYNC_HELLO de ${auth.deviceId.take(12)} anuncia otro dispositivo")
      return
    }
    if (!SyncHello.timestampIsFresh(packet.optLong("timestamp", 0L), now)) {
      Log.w(TAG, "SYNC_HELLO de ${auth.deviceId.take(12)} fuera de la ventana de reloj")
      return
    }
    state.syncPeers.remember(binding, sourceAddress, now)
    Log.i(TAG, "Dispositivo propio ${binding.deviceId.take(12)} registrado en $sourceAddress")
    if (!packet.optBoolean("ack", false) && !replyHello(activeBridge, sourceAddress, now)) {
      Log.w(TAG, "No se pudo responder SYNC_HELLO a $sourceAddress")
    }
    val peer = state.syncPeers.list().firstOrNull { it.deviceId == binding.deviceId } ?: return
    history.requestIfInitiator(activeBridge, peer)
  }

  private fun historyRequest(
    activeBridge: Bridge,
    packet: JSONObject,
    sourceAddress: String,
    auth: SyncSelfIdentity,
    now: Long,
  ) {
    val device = identity.currentDevice() ?: return
    val peer = state.syncPeers.list().firstOrNull { it.deviceId == auth.deviceId } ?: run {
      Log.w(TAG, "SYNC_HISTORY_REQ de ${auth.deviceId.take(12)} sin SYNC_HELLO previo")
      return
    }
    if (!SyncPackets.recipientMatches(packet, device.deviceId)) return
    val plaintext = channel.openRaw(device, deviceSignedPreKeys, packet, auth.devicePublicKey, auth.deviceId) ?: run {
      Log.w(TAG, "SYNC_HISTORY_REQ de ${auth.deviceId.take(12)} no descifrable")
      return
    }
    val payload = runCatching { JSONObject(plaintext) }.getOrNull() ?: return
    history.respond(activeBridge, peer, payload)
    state.syncPeers.remember(
      SyncDeviceBinding(peer.deviceId, peer.devicePublicKey, peer.certificateJSON, peer.signedPreKey),
      sourceAddress,
      now,
    )
    history.request(activeBridge, peer)
  }

  private fun historyResponse(
    activeBridge: Bridge,
    packet: JSONObject,
    sourceAddress: String,
    auth: SyncSelfIdentity,
    now: Long,
  ) {
    val device = identity.currentDevice() ?: return
    val peer = state.syncPeers.list().firstOrNull { it.deviceId == auth.deviceId } ?: run {
      Log.w(TAG, "SYNC_HISTORY_RES de ${auth.deviceId.take(12)} sin SYNC_HELLO previo")
      return
    }
    if (!SyncPackets.recipientMatches(packet, device.deviceId)) return
    val plaintext = channel.openRaw(device, deviceSignedPreKeys, packet, auth.devicePublicKey, auth.deviceId) ?: run {
      Log.w(TAG, "SYNC_HISTORY_RES de ${auth.deviceId.take(12)} no descifrable")
      return
    }
    val payload = runCatching { JSONObject(plaintext) }.getOrNull() ?: return
    history.apply(activeBridge, peer, payload)
    state.syncPeers.remember(
      SyncDeviceBinding(peer.deviceId, peer.devicePublicKey, peer.certificateJSON, peer.signedPreKey),
      sourceAddress,
      now,
    )
  }

  private fun acknowledge(packet: JSONObject, auth: SyncSelfIdentity) {
    val fields = SyncPackets.ackFields(packet) ?: run {
      Log.w(TAG, "SYNC_ACK inválido de ${auth.deviceId.take(12)}")
      return
    }
    if (state.syncEvents.recordAck(auth.deviceId, fields.sequence)) {
      Log.i(TAG, "SYNC_ACK de ${auth.deviceId.take(12)} hasta ${fields.sequence}")
    }
  }

  private fun envelope(
    activeBridge: Bridge,
    packet: JSONObject,
    sourceAddress: String,
    auth: SyncSelfIdentity,
    now: Long,
  ) {
    val device = identity.currentDevice() ?: return
    val peer = state.syncPeers.list().firstOrNull { it.deviceId == auth.deviceId } ?: run {
      Log.w(TAG, "SYNC_ENVELOPE de ${auth.deviceId.take(12)} sin SYNC_HELLO previo")
      return
    }
    val event = channel.open(device, deviceSignedPreKeys, packet, auth.devicePublicKey, auth.deviceId) ?: return
    val originVerified = verifyOriginSignature(event, now)
    when (val decision = SyncEnvelope.decide(packet, event, device.deviceId, auth.deviceId, originVerified)) {
      is SyncEnvelopeDecision.Rejected -> Log.w(TAG, "SYNC_ENVELOPE rechazado: ${decision.reason}")
      is SyncEnvelopeDecision.Accepted -> {
        val recorded = state.syncEvents.record(SyncEnvelope.draft(decision.event, decision.originDeviceId), now)
        if (recorded != null) {
          Log.i(TAG, "Evento ${decision.event.kind} de ${decision.originDeviceId.take(12)} registrado")
        }
      }
    }
    state.syncPeers.remember(
      SyncDeviceBinding(peer.deviceId, peer.devicePublicKey, peer.certificateJSON, peer.signedPreKey),
      sourceAddress,
      now,
    )
    sendAck(activeBridge, sourceAddress, event)
  }

  private fun verifyOriginSignature(event: SyncPlainEvent, now: Long): Boolean {
    if (event.originSignature == null) return false
    val account = identity.current()?.upeerID() ?: return false
    val record = SyncEventRecord(
      eventId = event.eventId,
      kind = event.kind,
      occurredAt = event.occurredAt,
      sequence = event.sequence,
      payload = event.payload,
      direction = "inbound",
      originDeviceId = event.originDeviceId,
      originSignature = event.originSignature,
      originCertificate = event.originCertificate,
    )
    return verifyOrigin(record, account, now)
  }

  private fun sendAck(activeBridge: Bridge, address: String, event: SyncPlainEvent) {
    val device = identity.currentDevice() ?: return
    val ack = SyncPackets.ack(SyncSender(device.deviceId, device.certificate), event.eventId, event.sequence) ?: return
    val frame = syncFrame(ack) ?: return
    if (!send(activeBridge, address, frame)) Log.w(TAG, "No se pudo enviar SYNC_ACK a $address")
  }

  private fun replyHello(activeBridge: Bridge, address: String, now: Long): Boolean {
    val device = identity.currentDevice() ?: return false
    val signedPreKey = runCatching { JSONObject(deviceSignedPreKeys.currentJSON(device)) }.getOrNull() ?: return false
    val hello = SyncPackets.hello(
      SyncSender(device.deviceId, device.certificate),
      JSONObject()
        .put("spkPub", signedPreKey.optString("spkPub"))
        .put("spkSig", signedPreKey.optString("spkSig"))
        .put("spkId", signedPreKey.optLong("spkId")),
      ack = true,
      nowMillis = now,
    )
    val frame = syncFrame(hello) ?: return false
    return send(activeBridge, address, frame)
  }

  private fun syncFrame(packet: JSONObject): String? {
    val device = identity.currentDevice() ?: return null
    val account = identity.current() ?: return null
    val address = selfAddress().takeIf(String::isNotEmpty) ?: return null
    return runCatching {
      account.buildLinkedControl(device.deviceId, account.upeerID(), address, packet.toString())
    }.getOrNull()
  }

  private fun verifyCertificate(certificateJSON: String, accountUpeerId: String, nowMillis: Long): Boolean =
    Transportbridge.verifyDeviceCertificate(certificateJSON, accountUpeerId, nowMillis)

  private fun isRevoked(deviceId: String): Boolean {
    val self = identity.current()?.upeerID() ?: return true
    return deviceBindings.isRevoked(self, deviceId)
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
