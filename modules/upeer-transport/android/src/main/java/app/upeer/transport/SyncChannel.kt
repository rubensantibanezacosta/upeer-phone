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
    val plaintext = openRaw(device, decryptedKeys, packet, senderDevicePublicKey, senderDeviceId) ?: return null
    val parsed = runCatching { SyncPackets.plainEvent(JSONObject(plaintext)) }.getOrNull()
    if (parsed == null) {
      Log.w(TAG, "SYNC_ENVELOPE de ${senderDeviceId.take(12)} con evento ilegible: ${plaintext.take(120)}")
    }
    return parsed
  }

  fun openRaw(
    device: DeviceIdentity,
    decryptedKeys: DeviceSignedPreKeyStore,
    packet: JSONObject,
    senderDevicePublicKey: String,
    senderDeviceId: String,
  ): String? {
    val content = packet.optString("content")
    val nonce = packet.optString("nonce")
    val header = packet.optJSONObject("ratchetHeader")
    if (content.isEmpty() || nonce.isEmpty() || header == null) {
      Log.w(TAG, "SYNC de ${senderDeviceId.take(12)} sin campos de ratchet")
      return null
    }
    val message = JSONObject().put("content", content).put("nonce", nonce).put("ratchetHeader", header).toString()
    val context = SyncPackets.contextId(senderDeviceId)
    val existing = state.sessionFor(context)
    val decrypted = existing?.let { session -> decrypt(device, session, message) }
    if (decrypted != null) {
      state.saveSession(context, decrypted.session)
      return decrypted.plaintext
    }
    if (existing != null) {
      state.clearSession(context)
      Log.w(TAG, "Sesión de sincronización con ${senderDeviceId.take(12)} descartada: el ratchet no pudo descifrar")
    }
    return acceptFresh(device, decryptedKeys, packet, senderDevicePublicKey, message, existing, context)
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
    val result = attempt {
      JSONObject(device.crypto.ratchetEncrypt(started.json, metadata.toString().toByteArray(Charsets.UTF_8)))
    } ?: return null
    val encrypted = runCatching { JSONObject(result.optString("message")) }.getOrNull() ?: return null
    val nextSession = result.optString("session").takeIf(String::isNotEmpty) ?: return null
    val packet = JSONObject(metadata.toString())
      .put("content", encrypted.optString("content"))
      .put("nonce", encrypted.optString("nonce"))
      .put("ratchetHeader", encrypted.optJSONObject("ratchetHeader"))
      .put("senderDeviceCertificate", device.certificate)
    started.x3dhInit?.let { packet.put("x3dhInit", JSONObject(it)) }
    state.saveSession(SyncPackets.contextId(peer.deviceId), nextSession)
    val address = activeBridge.address()
    return attempt {
      device.crypto.buildLinkedControl(device.deviceId, identity.current()?.upeerID().orEmpty(), address, packet.toString())
    }
  }

  private fun decrypt(device: DeviceIdentity, session: String, message: String): Decrypted? {
    val result = attempt { JSONObject(device.crypto.ratchetDecrypt(session, message)) } ?: return null
    val plaintext = attempt { YggdrasilPayloadCodec.hexToBytes(result.optString("plaintextHex")) } ?: return null
    val next = result.optString("session").takeIf(String::isNotEmpty) ?: return null
    return Decrypted(next, String(plaintext, Charsets.UTF_8))
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
    val init = packet.optJSONObject("x3dhInit") ?: run {
      Log.w(TAG, "SYNC de ${packet.optString("senderDeviceId").take(12)} sin x3dhInit y sin sesión previa")
      return null
    }
    val spkId = init.optLong("spkId")
    if (!init.optString("ikPub").equals(senderDevicePublicKey, ignoreCase = true)) {
      Log.w(TAG, "SYNC_ENVELOPE con identidad X3DH que no es la del dispositivo emisor")
      return null
    }
    val secret = decryptedKeys.secretFor(device, spkId) ?: run {
      Log.w(TAG, "SYNC de ${packet.optString("senderDeviceId").take(12)} con SPK $spkId desconocida para este dispositivo")
      return null
    }
    val fresh = attempt { device.crypto.acceptInboundSession(init.optString("ikPub"), init.toString(), secret) } ?: return null
    val decrypted = decrypt(device, fresh, message) ?: run {
      Log.w(TAG, "Sesión entrante de sincronización aceptada pero el mensaje no descifra")
      return null
    }
    if (existing != null) Log.i(TAG, "Sesión de sincronización reiniciada con el dispositivo")
    state.saveSession(context, decrypted.session)
    return decrypted.plaintext
  }

  private fun <T> attempt(block: () -> T): T? = try {
    block()
  } catch (cause: Exception) {
    Log.w(TAG, "Operación de sincronización fallida: ${(cause.message ?: cause.javaClass.simpleName).take(200)}")
    null
  }

  private data class Session(val json: String, val x3dhInit: String?)

  private data class Decrypted(val session: String, val plaintext: String)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
