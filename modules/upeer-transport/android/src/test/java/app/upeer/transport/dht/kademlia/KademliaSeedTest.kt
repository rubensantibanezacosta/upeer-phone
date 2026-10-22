package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.kademlia.KademliaTestFixtures.contact
import app.upeer.transport.dht.kademlia.KademliaTestFixtures.dnsRecord
import app.upeer.transport.dht.kademlia.KademliaTestFixtures.jsonSeed
import app.upeer.transport.dht.kademlia.KademliaTestFixtures.seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KademliaSeedTest {
  private val self = seed(99)

  @Test
  fun validatesIdentityAddressAndPublicKey() {
    val valid = seed(1)
    assertNotNull(KademliaSeedValidator.normalize(valid, self.upeerId))
    assertNull(KademliaSeedValidator.normalize(valid.copy(upeerId = seed(2).upeerId), self.upeerId))
    assertNull(KademliaSeedValidator.normalize(valid.copy(address = "not-an-address"), self.upeerId))
    assertNull(KademliaSeedValidator.normalize(valid.copy(address = "200::1 extra"), self.upeerId))
    assertNull(KademliaSeedValidator.normalize(valid.copy(publicKey = seed(2).publicKey), self.upeerId))
    assertNull(KademliaSeedValidator.normalize(valid, valid.upeerId))
  }

  @Test
  fun parsesDnsAndLocalFileSeeds() {
    val first = seed(1)
    val second = seed(2)
    val dns = KademliaSeedParser.parseDns(listOf(listOf(dnsRecord(first), "invalid;entry")))
    assertEquals(listOf(first), dns.map { KademliaSeedValidator.normalize(it, self.upeerId) })
    val file = KademliaSeedParser.parseFile("[${jsonSeed(second)}]")
    assertEquals(second.upeerId, KademliaSeedValidator.normalize(file.single(), self.upeerId)?.upeerId)
  }

  @Test
  fun loaderMergesSourcesAndRejectsInvalidEntries() {
    val existing = seed(1)
    val static = seed(2)
    val dns = seed(3)
    val file = seed(4)
    val invalid = seed(5).copy(publicKey = seed(6).publicKey)
    val loader = KademliaSeedLoader(
      self.upeerId,
      existingContacts = { listOf(contact(existing)) },
      readFile = { "[${jsonSeed(file)},${jsonSeed(existing)}]" },
      dnsResolver = { domain ->
        assertEquals(KademliaSeedParser.DNS_DOMAIN, domain)
        listOf(listOf(dnsRecord(dns), dnsRecord(invalid)))
      },
      staticSeeds = { listOf(static) },
    )
    val result = loader.load()
    assertEquals(listOf(existing, static, dns, file).map { it.upeerId }, result.seeds.map { it.upeerId })
    assertEquals(1, result.rejected)
    assertEquals(1, result.duplicates)
    assertEquals(0, result.sourceFailures)
  }

  @Test
  fun loaderBoundsSeedsAndCountsSourceFailures() {
    val seeds = (1..5).map(::seed)
    val loader = KademliaSeedLoader(
      self.upeerId,
      readFile = { throw IllegalStateException("disk unavailable") },
      dnsResolver = { throw IllegalStateException("dns unavailable") },
      staticSeeds = { seeds },
      maxSeeds = 2,
    )
    val result = loader.load()
    assertEquals(2, result.seeds.size)
    assertEquals(2, result.sourceFailures)
  }

  @Test
  fun parsesRawDnsTxtAnswer() {
    val packet = dnsPacket(
      listOf(
        listOf("upeerId=abc", "address=200::1"),
        listOf("publicKey=def"),
      ),
    )
    assertEquals(
      listOf(listOf("upeerId=abc", "address=200::1"), listOf("publicKey=def")),
      KademliaDnsTxtParser.parse(packet),
    )
    assertTrue(KademliaDnsTxtParser.parse(ByteArray(3)).isEmpty())
  }

  private fun dnsPacket(records: List<List<String>>): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    fun u16(value: Int) {
      output.write((value shr 8) and 0xff)
      output.write(value and 0xff)
    }
    u16(0x1234)
    u16(0x8180)
    u16(1)
    u16(records.size)
    u16(0)
    u16(0)
    listOf("dht-seeds", "upeer", "chat").forEach { label ->
      output.write(label.length)
      output.write(label.toByteArray(Charsets.UTF_8))
    }
    output.write(0)
    u16(16)
    u16(1)
    records.forEach { chunks ->
      u16(0xc00c)
      u16(16)
      u16(1)
      output.write(byteArrayOf(0, 0, 1, 44))
      val data = java.io.ByteArrayOutputStream()
      chunks.forEach { chunk ->
        data.write(chunk.length)
        data.write(chunk.toByteArray(Charsets.UTF_8))
      }
      u16(data.size())
      output.write(data.toByteArray())
    }
    return output.toByteArray()
  }
}
