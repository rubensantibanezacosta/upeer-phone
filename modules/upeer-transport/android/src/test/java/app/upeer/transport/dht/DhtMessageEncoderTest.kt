package app.upeer.transport.dht

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhtMessageEncoderTest {
  @Test
  fun roundTripsEveryKademliaMessage() {
    val messages = listOf(
      DhtPing("q1", "aa".repeat(20)),
      DhtPong("q2", "bb".repeat(20)),
      DhtFindNode("q3", "cc".repeat(20)),
      DhtFoundNodes("q4", listOf(DhtNode("dd".repeat(16), "200::1", "ee".repeat(32), "ff".repeat(20)))),
      DhtFindValue("q5", "aa".repeat(20)),
      DhtFoundValue("q6", "aa".repeat(20), "value", null, "dd".repeat(16), 123L),
      DhtStore("q7", "aa".repeat(20), "value", "dd".repeat(16), 123L, null),
      DhtStoreAck("q8", "aa".repeat(20)),
    )
    messages.forEach { message ->
      val encoded = DhtMessageEncoder.encode(message)
      val decoded = DhtPacketPolicy.decode(encoded)
      assertEquals(message.type, decoded.type)
      assertEquals(message.queryId, decoded.queryId)
    }
  }

  @Test
  fun encodesFoundNodesAndRejectsInvalidOutgoingValues() {
    val encoded = DhtMessageEncoder.encode(
      DhtFoundNodes(null, listOf(DhtNode("aa".repeat(16), "200::1", null, null))),
    )
    val packet = JSONObject(encoded)
    assertEquals("DHT_FOUND_NODES", packet.getString("type"))
    assertFalse(packet.has("queryId"))
    assertEquals("200::1", packet.getJSONArray("nodes").getJSONObject(0).getString("address"))

    val invalid = runCatching {
      DhtMessageEncoder.encode(DhtStore(null, "aa".repeat(20), "v".repeat(DhtLimits.MAX_VALUE_CHARS + 1), null, null, null))
    }
    assertTrue(invalid.isFailure)
  }
}
