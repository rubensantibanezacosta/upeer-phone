package app.upeer.transport.dht.kademlia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KademliaRoutingTableTest {
  private var now = 1_000L
  private val clock = { now }

  @Test
  fun evictsOldestContactAtKCapacity() {
    val bucket = KBucket(KademliaConfig.K, clock)
    repeat(KademliaConfig.K + 1) { index ->
      bucket.add(contact(index, "peer-$index"))
    }
    assertEquals(KademliaConfig.K, bucket.size())
    assertNull(bucket.all().firstOrNull { it.upeerId == "peer-0" })
    assertNotNull(bucket.all().firstOrNull { it.upeerId == "peer-${KademliaConfig.K}" })
  }

  @Test
  fun refreshUpdatesExistingContactWithoutGrowing() {
    val bucket = KBucket(KademliaConfig.K, clock)
    bucket.add(contact(1, "peer-1"))
    bucket.add(contact(2, "peer-2"))
    now += 10
    bucket.add(contact(1, "peer-1", now))
    assertEquals(2, bucket.size())
    assertEquals("peer-1", bucket.all().last().upeerId)
    assertEquals(now, bucket.all().last().lastSeen)
  }

  @Test
  fun routingTableUsesAllBucketsAndClosestOrder() {
    val upeerId = "0".repeat(32)
    val nodeId = KademliaId.fromUpeerId(upeerId)
    val table = KademliaRoutingTable(nodeId, clock)
    assertEquals(160, table.bucketCount())
    val first = contact("0".repeat(32), 0)
    val second = contact("1".repeat(32), 1)
    val third = contact("2".repeat(32), 2)
    assertFalse(table.addContact(first))
    assertTrue(table.addContact(second))
    assertTrue(table.addContact(third))
    assertEquals(2, table.contactCount())
    assertEquals(second, table.findContact(second.upeerId))
    assertEquals(third, table.findContact(third.upeerId))
    val expected = listOf(second, third)
      .sortedWith(Comparator { left, right -> left.nodeId.compareDistanceTo(right.nodeId, nodeId) })
    assertEquals(expected, table.findClosestContacts(nodeId, 2))
  }

  @Test
  fun reportsStaleBucketsAfterRefreshInterval() {
    val nodeId = KademliaId.fromUpeerId("3".repeat(32))
    val table = KademliaRoutingTable(nodeId, clock)
    assertTrue(table.addContact(contact("4".repeat(32), 4)))
    now += KademliaConfig.REFRESH_INTERVAL_MILLIS + 1
    assertTrue(table.refreshStaleBuckets().isNotEmpty())
  }

  private fun contact(index: Int, upeerId: String, lastSeen: Long = now): KademliaContact =
    KademliaContact(
      KademliaId.fromRawHex("%040x".format(index)),
      upeerId,
      "200::${index + 1}",
      "pk-$index",
      lastSeen,
    )

  private fun contact(upeerId: String, index: Int): KademliaContact =
    KademliaContact(
      KademliaId.fromUpeerId(upeerId),
      upeerId,
      "200::${index + 1}",
      "pk-$index",
      now,
    )
}
