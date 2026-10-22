package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtFoundValue
import app.upeer.transport.dht.DhtPong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KademliaQueriesTest {
  private var now = 1_000L
  private val clock = { now }

  @Test
  fun resolvesOnlyTheMatchingQueryAndTarget() {
    val queries = KademliaQueries(clock)
    assertTrue(queries.register("query-1", "aa"))
    assertFalse(queries.resolve(DhtFoundValue("other", "aa", "value", null, "peer", now)))
    assertFalse(queries.resolve(DhtFoundValue("query-1", "bb", "value", null, "peer", now)))
    val message = DhtFoundValue("query-1", "aa", "value", null, "peer", now)
    assertTrue(queries.resolve(message))
    assertFalse(queries.resolve(message))
    assertEquals(message, queries.await("query-1", 50))
  }

  @Test
  fun timesOutWithoutADeliveredResponse() {
    val queries = KademliaQueries(clock)
    assertTrue(queries.register("query-1"))
    assertNull(queries.await("query-1", 10))
    assertFalse(queries.resolve(DhtPong("query-1", "node")))
  }

  @Test
  fun cleansExpiredQueriesAndRejectsDuplicates() {
    val queries = KademliaQueries(clock, pendingTtlMillis = 100)
    assertTrue(queries.register("query-1"))
    assertFalse(queries.register("query-1"))
    now += 101
    assertEquals(1, queries.cleanup())
    assertEquals(0, queries.size())
  }

  @Test
  fun awaitAllReturnsResponsesThatArriveWhileAnotherQueryIsPending() {
    val queries = KademliaQueries(clock)
    assertTrue(queries.register("query-1", "aa"))
    assertTrue(queries.register("query-2", "bb"))
    val response = DhtFoundValue("query-2", "bb", "value", null, "peer", now)
    assertTrue(queries.resolve(response))
    val results = queries.awaitAll(listOf("query-1", "query-2"), 20)
    assertNull(results[0])
    assertEquals(response, results[1])
  }

  @Test
  fun awaitAllReturnsCompletedResponsesAndDoesNotWaitForMissingOnes() {
    val queries = KademliaQueries(clock)
    assertTrue(queries.register("query-1", "aa"))
    assertTrue(queries.register("query-2", "bb"))
    val response = DhtFoundValue("query-1", "aa", "value", null, "peer", now)
    assertTrue(queries.resolve(response))
    val started = System.currentTimeMillis()
    val results = queries.awaitAll(listOf("query-1", "query-2"), 20)
    assertEquals(response, results[0])
    assertNull(results[1])
    assertTrue(System.currentTimeMillis() - started < 1_000)
    assertNotNull(results[0])
  }
}
