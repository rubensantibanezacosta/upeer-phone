package app.upeer.transport.dht

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.HexFormat

class DhtLocationTest {
  private val fixture = JSONObject(fixtureFile().readText(Charsets.UTF_8))
  private val identity = fixture.getJSONObject("identity")
  private val seed = HexFormat.of().parseHex(identity.getString("secretKeySeed"))
  private val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(ED25519_PRIVATE_PREFIX + seed))
  private val publicKeyHex = identity.getString("publicKey")
  private val upeerId = identity.getString("upeerId")
  private val address = identity.getString("address")

  @Test
  fun buildsDeterministicSignedBlockWithDeviceMetadata() {
    val deviceMeta = JSONObject("""{"platform":"android","deviceClass":"mobile","capabilities":["dht","multi-device"]}""")
    val block = DhtLocation.buildWithSigner(
      listOf("200::2", address, "200::2", "200::1"),
      7L,
      DEVICE_ID,
      upeerId,
      0L,
      NOW,
      deviceMeta,
      ::sign,
    )
    val json = JSONObject(block)
    assertEquals(address, json.getString("address"))
    assertEquals(listOf(address, "200::1", "200::2"), json.getJSONArray("addresses").toStringList())
    assertEquals(7L, json.getLong("dhtSeq"))
    assertEquals(NOW + DhtLocation.DEFAULT_TTL_MILLIS, json.getLong("expiresAt"))
    assertEquals(DEVICE_ID, json.getString("deviceId"))
    assertEquals(deviceMeta.toString(), json.getJSONObject("deviceMeta").toString())
    val metadata = DhtLocation.verifyWith(block, publicKeyHex, upeerId, NOW + 1, ::verify)
    assertEquals(7L, metadata.getLong("dhtSeq"))
    assertEquals(DEVICE_ID, metadata.getString("deviceId"))
    assertEquals(deviceMeta.toString(), metadata.getJSONObject("deviceMeta").toString())
  }

  @Test
  fun parsesDeviceCertificateAttachedToADeviceSignedBlock() {
    val certificate = JSONObject()
      .put("deviceId", DEVICE_ID)
      .put("accountUpeerId", upeerId)
    val block = JSONObject(
      DhtLocation.buildWithSigner(listOf(address), 4L, DEVICE_ID, upeerId, 60_000L, NOW, null, ::sign),
    ).put("deviceCertificate", certificate)
    val decoded = DhtLocation.validateStructure(block.toString(), publicKeyHex, NOW + 1)
    assertEquals(DEVICE_ID, decoded.deviceId)
    assertNotNull(decoded.deviceCertificate)
    assertEquals(certificate.toString(), decoded.deviceCertificate.toString())
  }

  @Test
  fun rejectsMalformedDeviceCertificate() {
    val block = JSONObject(
      DhtLocation.buildWithSigner(listOf(address), 4L, DEVICE_ID, upeerId, 60_000L, NOW, null, ::sign),
    ).put("deviceCertificate", "not-an-object")
    assertTrue(runCatching { DhtLocation.validateStructure(block.toString(), publicKeyHex, NOW + 1) }.isFailure)
  }

  @Test
  fun clampsTTLToSupportedRange() {
    val defaultBlock = JSONObject(DhtLocation.buildWithSigner(listOf(address), 1L, "", upeerId, 0L, NOW, null, ::sign))
    assertEquals(NOW + DhtLocation.DEFAULT_TTL_MILLIS, defaultBlock.getLong("expiresAt"))
    val maxBlock = JSONObject(DhtLocation.buildWithSigner(listOf(address), 1L, "", upeerId, Long.MAX_VALUE, NOW, null, ::sign))
    assertEquals(NOW + DhtLocation.MAX_TTL_MILLIS, maxBlock.getLong("expiresAt"))
    val explicitBlock = JSONObject(DhtLocation.buildWithSigner(listOf(address), 1L, "", upeerId, 60_000L, NOW, null, ::sign))
    assertEquals(NOW + 60_000L, explicitBlock.getLong("expiresAt"))
  }

  @Test
  fun verifiesDesktopLocationVectors() {
    val blocks = fixture.getJSONArray("locationBlocks")
    for (index in 0 until blocks.length()) {
      val vector = blocks.getJSONObject(index)
      val metadata = DhtLocation.verifyWith(
        vector.getJSONObject("block").toString(),
        publicKeyHex,
        upeerId,
        VECTOR_NOW,
        ::verify,
      )
      assertEquals(vector.getString("name"), vector.getJSONObject("signedData").getLong("dhtSeq"), metadata.getLong("dhtSeq"))
    }
  }

  @Test
  fun ignoresUnsignedUpeerIdButRejectsSignedFieldTampering() {
    val block = JSONObject(
      DhtLocation.buildWithSigner(listOf("200::1", "200::2"), 9L, DEVICE_ID, upeerId, 60_000L, NOW, null, ::sign),
    )
    val ignored = JSONObject(block.toString()).put("upeerId", "00".repeat(32))
    DhtLocation.verifyWith(ignored.toString(), publicKeyHex, upeerId, NOW + 1, ::verify)
    for (tampered in listOf(
      JSONObject(block.toString()).put("dhtSeq", 10L),
      JSONObject(block.toString()).put("expiresAt", block.getLong("expiresAt") + 1),
      JSONObject(block.toString()).put("addresses", org.json.JSONArray(listOf("200::3"))),
      JSONObject(block.toString()).put("deviceId", "ff".repeat(32)),
      JSONObject(block.toString()).put("signature", "0".repeat(128)),
    )) {
      assertTrue(tampered.toString(), runCatching { DhtLocation.verifyWith(tampered.toString(), publicKeyHex, upeerId, NOW + 1, ::verify) }.isFailure)
    }
  }

  @Test
  fun rejectsExpiredWrongKeyAndMalformedBlocks() {
    val block = DhtLocation.buildWithSigner(listOf(address), 3L, "", upeerId, 1_000L, NOW, null, ::sign)
    assertTrue(runCatching { DhtLocation.verifyWith(block, publicKeyHex, upeerId, NOW + 1_000L, ::verify) }.isFailure)
    assertTrue(runCatching { DhtLocation.verifyWith(block, otherPublicKeyHex(), upeerId, NOW + 1, ::verify) }.isFailure)
    assertTrue(runCatching { DhtLocation.verifyWith(block, publicKeyHex, "00".repeat(32), NOW + 1, ::verify) }.isFailure)
    assertTrue(runCatching { DhtLocation.verifyWith("$block false", publicKeyHex, upeerId, NOW + 1, ::verify) }.isFailure)
    for (invalid in listOf(
      "{}",
      """{"address":"200::1","dhtSeq":1,"expiresAt":9999999999999,"signature":"${"0".repeat(128)}"}""",
      """{"addresses":[],"address":"200::1","dhtSeq":1,"expiresAt":9999999999999,"signature":"${"0".repeat(128)}"}""",
      """{"addresses":[1],"address":"200::1","dhtSeq":1,"expiresAt":9999999999999,"signature":"${"0".repeat(128)}"}""",
    )) {
      assertTrue(invalid, runCatching { DhtLocation.verifyWith(invalid, publicKeyHex, upeerId, NOW, ::verify) }.isFailure)
    }
  }

  @Test
  fun rejectsInvalidBuildOptions() {
    for (addresses in listOf(emptyList(), listOf("not-an-address"), listOf("::1"), listOf("fd00::1"))) {
      assertTrue(runCatching { DhtLocation.buildWithSigner(addresses, 1L, "", upeerId, 0L, NOW, null, ::sign) }.isFailure)
    }
    assertTrue(runCatching { DhtLocation.buildWithSigner(listOf(address), -1L, "", upeerId, 0L, NOW, null, ::sign) }.isFailure)
    assertTrue(runCatching { DhtLocation.buildWithSigner(listOf(address), 1L, "", "00", 0L, NOW, null, ::sign) }.isFailure)
    assertTrue(runCatching { DhtLocation.buildWithSigner(listOf(address), 1L, "x".repeat(129), upeerId, 0L, NOW, null, ::sign) }.isFailure)
    assertTrue(runCatching { DhtLocation.buildWithSigner(listOf(address), 1L, "", upeerId, 60_000L, Long.MAX_VALUE - 1, null, ::sign) }.isFailure)
  }

  @Test
  fun normalizesAddressesAndValidatesHex() {
    assertEquals(listOf("200::1", "200::2"), DhtLocation.normalize(listOf(" 200::2 ", "200::1", "200::2")))
    assertTrue(DhtLocation.isHex("ab".repeat(32), 32))
    assertFalse(DhtLocation.isHex("ab".repeat(32) + "00", 32))
    assertTrue(DhtLocation.decodeHex("zz".repeat(32), 32) == null)
    assertEquals("00ff10", DhtLocation.hex(byteArrayOf(0, -1, 16)))
    assertEquals(DhtLocation.DEFAULT_TTL_MILLIS, DhtLocation.clampTTL(-1L))
    assertEquals(DhtLocation.MAX_TTL_MILLIS, DhtLocation.clampTTL(DhtLocation.MAX_TTL_MILLIS + 1))
  }

  private fun org.json.JSONArray.toStringList(): List<String> {
    return (0 until length()).map { getString(it) }
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
    const val NOW = 1_700_000_000_000L
    const val VECTOR_NOW = 1_700_000_000_000L
    const val DEVICE_ID = "9c4a1f0e8d7b6a5c49382716f5e4d3c2b1a09187766554433221100ffeeddccbb"
    val ED25519_PRIVATE_PREFIX = HexFormat.of().parseHex("302e020100300506032b657004220420")
    val ED25519_PUBLIC_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100")
  }
}
