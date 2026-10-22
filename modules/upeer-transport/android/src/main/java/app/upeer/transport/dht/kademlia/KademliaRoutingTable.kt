package app.upeer.transport.dht.kademlia

class KademliaRoutingTable(
  val nodeId: KademliaId,
  private val clock: () -> Long = System::currentTimeMillis,
  private val bucketSize: Int = KademliaConfig.K,
) {
  private val lock = Any()
  private val buckets = List(KademliaConfig.BUCKET_COUNT) { KBucket(bucketSize, clock) }

  fun addContact(contact: KademliaContact): Boolean = synchronized(lock) {
    if (contact.nodeId == nodeId) return@synchronized false
    buckets[KademliaId.bucketIndex(nodeId, contact.nodeId)].add(contact)
  }

  fun removeContact(upeerId: String): Boolean = synchronized(lock) {
    val contact = findContactLocked(upeerId) ?: return@synchronized false
    buckets[KademliaId.bucketIndex(nodeId, contact.nodeId)].remove(upeerId)
  }

  fun findContact(upeerId: String): KademliaContact? = synchronized(lock) {
    findContactLocked(upeerId)
  }

  fun findClosestContacts(targetId: KademliaId, limit: Int = KademliaConfig.K): List<KademliaContact> =
    synchronized(lock) {
      buckets
        .flatMap { it.all() }
        .sortedWith(Comparator { left, right -> left.nodeId.compareDistanceTo(right.nodeId, targetId) })
        .take(limit)
    }

  fun allContacts(): List<KademliaContact> = synchronized(lock) {
    buckets.flatMap { it.all() }
  }

  fun contactCount(): Int = synchronized(lock) { buckets.sumOf { it.size() } }

  fun refreshStaleBuckets(): List<Int> = synchronized(lock) {
    buckets.indices.filter { buckets[it].needsRefresh() }
  }

  fun bucket(index: Int): KBucket = synchronized(lock) { buckets[index] }

  fun bucketCount(): Int = buckets.size

  private fun findContactLocked(upeerId: String): KademliaContact? =
    buckets
      .asSequence()
      .flatMap { it.all().asSequence() }
      .firstOrNull { it.upeerId.equals(upeerId, ignoreCase = true) }
}
