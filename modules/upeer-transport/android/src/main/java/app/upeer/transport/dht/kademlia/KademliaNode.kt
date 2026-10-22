package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtFindNode
import app.upeer.transport.dht.DhtFindValue
import app.upeer.transport.dht.DhtFoundNodes
import app.upeer.transport.dht.DhtFoundValue
import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.DhtNode
import app.upeer.transport.dht.DhtPing
import app.upeer.transport.dht.DhtPong
import app.upeer.transport.dht.DhtResponse
import app.upeer.transport.dht.DhtStore
import app.upeer.transport.dht.DhtStoreAck
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

data class KademliaStats(
  val messagesSent: Long,
  val messagesReceived: Long,
  val storeOperations: Long,
  val findOperations: Long,
)

class KademliaNode(
  val upeerId: String,
  private val send: (String, DhtMessage) -> Unit,
  private val clock: () -> Long = System::currentTimeMillis,
  private val queryTimeoutMillis: Long = KademliaConfig.QUERY_TIMEOUT_MILLIS,
) {
  val nodeId: KademliaId = KademliaId.fromUpeerId(upeerId)
  val routingTable: KademliaRoutingTable = KademliaRoutingTable(nodeId, clock)
  val valueStore: KademliaValueStore = KademliaValueStore(clock)
  private val queries = KademliaQueries(clock)
  private val random = SecureRandom()
  private val sent = AtomicLong()
  private val received = AtomicLong()
  private val stores = AtomicLong()
  private val finds = AtomicLong()
  private val iterative = KademliaIterative(
    nodeId,
    routingTable,
    valueStore,
    queries,
    { address, message -> sendMessage(address, message) },
    ::ingestNodes,
    clock,
    queryTimeoutMillis,
  )

  fun handleIncoming(senderUpeerId: String, message: DhtMessage, senderAddress: String): DhtMessage? {
    received.incrementAndGet()
    val senderId = runCatching { KademliaId.fromUpeerId(senderUpeerId) }.getOrNull() ?: return null
    trackContact(senderUpeerId.lowercase(), senderId, senderAddress)
    return when (message) {
      is DhtPing -> DhtPong(message.queryId, nodeId.hex)
      is DhtFindNode -> handleFindNode(message)
      is DhtFindValue -> handleFindValue(message)
      is DhtStore -> handleStore(senderUpeerId, message)
      is DhtFoundNodes -> {
        ingestNodes(message.nodes)
        queries.resolve(message)
        null
      }
      is DhtFoundValue -> {
        message.nodes?.let(::ingestNodes)
        queries.resolve(message)
        null
      }
      is DhtPong -> {
        queries.resolve(message)
        null
      }
      is DhtStoreAck -> {
        queries.resolve(message)
        null
      }
      is DhtResponse -> {
        queries.resolve(message)
        null
      }
      else -> null
    }
  }

  fun storeValue(key: String, value: Any, publisher: String = upeerId): Int {
    stores.incrementAndGet()
    return iterative.storeValue(key, value, publisher)
  }

  fun findValue(key: String, localFirst: Boolean = true): KademliaStoredValue? {
    finds.incrementAndGet()
    return iterative.findValue(key, localFirst)
  }

  fun findNode(targetId: String): List<KademliaContact> {
    finds.incrementAndGet()
    return iterative.findNode(targetId)
  }

  fun addContact(contact: KademliaContact): Boolean = routingTable.addContact(contact)

  fun applyLocation(upeerId: String, address: String, dhtSeq: Long): Boolean {
    val normalized = upeerId.trim().lowercase()
    if (address.isBlank() || dhtSeq < 0L) return false
    val nodeId = runCatching { KademliaId.fromUpeerId(normalized) }.getOrNull() ?: return false
    val existing = routingTable.findContact(normalized)
    return routingTable.addContact(
      KademliaContact(
        nodeId,
        normalized,
        address,
        existing?.publicKey.orEmpty(),
        clock(),
        maxOf(existing?.dhtSeq ?: 0L, dhtSeq),
      ),
    )
  }


  fun probeContacts(candidates: List<KademliaContact>, timeoutMillis: Long = queryTimeoutMillis): List<KademliaContact> {
    val pending = mutableListOf<Pair<String, KademliaContact>>()
    candidates.forEach { candidate ->
      val upeerId = candidate.upeerId.trim().lowercase()
      if (upeerId.isEmpty() || upeerId == this.upeerId) return@forEach
      if (pending.any { it.second.upeerId == upeerId }) return@forEach
      if (candidate.address.isBlank()) return@forEach
      val queryId = newQueryId()
      if (!queries.register(queryId)) return@forEach
      try {
        sendMessage(candidate.address, DhtPing(null, nodeId.hex))
        sendMessage(candidate.address, DhtFindNode(queryId, this.upeerId))
      } catch (_: Exception) {
        queries.cancel(queryId)
        return@forEach
      }
      pending.add(
        queryId to KademliaContact(
          candidate.nodeId,
          upeerId,
          candidate.address,
          candidate.publicKey,
          clock(),
          candidate.dhtSeq,
        ),
      )
    }
    if (pending.isEmpty()) return emptyList()
    val responses = queries.awaitAll(pending.map { it.first }, timeoutMillis.coerceAtLeast(0))
    val verified = mutableListOf<KademliaContact>()
    pending.forEachIndexed { index, entry ->
      if (responses.getOrNull(index) !is DhtFoundNodes) return@forEachIndexed
      routingTable.addContact(entry.second)
      verified.add(entry.second)
    }
    return verified
  }

  fun broadcastPing(address: String): Boolean {
    if (address.isBlank()) return false
    return try {
      sendMessage(address, DhtPing(null, nodeId.hex))
      true
    } catch (_: Exception) {
      false
    }
  }

  fun maintenance(): Int {
    queries.cleanup()
    return valueStore.cleanupExpiredValues()
  }

  fun staleBuckets(): List<Int> = routingTable.refreshStaleBuckets()

  fun stats(): KademliaStats = KademliaStats(
    sent.get(),
    received.get(),
    stores.get(),
    finds.get(),
  )

  private fun handleFindNode(message: DhtFindNode): DhtFoundNodes? {
    val target = runCatching { KademliaId.fromLookupHex(message.targetId) }.getOrNull() ?: return null
    return foundNodes(message.queryId, target)
  }

  private fun handleFindValue(message: DhtFindValue): DhtMessage {
    val key = message.key.lowercase()
    val stored = valueStore.get(key)
    if (stored != null) {
      return DhtFoundValue(message.queryId, key, stored.value, null, stored.publisher, stored.timestamp)
    }
    val target = runCatching { KademliaId.fromLookupHex(key) }.getOrNull()
      ?: return DhtFoundNodes(message.queryId, emptyList())
    return foundNodes(message.queryId, target)
  }

  private fun handleStore(senderUpeerId: String, message: DhtStore): DhtStoreAck {
    valueStore.set(message.key, message.value, message.publisher ?: senderUpeerId.lowercase())
    stores.incrementAndGet()
    return DhtStoreAck(message.queryId, message.key.lowercase())
  }

  private fun foundNodes(queryId: String?, target: KademliaId): DhtFoundNodes =
    DhtFoundNodes(
      queryId,
      routingTable.findClosestContacts(target).map { contact ->
        DhtNode(contact.upeerId, contact.address, contact.publicKey, contact.nodeId.hex)
      },
    )

  private fun trackContact(senderUpeerId: String, senderId: KademliaId, senderAddress: String) {
    val existing = routingTable.findContact(senderUpeerId)
    routingTable.addContact(
      KademliaContact(
        senderId,
        senderUpeerId,
        senderAddress,
        existing?.publicKey ?: "",
        clock(),
        existing?.dhtSeq,
      ),
    )
  }

  private fun ingestNodes(nodes: List<DhtNode>) {
    nodes.forEach { node ->
      val publicKey = node.publicKey ?: return@forEach
      if (publicKey.isBlank() || node.address.isBlank()) return@forEach
      val nodeId = runCatching { KademliaId.fromUpeerId(node.upeerId) }.getOrNull() ?: return@forEach
      routingTable.addContact(
        KademliaContact(nodeId, node.upeerId.lowercase(), node.address, publicKey, clock()),
      )
    }
  }

  private fun sendMessage(address: String, message: DhtMessage) {
    send(address, message)
    sent.incrementAndGet()
  }

  private fun newQueryId(): String {
    val bytes = ByteArray(16)
    random.nextBytes(bytes)
    return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
  }
}

