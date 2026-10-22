package app.upeer.transport

import android.util.Log
import app.upeer.transport.dht.ContactAdmissionException
import app.upeer.transport.dht.ContactEncryptionAdmission
import app.upeer.transport.dht.ExpectedContactDevice
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

internal class YggdrasilOutbox(
  private val identity: YggdrasilIdentity,
  private val signedPreKeys: SignedPreKeyStore,
  private val deviceSignedPreKeys: DeviceSignedPreKeyStore,
  private val deviceBindings: DeviceMessagingBindings,
  private val state: ProtocolStateStore,
  private val outbox: OutboxStore,
  private val admission: DeviceAdmissionGuard,
  private val contactAdmission: ContactEncryptionAdmission,
) {
  private val lock = Any()
  private val wakeLock = Object()
  private val inFlight = ConcurrentHashMap.newKeySet<String>()
  private val peerLocks = ConcurrentHashMap<String, Any>()
  private val sendPool = Executors.newFixedThreadPool(MAX_PARALLEL_SENDS, ThreadFactory { task ->
    Thread(task, "upeer-outbox-send").apply { isDaemon = true }
  })
  private val link = OutboxLink(identity, signedPreKeys, deviceSignedPreKeys, deviceBindings) { selfAddress }
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

  fun onAck(senderUpeerId: String, messageID: String, status: String): Boolean {
    if (!state.ackMessage(messageID, senderUpeerId, status)) return false
    outbox.remove(messageID)
    Log.i(TAG, "ACK recibido de $senderUpeerId para $messageID ($status)")
    wake()
    return true
  }

  fun onPeerReset(upeerId: String) {
    state.clearSession(upeerId)
    val reset = outbox.resetPeer(upeerId)
    Log.i(TAG, "Sesión de $upeerId reiniciada por DR_RESET ($reset entradas)")
    wake()
  }

  fun onHandshakeAccepted(upeerId: String) {
    val reset = outbox.resetPeer(upeerId)
    Log.i(TAG, "Handshake aceptado por $upeerId; outbox reactivado ($reset entradas)")
    wake()
  }

  fun sendDrReset(upeerId: String) {
    if (!admission.isAdmitted()) return
    val activeBridge = synchronized(lock) { bridge } ?: return
    val peer = state.peerFor(upeerId) ?: return
    val address = peer.optString("address")
    if (address.isBlank()) return
    if (!link.sendDrReset(activeBridge, address, upeerId, peer)) {
      Log.w(TAG, "DR_RESET no enviado a $upeerId")
    }
  }

  fun flush() {
    if (!admission.isAdmitted()) return
    val activeBridge = synchronized(lock) { if (running) bridge else null } ?: return
    for (entry in outbox.due(System.currentTimeMillis(), MAX_BATCH)) {
      val messageID = entry.optString("id")
      if (messageID.isEmpty() || !inFlight.add(messageID)) continue
      sendPool.execute {
        try {
          if (!isActive(activeBridge)) return@execute
          val peerLock = peerLocks.computeIfAbsent(entry.optString("upeerId")) { Any() }
          synchronized(peerLock) {
            if (isActive(activeBridge)) deliver(activeBridge, entry)
          }
        } catch (cause: Exception) {
          retry(messageID, entry, message(cause))
        } finally {
          inFlight.remove(messageID)
        }
      }
    }
  }

  private fun isActive(activeBridge: Bridge): Boolean = synchronized(lock) { running && bridge === activeBridge }

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
    if (!admission.isAdmitted()) return
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
      link.sendHandshakeRequest(activeBridge, address)
      retry(messageID, entry, "handshake pendiente")
      return
    }
    val deviceBinding = deviceBindings.bindingFor(peer, upeerId)
    try {
      contactAdmission.requireAdmitted(
        upeerId,
        deviceBinding?.let { ExpectedContactDevice(it.deviceId, it.devicePublicKey) },
      )
    } catch (cause: ContactAdmissionException) {
      retry(messageID, entry, message(cause))
      return
    }
    val packet = entry.optString("packet")
    if (packet.isNotEmpty()) {
      if (link.sendPacket(activeBridge, address, packet)) markSent(messageID, entry) else retry(messageID, entry, "writeFrame falló")
      return
    }
    val session = state.sessionFor(upeerId)
    val signedPreKey = peer.optJSONObject("signedPreKey")
    val currentIdentity = identity.current()
    if (currentIdentity == null) {
      retry(messageID, entry, "identidad no disponible")
      return
    }
    val currentDevice = deviceBinding?.let { identity.currentDevice() }
      ?.takeIf { it.accountUpeerId == currentIdentity.upeerID() }
    val useDevice = deviceBinding != null && currentDevice != null
    val recipientSpk = if (useDevice) deviceBinding?.signedPreKey else signedPreKey
    if (session == null && recipientSpk == null) {
      link.sendHandshakeRequest(activeBridge, address)
      retry(messageID, entry, "handshake pendiente")
      return
    }
    Log.i(
      TAG,
      "Entregando $messageID a $upeerId (sesión=${session != null}, device=${useDevice}, " +
        "spk=${recipientSpk?.optLong("spkId") ?: 0})",
    )
    try {
      val result = if (deviceBinding != null && currentDevice != null) {
        OutboxPacketFactory.chat(
          currentIdentity,
          currentDevice,
          deviceBinding,
          selfAddress,
          session.orEmpty(),
          entry.optString("content"),
          messageID,
          entry.optString("replyTo"),
          entry.optLong("timestamp"),
        )
      } else {
        JSONObject(
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
      }
      val nextSession = result.optString("session")
      val nextPacket = result.optString("packet")
      if (nextSession.isEmpty() || nextPacket.isEmpty()) {
        retry(messageID, entry, "resultado de cifrado inválido")
        return
      }
      state.saveSession(upeerId, nextSession)
      outbox.setPacket(messageID, nextPacket)
      if (link.sendPacket(activeBridge, address, nextPacket)) markSent(messageID, entry) else retry(messageID, entry, "writeFrame falló")
    } catch (cause: Exception) {
      retry(messageID, entry, message(cause))
    }
  }

  private fun markSent(messageID: String, entry: JSONObject) {
    outbox.markSent(messageID, System.currentTimeMillis() + ACK_TIMEOUT_MS)
    state.markMessageStatus(messageID, "sent")
    Log.i(TAG, "Mensaje $messageID enviado; esperando ACK")
  }

  private fun retry(messageID: String, entry: JSONObject, reason: String) {
    val attempts = entry.optInt("attempts", 0) + 1
    if (attempts >= MAX_ATTEMPTS) {
      outbox.remove(messageID)
      state.markMessageStatus(messageID, "failed")
      Log.w(TAG, "Mensaje $messageID marcado como fallido tras $attempts intentos: $reason")
      return
    }
    val delay = minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS * attempts)
    outbox.markAttempt(messageID, attempts, System.currentTimeMillis() + delay, reason)
    Log.w(TAG, "Reintento $attempts de $messageID para ${entry.optString("upeerId")} en ${delay}ms: $reason")
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val MAX_BATCH = 8
    private const val MAX_PARALLEL_SENDS = 12
    private const val MAX_ATTEMPTS = 30
    private const val MAX_MESSAGE_BYTES = 256 * 1024
    private const val RETRY_TICK_MS = 1_500L
    private const val ACK_TIMEOUT_MS = 5_000L
    private const val BASE_BACKOFF_MS = 2_000L
    private const val MAX_BACKOFF_MS = 60_000L
    private val ID_PATTERN = Regex("^[0-9a-fA-F]{64}$")
  }
}

