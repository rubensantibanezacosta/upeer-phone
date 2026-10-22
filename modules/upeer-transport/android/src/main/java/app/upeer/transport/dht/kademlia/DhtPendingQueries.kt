package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtLimits
import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.DhtResponse

class DhtPendingQueries(
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private data class Entry(
    val expectedType: String,
    val sourceAddress: String,
    val createdAt: Long,
  )

  private val lock = Any()
  private val entries = HashMap<String, Entry>()

  fun register(queryId: String, requestType: String, sourceAddress: String): Boolean {
    if (!validQueryId(queryId)) return false
    val expectedType = EXPECTED_RESPONSE[requestType] ?: return false
    val now = clock()
    synchronized(lock) {
      cleanupLocked(now)
      if (entries.containsKey(queryId)) return false
      entries[queryId] = Entry(expectedType, sourceAddress, now)
      return true
    }
  }

  fun consume(queryId: String, responseType: String, sourceAddress: String): Boolean {
    val now = clock()
    synchronized(lock) {
      cleanupLocked(now)
      val entry = entries[queryId] ?: return false
      if (entry.expectedType != responseType || entry.sourceAddress != sourceAddress) return false
      entries.remove(queryId)
      return true
    }
  }

  fun size(): Int = synchronized(lock) {
    cleanupLocked(clock())
    entries.size
  }

  fun cleanup() {
    synchronized(lock) { cleanupLocked(clock()) }
  }

  fun correlationKey(message: DhtMessage): String? =
    if (message is DhtResponse) message.queryId ?: message.targetId else message.queryId

  private fun cleanupLocked(now: Long) {
    entries.entries.removeAll { now - it.value.createdAt > DhtLimits.MAX_RESPONSE_AGE_MILLIS }
  }

  companion object {
    private val EXPECTED_RESPONSE = mapOf(
      "DHT_PING" to "DHT_PONG",
      "DHT_FIND_NODE" to "DHT_FOUND_NODES",
      "DHT_FIND_VALUE" to "DHT_FOUND_VALUE",
      "DHT_STORE" to "DHT_STORE_ACK",
      "DHT_QUERY" to "DHT_RESPONSE",
    )

    fun validQueryId(value: String): Boolean =
      value.isNotBlank() && value.length <= DhtLimits.MAX_QUERY_ID_LENGTH

    fun isResponse(type: String): Boolean = type in RESPONSE_TYPES

    fun expectsResponse(type: String): Boolean = EXPECTED_RESPONSE.containsKey(type)

    private val RESPONSE_TYPES = setOf(
      "DHT_PONG",
      "DHT_FOUND_NODES",
      "DHT_FOUND_VALUE",
      "DHT_STORE_ACK",
      "DHT_RESPONSE",
    )
  }
}
