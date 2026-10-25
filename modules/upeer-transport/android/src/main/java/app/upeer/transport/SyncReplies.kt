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
  private val selfAddress: () -> String,
  private val validate: (ByteArray, String, String) -> Unit,
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
    if (packet.optBoolean("ack", false)) return
    if (!replyHello(activeBridge, sourceAddress, now)) {
      Log.w(TAG, "No se pudo responder SYNC_HELLO a $sourceAddress")
    }
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

  private fun verifyCertificate(certificateJSON: String, accountUpeerId: String, nowMillis: Long): Boolean =
    Transportbridge.verifyDeviceCertificate(certificateJSON, accountUpeerId, nowMillis)

  private fun isRevoked(deviceId: String): Boolean {
    val self = identity.current()?.upeerID() ?: return true
    return deviceBindings.isRevoked(self, deviceId)
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private fun replyHello(activeBridge: Bridge, address: String, now: Long): Boolean {
    val device = identity.currentDevice() ?: return false
    val crypto = identity.current() ?: return false
    val ownAddress = selfAddress().takeIf(String::isNotEmpty) ?: return false
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
    val frame = runCatching {
      crypto.buildLinkedControl(device.deviceId, crypto.upeerID(), ownAddress, hello.toString())
    }.getOrNull() ?: return false
    return send(activeBridge, address, frame)
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
