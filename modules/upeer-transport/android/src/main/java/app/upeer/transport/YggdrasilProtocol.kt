package app.upeer.transport

import android.util.Log
import app.upeer.transport.dht.ContactEncryptionAdmission
import app.upeer.transport.dht.DhtLocation
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class YggdrasilProtocol(
  private val identity: YggdrasilIdentity,
  private val signedPreKeys: SignedPreKeyStore,
  private val deviceSignedPreKeys: DeviceSignedPreKeyStore,
  private val deviceBindings: DeviceMessagingBindings,
  private val state: ProtocolStateStore,
  private val outboxStore: OutboxStore,
  private val onInboundMessage: (String, String, String) -> Unit,
  private val admission: DeviceAdmissionGuard,
  private val contactAdmission: ContactEncryptionAdmission,
  private val rateLimiter: InboundRateLimiter = InboundRateLimiter(),
) {
  private val handshake = YggdrasilHandshake(identity, signedPreKeys, deviceSignedPreKeys, deviceBindings)
  private val outbox = YggdrasilOutbox(
    identity,
    signedPreKeys,
    deviceSignedPreKeys,
    deviceBindings,
    state,
    outboxStore,
    admission,
    contactAdmission,
  )
  private val controlReplies = ControlReplies(identity, deviceBindings, state, outbox, rateLimiter)
  private val chatDecryptor = YggdrasilChatDecryptor(
    identity,
    signedPreKeys,
    deviceSignedPreKeys,
    state,
    outbox::sendDrReset,
  )
  fun start(activeBridge: Bridge) = outbox.start(activeBridge)

  fun stop() = outbox.stop()

  fun clear() {
    outbox.stop()
    handshake.clear()
    outboxStore.clear()
    controlReplies.clear()
    contactAdmission.clear()
    state.clear()
  }

  fun sendChat(upeerId: String, content: String, replyTo: String): String = outbox.enqueue(upeerId, content, replyTo)

  fun editChat(upeerId: String, messageId: String, content: String): Int = outbox.editChat(upeerId, messageId, content)

  fun registerPeer(upeerId: String, address: String, publicKey: String, signedPreKeyJSON: String): Boolean {
    val accepted = state.upsertPeer(upeerId, address, publicKey, signedPreKeyJSON.ifBlank { null })
    if (accepted) outbox.onHandshakeAccepted(upeerId)
    return accepted
  }

  fun flushOutbox() = outbox.flush()
  fun peerPublicKey(upeerId: String): String? = state.publicKeyFor(upeerId)
  fun markConversationRead(upeerId: String) = state.markConversationRead(upeerId)

  fun deleteConversation(upeerId: String): Boolean = state.deleteConversation(upeerId)

  fun messagesJSON(upeerId: String, limit: Int): String = state.messagesJSON(upeerId, limit)

  fun conversationsJSON(): String = state.conversationsJSON()

  fun handleFrame(activeBridge: Bridge, payload: ByteArray, sourceAddress: String): String {
    val outer = JSONObject(String(payload, Charsets.UTF_8))
    val outerType = outer.optString("type")
    if (outerType != "SEALED") return handleSignedPacket(activeBridge, payload, sourceAddress)
    if (!rateLimiter.allow("ip:$sourceAddress", outerType)) {
      Log.w(TAG, "SEALED limitado por origen $sourceAddress")
      return rateLimitedMetadata(outerType)
    }
    val currentIdentity = identity.current() ?: throw IllegalStateException("uPeer identity is not available")
    val ciphertext = outer.optString("ciphertext")
    var accountFailure: Throwable? = null
    val accountInner = try {
      currentIdentity.openSealed(ciphertext)
    } catch (cause: Exception) {
      accountFailure = cause
      null
    }
    val inner = accountInner ?: identity.currentDevice()?.let { device ->
      try {
        device.crypto.openSealed(ciphertext)
      } catch (cause: Exception) {
        Log.w(TAG, "El paquete SEALED no se pudo abrir con la clave de dispositivo: ${message(cause)}")
        null
      }
    } ?: throw IllegalStateException(
      "SEALED packet could not be opened with identity or device key",
      accountFailure,
    )
    return handleSignedPacket(activeBridge, inner, sourceAddress)
  }

  fun statusJSON(): String {
    val result = JSONObject(state.statusJSON())
    result
      .put("identityReady", identity.current() != null)
      .put("outbox", outboxStore.count())
      .put("rateLimited", rateLimiter.limitedCount())
    return result.toString()
  }

  private fun handleSignedPacket(activeBridge: Bridge, payload: ByteArray, sourceAddress: String): String {
    val packet = JSONObject(String(payload, Charsets.UTF_8))
    val type = packet.optString("type")
    if (type == "HANDSHAKE_REQ" || type == "HANDSHAKE_ACCEPT") {
      val metadataJSON = activeBridge.validateControlFrame(payload)
      val metadata = JSONObject(metadataJSON)
      packet.optJSONObject("signedPreKey")?.let { metadata.put("signedPreKey", it) }
      packet.optString("alias").takeIf(String::isNotEmpty)?.let { metadata.put("alias", it) }
      val accountUpeerId = metadata.optString("senderUpeerId")
      val deviceAttached = deviceBindings.attach(metadata, packet, accountUpeerId)
      if (deviceBindings.hasFields(packet) && !deviceAttached) {
        Log.w(TAG, "Handshake $type de $accountUpeerId con identidad de dispositivo inválida")
      }
      if (!allow(metadata, sourceAddress)) return rateLimited(metadata)
      if (state.rememberPeer(metadata)) outbox.onPeerReset(metadata.optString("senderUpeerId"))
      Log.i(
        TAG,
        "Handshake $type de ${metadata.optString("senderUpeerId")} " +
          "spk=${packet.optJSONObject("signedPreKey")?.optLong("spkId") ?: 0} " +
          "peer=${state.peerFor(metadata.optString("senderUpeerId"))?.optJSONObject("signedPreKey") != null}",
      )
      if (type == "HANDSHAKE_REQ") {
        val responder = Thread({ handshake.respond(activeBridge, metadataJSON) }, "upeer-handshake")
        responder.isDaemon = true
        responder.start()
      } else {
        outbox.onHandshakeAccepted(metadata.optString("senderUpeerId"))
      }
      return metadataJSON
    }
    if (type == "ACK" || type == "DR_RESET") {
      return controlReplies.handle(activeBridge, payload, packet, type, sourceAddress)
    }
    if (type != "CHAT" && type != "CHAT_UPDATE") throw IllegalArgumentException("unsupported packet type $type")
    val senderUpeerId = packet.optString("senderUpeerId").lowercase()
    val publicKey = state.publicKeyFor(senderUpeerId)
      ?: throw IllegalStateException("unknown peer $senderUpeerId")
    val senderDeviceId = packet.optString("senderDeviceId").lowercase()
    val isDevicePacket = senderDeviceId.isNotEmpty()
    val senderIdentityKey: String
    val metadataJSON: String
    if (isDevicePacket) {
      if (!DhtLocation.isHex(senderDeviceId, 32)) throw IllegalArgumentException("$type senderDeviceId is invalid")
      val peer = state.peerFor(senderUpeerId) ?: throw IllegalStateException("unknown peer $senderUpeerId")
      if (!peer.optString("deviceId").equals(senderDeviceId, ignoreCase = true)) {
        throw IllegalArgumentException("$type announced an unknown sender device")
      }
      senderIdentityKey = peer.optString("devicePublicKey")
      if (!DhtLocation.isHex(senderIdentityKey, 32)) throw IllegalArgumentException("peer device key is unavailable")
      metadataJSON = activeBridge.validateLinkedSignedPacket(payload, senderIdentityKey, senderUpeerId)
    } else {
      senderIdentityKey = publicKey
      metadataJSON = activeBridge.validateSignedPacket(payload, publicKey)
    }
    val metadata = JSONObject(metadataJSON)
    if (!allow(metadata, sourceAddress)) return rateLimited(metadata)
    if (!isDevicePacket) state.rememberPeer(metadata)
    return if (type == "CHAT_UPDATE") {
      handleChatUpdate(packet, senderUpeerId, senderIdentityKey, isDevicePacket, metadataJSON)
    } else {
      handleChat(activeBridge, packet, senderUpeerId, senderIdentityKey, isDevicePacket, metadataJSON)
    }
  }

  private fun handleChat(
    activeBridge: Bridge,
    packet: JSONObject,
    senderUpeerId: String,
    senderIdentityKey: String,
    isDevicePacket: Boolean,
    metadataJSON: String,
  ): String {
    val messageID = packet.optString("id")
    if (!ReplayPolicy.validMessageID(messageID)) throw IllegalArgumentException("CHAT message id must be a UUID")
    val existingOwner = state.messageOwner(messageID)
    if (existingOwner != null) {
      if (existingOwner != senderUpeerId) throw IllegalArgumentException("CHAT message ID collision across conversations")
      scheduleAck(activeBridge, senderUpeerId, messageID)
      return metadataJSON
    }
    val decrypted = chatDecryptor.decrypt(packet, senderUpeerId, senderIdentityKey, isDevicePacket)
    if (decrypted.messageID.isNotEmpty()) scheduleAck(activeBridge, senderUpeerId, decrypted.messageID)
    try {
      onInboundMessage(senderUpeerId, state.peerFor(senderUpeerId)?.optString("alias").orEmpty(), decrypted.plaintext)
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo notificar el mensaje de $senderUpeerId: ${message(cause)}")
    }
    return metadataJSON
  }

  private fun handleChatUpdate(
    packet: JSONObject,
    senderUpeerId: String,
    senderIdentityKey: String,
    isDevicePacket: Boolean,
    metadataJSON: String,
  ): String {
    val messageID = packet.optString("msgId")
    if (!ReplayPolicy.validMessageID(messageID)) throw IllegalArgumentException("CHAT_UPDATE msgId must be a UUID")
    val version = packet.optInt("version", 0)
    if (version <= 0) throw IllegalArgumentException("CHAT_UPDATE version must be positive")
    val current = state.messageFor(messageID)
    if (current == null || current.optString("upeerId") != senderUpeerId || current.optBoolean("isMine")) {
      Log.w(TAG, "CHAT_UPDATE ignorado para un mensaje ajeno: $messageID")
      return metadataJSON
    }
    if (version <= current.optInt("version", 0)) {
      Log.i(TAG, "CHAT_UPDATE duplicado ignorado para $messageID (versión $version)")
      return metadataJSON
    }
    val plaintext = chatDecryptor.decryptUpdate(packet, senderUpeerId, senderIdentityKey, isDevicePacket)
    val updated = state.updateMessageContent(messageID, senderUpeerId, plaintext, version)
    if (updated == null) {
      Log.w(TAG, "CHAT_UPDATE obsoleto ignorado para $messageID (versión $version)")
    } else {
      Log.i(TAG, "CHAT_UPDATE aplicado a $messageID (versión $version)")
    }
    return metadataJSON
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

  private fun rateLimitedMetadata(type: String): String = JSONObject()
    .put("type", type)
    .put("rateLimited", true)
    .toString()

  private fun scheduleAck(activeBridge: Bridge, senderUpeerId: String, messageID: String) {
    val ackThread = Thread({ sendAck(activeBridge, senderUpeerId, messageID) }, "upeer-ack")
    ackThread.isDaemon = true
    ackThread.start()
  }

  private fun sendAck(activeBridge: Bridge, senderUpeerId: String, messageID: String) {
    val address = state.addressFor(senderUpeerId) ?: return
    val currentIdentity = identity.current() ?: return
    var connectionID = 0
    try {
      val ack = currentIdentity.buildAck(activeBridge.address(), messageID, "delivered")
      connectionID = activeBridge.dialTCP(address, PORT)
      activeBridge.writeFrame(connectionID, ack.toByteArray(Charsets.UTF_8))
      Log.i(TAG, "ACK enviado a $address para $messageID")
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar ACK a $address: ${message(cause)}")
    } finally {
      if (connectionID != 0) {
        try {
          activeBridge.closeConnection(connectionID)
        } catch (cause: Exception) {
          Log.w(TAG, "No se pudo cerrar la conexión de ACK: ${message(cause)}")
        }
      }
    }
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val PORT = 50005L
  }
}
