package app.upeer.transport.dht

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DhtLocationCacheTest {
  private var now = 1_000L
  private val clock = { now }
  private val account = "aa".repeat(32)

  @Test
  fun cachesVerifiedBlocksUntilTheCacheDeadline() {
    val cache = DhtLocationCache(clock, ttlMillis = 100L)
    cache.put(account, "block", now + 10_000L)
    assertEquals("block", cache.get(account))
    now += 99
    assertEquals("block", cache.get(account))
    now += 2
    assertNull(cache.get(account))
  }

  @Test
  fun respectsTheBlockExpiration() {
    val cache = DhtLocationCache(clock, ttlMillis = 100L)
    cache.put(account, "block", now + 20L)
    now += 21
    assertNull(cache.get(account))
  }

  @Test
  fun refusesAlreadyExpiredBlocks() {
    val cache = DhtLocationCache(clock, ttlMillis = 100L)
    cache.put(account, "block", now)
    assertNull(cache.get(account))
  }

  @Test
  fun invalidatesAndBoundsEntries() {
    val cache = DhtLocationCache(clock, ttlMillis = 100L, maxEntries = 1)
    val other = "bb".repeat(32)
    cache.put(account, "first", now + 1_000L)
    cache.put(other, "second", now + 1_000L)
    assertNull(cache.get(account))
    assertEquals("second", cache.get(other))
    cache.invalidate(other)
    assertNull(cache.get(other))
  }
}
