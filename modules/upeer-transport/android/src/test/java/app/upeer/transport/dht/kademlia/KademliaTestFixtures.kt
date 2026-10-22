package app.upeer.transport.dht.kademlia

import app.upeer.transport.crypto.Blake2b
import app.upeer.transport.dht.DhtMessage

internal object KademliaTestFixtures {
  fun seed(index: Int): KademliaSeed {
    val publicKey = ByteArray(32) { offset -> ((index + 1) * 37 + offset * 11).toByte() }
    val upeerId = hex(Blake2b.hash256(publicKey))
    return KademliaSeed(upeerId, "200::${(index + 1).toString(16)}", hex(publicKey))
  }

  fun contact(seed: KademliaSeed, lastSeen: Long = 1_000L): KademliaContact =
    KademliaContact(KademliaId.fromUpeerId(seed.upeerId), seed.upeerId, seed.address, seed.publicKey, lastSeen)

  fun hex(bytes: ByteArray): String =
    bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

  fun jsonSeed(seed: KademliaSeed): String =
    """{"upeerId":"${seed.upeerId}","address":"${seed.address}","publicKey":"${seed.publicKey}"}"""

  fun dnsRecord(seed: KademliaSeed): String =
    "upeerId=${seed.upeerId};address=${seed.address};publicKey=${seed.publicKey}"
}

internal class SimNetwork {
  private val nodes = mutableMapOf<String, KademliaNode>()

  fun register(address: String, node: KademliaNode) {
    nodes[address] = node
  }

  fun node(address: String): KademliaNode? = nodes[address]

  fun send(sourceUpeerId: String, sourceAddress: String, targetAddress: String, message: DhtMessage) {
    val target = nodes[targetAddress] ?: return
    val response = target.handleIncoming(sourceUpeerId, message, sourceAddress)
    if (response != null) {
      nodes[sourceAddress]?.handleIncoming(target.upeerId, response, targetAddress)
    }
  }
}
