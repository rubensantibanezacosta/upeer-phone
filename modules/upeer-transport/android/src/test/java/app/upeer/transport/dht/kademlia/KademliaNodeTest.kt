package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KademliaNodeTest {
  private var now = 1_000L
  private val clock = { now }
  private val upeerA = "a1".repeat(16)
  private val upeerB = "b2".repeat(16)
  private val upeerC = "c3".repeat(16)
  private val addressA = "200::a1"
  private val addressB = "200::b2"
  private val addressC = "200::c3"

  @Test
  fun threeNodeNetworkDiscoversStoresAndFindsValues() {
    val network = SimNetwork()
    val nodeA = node(upeerA, addressA, network)
    val nodeB = node(upeerB, addressB, network)
    val nodeC = node(upeerC, addressC, network)
    nodeA.routingTable.addContact(contact(upeerB, addressB))
    nodeA.routingTable.addContact(contact(upeerC, addressC))
    nodeB.routingTable.addContact(contact(upeerA, addressA))
    nodeB.routingTable.addContact(contact(upeerC, addressC))
    nodeC.routingTable.addContact(contact(upeerA, addressA))

    val discovered = nodeC.findNode(upeerB)
    assertTrue(discovered.any { it.upeerId == upeerB })
    assertNotNull(nodeC.routingTable.findContact(upeerB))

    val key = "11".repeat(20)
    assertEquals(2, nodeA.storeValue(key, "hello"))
    assertEquals("hello", nodeB.valueStore.get(key)?.value)
    assertEquals("hello", nodeC.valueStore.get(key)?.value)

    val remoteKey = "22".repeat(20)
    nodeB.valueStore.set(remoteKey, "remote", upeerB)
    val found = nodeC.findValue(remoteKey)
    assertEquals("remote", found?.value)
    assertEquals(upeerB, found?.publisher)
  }

  @Test
  fun expiryAndMaintenanceRemoveValues() {
    val node = node(upeerA, addressA, SimNetwork())
    node.valueStore.set("33".repeat(20), "value", upeerA)
    now += KademliaConfig.VALUE_TTL_MILLIS + 1
    assertEquals(1, node.maintenance())
    assertNull(node.valueStore.get("33".repeat(20)))
  }

  @Test
  fun unresponsivePeerDoesNotBlockLookup() {
    val node = KademliaNode(upeerA, { _, _ -> }, clock, queryTimeoutMillis = 25)
    node.routingTable.addContact(contact(upeerB, addressB))
    val started = System.currentTimeMillis()
    assertNull(node.findValue("44".repeat(20)))
    assertTrue(System.currentTimeMillis() - started < 1_000)
    assertEquals(1, node.stats().findOperations)
  }

  @Test
  fun forcedLookupBypassesAStaleLocalValue() {
    val network = SimNetwork()
    val nodeA = node(upeerA, addressA, network)
    val nodeB = node(upeerB, addressB, network)
    nodeA.routingTable.addContact(contact(upeerB, addressB))
    val key = "55".repeat(20)
    nodeA.valueStore.set(key, "stale", upeerA)
    nodeB.valueStore.set(key, "fresh", upeerB)

    assertEquals("stale", nodeA.findValue(key)?.value)
    assertEquals("fresh", nodeA.findValue(key, localFirst = false)?.value)
  }

  private fun node(upeerId: String, address: String, network: SimNetwork): KademliaNode {
    val node = KademliaNode(
      upeerId,
      { targetAddress, message -> network.send(upeerId, address, targetAddress, message) },
      clock,
      queryTimeoutMillis = 200,
    )
    network.register(address, node)
    return node
  }

  private fun contact(upeerId: String, address: String): KademliaContact =
    KademliaContact(KademliaId.fromUpeerId(upeerId), upeerId, address, "pk-${upeerId.take(4)}", now)

  private class SimNetwork {
    private val nodes = mutableMapOf<String, KademliaNode>()

    fun register(address: String, node: KademliaNode) {
      nodes[address] = node
    }

    fun send(sourceUpeerId: String, sourceAddress: String, targetAddress: String, message: DhtMessage) {
      val target = nodes[targetAddress] ?: return
      val response = target.handleIncoming(sourceUpeerId, message, sourceAddress)
      if (response != null) {
        nodes[sourceAddress]?.handleIncoming(target.upeerId, response, targetAddress)
      }
    }
  }
}
