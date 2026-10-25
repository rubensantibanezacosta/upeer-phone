package app.upeer.transport

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
) {
  private val channel = SyncChannel(identity, state)
  private val history = SyncHistory(
    identity,
    state,
    channel,
    { record -> signOrigin(record) },
    { record -> verifyOriginRecord(record) },
    { active, address, json -> send(active, address, json) },
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
  )
  private var discovery: SyncDiscovery? = null
  private var registrySource: () -> String = { "" }

  @Volatile
  private var discoveryLoop: Thread? = null

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
    return state.syncPeers.list()
      .filter { it.lastSeen >= threshold && it.address.isNotEmpty() }
      .map { it.address }
      .toSet()
  }

  fun isSyncType(type: String): Boolean = SyncPackets.isSyncType(type)

  fun handle(activeBridge: Bridge, payload: ByteArray, packet: org.json.JSONObject, type: String, source: String): String =
    replies.handle(activeBridge, payload, packet, type, source)

  fun start(activeBridge: Bridge) {
    val active = discovery ?: return
    val registry = registrySource
    discoveryLoop?.interrupt()
    val thread = Thread({
      while (!Thread.currentThread().isInterrupted) {
        runCatching { active.publish() }
        runCatching { active.discover(activeBridge, registry()) }
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

  private fun verifyOrigin(record: SyncEventRecord, account: String, now: Long): Boolean {
    val crypto = identity.current() ?: return false
    return SyncEventOrigin.fromBridge(crypto).verify(record, account, now)
  }

  private fun verifyOriginRecord(record: SyncEventRecord): Boolean {
    val crypto = identity.current() ?: return false
    return SyncEventOrigin.fromBridge(crypto).verify(record, crypto.upeerID(), System.currentTimeMillis())
  }

  private fun signOrigin(record: SyncEventRecord): SyncOriginSignature? {
    val crypto = identity.current() ?: return null
    val device = identity.currentDevice() ?: return null
    return SyncEventOrigin.fromBridge(crypto).sign(record, device.deviceId, device.certificate)
  }

  private companion object {
    private const val DISCOVERY_INTERVAL_MS = 60_000L
    private const val LIVE_PEER_WINDOW_MS = 300_000L
  }

}
