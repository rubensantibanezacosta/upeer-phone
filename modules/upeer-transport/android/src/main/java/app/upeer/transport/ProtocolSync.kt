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
  private val replies = SyncReplies(
    identity,
    deviceSignedPreKeys,
    deviceBindings,
    state,
    policy,
    channel,
    ::verifyOrigin,
    validate,
    selfAddress,
    send,
  )
  private var discovery: SyncDiscovery? = null
  private var registrySource: () -> String = { "" }

  fun attachDiscovery(store: (String, String) -> Int, read: (String) -> Any?, registry: () -> String) {
    discovery = SyncDiscovery(identity, deviceSignedPreKeys, selfAddress, store, read, send)
    registrySource = registry
  }

  fun isSyncType(type: String): Boolean = SyncPackets.isSyncType(type)

  fun handle(activeBridge: Bridge, payload: ByteArray, packet: org.json.JSONObject, type: String, source: String): String =
    replies.handle(activeBridge, payload, packet, type, source)

  fun start(activeBridge: Bridge) {
    val active = discovery ?: return
    val registry = registrySource
    val thread = Thread({
      active.publish()
      active.discover(activeBridge, registry())
    }, "upeer-sync-discovery")
    thread.isDaemon = true
    thread.start()
  }

  private fun verifyOrigin(record: SyncEventRecord, account: String, now: Long): Boolean {
    val crypto = identity.current() ?: return false
    return SyncEventOrigin.fromBridge(crypto).verify(record, account, now)
  }

}
