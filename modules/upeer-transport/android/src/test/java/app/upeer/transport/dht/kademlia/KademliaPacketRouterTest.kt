package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.DhtPong
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KademliaPacketRouterTest {
  private val self = "aa".repeat(32)
  private val sender = "bb".repeat(32)
  private val senderKey = "cc".repeat(32)

  @Test
  fun routesAVerifiedPingAndReturnsTheResponseToTheSenderAddress() {
    val sent = mutableListOf<Pair<String, DhtMessage>>()
    val node = KademliaNode(self, send = { address, message -> sent += address to message })
    val router = router(node, sent)

    val result = router.handle(ping(), "200::2")

    assertTrue(result!!.getBoolean("handled"))
    assertTrue(result.getBoolean("responded"))
    assertEquals("200::1", sent.single().first)
    assertTrue(sent.single().second is DhtPong)
  }

  @Test
  fun rejectsAnUnknownSenderBeforeDecodingTheMessage() {
    val sent = mutableListOf<Pair<String, DhtMessage>>()
    val node = KademliaNode(self, send = { address, message -> sent += address to message })
    val router = KademliaPacketRouter(node, { null }, { address, message -> sent += address to message })

    assertThrows(IllegalArgumentException::class.java) { router.handle(ping(), "200::2") }
    assertTrue(sent.isEmpty())
  }

  @Test
  fun rejectsAPublicKeyThatDoesNotBelongToTheSender() {
    val sent = mutableListOf<Pair<String, DhtMessage>>()
    val node = KademliaNode(self, send = { address, message -> sent += address to message })
    val router = KademliaPacketRouter(
      node,
      { senderKey },
      { address, message -> sent += address to message },
      verify = { _, _ -> JSONObject() },
      validatePublicKey = { _, _ -> false },
    )

    assertThrows(IllegalArgumentException::class.java) { router.handle(ping(), "200::2") }
    assertTrue(sent.isEmpty())
  }

  @Test
  fun acceptsALocationStoreOnlyWhenItsDeviceCertificateVerifies() {
    val sent = mutableListOf<Pair<String, DhtMessage>>()
    val node = KademliaNode(self, send = { address, message -> sent += address to message })
    val router = KademliaPacketRouter(
      node,
      { senderKey },
      { address, message -> sent += address to message },
      verify = { _, _ -> JSONObject() },
      registryKeyFor = { null },
      validatePublicKey = { _, _ -> true },
      verifyLocation = { _, _, _, _ -> true },
    )

    router.handle(locationStore(), "200::2")

    assertTrue(node.valueStore.has(sender))
  }

  @Test
  fun rejectsALocationStoreWhoseDeviceCertificateDoesNotVerify() {
    val sent = mutableListOf<Pair<String, DhtMessage>>()
    val node = KademliaNode(self, send = { address, message -> sent += address to message })
    val router = KademliaPacketRouter(
      node,
      { senderKey },
      { address, message -> sent += address to message },
      verify = { _, _ -> JSONObject() },
      registryKeyFor = { null },
      validatePublicKey = { _, _ -> true },
      verifyLocation = { _, _, _, _ -> false },
    )

    assertThrows(IllegalArgumentException::class.java) { router.handle(locationStore(), "200::2") }
    assertTrue(!node.valueStore.has(sender))
  }

  @Test
  fun appliesADhtUpdateWithoutSendingAResponse() {
    val sent = mutableListOf<Pair<String, DhtMessage>>()
    val updates = mutableListOf<Triple<String, String, JSONObject>>()
    val node = KademliaNode(self, send = { address, message -> sent += address to message })
    val router = KademliaPacketRouter(
      node,
      { senderKey },
      { address, message -> sent += address to message },
      verify = { _, _ -> JSONObject() },
      validatePublicKey = { _, _ -> true },
      applyUpdate = { upeerId, publicKey, block, _ ->
        updates += Triple(upeerId, publicKey, block)
        true
      },
    )

    val result = router.handle(locationUpdate(), "200::2")

    assertEquals("DHT_UPDATE", result!!.getString("type"))
    assertTrue(result.getBoolean("applied"))
    assertTrue(!result.getBoolean("responded"))
    assertEquals(1, updates.size)
    assertEquals(sender, updates.single().first)
    assertEquals("200::1", updates.single().third.getString("address"))
    assertTrue(sent.isEmpty())
  }

  private fun locationUpdate(): String {
    val block = JSONObject()
      .put("address", "200::1")
      .put("dhtSeq", 4L)
      .put("expiresAt", System.currentTimeMillis() + 86_400_000L)
      .put("signature", "aa".repeat(64))
    return JSONObject()
      .put("type", "DHT_UPDATE")
      .put("senderUpeerId", sender)
      .put("senderYggAddress", "200::1")
      .put("isInternalSync", false)
      .put("signature", "00".repeat(64))
      .put("locationBlock", block)
      .toString()
  }

  private fun locationStore(): String {
    val block = JSONObject()
      .put("address", "200::1")
      .put("addresses", org.json.JSONArray().put("200::1"))
      .put("dhtSeq", 1L)
      .put("expiresAt", System.currentTimeMillis() + 86_400_000L)
      .put("signature", "aa".repeat(64))
      .put("deviceId", sender)
      .put(
        "deviceCertificate",
        JSONObject().put("accountUpeerId", sender).put("deviceId", sender),
      )
    return JSONObject()
      .put("type", "DHT_STORE")
      .put("senderUpeerId", sender)
      .put("senderYggAddress", "200::1")
      .put("isInternalSync", false)
      .put("signature", "00".repeat(64))
      .put("key", sender)
      .put("value", block)
      .put("publisher", sender)
      .put("timestamp", System.currentTimeMillis())
      .put("ttl", 60L)
      .toString()
  }

  private fun router(node: KademliaNode, sent: MutableList<Pair<String, DhtMessage>>) = KademliaPacketRouter(
    node,
    { senderKey },
    { address, message -> sent += address to message },
    verify = { _, _ -> JSONObject() },
    validatePublicKey = { _, _ -> true },
  )

  private fun ping(): String = JSONObject()
    .put("type", "DHT_PING")
    .put("senderUpeerId", sender)
    .put("senderYggAddress", "200::1")
    .put("isInternalSync", false)
    .put("signature", "00".repeat(64))
    .toString()
}
