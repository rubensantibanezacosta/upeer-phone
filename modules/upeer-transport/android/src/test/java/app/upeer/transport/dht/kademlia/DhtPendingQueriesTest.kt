package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhtPendingQueriesTest {
  private var now = 1_000L

  @Test
  fun consumesOnlyTheExpectedResponseFromTheRegisteredSource() {
    val pending = DhtPendingQueries { now }
    assertTrue(pending.register("q1", "DHT_FIND_NODE", "200::1"))
    assertFalse(pending.consume("q1", "DHT_FOUND_VALUE", "200::1"))
    assertFalse(pending.consume("q1", "DHT_FOUND_NODES", "200::2"))
    assertEquals(1, pending.size())
    assertTrue(pending.consume("q1", "DHT_FOUND_NODES", "200::1"))
    assertFalse(pending.consume("q1", "DHT_FOUND_NODES", "200::1"))
    assertEquals(0, pending.size())
  }

  @Test
  fun rejectsInvalidOrDuplicateQueries() {
    val pending = DhtPendingQueries { now }
    assertFalse(pending.register("", "DHT_FIND_NODE", "200::1"))
    assertFalse(pending.register("q".repeat(DhtLimits.MAX_QUERY_ID_LENGTH + 1), "DHT_FIND_NODE", "200::1"))
    assertFalse(pending.register("q1", "DHT_FOUND_NODES", "200::1"))
    assertTrue(pending.register("q1", "DHT_PING", "200::1"))
    assertFalse(pending.register("q1", "DHT_FIND_VALUE", "200::1"))
  }

  @Test
  fun supportsLegacyResponseCorrelationByTarget() {
    val pending = DhtPendingQueries { now }
    assertTrue(pending.register("target", "DHT_QUERY", "200::1"))
    assertTrue(pending.consume("target", "DHT_RESPONSE", "200::1"))
  }

  @Test
  fun expiresQueriesAfterDesktopTimeout() {
    val pending = DhtPendingQueries { now }
    assertTrue(pending.register("q1", "DHT_FIND_VALUE", "200::1"))
    now += DhtLimits.MAX_RESPONSE_AGE_MILLIS + 1
    assertEquals(0, pending.size())
    assertFalse(pending.consume("q1", "DHT_FOUND_VALUE", "200::1"))
  }
}
