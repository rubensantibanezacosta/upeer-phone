package app.upeer.transport.dht

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.HexFormat

class DhtCanonicalVectorTest {
  private val fixture = JSONObject(fixtureFile().readText(Charsets.UTF_8))
  private val identity = fixture.getJSONObject("identity")
  private val seed = HexFormat.of().parseHex(identity.getString("secretKeySeed"))
  private val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(ED25519_PRIVATE_PREFIX + seed))
  private val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(ED25519_PUBLIC_PREFIX + HexFormat.of().parseHex(identity.getString("publicKey"))))

  @Test
  fun canonicalVectorsMatchDesktopBytesAndSignatures() {
    val vectors = fixture.getJSONArray("canonicalVectors")
    for (index in 0 until vectors.length()) {
      val vector = vectors.getJSONObject(index)
      val canonical = DhtCanonicalJson.canonical(vector.get("payload"))
      assertEquals(vector.getString("canonical"), canonical)
      assertEquals(vector.getString("signature"), sign(canonical))
      assertTrue(verify(canonical, vector.getString("signature")))
    }
  }

  @Test
  fun canonicalKeyOrderUsesUtf16CodeUnits() {
    val vectors = fixture.getJSONArray("canonicalVectors")
    val vector = (0 until vectors.length()).map(vectors::getJSONObject).first { it.getString("name") == "utf16-key-order" }
    assertEquals("{\"a\":3,\"z\":5,\"á\":4,\"😀\":1,\"\uE000\":2}", vector.getString("canonical"))
    assertEquals(vector.getString("canonical"), DhtCanonicalJson.canonical(vector.get("payload")))
  }

  @Test
  fun signedDhtPacketsMatchDesktopCanonicalPayload() {
    val packets = fixture.getJSONArray("signedPackets")
    for (index in 0 until packets.length()) {
      val vector = packets.getJSONObject(index)
      val packet = JSONObject(vector.getJSONObject("packet").toString())
      packet.remove("signature")
      packet.remove("contactCache")
      packet.remove("renewalToken")
      val canonical = DhtCanonicalJson.canonical(packet)
      assertEquals(vector.getString("canonical"), canonical)
      assertEquals(vector.getString("signature"), sign(canonical))
      assertTrue(verify(canonical, vector.getString("signature")))
      assertEquals(identity.getString("upeerId"), packet.getString("senderUpeerId"))
      assertEquals(identity.getString("address"), packet.getString("senderYggAddress"))
    }
  }

  @Test
  fun locationBlocksMatchDesktopCanonicalPayload() {
    val blocks = fixture.getJSONArray("locationBlocks")
    for (index in 0 until blocks.length()) {
      val vector = blocks.getJSONObject(index)
      val canonical = DhtCanonicalJson.canonical(vector.get("signedData"))
      assertEquals(vector.getString("canonical"), canonical)
      assertEquals(vector.getString("signature"), sign(canonical))
      assertTrue(verify(canonical, vector.getString("signature")))
    }
  }

  @Test
  fun rejectsFloatsAndTrailingJson() {
    val unsupported = runCatching { DhtCanonicalJson.stringify("{\"value\":1.5}") }.isFailure
    val trailing = runCatching { DhtCanonicalJson.stringify("{\"value\":1} false") }.isFailure
    assertTrue(unsupported)
    assertTrue(trailing)
  }

  private fun sign(payload: String): String {
    val signature = Signature.getInstance("Ed25519")
    signature.initSign(privateKey)
    signature.update(payload.toByteArray(Charsets.UTF_8))
    return HexFormat.of().formatHex(signature.sign())
  }

  private fun verify(payload: String, signatureHex: String): Boolean {
    val signature = Signature.getInstance("Ed25519")
    signature.initVerify(publicKey)
    signature.update(payload.toByteArray(Charsets.UTF_8))
    return signature.verify(HexFormat.of().parseHex(signatureHex))
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
    val ED25519_PRIVATE_PREFIX = HexFormat.of().parseHex("302e020100300506032b657004220420")
    val ED25519_PUBLIC_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100")
  }
}
