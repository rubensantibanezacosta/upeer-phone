package app.upeer.transport.dht

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DhtUpdateDispatcherTest {
  private val peer = "ab".repeat(32)
  private val other = "bc".repeat(32)

  @Test
  fun sendsOncePerSequenceAndRetriesWhenAddressesChange() {
    val sent = mutableListOf<Pair<String, DhtUpdate>>()
    val dispatcher = DhtUpdateDispatcher(
      { listOf(DhtPeerEndpoint(peer, listOf("200::2", "200::3"))) },
      { address, message -> sent += address to message as DhtUpdate },
    )

    assertEquals(2, dispatcher.dispatch(block(1, "200::1")))
    assertEquals(0, dispatcher.dispatch(block(1, "200::1")))
    assertEquals(2, dispatcher.dispatch(block(2, "200::1")))
    assertEquals(0, dispatcher.dispatch(block(1, "200::1")))
    assertEquals(2, dispatcher.dispatch(block(2, "200::4")))

    assertTrue(sent.all { it.second.type == "DHT_UPDATE" })
    assertTrue(sent.all { it.second.queryId == null })
  }

  @Test
  fun sendsTheSameSequenceToANewContactWithoutRebroadcastingToTheOldOne() {
    val sent = mutableListOf<String>()
    var targets = listOf(DhtPeerEndpoint(peer, listOf("200::2")))
    val dispatcher = DhtUpdateDispatcher({ targets }, { address, _ -> sent += address })

    assertEquals(1, dispatcher.dispatch(block(4, "200::1")))
    targets = targets + DhtPeerEndpoint(other, listOf("200::5"))
    assertEquals(1, dispatcher.dispatch(block(4, "200::1")))
    assertEquals(listOf("200::2", "200::5"), sent)
  }

  @Test
  fun buildsTargetsFromIntimatePeersAndExtraAddresses() {
    val peers = JSONObject()
      .put(peer, JSONObject().put("publicKey", "11".repeat(32)).put("address", "200::2"))
      .put(other, JSONObject().put("publicKey", "22".repeat(32)).put("address", "200::6"))
      .put("bad", JSONObject().put("publicKey", "33".repeat(32)).put("address", "200::7"))

    val targets = dhtUpdateTargets(peers.toString()) { upeerId ->
      if (upeerId == peer) listOf("200::2", "200::3") else emptyList()
    }

    assertEquals(2, targets.size)
    assertEquals(listOf("200::2", "200::3"), targets.first { it.upeerId == peer }.addresses)
    assertEquals(listOf("200::6"), targets.first { it.upeerId == other }.addresses)
  }

  @Test
  fun ignoresInvalidBlocksAndTargetsWithoutAddresses() {
    val sent = mutableListOf<String>()
    val dispatcher = DhtUpdateDispatcher(
      { listOf(DhtPeerEndpoint(peer, emptyList()), DhtPeerEndpoint(other, listOf("not-an-address"))) },
      { address, _ -> sent += address },
    )

    assertEquals(0, dispatcher.dispatch("{}"))
    assertEquals(0, dispatcher.dispatch(JSONObject().put("address", "200::1").put("dhtSeq", -1).toString()))
    assertEquals(0, dispatcher.dispatch(block(1, "200::1")))
    assertTrue(sent.isEmpty())
  }

  private fun block(dhtSeq: Long, address: String): String = JSONObject()
    .put("address", address)
    .put("addresses", JSONArray().put(address))
    .put("dhtSeq", dhtSeq)
    .put("signature", "aa".repeat(64))
    .toString()
}
