package app.upeer.transport

import android.util.Log
import app.upeer.transport.dht.DhtLocation
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class ControlReplies(
  private val identity: YggdrasilIdentity,
  private val deviceBindings: DeviceMessagingBindings,
  private val state: ProtocolStateStore,
  private val outbox: YggdrasilOutbox,
  private val rateLimiter: InboundRateLimiter,
  private val sendPong: (String) -> Unit,
) {
  private val resetCache = DrResetReplayCache()

  fun clear() = resetCache.clear()

  fun handle(
    activeBridge: Bridge,
    payload: ByteArray,
    packet: JSONObject,
    type: String,
    sourceAddress: String,
  ): String {
    val senderUpeerId = packet.optString("senderUpeerId").lowercase()
    val publicKey = state.publicKeyFor(senderUpeerId)
      ?: throw IllegalStateException("unknown peer $senderUpeerId")
    val deviceBinding = if (type == "DR_RESET") deviceBinding(packet, senderUpeerId) else null
    val metadataJSON = if (deviceBinding != null) {
      activeBridge.validateLinkedSignedPacket(payload, deviceBinding.devicePublicKey, senderUpeerId)
    } else {
      activeBridge.validateSignedPacket(payload, publicKey)
    }
    val metadata = JSONObject(metadataJSON)
    if (!allow(metadata, sourceAddress)) return rateLimited(metadata)
    state.touchPresence(senderUpeerId)
    if (deviceBinding == null) state.rememberPeer(metadata)
    if (type == "PING") {
      state.presence.touch(senderUpeerId, packet.optLong("activeAt", 0L), System.currentTimeMillis())
      sendPong(senderUpeerId)
      return metadataJSON
    }
    if (type == "ACK") {
      handleAck(senderUpeerId, packet)
      return metadataJSON
    }
    if (type == "READ") {
      handleRead(senderUpeerId, packet)
      return metadataJSON
    }
    if (type == "TYPING") {
      state.markTyping(senderUpeerId, TypingIndicators.expiresAt(System.currentTimeMillis()))
      return metadataJSON
    }
    if (type == "CHAT_REACTION") {
      handleReaction(senderUpeerId, packet)
      return metadataJSON
    }
    if (type == "CHAT_DELETE") {
      handleDelete(senderUpeerId, packet)
      return metadataJSON
    }
    handleReset(packet, senderUpeerId, publicKey, deviceBinding)
    return metadataJSON
  }

  private fun deviceBinding(packet: JSONObject, senderUpeerId: String): DeviceBinding? {
    val senderDeviceId = packet.optString("senderDeviceId").lowercase()
    if (senderDeviceId.isEmpty()) return null
    if (!DhtLocation.isHex(senderDeviceId, 32)) {
      throw IllegalArgumentException("DR_RESET senderDeviceId is invalid")
    }
    val peer = state.peerFor(senderUpeerId)
      ?: throw IllegalStateException("unknown peer $senderUpeerId")
    val binding = deviceBindings.bindingFor(peer, senderUpeerId)
      ?: throw IllegalStateException("peer device binding is unavailable")
    if (!binding.deviceId.equals(senderDeviceId, ignoreCase = true)) {
      throw IllegalArgumentException("DR_RESET announced an unknown sender device")
    }
    return binding
  }

  private fun handleAck(senderUpeerId: String, packet: JSONObject) {
    val messageID = packet.optString("id")
    if (!ReplayPolicy.validMessageID(messageID)) {
      Log.w(TAG, "ACK ignorado con id inválido de $senderUpeerId")
      return
    }
    val status = packet.optString("status")
    if (!outbox.onAck(senderUpeerId, messageID, status)) {
      Log.w(TAG, "ACK ignorado de $senderUpeerId para $messageID ($status)")
    }
  }

  private fun handleRead(senderUpeerId: String, packet: JSONObject) {
    val messageID = ReadReceipts.messageID(packet)
    if (messageID == null) {
      Log.w(TAG, "READ ignorado con id inválido de $senderUpeerId")
      return
    }
    if (state.messages.ack(messageID, senderUpeerId, "read")) {
      Log.i(TAG, "READ aplicado a $messageID de $senderUpeerId")
    } else {
      Log.w(TAG, "READ ignorado de $senderUpeerId para $messageID")
    }
  }

  private fun handleReaction(senderUpeerId: String, packet: JSONObject) {
    val messageID = ChatReactions.messageID(packet)
    if (messageID == null) {
      Log.w(TAG, "CHAT_REACTION ignorado con id inválido de $senderUpeerId")
      return
    }
    val emoji = packet.optString("emoji")
    if (!ChatReactions.validEmoji(emoji)) {
      Log.w(TAG, "CHAT_REACTION ignorado con emoji inválido de $senderUpeerId")
      return
    }
    val remove = packet.optBoolean("remove")
    if (!state.messages.applyReaction(messageID, senderUpeerId, emoji, remove)) {
      Log.w(TAG, "CHAT_REACTION ignorado de $senderUpeerId para $messageID")
    }
  }

  private fun handleDelete(senderUpeerId: String, packet: JSONObject) {
    val messageID = ChatDeletes.messageID(packet)
    if (messageID == null) {
      Log.w(TAG, "CHAT_DELETE ignorado con id inválido de $senderUpeerId")
      return
    }
    if (!state.messages.delete(messageID, senderUpeerId)) {
      Log.w(TAG, "CHAT_DELETE ignorado de $senderUpeerId para $messageID")
    }
  }

  private fun handleReset(
    packet: JSONObject,
    senderUpeerId: String,
    publicKey: String,
    deviceBinding: DeviceBinding?,
  ) {
    val ratchetContext = packet.optString("ratchetContext", "direct")
    if (ratchetContext != "direct" && !GROUP_CONTEXT.matches(ratchetContext)) {
      throw IllegalArgumentException("invalid DR_RESET ratchet context")
    }
    if (ratchetContext != "direct") return
    val signedPreKey = packet.optJSONObject("signedPreKey")
      ?: throw IllegalArgumentException("DR_RESET is missing signedPreKey")
    val currentIdentity = identity.current() ?: throw IllegalStateException("uPeer identity is not available")
    val verificationKey = deviceBinding?.devicePublicKey ?: publicKey
    if (!currentIdentity.verifySignedPreKey(
        verificationKey,
        signedPreKey.optString("spkPub"),
        signedPreKey.optString("spkSig"),
      )
    ) {
      throw IllegalArgumentException("DR_RESET signed pre-key signature is invalid")
    }
    val resetId = packet.optString("resetId")
    if (resetId.isNotEmpty() && !DrResetPolicy.validResetId(resetId)) {
      throw IllegalArgumentException("invalid DR_RESET resetId")
    }
    if (resetId.isNotEmpty() && !resetCache.accept(senderUpeerId, resetId)) {
      Log.i(TAG, "DR_RESET duplicado ignorado para $senderUpeerId")
      return
    }
    val update = if (deviceBinding != null) {
      state.updateDeviceSignedPreKey(senderUpeerId, signedPreKey)
    } else {
      state.updateSignedPreKey(senderUpeerId, signedPreKey)
    }
    if (update == SignedPreKeyUpdate.STALE || update == SignedPreKeyUpdate.INVALID) {
      Log.w(TAG, "DR_RESET no aplicado para $senderUpeerId: $update")
      return
    }
    outbox.onPeerReset(senderUpeerId)
  }

  private fun allow(metadata: JSONObject, sourceAddress: String): Boolean {
    val type = metadata.optString("type")
    val senderUpeerId = metadata.optString("senderUpeerId")
    return rateLimiter.allow("ip:$sourceAddress", type) && rateLimiter.allow("id:$senderUpeerId", type)
  }

  private fun rateLimited(metadata: JSONObject): String {
    Log.w(TAG, "Límite de recepción alcanzado para ${metadata.optString("type")} de ${metadata.optString("senderUpeerId")}")
    return JSONObject(metadata.toString()).put("rateLimited", true).toString()
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private val GROUP_CONTEXT = Regex("^group:[^:]+$")
  }
}
