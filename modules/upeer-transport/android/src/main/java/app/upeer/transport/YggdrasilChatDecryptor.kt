package app.upeer.transport

import android.util.Log
import org.json.JSONObject

internal data class DecryptedChat(val messageID: String, val plaintext: String)

internal class YggdrasilChatDecryptor(
  private val identity: YggdrasilIdentity,
  private val signedPreKeys: SignedPreKeyStore,
  private val deviceSignedPreKeys: DeviceSignedPreKeyStore,
  private val state: ProtocolStateStore,
  private val sendDrReset: (String) -> Unit,
) {
  fun decrypt(packet: JSONObject, senderUpeerId: String, publicKey: String, isDevicePacket: Boolean): DecryptedChat {
    val plaintext = decryptContent(packet, senderUpeerId, publicKey, isDevicePacket)
    state.messages.save(
      JSONObject()
        .put("id", packet.optString("id"))
        .put("upeerId", senderUpeerId)
        .put("isMine", false)
        .put("content", plaintext)
        .put("status", "delivered")
        .put("version", packet.optInt("version", 1))
        .put("isEdited", false)
        .put("timestamp", packet.optLong("timestamp", System.currentTimeMillis()))
        .put("receivedAt", System.currentTimeMillis()),
    )
    Log.i(TAG, "CHAT descifrado de $senderUpeerId id=${packet.optString("id")} (${plaintext.length} caracteres)")
    return DecryptedChat(packet.optString("id"), plaintext)
  }

  fun decryptUpdate(packet: JSONObject, senderUpeerId: String, publicKey: String, isDevicePacket: Boolean): String {
    val plaintext = decryptContent(packet, senderUpeerId, publicKey, isDevicePacket)
    Log.i(TAG, "CHAT_UPDATE descifrado de $senderUpeerId id=${packet.optString("msgId")} (${plaintext.length} caracteres)")
    return plaintext
  }

  private fun decryptContent(packet: JSONObject, senderUpeerId: String, publicKey: String, isDevicePacket: Boolean): String {
    val content = packet.optString("content")
    val nonce = packet.optString("nonce")
    val ratchetHeader = packet.optJSONObject("ratchetHeader")
    if (content.isEmpty() || nonce.isEmpty() || ratchetHeader == null) {
      sendDrReset(senderUpeerId)
      throw IllegalArgumentException("CHAT is missing ratchet fields")
    }
    val currentIdentity = identity.current() ?: throw IllegalStateException("uPeer identity is not available")
    val existingSession = state.sessionFor(senderUpeerId)
    val message = JSONObject()
      .put("content", content)
      .put("nonce", nonce)
      .put("ratchetHeader", ratchetHeader)
      .toString()
    val x3dhInit = packet.optJSONObject("x3dhInit")
    var resultJSON: String? = null
    if (existingSession != null) {
      resultJSON = runCatching { currentIdentity.ratchetDecrypt(existingSession, message) }.getOrNull()
    }
    if (resultJSON == null && x3dhInit != null) {
      val spkId = x3dhInit.optLong("spkId")
      val initIdentityKey = x3dhInit.optString("ikPub").lowercase()
      val activeDevice = if (isDevicePacket) identity.currentDevice() else null
      if (isDevicePacket && !initIdentityKey.equals(publicKey, ignoreCase = true)) {
        throw IllegalArgumentException("CHAT x3dh identity does not match the sender device")
      }
      val deviceSpkSecret = activeDevice?.let { deviceSignedPreKeys.secretFor(it, spkId) }
      val spkSecret = deviceSpkSecret ?: signedPreKeys.secretFor(spkId)
      if (spkSecret != null) {
        val acceptingIdentity = if (deviceSpkSecret != null) activeDevice?.crypto else currentIdentity
        val sessionPeerKey = if (deviceSpkSecret != null) initIdentityKey else publicKey
        if (acceptingIdentity != null) {
          val freshSession = acceptingIdentity.acceptInboundSession(sessionPeerKey, x3dhInit.toString(), spkSecret)
          resultJSON = currentIdentity.ratchetDecrypt(freshSession, message)
          if (existingSession != null) Log.i(TAG, "Sesión X3DH reiniciada para $senderUpeerId")
        }
      }
    }
    if (resultJSON == null) {
      sendDrReset(senderUpeerId)
      throw IllegalStateException("CHAT has no usable ratchet session")
    }
    val result = JSONObject(resultJSON)
    val plaintext = YggdrasilPayloadCodec.decompress(YggdrasilPayloadCodec.hexToBytes(result.optString("plaintextHex")))
    state.saveSession(senderUpeerId, result.optString("session"))
    return plaintext
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
