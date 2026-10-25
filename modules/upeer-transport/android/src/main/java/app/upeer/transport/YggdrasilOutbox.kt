package app.upeer.transport

import android.util.Log
import app.upeer.transport.dht.ContactAdmissionException
import app.upeer.transport.dht.ContactEncryptionAdmission
import app.upeer.transport.dht.ExpectedContactDevice
import app.upeer.transport.vault.VaultDirectory
import app.upeer.transport.vault.VaultMailboxStore
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
  private val vaultMailbox: VaultMailboxStore,
) {
  private val lock = Any()
  private val wakeLock = Object()
  private val inFlight = ConcurrentHashMap.newKeySet<String>()
  private val peerLocks = ConcurrentHashMap<String, Any>()
  private val sendPool = Executors.newFixedThreadPool(MAX_PARALLEL_SENDS, ThreadFactory { task ->
    Thread(task, "upeer-outbox-send").apply { isDaemon = true }
  })
  private val link = OutboxLink(identity, signedPreKeys, deviceSignedPreKeys, deviceBindings, state) { selfAddress }
  private val sealer = OutboxSealer(identity, state, outbox, link) { selfAddress }
  private val vault = OutboxVault(
    identity,
    state,
    outbox,
    link,
    admission,
    vaultMailbox,
    { synchronized(lock) { selfAddress } },
    ::isActive,
  )
  private val actions = OutboxActions(link, state, outbox, admission, { synchronized(lock) { bridge } }, { synchronized(lock) { running } }) { wake() }
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

  fun wake() = synchronized(wakeLock) { wakeLock.notifyAll() }

  fun enqueue(upeerId: String, content: String, replyTo: String): String {
    val text = ChatSendPolicy.text(upeerId, content, replyTo)
    val messageID = UUID.randomUUID().toString()
    val timestamp = System.currentTimeMillis()
    state.messages.save(
      JSONObject()
        .put("id", messageID)
        .put("upeerId", upeerId)
        .put("isMine", true)
        .put("content", text)
        .put("status", "pending")
        .put("version", 1)
        .put("isEdited", false)
        .put("timestamp", timestamp)
        .apply { if (replyTo.isNotEmpty()) put("replyTo", replyTo) },
    )
    outbox.enqueue(messageID, upeerId, text, replyTo, timestamp)
    wake()
    return messageID
  }

  fun editChat(upeerId: String, messageID: String, content: String): Int {
    if (!ID_PATTERN.matches(upeerId)) throw IllegalArgumentException("uPeer ID no válido")
    val message = state.messages.find(messageID) ?: throw IllegalArgumentException("El mensaje no existe")
    val plan = ChatEditPolicy.plan(message, upeerId, messageID, content)
    if (!state.messages.updateLocalContent(messageID, upeerId, plan.text, plan.version)) {
      throw IllegalArgumentException("El mensaje ya no se puede editar")
    }
    if (plan.pending) {
      if (!outbox.updatePendingContent(messageID, plan.text)) {
        throw IllegalArgumentException("El mensaje pendiente ya no está en el outbox")
      }
    } else {
      val entryID = "$messageID:u${plan.version}"
      outbox.enqueueUpdate(entryID, messageID, upeerId, plan.text, plan.version, System.currentTimeMillis())
    }
    wake()
    return plan.version
  }

  fun retryChat(upeerId: String, messageID: String): Boolean = actions.retryChat(upeerId, messageID)

  fun onAck(senderUpeerId: String, messageID: String, status: String): Boolean {
    if (!state.messages.ack(messageID, senderUpeerId, status)) return false
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

  fun announceProfile() = actions.announceProfile()

  fun announceProfileTo(upeerId: String) = actions.announceProfileTo(upeerId)

  fun onHandshakeAccepted(upeerId: String) {
    val activeBridge: Bridge? = synchronized(lock) { bridge }
    if (running && activeBridge != null) queryVault(activeBridge, upeerId)
    val reset = outbox.resetPeer(upeerId)
    Log.i(TAG, "Handshake aceptado por $upeerId; outbox reactivado ($reset entradas)")
    wake()
  }

  fun sendDrReset(upeerId: String) = actions.sendDrReset(upeerId)

  fun attachVaultDirectory(directory: VaultDirectory) = vault.attachDirectory(directory)

  fun queryVaults(activeBridge: Bridge) = vault.queryVaults(activeBridge)

  fun renewVaultEntries(activeBridge: Bridge) = vault.renewExpiring(activeBridge)

  fun queryVault(activeBridge: Bridge, upeerId: String): Boolean = vault.queryVault(activeBridge, upeerId)

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
      val sends = entry.optInt("sends", 0)
      if (entry.has("sentAt") && !OutboxRetryPolicy.canResend(sends)) {
        if (vault.escrow(activeBridge, entry)) return
        fail(messageID, entry, "sin ACK tras $sends reenvíos")
        return
      }
      if (link.sendPacket(activeBridge, address, packet)) markSent(messageID, entry) else retry(messageID, entry, "writeFrame falló")
      return
    }
    val target = OutboxSealer.Target(
      upeerId,
      address,
      publicKey,
      state.sessionFor(upeerId),
      peer.optJSONObject("signedPreKey"),
      deviceBinding,
    )
    if (target.session == null && sealer.recipientSpk(target) == null) {
      link.sendHandshakeRequest(activeBridge, address)
      retry(messageID, entry, "handshake pendiente")
      return
    }
    when (val result = sealer.sealAndSend(activeBridge, entry, target)) {
      OutboxSealer.Result.Sent -> markSent(messageID, entry)
      is OutboxSealer.Result.Failed -> retry(messageID, entry, result.reason)
    }
  }

  private fun markSent(entryID: String, entry: JSONObject) {
    if (entry.optString("kind", "CHAT") == "CHAT_UPDATE") {
      outbox.remove(entryID)
      Log.i(TAG, "CHAT_UPDATE $entryID enviado a ${entry.optString("upeerId")}")
      return
    }
    val sends = entry.optInt("sends", 0) + if (entry.has("sentAt")) 1 else 0
    outbox.markSent(entryID, System.currentTimeMillis() + ACK_TIMEOUT_MS, sends)
    state.messages.markStatus(entryID, "sent")
    Log.i(TAG, "Mensaje $entryID enviado; esperando ACK (reenvíos=$sends)")
  }

  private fun retry(entryID: String, entry: JSONObject, reason: String) {
    val attempts = entry.optInt("attempts", 0) + 1
    if (!OutboxRetryPolicy.canRetry(attempts)) {
      fail(entryID, entry, "descartada tras $attempts intentos: $reason")
      return
    }
    outbox.markAttempt(entryID, attempts, System.currentTimeMillis() + OutboxRetryPolicy.backoffMs(attempts), reason)
    Log.w(TAG, "Reintento $attempts de $entryID para ${entry.optString("upeerId")} en ${OutboxRetryPolicy.backoffMs(attempts)}ms: $reason")
  }

  private fun fail(entryID: String, entry: JSONObject, reason: String) {
    outbox.remove(entryID)
    if (entry.optString("kind", "CHAT") != "CHAT_UPDATE") state.messages.markStatus(entryID, "failed")
    Log.w(TAG, "Entrada $entryID marcada como fallida: $reason")
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val MAX_BATCH = 8
    private const val MAX_PARALLEL_SENDS = 12
    private const val RETRY_TICK_MS = 1_500L
    private const val ACK_TIMEOUT_MS = 5_000L
    private val ID_PATTERN = Regex("^[0-9a-fA-F]{64}$")
  }
}

