package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.kademlia.KademliaTestFixtures.contact
import app.upeer.transport.dht.kademlia.KademliaTestFixtures.seed
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KademliaBootstrapTest {
  private var now = 1_000L
  private val clock = { now }

  @Test
  fun bootstrapsFromDesktopSeedToTenVerifiedNodes() {
    val desktopSeed = seed(1)
    val peerSeeds = (2..15).map(::seed)
    val network = SimNetwork()
    val desktop = node(desktopSeed, network)
    peerSeeds.forEach { peer -> node(peer, network) }
    peerSeeds.forEach { peer -> desktop.routingTable.addContact(contact(peer)) }

    val mobileSeed = seed(99)
    val mobile = node(mobileSeed, network)
    val loader = KademliaSeedLoader(mobile.upeerId, staticSeeds = { listOf(desktopSeed) })
    val bootstrap = KademliaBootstrap(
      mobile,
      loader,
      clock = clock,
      minNodes = 10,
      retryMillis = 30_000L,
      probeTimeoutMillis = 100L,
      lookupRounds = 1,
      probeBatch = 20,
    )

    val result = bootstrap.attempt()
    assertTrue(result.ran)
    assertTrue(result.bootstrapped)
    assertTrue(result.verifiedContacts >= 10)
    assertTrue(mobile.routingTable.contactCount() >= 10)
    assertNotNull(mobile.routingTable.findContact(desktopSeed.upeerId))
    assertFalse(bootstrap.attempt().ran)
  }

  @Test
  fun retriesWithBackoffAndResetsOnNetworkChange() {
    val network = SimNetwork()
    val mobile = node(seed(90), network)
    val bootstrap = KademliaBootstrap(
      mobile,
      KademliaSeedLoader(mobile.upeerId),
      clock = clock,
      minNodes = 10,
      retryMillis = 30_000L,
      maxRetryMillis = 120_000L,
      probeTimeoutMillis = 50L,
    )

    val first = bootstrap.attempt()
    assertTrue(first.ran)
    assertFalse(first.bootstrapped)
    assertEquals(30_000L, first.nextAttemptMillis - now)
    now += 10_000L
    assertNull(bootstrap.maintenance())
    assertEquals(1L, bootstrap.stats().failures)

    now += 20_000L
    assertNotNull(bootstrap.maintenance())
    assertEquals(2L, bootstrap.stats().attempts)
    assertEquals(60_000L, bootstrap.stats().nextAttemptMillis - now)

    bootstrap.onNetworkChanged()
    now += 1L
    assertNotNull(bootstrap.maintenance())
    assertEquals(3L, bootstrap.stats().attempts)
    assertEquals(30_000L, bootstrap.stats().nextAttemptMillis - now)
  }

  @Test
  fun publishesWithASingleVerifiedPeer() {
    val desktopSeed = seed(1)
    val network = SimNetwork()
    node(desktopSeed, network)
    val mobile = node(seed(96), network)
    val bootstrap = KademliaBootstrap(
      mobile,
      KademliaSeedLoader(mobile.upeerId, staticSeeds = { listOf(desktopSeed) }),
      clock = clock,
      minNodes = 10,
      retryMillis = 30_000L,
      probeTimeoutMillis = 100L,
      lookupRounds = 1,
      probeBatch = 20,
    )
    assertFalse(bootstrap.readyToPublish())

    val result = bootstrap.attempt()

    assertTrue(result.ran)
    assertFalse(result.bootstrapped)
    assertEquals(1, bootstrap.verifiedContactCount())
    assertTrue(bootstrap.readyToPublish())
  }

  @Test
  fun doesNotPublishWithoutVerifiedPeers() {
    val network = SimNetwork()
    val mobile = node(seed(95), network)
    val bootstrap = KademliaBootstrap(mobile, KademliaSeedLoader(mobile.upeerId), clock = clock)

    bootstrap.attempt()

    assertFalse(bootstrap.readyToPublish())
  }

  @Test
  fun restoresVerifiedContactsAfterRestart() {
    val directory = Files.createTempDirectory("kademlia-bootstrap").toFile()
    try {
      val desktopSeed = seed(1)
      val peerSeeds = (2..15).map(::seed)
      val network = SimNetwork()
      val desktop = node(desktopSeed, network)
      peerSeeds.forEach { peer -> node(peer, network) }
      peerSeeds.forEach { peer -> desktop.routingTable.addContact(contact(peer)) }
      val mobileSeed = seed(98)
      val mobile = node(mobileSeed, network)
      val store = KademliaContactFileStore(File(directory, "contacts.json"), mobile.upeerId)
      val bootstrap = KademliaBootstrap(
        mobile,
        KademliaSeedLoader(mobile.upeerId, staticSeeds = { listOf(desktopSeed) }),
        store,
        clock,
        minNodes = 10,
        probeTimeoutMillis = 100L,
        probeBatch = 20,
      )
      assertTrue(bootstrap.attempt().bootstrapped)

      val restartedSeed = seed(97)
      val restarted = KademliaNode(restartedSeed.upeerId, { _, _ -> }, clock, 50L)
      val restartedBootstrap = KademliaBootstrap(
        restarted,
        KademliaSeedLoader(restarted.upeerId),
        KademliaContactFileStore(File(directory, "contacts.json"), restarted.upeerId),
        clock,
        minNodes = 10,
      )
      assertTrue(restartedBootstrap.isBootstrapped())
      assertTrue(restartedBootstrap.verifiedContactCount() >= 10)
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun doesNotPersistInvalidOrDuplicateContacts() {
    val directory = Files.createTempDirectory("kademlia-contacts").toFile()
    try {
      val valid = (1..5).map(::seed)
      val invalid = seed(6).copy(publicKey = seed(7).publicKey)
      val store = KademliaContactFileStore(File(directory, "contacts.json"), seed(99).upeerId, maxContacts = 2)
      assertTrue(
        store.save(
          listOf(
            contact(valid[0], lastSeen = 1_000L),
            contact(valid[0], lastSeen = 1_500L),
            contact(invalid),
            contact(valid[1], lastSeen = 2_000L),
            contact(valid[2], lastSeen = 3_000L),
            contact(valid[3], lastSeen = 4_000L),
            contact(valid[4], lastSeen = 5_000L),
          ),
        ),
      )
      val restored = store.load()
      assertEquals(2, restored.size)
      assertEquals(valid[4].upeerId, restored[0].upeerId)
      assertEquals(valid[3].upeerId, restored[1].upeerId)
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun overwritesExistingContactFile() {
    val directory = Files.createTempDirectory("kademlia-overwrite").toFile()
    try {
      val store = KademliaContactFileStore(File(directory, "contacts.json"), seed(99).upeerId)
      val first = seed(1)
      val second = seed(2)
      assertTrue(store.save(listOf(contact(first, lastSeen = 1_000L), contact(second, lastSeen = 2_000L))))
      assertEquals(2, store.load().size)
      assertTrue(store.save(listOf(contact(first, lastSeen = 3_000L))))
      val restored = store.load()
      assertEquals(1, restored.size)
      assertEquals(first.upeerId, restored.single().upeerId)
    } finally {
      directory.deleteRecursively()
    }
  }

  private fun node(seed: KademliaSeed, network: SimNetwork): KademliaNode {
    val node = KademliaNode(
      seed.upeerId,
      { address: String, message: DhtMessage -> network.send(seed.upeerId, seed.address, address, message) },
      clock,
      queryTimeoutMillis = 100L,
    )
    network.register(seed.address, node)
    return node
  }
}
