package app.upeer.transport

import android.util.Base64
import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

internal class YggdrasilProtocol(
  private val identity: YggdrasilIdentity,
  private val signedPreKeys: SignedPreKeyStore,
  private val state: ProtocolStateStore,
  private val outboxStore: OutboxStore,
  private val onInboundMessage: (String, String, String) -> Unit,
  private val rateLimiter: InboundRateLimiter = InboundRateLimiter(),
) {
  private val handshake = YggdrasilHandshake(identity, signedPreKeys)
  private val outbox = YggdrasilOutbox(identity, signedPreKeys, state, outboxStore)

  fun start(activeBridge: Bridge) = outbox.start(activeBridge)

  fun stop() = outbox.stop()

  fun clear() {
    outbox.stop()
    handshake.clear()
    outboxStore.clear()
    state.clear()
  }

  fun sendChat(upeerId: String, content: String, replyTo: String): String = outbox.enqueue(upeerId, content, replyTo)

  fun registerPeer(upeerId: String, address: String, publicKey: String, signedPreKeyJSON: String): Boolean {
    val accepted = state.upsertPeer(upeerId, address, publicKey, signedPreKeyJSON.ifBlank { null })
    if (accepted) outbox.onHandshakeAccepted(upeerId)
    return accepted
  }

  fun flushOutbox() = outbox.flush()

  fun markConversationRead(upeerId: String) = state.markConversationRead(upeerId)

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
    val inner = currentIdentity.openSealed(outer.optString("ciphertext"))
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
    if (type == "ACK" || type == "DR_RESET") return handleControlReply(activeBridge, payload, packet, type, sourceAddress)
    if (type != "CHAT") throw IllegalArgumentException("unsupported packet type $type")
    val senderUpeerId = packet.optString("senderUpeerId").lowercase()
    val publicKey = state.publicKeyFor(senderUpeerId)
      ?: throw IllegalStateException("unknown peer $senderUpeerId")
    val metadataJSON = activeBridge.validateSignedPacket(payload, publicKey)
    val metadata = JSONObject(metadataJSON)
    if (!allow(metadata, sourceAddress)) return rateLimited(metadata)
    state.rememberPeer(metadata)
    val messageID = packet.optString("id")
    if (!ReplayPolicy.validMessageID(messageID)) throw IllegalArgumentException("CHAT message id must be a UUID")
    val existingOwner = state.messageOwner(messageID)
    if (existingOwner != null) {
      if (existingOwner != senderUpeerId) throw IllegalArgumentException("CHAT message ID collision across conversations")
      scheduleAck(activeBridge, senderUpeerId, messageID)
      return metadataJSON
    }
    decryptChat(activeBridge, packet, senderUpeerId, publicKey)
    return metadataJSON
  }

  private fun handleControlReply(
    activeBridge: Bridge,
    payload: ByteArray,
    packet: JSONObject,
    type: String,
    sourceAddress: String,
  ): String {
    val senderUpeerId = packet.optString("senderUpeerId").lowercase()
    val publicKey = state.publicKeyFor(senderUpeerId)
      ?: throw IllegalStateException("unknown peer $senderUpeerId")
    val metadataJSON = activeBridge.validateSignedPacket(payload, publicKey)
    val metadata = JSONObject(metadataJSON)
    if (!allow(metadata, sourceAddress)) return rateLimited(metadata)
    state.rememberPeer(metadata)
    if (type == "ACK") {
      val messageID = packet.optString("id")
      if (!ReplayPolicy.validMessageID(messageID)) {
        Log.w(TAG, "ACK ignorado con id inválido de $senderUpeerId")
        return metadataJSON
      }
      val status = packet.optString("status")
      if (!outbox.onAck(senderUpeerId, messageID, status)) {
        Log.w(TAG, "ACK ignorado de $senderUpeerId para $messageID ($status)")
      }
      return metadataJSON
    }
    val ratchetContext = packet.optString("ratchetContext", "direct")
    if (ratchetContext != "direct" && !GROUP_CONTEXT.matches(ratchetContext)) {
      throw IllegalArgumentException("invalid DR_RESET ratchet context")
    }
    if (ratchetContext != "direct") return metadataJSON
    val signedPreKey = packet.optJSONObject("signedPreKey")
      ?: throw IllegalArgumentException("DR_RESET is missing signedPreKey")
    val currentIdentity = identity.current() ?: throw IllegalStateException("uPeer identity is not available")
    if (!currentIdentity.verifySignedPreKey(publicKey, signedPreKey.optString("spkPub"), signedPreKey.optString("spkSig"))) {
      throw IllegalArgumentException("DR_RESET signed pre-key signature is invalid")
    }
    if (!state.updateSignedPreKey(senderUpeerId, signedPreKey)) {
      Log.i(TAG, "DR_RESET obsoleto ignorado para $senderUpeerId spk=${signedPreKey.optLong("spkId")}")
      return metadataJSON
    }
    outbox.onPeerReset(senderUpeerId)
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


  private fun decryptChat(activeBridge: Bridge, packet: JSONObject, senderUpeerId: String, publicKey: String) {
    val content = packet.optString("content")
    val nonce = packet.optString("nonce")
    val ratchetHeader = packet.optJSONObject("ratchetHeader")
    if (content.isEmpty() || nonce.isEmpty() || ratchetHeader == null) {
      outbox.sendDrReset(senderUpeerId)
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
      val spkSecret = signedPreKeys.secretFor(x3dhInit.optLong("spkId"))
      if (spkSecret != null) {
        val freshSession = currentIdentity.acceptInboundSession(publicKey, x3dhInit.toString(), spkSecret)
        resultJSON = currentIdentity.ratchetDecrypt(freshSession, message)
        if (existingSession != null) Log.i(TAG, "Sesión X3DH reiniciada para $senderUpeerId")
      }
    }
    if (resultJSON == null) {
      outbox.sendDrReset(senderUpeerId)
      throw IllegalStateException("CHAT has no usable ratchet session")
    }
    val result = JSONObject(resultJSON)
    val plaintext = decompress(hexToBytes(result.optString("plaintextHex")))
    state.saveSession(senderUpeerId, result.optString("session"))
    state.saveMessage(
      JSONObject()
        .put("id", packet.optString("id"))
        .put("upeerId", senderUpeerId)
        .put("isMine", false)
        .put("content", plaintext)
        .put("status", "delivered")
        .put("timestamp", packet.optLong("timestamp", System.currentTimeMillis()))
        .put("receivedAt", System.currentTimeMillis()),
    )
    Log.i(TAG, "CHAT descifrado de $senderUpeerId id=${packet.optString("id")} (${plaintext.length} caracteres)")
    val messageID = packet.optString("id")
    if (messageID.isNotEmpty()) scheduleAck(activeBridge, senderUpeerId, messageID)
    try {
      onInboundMessage(senderUpeerId, state.peerFor(senderUpeerId)?.optString("alias").orEmpty(), plaintext)
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo notificar el mensaje de $senderUpeerId: ${message(cause)}")
    }
  }

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

  private fun decompress(value: ByteArray): String {
    val text = String(value, Charsets.UTF_8)
    if (!text.startsWith(COMPRESSION_PREFIX)) return text
    return try {
      val compressed = Base64.decode(text.substring(COMPRESSION_PREFIX.length), Base64.DEFAULT)
      GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo descomprimir el mensaje: ${message(cause)}")
      text
    }
  }

  private fun hexToBytes(value: String): ByteArray {
    if (value.length % 2 != 0) throw IllegalArgumentException("invalid plaintext hex")
    return ByteArray(value.length / 2) { index ->
      val high = Character.digit(value[index * 2], 16)
      val low = Character.digit(value[index * 2 + 1], 16)
      if (high < 0 || low < 0) throw IllegalArgumentException("invalid plaintext hex")
      ((high shl 4) or low).toByte()
    }
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val COMPRESSION_PREFIX = "\u2763UP\u2763"
    private const val PORT = 50005L
    private val GROUP_CONTEXT = Regex("^group:[^:]+$")
  }
}
