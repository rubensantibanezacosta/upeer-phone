package app.upeer.transport

import android.util.Log
import org.json.JSONObject

internal class ChatInbound(
  private val state: ProtocolStateStore,
  private val decryptor: YggdrasilChatDecryptor,
  private val controlSender: ControlSender,
  private val onInboundMessage: (String, String, String) -> Unit,
) {
  fun receive(
    packet: JSONObject,
    senderUpeerId: String,
    senderIdentityKey: String,
    isDevicePacket: Boolean,
    metadataJSON: String,
  ): String {
    val messageID = packet.optString("id")
    if (!ReplayPolicy.validMessageID(messageID)) throw IllegalArgumentException("CHAT message id must be a UUID")
    state.touchPresence(senderUpeerId)
    val existingOwner = state.messages.owner(messageID)
    if (existingOwner != null) {
      if (existingOwner != senderUpeerId) throw IllegalArgumentException("CHAT message ID collision across conversations")
      controlSender.scheduleAck(senderUpeerId, messageID)
      return metadataJSON
    }
    state.clearTyping(senderUpeerId)
    val decrypted = decryptor.decrypt(packet, senderUpeerId, senderIdentityKey, isDevicePacket)
    if (decrypted.messageID.isNotEmpty()) controlSender.scheduleAck(senderUpeerId, decrypted.messageID)
    try {
      onInboundMessage(senderUpeerId, state.peerFor(senderUpeerId)?.optString("alias").orEmpty(), decrypted.plaintext)
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo notificar el mensaje de $senderUpeerId: ${message(cause)}")
    }
    return metadataJSON
  }

  fun receiveUpdate(
    packet: JSONObject,
    senderUpeerId: String,
    senderIdentityKey: String,
    isDevicePacket: Boolean,
    metadataJSON: String,
  ): String {
    val messageID = packet.optString("msgId")
    if (!ReplayPolicy.validMessageID(messageID)) throw IllegalArgumentException("CHAT_UPDATE msgId must be a UUID")
    state.touchPresence(senderUpeerId)
    val version = packet.optInt("version", 0)
    if (version <= 0) throw IllegalArgumentException("CHAT_UPDATE version must be positive")
    val current = state.messages.find(messageID)
    if (current == null || current.optString("upeerId") != senderUpeerId || current.optBoolean("isMine")) {
      Log.w(TAG, "CHAT_UPDATE ignorado para un mensaje ajeno: $messageID")
      return metadataJSON
    }
    if (version <= current.optInt("version", 0)) {
      Log.i(TAG, "CHAT_UPDATE duplicado ignorado para $messageID (versión $version)")
      return metadataJSON
    }
    val plaintext = decryptor.decryptUpdate(packet, senderUpeerId, senderIdentityKey, isDevicePacket)
    val updated = state.messages.updateIncomingContent(messageID, senderUpeerId, plaintext, version)
    if (updated == null) {
      Log.w(TAG, "CHAT_UPDATE obsoleto ignorado para $messageID (versión $version)")
    } else {
      Log.i(TAG, "CHAT_UPDATE aplicado a $messageID (versión $version)")
    }
    return metadataJSON
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
