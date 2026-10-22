package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtFoundNodes
import app.upeer.transport.dht.DhtFoundValue
import app.upeer.transport.dht.DhtLimits
import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.DhtResponse
import app.upeer.transport.dht.DhtStoreAck
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class KademliaQueries(
  private val clock: () -> Long = System::currentTimeMillis,
  private val pendingTtlMillis: Long = KademliaConfig.PENDING_QUERY_TTL_MILLIS,
) {
  private data class Entry(
    val target: String?,
    val createdAt: Long,
    var message: DhtMessage? = null,
    var cancelled: Boolean = false,
  )

  private val lock = ReentrantLock()
  private val condition = lock.newCondition()
  private val entries = HashMap<String, Entry>()

  fun register(queryId: String, target: String? = null): Boolean {
    if (queryId.isBlank() || queryId.length > DhtLimits.MAX_QUERY_ID_LENGTH) return false
    lock.lock()
    try {
      cleanupLocked(clock())
      if (entries.containsKey(queryId)) return false
      entries[queryId] = Entry(target?.lowercase(), clock())
      return true
    } finally {
      lock.unlock()
    }
  }

  fun await(queryId: String, timeoutMillis: Long): DhtMessage? {
    val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis.coerceAtLeast(0))
    val deadline = System.nanoTime() + timeoutNanos
    lock.lock()
    try {
      val entry = entries[queryId] ?: return null
      while (entry.message == null && !entry.cancelled) {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) break
        try {
          condition.awaitNanos(remaining)
        } catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
          break
        }
      }
      entries.remove(queryId)
      if (entry.message == null) entry.cancelled = true
      return entry.message
    } finally {
      lock.unlock()
    }
  }

  fun resolve(message: DhtMessage): Boolean {
    val queryId = message.queryId ?: return false
    lock.lock()
    try {
      cleanupLocked(clock())
      val entry = entries[queryId] ?: return false
      if (entry.message != null) return false
      val expected = entry.target
      val actual = responseTarget(message)
      if (expected != null && actual != null && !expected.equals(actual, ignoreCase = true)) return false
      entry.message = message
      condition.signalAll()
      return true
    } finally {
      lock.unlock()
    }
  }

  fun awaitAll(queryIds: List<String>, timeoutMillis: Long): List<DhtMessage?> {
    if (queryIds.isEmpty()) return emptyList()
    val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis.coerceAtLeast(0))
    val deadline = System.nanoTime() + timeoutNanos
    lock.lock()
    try {
      while (hasPendingLocked(queryIds)) {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) break
        try {
          condition.awaitNanos(remaining)
        } catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
          break
        }
      }
      return queryIds.map { queryId ->
        val entry = entries[queryId]
        val message = entry?.message
        if (message == null) entry?.cancelled = true
        message
      }
    } finally {
      queryIds.forEach(entries::remove)
      lock.unlock()
    }
  }

  fun cleanup(): Int {
    lock.lock()
    try {
      return cleanupLocked(clock())
    } finally {
      lock.unlock()
    }
  }

  fun cancel(queryId: String) {
    lock.lock()
    try {
      val entry = entries.remove(queryId) ?: return
      entry.cancelled = true
      condition.signalAll()
    } finally {
      lock.unlock()
    }
  }

  fun size(): Int {
    lock.lock()
    try {
      cleanupLocked(clock())
      return entries.size
    } finally {
      lock.unlock()
    }
  }

  private fun hasPendingLocked(queryIds: List<String>): Boolean =
    queryIds.any { queryId ->
      val entry = entries[queryId]
      entry != null && entry.message == null && !entry.cancelled
    }

  private fun cleanupLocked(now: Long): Int {
    val expired = entries.filterValues { now - it.createdAt > pendingTtlMillis }
    expired.forEach { (queryId, entry) ->
      entries.remove(queryId)
      entry.cancelled = true
    }
    if (expired.isNotEmpty()) condition.signalAll()
    return expired.size
  }

  private fun responseTarget(message: DhtMessage): String? = when (message) {
    is DhtFoundValue -> message.key
    is DhtStoreAck -> message.key
    is DhtResponse -> message.targetId
    is DhtFoundNodes -> null
    else -> null
  }
}

