package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject
import java.util.UUID

internal class YggdrasilOutbox(
  private val identity: YggdrasilIdentity,
  private val signedPreKeys: SignedPreKeyStore,
  private val state: ProtocolStateStore,
  private val outbox: OutboxStore,
) {
  private val lock = Any()
  private val wakeLock = Object()
  private var bridge: Bridge? = null
  private var selfAddress = ""
  private var worker: Thread? = null
  @Volatile private var running = false

  fun start(activeBridge: Bridge) {
    synchronized(lock) {
      if (running) return
      bridge = activeBridge
      selfAddress = activeBridge.address()
      running = true
      val thread = Thread({ loop() }, "upeer-outbox")
      thread.isDaemon = true
      worker = thread
      thread.start()
    }
  }

  fun stop() {
    synchronized(lock) {
      running = false
      bridge = null
      selfAddress = ""
      worker = null
    }
    synchronized(wakeLock) { wakeLock.notifyAll() }
  }

  fun wake() {
    synchronized(wakeLock) { wakeLock.notifyAll() }
  }

  fun enqueue(upeerId: String, content: String, replyTo: String): String {
    if (!ID_PATTERN.matches(upeerId)) throw IllegalArgumentException("uPeer ID no válido")
    val text = content.trim()
    if (text.isEmpty()) throw IllegalArgumentException("El mensaje está vacío")
    if (text.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) throw IllegalArgumentException("El mensaje es demasiado grande")
    val messageID = UUID.randomUUID().toString()
    val timestamp = System.currentTimeMillis()
    state.saveMessage(
      JSONObject()
        .put("id", messageID)
        .put("upeerId", upeerId)
        .put("isMine", true)
        .put("content", text)
        .put("status", "pending")
        .put("timestamp", timestamp)
        .apply { if (replyTo.isNotEmpty()) put("replyTo", replyTo) },
    )
    outbox.enqueue(messageID, upeerId, text, replyTo, timestamp)
    wake()
    return messageID
  }

  fun onAck(messageID: String, status: String): Boolean {
    if (!state.ackMessage(messageID, status)) return false
    outbox.remove(messageID)
    Log.i(TAG, "ACK recibido para $messageID ($status)")
    wake()
    return true
  }

  fun onPeerReset(upeerId: String) {
    state.clearSession(upeerId)
    outbox.resetPeer(upeerId)
    Log.i(TAG, "Sesión de $upeerId reiniciada por DR_RESET")
    wake()
  }

  fun onHandshakeAccepted(upeerId: String) {
    outbox.resetPeer(upeerId)
    wake()
  }

  fun sendDrReset(upeerId: String) {
    val activeBridge = synchronized(lock) { bridge } ?: return
    val peer = state.peerFor(upeerId) ?: return
    val address = peer.optString("address")
    val currentIdentity = identity.current() ?: return
    if (address.isBlank()) return
    try {
      val packet = currentIdentity.buildDrReset(selfAddress, signedPreKeys.currentJSON(currentIdentity), "direct")
      if (sendPacket(activeBridge, address, packet)) Log.i(TAG, "DR_RESET enviado a $address")
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar DR_RESET a $address: ${message(cause)}")
    }
  }

  fun flush() {
    val activeBridge = synchronized(lock) { if (running) bridge else null } ?: return
    for (entry in outbox.due(System.currentTimeMillis(), MAX_BATCH)) {
      if (!running) return
      deliver(activeBridge, entry)
    }
  }

  private fun loop() {
    while (true) {
      val active = synchronized(lock) { running && bridge != null }
      if (!active) return
      try {
        flush()
      } catch (cause: Exception) {
        Log.w(TAG, "Error en el outbox: ${message(cause)}")
      }
      synchronized(wakeLock) {
        try {
          wakeLock.wait(RETRY_TICK_MS)
        } catch (cause: InterruptedException) {
          if (!running) return
        }
      }
    }
  }

  private fun deliver(activeBridge: Bridge, entry: JSONObject) {
    val messageID = entry.optString("id")
    val upeerId = entry.optString("upeerId")
    val peer = state.peerFor(upeerId)
    if (peer == null) {
      retry(messageID, entry, "peer desconocido")
      return
    }
    val address = peer.optString("address")
    val publicKey = peer.optString("publicKey")
    if (address.isBlank()) {
      retry(messageID, entry, "peer sin dirección")
      return
    }
    if (publicKey.length != 64) {
      sendHandshakeRequest(activeBridge, address)
      retry(messageID, entry, "handshake pendiente")
      return
    }
    val packet = entry.optString("packet")
    if (packet.isNotEmpty()) {
      if (sendPacket(activeBridge, address, packet)) markSent(messageID, entry) else retry(messageID, entry, "writeFrame falló")
      return
    }
    val session = state.sessionFor(upeerId)
    val signedPreKey = peer.optJSONObject("signedPreKey")
    if (session == null && signedPreKey == null) {
      sendHandshakeRequest(activeBridge, address)
      retry(messageID, entry, "handshake pendiente")
      return
    }
    val currentIdentity = identity.current()
    if (currentIdentity == null) {
      retry(messageID, entry, "identidad no disponible")
      return
    }
    try {
      val result = JSONObject(
        currentIdentity.buildChat(
          selfAddress,
          publicKey,
          signedPreKey?.optString("spkPub").orEmpty(),
          session.orEmpty(),
          entry.optString("content"),
          messageID,
          entry.optString("replyTo"),
          signedPreKey?.optLong("spkId") ?: 0L,
          entry.optLong("timestamp"),
        ),
      )
      val nextSession = result.optString("session")
      val nextPacket = result.optString("packet")
      if (nextSession.isEmpty() || nextPacket.isEmpty()) {
        retry(messageID, entry, "resultado de cifrado inválido")
        return
      }
      state.saveSession(upeerId, nextSession)
      outbox.setPacket(messageID, nextPacket)
      if (sendPacket(activeBridge, address, nextPacket)) markSent(messageID, entry) else retry(messageID, entry, "writeFrame falló")
    } catch (cause: Exception) {
      retry(messageID, entry, message(cause))
    }
  }

  private fun sendHandshakeRequest(activeBridge: Bridge, address: String) {
    val currentIdentity = identity.current() ?: return
    try {
      val packet = currentIdentity.buildHandshakeRequest(selfAddress, signedPreKeys.currentJSON(currentIdentity), "")
      if (sendPacket(activeBridge, address, packet)) Log.i(TAG, "HANDSHAKE_REQ enviado a $address")
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar HANDSHAKE_REQ a $address: ${message(cause)}")
    }
  }

  private fun sendPacket(activeBridge: Bridge, address: String, packet: String): Boolean {
    var connectionID = 0
    return try {
      connectionID = activeBridge.dialTCP(address, PORT)
      activeBridge.writeFrame(connectionID, packet.toByteArray(Charsets.UTF_8))
      true
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar paquete a $address: ${message(cause)}")
      false
    } finally {
      if (connectionID != 0) {
        try {
          activeBridge.closeConnection(connectionID)
        } catch (cause: Exception) {
          Log.w(TAG, "No se pudo cerrar la conexión saliente: ${message(cause)}")
        }
      }
    }
  }

  private fun markSent(messageID: String, entry: JSONObject) {
    outbox.markSent(messageID, System.currentTimeMillis() + ACK_TIMEOUT_MS)
    state.markMessageStatus(messageID, "sent")
    Log.i(TAG, "Mensaje $messageID enviado; esperando ACK")
  }

  private fun retry(messageID: String, entry: JSONObject, reason: String) {
    val attempts = entry.optInt("attempts", 0) + 1
    val delay = minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS * attempts)
    outbox.markAttempt(messageID, attempts, System.currentTimeMillis() + delay, reason)
    Log.w(TAG, "Reintento $attempts de $messageID en ${delay}ms: $reason")
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val PORT = 50005L
    private const val MAX_BATCH = 8
    private const val MAX_MESSAGE_BYTES = 256 * 1024
    private const val RETRY_TICK_MS = 1_500L
    private const val ACK_TIMEOUT_MS = 5_000L
    private const val BASE_BACKOFF_MS = 2_000L
    private const val MAX_BACKOFF_MS = 60_000L
    private val ID_PATTERN = Regex("^[0-9a-fA-F]{64}$")
  }
}

