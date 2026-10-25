package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class SyncChannel(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
) {
  fun open(
    device: DeviceIdentity,
    decryptedKeys: DeviceSignedPreKeyStore,
    packet: JSONObject,
    senderDevicePublicKey: String,
    senderDeviceId: String,
  ): SyncPlainEvent? {
    val content = packet.optString("content")
    val nonce = packet.optString("nonce")
    val header = packet.optJSONObject("ratchetHeader")
    if (content.isEmpty() || nonce.isEmpty() || header == null) {
      Log.w(TAG, "SYNC_ENVELOPE de ${senderDeviceId.take(12)} sin campos de ratchet")
      return null
    }
    val message = JSONObject().put("content", content).put("nonce", nonce).put("ratchetHeader", header).toString()
    val context = SyncPackets.contextId(senderDeviceId)
    val existing = state.sessionFor(context)
    var plaintext = existing?.let { session -> attempt { device.crypto.ratchetDecrypt(session, message) } }
    if (plaintext == null) {
      plaintext = acceptFresh(device, decryptedKeys, packet, senderDevicePublicKey, message, existing, context)
    }
    if (plaintext == null) {
      Log.w(TAG, "SYNC_ENVELOPE de ${senderDeviceId.take(12)} no descifrable")
      return null
    }
    return runCatching { SyncPackets.plainEvent(JSONObject(plaintext)) }.getOrNull()
  }

  fun seal(
    activeBridge: Bridge,
    device: DeviceIdentity,
    peer: SyncPeerRecord,
    metadata: JSONObject,
  ): String? {
    val session = state.sessionFor(SyncPackets.contextId(peer.deviceId))
    val started = if (session != null) {
      Session(session, null)
    } else {
      val spk = peer.signedPreKey
      val init = attempt {
        device.crypto.startOutboundSession(peer.devicePublicKey, spk.optString("spkPub"), spk.optLong("spkId"))
      } ?: return null
      val parsed = runCatching { JSONObject(init) }.getOrNull() ?: return null
      Session(parsed.optString("session"), parsed.optString("x3dhInit").takeIf(String::isNotEmpty))
    }
    val encrypted = attempt { JSONObject(device.crypto.ratchetEncrypt(started.json, metadata.toString().toByteArray(Charsets.UTF_8))) }
      ?: return null
    val packet = JSONObject(metadata.toString())
      .put("content", encrypted.optString("content"))
      .put("nonce", encrypted.optString("nonce"))
      .put("ratchetHeader", encrypted.optJSONObject("ratchetHeader"))
      .put("senderDeviceCertificate", device.certificate)
    started.x3dhInit?.let { packet.put("x3dhInit", JSONObject(it)) }
    state.saveSession(SyncPackets.contextId(peer.deviceId), started.json)
    val address = activeBridge.address()
    return attempt {
      identity.current()?.buildLinkedControl(device.deviceId, identity.current()?.upeerID().orEmpty(), address, packet.toString())
    }
  }

  private fun acceptFresh(
    device: DeviceIdentity,
    decryptedKeys: DeviceSignedPreKeyStore,
    packet: JSONObject,
    senderDevicePublicKey: String,
    message: String,
    existing: String?,
    context: String,
  ): String? {
    val init = packet.optJSONObject("x3dhInit") ?: return null
    val spkId = init.optLong("spkId")
    if (!init.optString("ikPub").equals(senderDevicePublicKey, ignoreCase = true)) {
      Log.w(TAG, "SYNC_ENVELOPE con identidad X3DH que no es la del dispositivo emisor")
      return null
    }
    val secret = decryptedKeys.secretFor(device, spkId) ?: return null
    val fresh = attempt { device.crypto.acceptInboundSession(init.optString("ikPub"), init.toString(), secret) } ?: return null
    val plaintext = attempt { device.crypto.ratchetDecrypt(fresh, message) } ?: return null
    if (existing != null) Log.i(TAG, "Sesión de sincronización reiniciada con el dispositivo")
    state.saveSession(context, fresh)
    return plaintext
  }

  private fun <T> attempt(block: () -> T): T? = try {
    block()
  } catch (cause: Exception) {
    Log.w(TAG, "Operación de sincronización fallida: ${(cause.message ?: cause.javaClass.simpleName).take(200)}")
    null
  }

  private data class Session(val json: String, val x3dhInit: String?)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
