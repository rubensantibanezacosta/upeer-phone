package app.upeer.transport.dht.kademlia

class KBucket(
  private val maxSize: Int = KademliaConfig.K,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val contacts = mutableListOf<KademliaContact>()
  private var lastUpdated = clock()

  fun add(contact: KademliaContact): Boolean {
    val index = contacts.indexOfFirst { it.upeerId.equals(contact.upeerId, ignoreCase = true) }
    if (index >= 0) {
      contacts.removeAt(index)
    } else if (contacts.size >= maxSize) {
      contacts.removeAt(0)
    }
    contacts.add(contact)
    lastUpdated = clock()
    return true
  }

  fun remove(upeerId: String): Boolean =
    contacts.removeAll { it.upeerId.equals(upeerId, ignoreCase = true) }

  fun findClosest(targetId: KademliaId, limit: Int = KademliaConfig.K): List<KademliaContact> =
    contacts
      .sortedWith(Comparator { left, right -> left.nodeId.compareDistanceTo(right.nodeId, targetId) })
      .take(limit)

  fun needsRefresh(): Boolean = clock() - lastUpdated > KademliaConfig.REFRESH_INTERVAL_MILLIS

  fun all(): List<KademliaContact> = contacts.toList()

  fun size(): Int = contacts.size
}
