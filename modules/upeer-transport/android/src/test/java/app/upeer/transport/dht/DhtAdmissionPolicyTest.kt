package app.upeer.transport.dht

import app.upeer.transport.InboundRateLimiter
import app.upeer.transport.dht.kademlia.DhtPendingQueries
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.HexFormat

class DhtAdmissionPolicyTest {
  private val fixture = JSONObject(fixtureFile().readText(Charsets.UTF_8))
  private val identity = fixture.getJSONObject("identity")
  private val publicKey = identity.getString("publicKey")
  private val upeerId = identity.getString("upeerId")
  private val address = identity.getString("address")

  @Test
  fun acceptsSignedRequestsFromDesktopVectors() {
    for (name in listOf(
      "DHT_PING",
      "DHT_FIND_NODE",
      "DHT_FIND_VALUE",
      "DHT_STORE",
      "DHT_QUERY",
      "DHT_UPDATE",
      "DHT_EXCHANGE",
    )) {
      val result = policy().admit(vector(name).toString(), address, publicKey, upeerId, ::verifyPacket)
      assertTrue("$name -> $result", result is DhtAdmissionResult.Accepted)
      assertEquals(name, (result as DhtAdmissionResult.Accepted).message.type)
    }
  }

  @Test
  fun rejectsResponsesWithoutPendingQuery() {
    val result = policy().admit(vector("DHT_FOUND_VALUE").toString(), address, publicKey, upeerId, ::verifyPacket)
    assertEquals("missing pending query", rejection(result))
  }

  @Test
  fun rejectsLegacyResponsesWithoutPendingQuery() {
    val result = policy().admit(vector("DHT_RESPONSE").toString(), address, publicKey, upeerId, ::verifyPacket)
    assertEquals("missing pending query", rejection(result))
  }

  @Test
  fun acceptsLegacyResponseWithTargetPending() {
    val pending = DhtPendingQueries { 0L }
    val policy = DhtAdmissionPolicy(InboundRateLimiter { 0L }, pending)
    val packet = vector("DHT_RESPONSE")
    assertTrue(policy.registerQuery(packet.getString("targetId"), "DHT_QUERY", address))
    val result = policy.admit(packet.toString(), address, publicKey, upeerId, ::verifyPacket)
    assertTrue("$result", result is DhtAdmissionResult.Accepted)
  }

  @Test
  fun acceptsResponsesWithMatchingPendingQuery() {
    val pending = DhtPendingQueries { 0L }
    val policy = DhtAdmissionPolicy(InboundRateLimiter { 0L }, pending)
    val packet = vector("DHT_FOUND_VALUE")
    assertTrue(policy.registerQuery(packet.getString("queryId"), "DHT_FIND_VALUE", address))
    val result = policy.admit(packet.toString(), address, publicKey, upeerId, ::verifyPacket)
    assertTrue("$result", result is DhtAdmissionResult.Accepted)
  }

  @Test
  fun rejectsUnknownTypeAndOversizeBeforeVerification() {
    var verified = 0
    val verifier: (String, String, String) -> JSONObject = { _, _, _ ->
      verified += 1
      JSONObject().put("senderUpeerId", upeerId)
    }
    val policy = policy()
    assertEquals(
      "unsupported packet type",
      rejection(policy.admit("""{"type":"DHT_NOPE"}""", address, publicKey, upeerId, verifier)),
    )
    assertEquals(
      "packet too large",
      rejection(policy.admit("x".repeat(DhtLimits.MAX_JSON_CHARS + 1), address, publicKey, upeerId, verifier)),
    )
    assertEquals(0, verified)
  }

  @Test
  fun rejectsSenderUpeerIdMismatch() {
    val verifier: (String, String, String) -> JSONObject = { _, _, _ ->
      JSONObject().put("senderUpeerId", upeerId)
    }
    val result = policy().admit(vector("DHT_PING").toString(), address, publicKey, "a".repeat(64), verifier)
    assertEquals("senderUpeerId mismatch", rejection(result))
  }

  @Test
  fun rateLimitsBySourceAndIdentity() {
    val bySource = policy()
    repeat(30) { index ->
      val result = bySource.admit(findNode(index), "200::1", publicKey, upeerId, fakeVerifier(upeerId))
      assertTrue("$index -> $result", result is DhtAdmissionResult.Accepted)
    }
    assertEquals(
      "rate limited by source",
      rejection(bySource.admit(findNode(31), "200::1", publicKey, upeerId, fakeVerifier(upeerId))),
    )

    val byIdentity = policy()
    repeat(30) { index ->
      val result = byIdentity.admit(findNode(index), "200::${index + 1}", publicKey, upeerId, fakeVerifier(upeerId))
      assertTrue("$index -> $result", result is DhtAdmissionResult.Accepted)
    }
    assertEquals(
      "rate limited by identity",
      rejection(byIdentity.admit(findNode(31), "200::31", publicKey, upeerId, fakeVerifier(upeerId))),
    )
  }

  private fun policy(): DhtAdmissionPolicy = DhtAdmissionPolicy(InboundRateLimiter { 0L }, DhtPendingQueries { 0L })

  private fun findNode(sequence: Int): String = JSONObject()
    .put("type", "DHT_FIND_NODE")
    .put("queryId", "query-$sequence")
    .put("targetId", "a".repeat(40))
    .toString()

  private fun fakeVerifier(sender: String): (String, String, String) -> JSONObject = { _, _, _ ->
    JSONObject().put("senderUpeerId", sender)
  }

  private fun rejection(result: DhtAdmissionResult): String =
    (result as? DhtAdmissionResult.Rejected)?.reason ?: error("expected rejection, got $result")

  private fun verifyPacket(payload: String, key: String, expected: String): JSONObject =
    DhtPacketCodec.verifyWith(payload, key, expected, ::verify)

  private fun verify(payload: String, signatureHex: String, keyHex: String): Boolean {
    val key = KeyFactory.getInstance("Ed25519")
      .generatePublic(X509EncodedKeySpec(ED25519_PUBLIC_PREFIX + HexFormat.of().parseHex(keyHex)))
    val signature = Signature.getInstance("Ed25519")
    signature.initVerify(key)
    signature.update(payload.toByteArray(Charsets.UTF_8))
    return signature.verify(HexFormat.of().parseHex(signatureHex))
  }

  private fun vector(name: String): JSONObject {
    val packets = fixture.getJSONArray("signedPackets")
    for (index in 0 until packets.length()) {
      val vector = packets.getJSONObject(index)
      if (vector.getString("name") == name) return vector.getJSONObject("packet")
    }
    error("vector $name is missing")
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

  private companion object {
    private val ED25519_PUBLIC_PREFIX = byteArrayOf(
      0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
    )
  }
}
