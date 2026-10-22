package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.DhtStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KademliaIterativeTest {
  private var now = 1_000L
  private val clock = { now }
  private val upeerA = "a1".repeat(16)
  private val upeerB = "b2".repeat(16)
  private val upeerC = "c3".repeat(16)
  private val addressA = "200::a1"
  private val addressB = "200::b2"
  private val addressC = "200::c3"

  @Test
  fun findValueLearnsNodesBeyondTheFirstContact() {
    val network = SimNetwork()
    val nodeA = node(upeerA, addressA, network)
    val nodeB = node(upeerB, addressB, network)
    val nodeC = node(upeerC, addressC, network)
    nodeA.addContact(contact(upeerB, addressB))
    nodeB.addContact(contact(upeerC, addressC))
    val key = "22".repeat(20)
    nodeC.valueStore.set(key, "remote", upeerC)

    val found = nodeA.findValue(key, localFirst = false)

    assertEquals("remote", found?.value)
    assertNotNull(nodeA.routingTable.findContact(upeerC))
  }

  @Test
  fun storeReplicatesToNodesDiscoveredIteratively() {
    val network = SimNetwork()
    val nodeA = node(upeerA, addressA, network)
    val nodeB = node(upeerB, addressB, network)
    val nodeC = node(upeerC, addressC, network)
    nodeA.addContact(contact(upeerB, addressB))
    nodeB.addContact(contact(upeerC, addressC))
    val key = "33".repeat(20)

    val deliveries = nodeA.storeValue(key, "value")

    assertEquals(2, deliveries)
    assertEquals("value", nodeB.valueStore.get(key)?.value)
    assertEquals("value", nodeC.valueStore.get(key)?.value)
  }

  @Test
  fun findValueMergesBlocksFromTwoDevicesAndKeepsTheHighestSequence() {
    val network = SimNetwork()
    val nodeA = node(upeerA, addressA, network)
    val nodeB = node(upeerB, addressB, network)
    val nodeC = node(upeerC, addressC, network)
    nodeA.addContact(contact(upeerB, addressB))
    nodeA.addContact(contact(upeerC, addressC))
    nodeB.addContact(contact(upeerC, addressC))
    val key = "55".repeat(20)
    nodeB.valueStore.set(key, deviceBlock("device-a", 1L), upeerB)
    nodeC.valueStore.set(key, deviceBlock("device-b", 2L), upeerC)

    val found = nodeA.findValue(key, localFirst = false)
    val blocks = found?.value as JSONArray

    assertEquals(2, blocks.length())
    assertEquals(2L, blocks.getJSONObject(0).getLong("dhtSeq"))
    assertEquals("device-b", blocks.getJSONObject(0).getString("deviceId"))
  }

  @Test
  fun storeRetriesOnceWhenTheAckIsLost() {
    val network = SimNetwork()
    val nodeA = node(upeerA, addressA, network)
    val nodeB = node(upeerB, addressB, network)
    nodeA.addContact(contact(upeerB, addressB))
    network.dropStoreAcks.add(addressB)
    val started = System.currentTimeMillis()

    val deliveries = nodeA.storeValue("44".repeat(20), "value")

    assertEquals(0, deliveries)
    assertEquals(2, network.storeCount(addressB))
    assertTrue(System.currentTimeMillis() - started < 2_000)
  }

  private fun node(upeerId: String, address: String, network: SimNetwork): KademliaNode {
    val node = KademliaNode(
      upeerId,
      { targetAddress, message -> network.send(upeerId, address, targetAddress, message) },
      clock,
      queryTimeoutMillis = 40,
    )
    network.register(address, node)
    return node
  }

  private fun contact(upeerId: String, address: String): KademliaContact =
    KademliaContact(KademliaId.fromUpeerId(upeerId), upeerId, address, "pk-${upeerId.take(4)}", now)

  private fun deviceBlock(deviceId: String, dhtSeq: Long): JSONObject =
    JSONObject().put("deviceId", deviceId).put("dhtSeq", dhtSeq).put("address", "200::1")

  private class SimNetwork {
    private val nodes = mutableMapOf<String, KademliaNode>()
    private val storeCounts = mutableMapOf<String, Int>()
    val dropStoreAcks: MutableSet<String> = mutableSetOf()

    fun register(address: String, node: KademliaNode) {
      nodes[address] = node
    }

    fun storeCount(address: String): Int = storeCounts[address] ?: 0

    fun send(sourceUpeerId: String, sourceAddress: String, targetAddress: String, message: DhtMessage) {
      val target = nodes[targetAddress] ?: return
      if (message is DhtStore) {
        storeCounts[targetAddress] = storeCount(targetAddress) + 1
      }
      val response = target.handleIncoming(sourceUpeerId, message, sourceAddress)
      if (response != null && !(message is DhtStore && targetAddress in dropStoreAcks)) {
        nodes[sourceAddress]?.handleIncoming(target.upeerId, response, targetAddress)
      }
    }
  }
}
