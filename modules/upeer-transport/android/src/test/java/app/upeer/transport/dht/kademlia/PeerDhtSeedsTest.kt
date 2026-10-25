package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.kademlia.KademliaTestFixtures.seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerDhtSeedsTest {
  @Test
  fun readsAddressAndPublicKeyFromPeers() {
    val peer = seed(3)
    val peers = """{"${peer.upeerId}":{"address":"${peer.address}","publicKey":"${peer.publicKey}"}}"""

    val seeds = PeerDhtSeeds.fromPeers(peers, seed(9).upeerId)

    assertEquals(1, seeds.size)
    assertEquals(peer.upeerId, seeds[0].upeerId)
    assertEquals(peer.address, seeds[0].address)
  }

  @Test
  fun dropsPeersWithoutAddressOrPublicKey() {
    val peer = seed(4)
    val peers = """
      {"${peer.upeerId}":{"address":""},
       "${seed(5).upeerId}":{"address":"200::6"}}
    """.trimIndent()

    assertTrue(PeerDhtSeeds.fromPeers(peers, seed(9).upeerId).isEmpty())
  }

  @Test
  fun dropsSelfAndKeysThatDoNotDeriveTheId() {
    val self = seed(6)
    val impostor = seed(7)
    val peers = """
      {"${self.upeerId}":{"address":"${self.address}","publicKey":"${self.publicKey}"},
       "${impostor.upeerId}":{"address":"${impostor.address}","publicKey":"${seed(8).publicKey}"}}
    """.trimIndent()

    assertTrue(PeerDhtSeeds.fromPeers(peers, self.upeerId).isEmpty())
  }

  @Test
  fun toleratesMissingOrBrokenJSON() {
    assertTrue(PeerDhtSeeds.fromPeers(null, seed(1).upeerId).isEmpty())
    assertTrue(PeerDhtSeeds.fromPeers("", seed(1).upeerId).isEmpty())
    assertTrue(PeerDhtSeeds.fromPeers("no-json", seed(1).upeerId).isEmpty())
    assertTrue(PeerDhtSeeds.fromPeers("""{"abc":5}""", seed(1).upeerId).isEmpty())
  }

  @Test
  fun keepsEveryValidPeerAndIgnoresUnknownFields() {
    val first = seed(10)
    val second = seed(11)
    val peers = """
      {"${first.upeerId}":{"address":"${first.address}","publicKey":"${first.publicKey}","lastSeen":5},
       "${second.upeerId}":{"address":"${second.address}","publicKey":"${second.publicKey}","deviceId":"x"}}
    """.trimIndent()

    val seeds = PeerDhtSeeds.fromPeers(peers, seed(9).upeerId)

    assertEquals(setOf(first.upeerId, second.upeerId), seeds.map { it.upeerId }.toSet())
  }
}
