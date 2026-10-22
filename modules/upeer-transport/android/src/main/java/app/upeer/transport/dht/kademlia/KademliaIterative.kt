package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtFindNode
import app.upeer.transport.dht.DhtFindValue
import app.upeer.transport.dht.DhtFoundNodes
import app.upeer.transport.dht.DhtFoundValue
import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.DhtNode
import app.upeer.transport.dht.DhtStore
import app.upeer.transport.dht.DhtStoreAck
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

internal class KademliaIterative(
  private val selfId: KademliaId,
  private val routingTable: KademliaRoutingTable,
  private val valueStore: KademliaValueStore,
  private val queries: KademliaQueries,
  private val send: (String, DhtMessage) -> Unit,
  private val ingest: (List<DhtNode>) -> Unit,
  private val clock: () -> Long,
  private val queryTimeoutMillis: Long = KademliaConfig.QUERY_TIMEOUT_MILLIS,
  private val totalTimeoutMillis: Long = KademliaConfig.LOOKUP_TOTAL_TIMEOUT_MILLIS,
  private val storeTimeoutMillis: Long = KademliaConfig.STORE_TIMEOUT_MILLIS,
  private val random: SecureRandom = SecureRandom(),
) {
  private data class Search(
    val contacts: List<KademliaContact>,
    val values: List<DhtFoundValue>,
  )

  fun findNode(targetId: String): List<KademliaContact> {
    val target = runCatching { KademliaId.fromLookupHex(targetId) }.getOrNull() ?: return emptyList()
    return search(targetId, null, target, deadline()).contacts
  }

  fun findValue(key: String, localFirst: Boolean): KademliaStoredValue? {
    if (localFirst) valueStore.get(key)?.let { return it }
    val normalized = key.lowercase()
    val target = runCatching { KademliaId.fromLookupHex(normalized) }.getOrNull() ?: return null
    val result = search(null, normalized, target, deadline())
    val merged = KademliaValueMerge.merge(result.values.mapNotNull { it.value }) ?: return null
    val metadata = result.values.maxByOrNull { it.timestamp ?: 0L }
    return KademliaStoredValue(
      normalized,
      merged,
      metadata?.publisher ?: "",
      metadata?.timestamp ?: clock(),
    )
  }

  fun storeValue(key: String, value: Any, publisher: String): Int {
    valueStore.set(key, value, publisher)
    val normalized = key.lowercase()
    val target = runCatching { KademliaId.fromLookupHex(normalized) }.getOrNull() ?: return 0
    val contacts = search(normalized, null, target, deadline()).contacts
    return replicate(normalized, value, publisher, contacts)
  }

  private fun search(
    targetId: String?,
    key: String?,
    target: KademliaId,
    deadlineNanos: Long,
  ): Search {
    val candidates = linkedMapOf<String, KademliaContact>()
    routingTable.findClosestContacts(target).forEach { contact -> candidates[contact.upeerId] = contact }
    val queried = HashSet<String>()
    val values = mutableListOf<DhtFoundValue>()
    var rounds = 0
    while (rounds < KademliaConfig.MAX_LOOKUP_ROUNDS && System.nanoTime() < deadlineNanos) {
      rounds += 1
      val closest = closest(candidates, target)
      if (closest.take(KademliaConfig.K).all { queried.contains(it.upeerId) }) break
      val toQuery = closest.filter { queried.add(it.upeerId) }.take(KademliaConfig.ALPHA)
      if (toQuery.isEmpty()) break
      val pending = mutableListOf<Pair<String, KademliaContact>>()
      toQuery.forEach { contact ->
        val queryId = newQueryId()
        if (!queries.register(queryId, key)) return@forEach
        try {
          val message = if (key == null) {
            DhtFindNode(queryId, targetId ?: return@forEach)
          } else {
            DhtFindValue(queryId, key)
          }
          send(contact.address, message)
          pending.add(queryId to contact)
        } catch (_: Exception) {
          queries.cancel(queryId)
        }
      }
      if (pending.isEmpty()) break
      val remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()).coerceAtLeast(0)
      val responses = queries.awaitAll(
        pending.map { it.first },
        minOf(queryTimeoutMillis, remainingMillis),
      )
      pending.forEachIndexed { index, _ ->
        when (val response = responses.getOrNull(index)) {
          is DhtFoundNodes -> ingestNodes(response.nodes, candidates)
          is DhtFoundValue -> {
            values.add(response)
            response.nodes?.let { ingestNodes(it, candidates) }
          }
          else -> Unit
        }
      }
    }
    return Search(closest(candidates, target).take(KademliaConfig.K), values)
  }

  private fun replicate(
    key: String,
    value: Any,
    publisher: String,
    contacts: List<KademliaContact>,
  ): Int {
    val targets = contacts
      .filter { it.nodeId != selfId && it.upeerId.isNotBlank() && it.address.isNotBlank() }
      .distinctBy { it.upeerId.lowercase() }
      .take(KademliaConfig.K)
    if (targets.isEmpty()) return 0
    val accepted = sendStores(key, value, publisher, targets).toMutableSet()
    repeat(KademliaConfig.STORE_RETRIES) {
      if (accepted.size >= targets.size) return@repeat
      val failed = targets.filter { !accepted.contains(it.upeerId.lowercase()) }
      accepted.addAll(sendStores(key, value, publisher, failed))
    }
    return accepted.size
  }

  private fun sendStores(
    key: String,
    value: Any,
    publisher: String,
    contacts: List<KademliaContact>,
  ): Set<String> {
    val pending = mutableListOf<Pair<String, KademliaContact>>()
    contacts.forEach { contact ->
      val queryId = newQueryId()
      if (!queries.register(queryId, key)) return@forEach
      try {
        send(contact.address, DhtStore(queryId, key, value, publisher, clock(), null))
        pending.add(queryId to contact)
      } catch (_: Exception) {
        queries.cancel(queryId)
      }
    }
    if (pending.isEmpty()) return emptySet()
    val responses = queries.awaitAll(
      pending.map { it.first },
      minOf(storeTimeoutMillis, queryTimeoutMillis),
    )
    return pending.mapIndexedNotNull { index, entry ->
      val ack = responses.getOrNull(index) as? DhtStoreAck ?: return@mapIndexedNotNull null
      if (!ack.key.equals(key, ignoreCase = true)) return@mapIndexedNotNull null
      entry.second.upeerId.lowercase()
    }.toSet()
  }

  private fun ingestNodes(nodes: List<DhtNode>, candidates: MutableMap<String, KademliaContact>) {
    nodes.forEach { node ->
      val upeerId = node.upeerId.trim().lowercase()
      if (upeerId.isEmpty() || candidates.containsKey(upeerId)) return@forEach
      val publicKey = node.publicKey ?: return@forEach
      if (publicKey.isBlank() || node.address.isBlank()) return@forEach
      val nodeId = runCatching { KademliaId.fromUpeerId(upeerId) }.getOrNull() ?: return@forEach
      if (nodeId == selfId) return@forEach
      candidates[upeerId] = KademliaContact(nodeId, upeerId, node.address, publicKey, clock())
    }
    ingest(nodes)
  }

  private fun closest(candidates: Map<String, KademliaContact>, target: KademliaId): List<KademliaContact> =
    candidates.values.sortedWith { left, right -> left.nodeId.compareDistanceTo(right.nodeId, target) }

  private fun deadline(): Long =
    System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMillis.coerceAtLeast(1))

  private fun newQueryId(): String {
    val bytes = ByteArray(16)
    random.nextBytes(bytes)
    return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
  }
}
