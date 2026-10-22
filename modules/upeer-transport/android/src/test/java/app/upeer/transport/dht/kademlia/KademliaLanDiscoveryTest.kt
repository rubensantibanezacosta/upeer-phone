package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.DhtPing
import app.upeer.transport.dht.kademlia.KademliaTestFixtures.seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KademliaLanDiscoveryTest {
  private var now = 1_000L
  private val sent = mutableListOf<Pair<String, DhtMessage>>()

  @Test
  fun probesMulticastOnlyWhenPolicyAllowsAndRateLimits() {
    val node = KademliaNode(seed(1).upeerId, { address, message -> sent.add(address to message) })
    var policy = allowedPolicy()
    val discovery = KademliaLanDiscovery(node, { policy }, { now })
    assertTrue(discovery.probe(3))
    assertEquals("ff02::1%3", sent.single().first)
    assertTrue(sent.single().second is DhtPing)
    assertFalse(discovery.probe(3))
    now += KademliaConfig.LAN_PROBE_INTERVAL_MILLIS
    assertTrue(discovery.probe(0))
    assertEquals("ff02::1", sent.last().first)

    policy = policy.copy(batterySaving = true)
    now += KademliaConfig.LAN_PROBE_INTERVAL_MILLIS
    assertFalse(discovery.probe(3))
    assertEquals(2, sent.size)
  }

  @Test
  fun blocksWhenServiceOrPermissionsAreMissing() {
    val node = KademliaNode(seed(2).upeerId, { address, message -> sent.add(address to message) })
    val policies = listOf(
      allowedPolicy().copy(multicastPermitted = false),
      allowedPolicy().copy(wifiMulticastPermission = false),
      allowedPolicy().copy(serviceActive = false),
      allowedPolicy().copy(networkAvailable = false),
    )
    policies.forEach { policy ->
      val discovery = KademliaLanDiscovery(node, { policy }, { now })
      assertFalse(discovery.probe(3))
    }
    assertTrue(sent.isEmpty())
  }

  private fun allowedPolicy(): KademliaLanPolicy =
    KademliaLanPolicy(
      multicastPermitted = true,
      wifiMulticastPermission = true,
      serviceActive = true,
      networkAvailable = true,
      batterySaving = false,
    )
}
