package app.upeer.transport.dht.kademlia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KademliaIdTest {
  @Test
  fun derivesDesktopCompatibleIdentifiers() {
    assertEquals(
      "374708fff7719dd5979ec875d56cd2286f6d3cf7",
      KademliaId.fromUpeerId("0".repeat(32)).hex,
    )
    assertEquals(
      "d953164221500855188346874cb48807d413fb4e",
      KademliaId.fromLookupHex("8d2d1c260127c74476b27136c5e38c00").hex,
    )
    assertEquals(KademliaConfig.ID_BYTES, KademliaId.fromUpeerId("1".repeat(32)).hex.length / 2)
    assertTrue(KademliaConfig.BUCKET_COUNT == 160)
    assertTrue(KademliaConfig.K == 20)
    assertTrue(KademliaConfig.ALPHA == 3)
  }

  @Test
  fun computesBucketsAndDistanceOrdering() {
    val zero = KademliaId.fromRawHex("0".repeat(40))
    val firstBit = KademliaId.fromRawHex("8" + "0".repeat(39))
    val lastBit = KademliaId.fromRawHex("0".repeat(39) + "1")
    assertEquals(0, KademliaId.bucketIndex(zero, firstBit))
    assertEquals(159, KademliaId.bucketIndex(zero, lastBit))
    assertEquals(159, KademliaId.bucketIndex(zero, zero))
    assertEquals(1, firstBit.compareDistanceTo(lastBit, zero))
    assertEquals(-1, lastBit.compareDistanceTo(firstBit, zero))
    assertEquals(0, firstBit.compareDistanceTo(firstBit, zero))
  }

  @Test
  fun rejectsMalformedHex() {
    assertFalse(KademliaId.isHex("abc"))
    assertFalse(KademliaId.isHex("zz"))
    assertTrue(runCatching { KademliaId.fromUpeerId("abc") }.isFailure)
    assertTrue(runCatching { KademliaId.fromRawHex("00") }.isFailure)
  }
}
