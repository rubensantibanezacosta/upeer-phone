package app.upeer.transport.dht.kademlia

data class KademliaLanPolicy(
  val multicastPermitted: Boolean,
  val wifiMulticastPermission: Boolean,
  val serviceActive: Boolean,
  val networkAvailable: Boolean,
  val batterySaving: Boolean,
) {
  fun allowed(): Boolean =
    multicastPermitted && wifiMulticastPermission && serviceActive && networkAvailable && !batterySaving
}

class KademliaLanDiscovery(
  private val node: KademliaNode,
  private val policy: () -> KademliaLanPolicy,
  private val clock: () -> Long = System::currentTimeMillis,
  private val probeIntervalMillis: Long = KademliaConfig.LAN_PROBE_INTERVAL_MILLIS,
) {
  private val lock = Any()
  private var lastProbe = 0L

  fun probe(scopeId: Int): Boolean {
    if (!policy().allowed()) return false
    val now = clock()
    synchronized(lock) {
      if (lastProbe > 0L && now - lastProbe < probeIntervalMillis) return false
    }
    if (!node.broadcastPing(destination(scopeId))) return false
    synchronized(lock) { lastProbe = now }
    return true
  }

  fun lastProbeMillis(): Long = synchronized(lock) { lastProbe }

  companion object {
    const val MULTICAST_ADDRESS = "ff02::1"

    fun destination(scopeId: Int): String =
      if (scopeId > 0) "$MULTICAST_ADDRESS%$scopeId" else MULTICAST_ADDRESS
  }
}
