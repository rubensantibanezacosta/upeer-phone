package app.upeer.transport

import android.util.Log
import app.upeer.transportbridge.transportbridge.Bridge

internal class ProtocolSync(
  private val identity: YggdrasilIdentity,
  private val deviceSignedPreKeys: DeviceSignedPreKeyStore,
  private val deviceBindings: DeviceMessagingBindings,
  private val state: ProtocolStateStore,
  private val policy: InboundPolicy,
  private val selfAddress: () -> String,
  private val validate: (ByteArray, String, String) -> Unit,
  private val send: (Bridge, String, String) -> Boolean,
  private val onAdmissionChanged: () -> Unit = {},
) {
  private val channel = SyncChannel(identity, state)
  private val applier = SyncApplier(state)
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

  @Volatile
  private var discoveryLoop: Thread? = null
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

  private fun activePeerAddresses(): Set<String> {
    val threshold = System.currentTimeMillis() - LIVE_PEER_WINDOW_MS
    return seenPeers.entries.filter { it.value >= threshold }.map { it.key }.toSet()
  }

  fun isSyncType(type: String): Boolean = SyncPackets.isSyncType(type)

  fun handle(activeBridge: Bridge, payload: ByteArray, packet: org.json.JSONObject, type: String, source: String): String {
    seenPeers[source] = System.currentTimeMillis()
    return replies.handle(activeBridge, payload, packet, type, source)
  }

  fun start(activeBridge: Bridge) {
    val active = discovery ?: return
    val registry = registrySource
    discoveryLoop?.interrupt()
    val thread = Thread({
      while (!Thread.currentThread().isInterrupted) {
        runCatching { active.publish() }
        runCatching { active.discover(activeBridge, registry()) }
        runCatching { applyPending() }
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
  }

  private fun applyPending() {
    val pending = state.syncEvents.pendingApplication(APPLY_BATCH)
    if (pending.isEmpty()) return
    val applied = applier.applyAll(pending)
    Log.i("UPeerTransport", "Eventos de sincronización aplicados: $applied de ${pending.size}")
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
    private const val LIVE_PEER_WINDOW_MS = 300_000L
    private const val APPLY_BATCH = 500
  }

}
