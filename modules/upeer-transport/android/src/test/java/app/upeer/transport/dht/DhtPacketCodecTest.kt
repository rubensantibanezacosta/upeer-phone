package app.upeer.transport.dht

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.HexFormat

class DhtPacketCodecTest {
  private val fixture = JSONObject(fixtureFile().readText(Charsets.UTF_8))
  private val identity = fixture.getJSONObject("identity")
  private val seed = HexFormat.of().parseHex(identity.getString("secretKeySeed"))
  private val publicKeyHex = identity.getString("publicKey")
  private val upeerId = identity.getString("upeerId")
  private val address = identity.getString("address")
  private val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(ED25519_PRIVATE_PREFIX + seed))

  @Test
  fun buildsAndVerifiesSignedPacket() {
    val packet = DhtPacketCodec.buildWithSigner(
      """{"type":"DHT_PING","queryId":"query-1"}""",
      address,
      upeerId,
      ::sign,
    )
    val metadata = DhtPacketCodec.verifyWith(packet, publicKeyHex, upeerId, ::verify)
    assertEquals("DHT_PING", metadata.getString("type"))
    assertEquals(upeerId, metadata.getString("senderUpeerId"))
    assertEquals(address, metadata.getString("senderYggAddress"))
    val decoded = JSONObject(packet)
    assertEquals(upeerId, decoded.getString("senderUpeerId"))
    assertEquals(address, decoded.getString("senderYggAddress"))
    assertTrue(decoded.has("signature"))
  }

  @Test
  fun rejectsAlteredSignatureAndDifferentKey() {
    val packet = JSONObject(
      DhtPacketCodec.buildWithSigner("""{"type":"DHT_PING","queryId":"query-1"}""", address, upeerId, ::sign),
    )
    val altered = JSONObject(packet.toString()).put("signature", "0".repeat(128))
    assertTrue(runCatching { DhtPacketCodec.verifyWith(altered.toString(), publicKeyHex, upeerId, ::verify) }.isFailure)
    val otherKey = otherPublicKeyHex()
    assertTrue(runCatching { DhtPacketCodec.verifyWith(packet.toString(), otherKey, upeerId, ::verify) }.isFailure)
  }

  @Test
  fun rejectsTamperingAndUpeerIdMismatch() {
    val packet = JSONObject(
      DhtPacketCodec.buildWithSigner("""{"type":"DHT_PING","queryId":"query-1"}""", address, upeerId, ::sign),
    )
    val tampered = JSONObject(packet.toString()).put("queryId", "query-2")
    assertTrue(runCatching { DhtPacketCodec.verifyWith(tampered.toString(), publicKeyHex, upeerId, ::verify) }.isFailure)
    assertTrue(runCatching { DhtPacketCodec.verifyWith(packet.toString(), publicKeyHex, "00", ::verify) }.isFailure)
  }

  @Test
  fun rejectsReservedAndMalformedPayloads() {
    for (payload in listOf(
      """{"type":"DHT_PING","signature":"00"}""",
      """{"type":"DHT_PING","senderUpeerId":"00"}""",
      """{"type":"DHT_PING","senderYggAddress":"200::1"}""",
      """{"queryId":"query-1"}""",
      """[]""",
      """{"type":"DHT_PING"} false""",
    )) {
      assertTrue(payload, runCatching { DhtPacketCodec.buildWithSigner(payload, address, upeerId, ::sign) }.isFailure)
    }
    assertTrue(runCatching { DhtPacketCodec.buildWithSigner("""{"type":"DHT_PING"}""", "not-an-address", upeerId, ::sign) }.isFailure)
  }

  private fun sign(payload: String): String {
    val signature = Signature.getInstance("Ed25519")
    signature.initSign(privateKey)
    signature.update(payload.toByteArray(Charsets.UTF_8))
    return HexFormat.of().formatHex(signature.sign())
  }

  private fun verify(payload: String, signatureHex: String, keyHex: String): Boolean {
    val key = KeyFactory.getInstance("Ed25519")
      .generatePublic(X509EncodedKeySpec(ED25519_PUBLIC_PREFIX + HexFormat.of().parseHex(keyHex)))
    val signature = Signature.getInstance("Ed25519")
    signature.initVerify(key)
    signature.update(payload.toByteArray(Charsets.UTF_8))
    return signature.verify(HexFormat.of().parseHex(signatureHex))
  }

  private fun otherPublicKeyHex(): String {
    val encoded = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded
    return HexFormat.of().formatHex(encoded.copyOfRange(encoded.size - 32, encoded.size))
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
    private val ED25519_PRIVATE_PREFIX = byteArrayOf(
      0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70,
      0x04, 0x22, 0x04, 0x20,
    )
    private val ED25519_PUBLIC_PREFIX = byteArrayOf(
      0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00,
    )
  }
}
