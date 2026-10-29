package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal class ProtocolSync(
  private val identity: YggdrasilIdentity,
  private val deviceSignedPreKeys: DeviceSignedPreKeyStore,
  private val deviceBindings: DeviceMessagingBindings,
  private val state: ProtocolStateStore,
  private val policy: InboundPolicy,
  private val selfAddress: () -> String,
  private val validate: (ByteArray, String, String) -> Unit,
  private val send: (Bridge, String, String) -> Boolean,
  private val streamFrames: (Bridge, String, Iterator<String>, String) -> Boolean = { _, _, _, _ -> false },
  private val onAdmissionChanged: () -> Unit = {},
) {
  private val channel = SyncChannel(identity, state, ::greetForSession)
  private val greeted = java.util.concurrent.ConcurrentHashMap<String, Long>()
  private val applier = SyncApplier(state)
  private val files = SyncFileChannel(
    identity,
    state,
    deviceSignedPreKeys,
    channel,
    { peer, packet -> sendFilePacket(peer, packet) },
    { peer, packets -> streamFilePackets(peer, packets) },
    { fileId -> fileStored(fileId) },
  )
  private val history = SyncHistory(
    identity,
    state,
    channel,
    { record -> signOrigin(record) },
    { record -> verifyOriginRecord(record) },
    { active, address, json -> send(active, address, json) },
    { applier.applyAll(state.syncEvents.pendingApplication(APPLY_BATCH)) },
  )
  private val replies = SyncReplies(
    identity,
    deviceSignedPreKeys,
    deviceBindings,
    state,
    policy,
    channel,
    history,
    ::verifyOrigin,
    validate,
    selfAddress,
    send,
    { applier.applyAll(state.syncEvents.pendingApplication(APPLY_BATCH)) },
    onAdmissionChanged,
  )
  private var discovery: SyncDiscovery? = null
  private var registrySource: () -> String = { "" }

  private val outbound = SyncOutbound(
    identity,
    state,
    channel,
    { record -> signOrigin(record) },
    { active, address, json -> send(active, address, json) },
  )

  private val autofetch = SyncFileAutoFetch(
    { state.messages.attachmentRefs() },
    { fileId -> SyncFileReceive.isStored(fileId) },
    { fileId -> files.request(fileId) },
    { fileId -> files.isPending(fileId) },
    { fileId -> files.progressOf(fileId) },
  )

  private fun fileStored(fileId: String) {
    autofetch.forget(fileId)
    state.fileWork = autofetch.tick()
  }

  @Volatile
  private var activeBridge: Bridge? = null

  fun publishSettings(block: String): Boolean {
    val payload = SyncSettings.payload(block, settingsValues(block)) ?: return false
    return outbound.publish(activeBridge, SyncEventKinds.SETTINGS_UPDATE, payload)
  }

  fun publishRetention(chatId: String, retention: String): Boolean {
    val payload = SyncSettings.retentionPayload(chatId, retention) ?: return false
    return outbound.publish(activeBridge, SyncEventKinds.SETTINGS_UPDATE, payload)
  }

  fun publishPatch(payload: JSONObject): Boolean =
    outbound.publish(activeBridge, SyncEventKinds.SETTINGS_UPDATE, payload)

  fun publishReaction(messageId: String, emoji: String, remove: Boolean): Boolean {
    val self = identity.current()?.upeerID() ?: return false
    return outbound.publish(activeBridge, SyncEventKinds.REACTION_SET, SyncReaction.payload(messageId, self, emoji, remove))
  }

  fun flushOutbound(): Int {
    val bridge = activeBridge ?: return 0
    return outbound.flush(bridge)
  }

  private fun settingsValues(block: String): Map<String, Boolean> = when (block) {
    SyncSettings.PRIVACY -> state.privacyValues()
    SyncSettings.NOTIFICATIONS -> state.notificationValues()
    else -> emptyMap()
  }

  @Volatile
  private var discoveryLoop: Thread? = null
  @Volatile
  private var progressLoop: Thread? = null
  private val seenPeers = java.util.concurrent.ConcurrentHashMap<String, Long>()

  fun attachDiscovery(
    store: (String, String) -> Int,
    read: (String) -> Any?,
    registry: () -> String,
    directPeer: () -> String = { "" },
  ) {
    discovery = SyncDiscovery(
      identity,
      deviceSignedPreKeys,
      selfAddress,
      store,
      read,
      send,
      directPeer,
      { activePeerAddresses() },
    )
    registrySource = registry
  }

  private fun greetForSession(activeBridge: Bridge, peer: SyncPeerRecord) {
    val now = System.currentTimeMillis()
    val previous = greeted[peer.deviceId] ?: 0L
    if (now - previous < GREET_INTERVAL_MS) return
    greeted[peer.deviceId] = now
    val discovery = discovery ?: return
    if (discovery.greet(activeBridge, peer.address)) {
      Log.i("UPeerTransport", "Sesión sin abrir con ${peer.deviceId.take(12)}: saludo reenviado para que la inicie")
    }
  }

  private fun activePeerAddresses(): Set<String> {
    val threshold = System.currentTimeMillis() - LIVE_PEER_WINDOW_MS
    return seenPeers.entries.filter { it.value >= threshold }.map { it.key }.toSet()
  }

  fun isSyncType(type: String): Boolean = SyncPackets.isSyncType(type)

  fun isSyncFileType(type: String): Boolean = files.isFileType(type)

  fun requestFileSync(fileId: String): Boolean = files.request(fileId)

  fun fileWork(): SyncFileWork = autofetch.snapshot()

  fun tickFileWork(): SyncFileWork {
    val peers = state.syncPeers.list().count { it.address.isNotBlank() }
    val work = if (peers == 0) autofetch.snapshot() else autofetch.tick()
    state.fileWork = work
    return work
  }

  private fun refreshFileWork(): SyncFileWork {
    val work = autofetch.refresh()
    if (work != state.fileWork) state.fileWork = work
    return work
  }

  fun filesHandling(packet: org.json.JSONObject, senderDeviceId: String, senderDevicePublicKey: String, source: String): Boolean {
    val type = packet.optString("type")
    if (!files.isFileType(type)) return false
    val device = identity.currentDevice()
    if (device == null) return true
    if (!packet.optString("recipientDeviceId").equals(device.deviceId, ignoreCase = true)) return true
    if (!files.handle(packet, senderDeviceId, senderDevicePublicKey)) {
      val bridge = activeBridge
      val peer = state.syncPeers.list().firstOrNull { it.deviceId == senderDeviceId }
      if (bridge != null && peer != null) {
        replies.requestSessionReset(bridge, peer, source, clearHistory = false)
      }
    }
    return true
  }

  private fun sendFilePacket(peer: SyncPeerRecord, packet: org.json.JSONObject): Boolean {
    val bridge = activeBridge ?: return false
    if (peer.address.isBlank()) return false
    val device = identity.currentDevice() ?: return false
    val frame = channel.sealEnvelope(bridge, device, peer, packet, packet) ?: run {
      Log.w("UPeerTransport", "No se pudo sellar el paquete de ficheros de sincronización")
      return false
    }
    return send(bridge, peer.address, frame)
  }

  private fun streamFilePackets(peer: SyncPeerRecord, packets: Iterator<org.json.JSONObject>): Boolean {
    val bridge = activeBridge ?: return false
    if (peer.address.isBlank()) return false
    val device = identity.currentDevice() ?: return false
    val sealed = packets.asSequence()
      .mapNotNull { packet -> channel.sealEnvelope(bridge, device, peer, packet, packet) }
      .iterator()
    return streamFrames(bridge, peer.address, sealed, "adjunto de sincronización")
  }

  private fun handleFilePacket(payload: ByteArray, packet: org.json.JSONObject, type: String, source: String): String {
    val self = identity.current()?.upeerID() ?: return InboundPolicy.rejected(packet)
    val auth = when (val result = SyncSelfAuth.authenticate(packet, self, System.currentTimeMillis(), ::verifyCertificate, ::isRevoked)) {
      is SyncSelfAuthResult.Rejected -> {
        Log.w("UPeerTransport", "Paquete $type rechazado: ${result.reason}")
        return InboundPolicy.rejected(packet)
      }
      is SyncSelfAuthResult.Accepted -> result.identity
    }
    if (!policy.allow(packet, source)) return policy.rateLimited(packet)
    try {
      validate(payload, auth.devicePublicKey, self)
    } catch (cause: Exception) {
      Log.w("UPeerTransport", "Firma de $type inválida para ${auth.deviceId.take(12)}: ${cause.message ?: cause.javaClass.simpleName}")
      return InboundPolicy.rejected(packet)
    }
    filesHandling(packet, auth.deviceId, auth.devicePublicKey, source)
    return JSONObject().put("type", type).put("senderUpeerId", self).put("deviceId", auth.deviceId).toString()
  }

  private fun verifyCertificate(certificateJSON: String, accountUpeerId: String, nowMillis: Long): Boolean =
    app.upeer.transportbridge.transportbridge.Transportbridge.verifyDeviceCertificate(certificateJSON, accountUpeerId, nowMillis)

  private fun isRevoked(deviceId: String): Boolean {
    val self = identity.current()?.upeerID() ?: return true
    return deviceBindings.isRevoked(self, deviceId)
  }

  fun handle(activeBridge: Bridge, payload: ByteArray, packet: org.json.JSONObject, type: String, source: String): String {
    seenPeers[source] = System.currentTimeMillis()
    this.activeBridge = activeBridge
    if (files.isFileType(type)) return handleFilePacket(payload, packet, type, source)
    return replies.handle(activeBridge, payload, packet, type, source)
  }

  fun start(activeBridge: Bridge) {
    val active = discovery ?: return
    this.activeBridge = activeBridge
    runCatching { history.resetSession() }
    runCatching { state.history.beginAllRuns() }
    runCatching { state.fileWork = autofetch.refresh() }
    val registry = registrySource
    discoveryLoop?.interrupt()
    val thread = Thread({
      while (!Thread.currentThread().isInterrupted) {
        runCatching { active.publish() }
        runCatching { active.discover(activeBridge, registry()) }
        runCatching { applyPending() }
        runCatching { flushOutbound() }
        try {
          Thread.sleep(DISCOVERY_INTERVAL_MS)
        } catch (_: InterruptedException) {
          return@Thread
        }
      }
    }, "upeer-sync-discovery")
    thread.isDaemon = true
    discoveryLoop = thread
    thread.start()
    startProgressLoop(activeBridge)
  }

  private fun startProgressLoop(activeBridge: Bridge) {
    progressLoop?.interrupt()
    val thread = Thread({
      var round = 0
      while (!Thread.currentThread().isInterrupted) {
        if (round % HEAVY_EVERY_TICKS == 0) {
          runCatching { applyPending() }
          runCatching { replies.pullHistory(activeBridge) }
          runCatching { tickFileWork() }
        } else {
          runCatching { refreshFileWork() }
        }
        round += 1
        try {
          Thread.sleep(PROGRESS_TICK_MS)
        } catch (_: InterruptedException) {
          return@Thread
        }
      }
    }, "upeer-sync-progress")
    thread.isDaemon = true
    progressLoop = thread
    thread.start()
  }

  private fun applyPending() {
    val pending = state.syncEvents.pendingApplication(APPLY_BATCH)
    if (pending.isNotEmpty()) {
      val applied = applier.applyAll(pending)
      Log.i("UPeerTransport", "Eventos de sincronización aplicados: $applied de ${pending.size}")
    }
    val repaired = applier.repairMissingMessages()
    if (repaired > 0) Log.i("UPeerTransport", "Mensajes sincronizados recuperados: $repaired")
    val relinked = state.messages.repairAttachmentPaths(SyncFileReceive::storedPath)
    if (relinked > 0) Log.i("UPeerTransport", "Adjuntos reenlazados a la copia sincronizada: $relinked")
  }

  private fun verifyOrigin(record: SyncEventRecord, account: String, now: Long): Boolean {
    val crypto = identity.current() ?: return false
    return SyncEventOrigin.fromBridge(crypto).verify(record, account, now)
  }

  private fun verifyOriginRecord(record: SyncEventRecord): Boolean {
    val crypto = identity.current() ?: return false
    return SyncEventOrigin.fromBridge(crypto).verify(record, crypto.upeerID(), System.currentTimeMillis())
  }

  private fun signOrigin(record: SyncEventRecord): SyncOriginSignature? {
    val device = identity.currentDevice() ?: return null
    return SyncEventOrigin.fromBridge(device.crypto).sign(record, device.deviceId, device.certificate)
  }

  private companion object {
    private const val DISCOVERY_INTERVAL_MS = 60_000L
    private const val PROGRESS_TICK_MS = 1_500L
    private const val HEAVY_EVERY_TICKS = 3
    private const val LIVE_PEER_WINDOW_MS = 300_000L
    private const val GREET_INTERVAL_MS = 30_000L
    private const val APPLY_BATCH = 500
  }

}
