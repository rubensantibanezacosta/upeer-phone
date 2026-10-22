package app.upeer.transport.dht

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DhtPacketPolicyTest {
  private val fixture = JSONObject(fixtureFile().readText(Charsets.UTF_8))

  @Test
  fun decodesEveryDesktopSignedVector() {
    val packets = fixture.getJSONArray("signedPackets")
    assertTrue(packets.length() >= 12)
    for (index in 0 until packets.length()) {
      val vector = packets.getJSONObject(index)
      val packet = vector.getJSONObject("packet")
      val message = DhtPacketPolicy.decode(packet.toString())
      assertEquals(vector.getString("name"), message.type)
    }
  }

  @Test
  fun rejectsEmptyGiantTruncatedAndNestedPackets() {
    expectReason("empty packet") { DhtPacketPolicy.decode("") }
    expectReason("packet too large") { DhtPacketPolicy.decode("a".repeat(DhtLimits.MAX_JSON_CHARS + 1)) }
    expectReason("invalid JSON") { DhtPacketPolicy.decode("""{"type":}""") }
    val valid = fixture.getJSONArray("signedPackets").getJSONObject(0).getJSONObject("packet").toString()
    expectReason("truncated JSON") { DhtPacketPolicy.decode(valid.dropLast(1)) }
    val nested = buildString {
      append("""{"type":"DHT_STORE","key":""")
      append("\"")
      append("a".repeat(40))
      append("\",\"value\":")
      repeat(DhtLimits.MAX_JSON_DEPTH + 1) { append('[') }
      append("1")
      repeat(DhtLimits.MAX_JSON_DEPTH + 1) { append(']') }
      append("}")
    }
    expectReason("JSON is too deeply nested") { DhtPacketPolicy.decode(nested) }
  }

  @Test
  fun rejectsUnexpectedFieldsAndUnknownTypes() {
    val packet = JSONObject(fixture.getJSONArray("signedPackets").getJSONObject(0).getJSONObject("packet").toString())
    packet.put("injected", true)
    expectReason("unexpected fields: injected") { DhtPacketPolicy.decode(packet.toString()) }
    expectReason("unsupported packet type") {
      DhtPacketPolicy.decode("""{"type":"DHT_UNKNOWN","queryId":"q"}""")
    }
    expectReason("missing packet type") { DhtPacketPolicy.decode("""{"queryId":"q"}""") }
  }

  @Test
  fun enforcesQueryKeyAndTargetLimits() {
    expectReason("invalid queryId") {
      DhtPacketPolicy.decode("""{"type":"DHT_PING","queryId":"${"q".repeat(DhtLimits.MAX_QUERY_ID_LENGTH + 1)}"}""")
    }
    expectReason("invalid key") {
      DhtPacketPolicy.decode("""{"type":"DHT_FIND_VALUE","key":"${"a".repeat(32)}"}""")
    }
    expectReason("invalid targetId") {
      DhtPacketPolicy.decode("""{"type":"DHT_FIND_NODE","targetId":"${"a".repeat(DhtLimits.MAX_NODE_ID_LENGTH + 1)}"}""")
    }
    expectReason("invalid targetId") {
      DhtPacketPolicy.decode("""{"type":"DHT_QUERY","targetId":"short"}""")
    }
  }

  @Test
  fun enforcesNodeValueAndPeerLimits() {
    val node = JSONObject()
      .put("upeerId", "a".repeat(64))
      .put("address", "200::1")
    val nodes = JSONArray()
    repeat(DhtLimits.MAX_NODES + 1) { nodes.put(node) }
    expectReason("too many nodes") {
      DhtPacketPolicy.decode(JSONObject().put("type", "DHT_FOUND_NODES").put("nodes", nodes).toString())
    }
    val peers = JSONArray()
    repeat(DhtLimits.MAX_PEERS + 1) {
      peers.put(JSONObject().put("upeerId", "a".repeat(64)).put("publicKey", "b".repeat(64)))
    }
    expectReason("too many peers") {
      DhtPacketPolicy.decode(JSONObject().put("type", "DHT_EXCHANGE").put("peers", peers).toString())
    }
    expectReason("value too large") {
      DhtPacketPolicy.decode(
        JSONObject()
          .put("type", "DHT_STORE")
          .put("key", "a".repeat(40))
          .put("value", "v".repeat(DhtLimits.MAX_VALUE_CHARS + 1))
          .toString(),
      )
    }
    expectReason("invalid TTL") {
      DhtPacketPolicy.decode(
        JSONObject()
          .put("type", "DHT_STORE")
          .put("key", "a".repeat(40))
          .put("value", "ok")
          .put("ttl", DhtLimits.MAX_STORE_TTL_SECONDS + 1)
          .toString(),
      )
    }
  }

  @Test
  fun enforcesLocationBlockShape() {
    val block = JSONObject()
      .put("address", "200::1")
      .put("dhtSeq", 1)
      .put("signature", "a".repeat(127))
    expectReason("invalid locationBlock.signature") {
      DhtPacketPolicy.decode(JSONObject().put("type", "DHT_UPDATE").put("locationBlock", block).toString())
    }
    block.put("signature", "a".repeat(128)).put("dhtSeq", -1)
    expectReason("invalid locationBlock.dhtSeq") {
      DhtPacketPolicy.decode(JSONObject().put("type", "DHT_UPDATE").put("locationBlock", block).toString())
    }
  }

  private fun expectReason(expected: String, block: () -> Unit) {
    val cause = runCatching(block).exceptionOrNull()
    assertTrue("expected DhtPacketException but got $cause", cause is DhtPacketException)
    assertEquals(expected, (cause as DhtPacketException).reason)
  }

  private fun fixtureFile(): File {
    val candidates = listOf(
      File("../../../../transport-bridge/testdata/dht_vectors.json"),
      File("../../../transport-bridge/testdata/dht_vectors.json"),
      File("../transport-bridge/testdata/dht_vectors.json"),
      File("transport-bridge/testdata/dht_vectors.json"),
    )
    return candidates.firstOrNull(File::exists) ?: error("No se encontró transport-bridge/testdata/dht_vectors.json")
  }
}
