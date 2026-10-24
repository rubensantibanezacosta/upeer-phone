package app.upeer.transport

import app.upeer.transport.crypto.Blake2b
import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMessagingBindingsTest {
  private val account = "aa".repeat(32)
  private val devicePublicKey = "bb".repeat(32)
  private val deviceId = DhtLocation.hex(Blake2b.hash256(DhtLocation.decodeHex(devicePublicKey, 32)!!))

  private fun packet(): JSONObject = JSONObject()
    .put("devicePublicKey", devicePublicKey)
    .put("deviceId", deviceId)
    .put(
      "deviceCertificate",
      JSONObject()
        .put("accountUpeerId", account)
        .put("deviceId", deviceId)
        .put("devicePublicKey", devicePublicKey),
    )
    .put(
      "deviceSignedPreKey",
      JSONObject()
        .put("spkPub", "cc".repeat(32))
        .put("spkSig", "dd".repeat(64))
        .put("spkId", 123L),
    )

  private fun bindings(
    certificateValid: Boolean = true,
    signatureValid: Boolean = true,
    revoked: Set<String> = emptySet(),
  ) = DeviceMessagingBindings(
    nowMillis = { 1_000L },
    verifyCertificate = { _, _, _ -> certificateValid },
    verifySignedPreKey = { _, _, _ -> signatureValid },
    revokedFor = { revoked },
  )

  @Test
  fun parseAcceptsBoundDeviceFields() {
    val binding = bindings().parse(packet(), account.uppercase())
    assertNotNull(binding)
    assertEquals(devicePublicKey, binding?.devicePublicKey)
    assertEquals(deviceId, binding?.deviceId)
    assertEquals(123L, binding?.signedPreKey?.optLong("spkId"))
  }

  @Test
  fun parseRejectsInvalidCertificateOrSignature() {
    assertNull(bindings(certificateValid = false).parse(packet(), account))
    assertNull(bindings(signatureValid = false).parse(packet(), account))
  }

  @Test
  fun parseRejectsCertificateForAnotherDevice() {
    val mismatched = packet().put(
      "deviceCertificate",
      JSONObject()
        .put("accountUpeerId", account)
        .put("deviceId", "ee".repeat(32))
        .put("devicePublicKey", devicePublicKey),
    )
    assertNull(bindings().parse(mismatched, account))
  }

  @Test
  fun attachCopiesOnlyValidatedFields() {
    val metadata = JSONObject()
      .put("type", "HANDSHAKE_REQ")
      .put("senderUpeerId", account)
      .put("publicKey", "ff".repeat(32))
    assertTrue(bindings().attach(metadata, packet(), account))
    assertEquals(deviceId, metadata.optString("deviceId"))
    assertEquals(devicePublicKey, metadata.optString("devicePublicKey"))
    assertEquals(123L, metadata.optJSONObject("deviceSignedPreKey")?.optLong("spkId"))
    assertFalse(metadata.optJSONObject("deviceSignedPreKey")?.has("spkSk") ?: true)
  }

  @Test
  fun attachIgnoresPacketsWithoutDeviceFields() {
    val metadata = JSONObject().put("type", "HANDSHAKE_REQ")
    assertFalse(bindings().attach(metadata, JSONObject().put("type", "HANDSHAKE_REQ"), account))
    assertFalse(metadata.has("deviceId"))
  }

  @Test
  fun parseRejectsARevokedDevice() {
    assertNull(bindings(revoked = setOf(deviceId)).parse(packet(), account))
  }

  @Test
  fun parseRejectsARevokedDeviceRegardlessOfCase() {
    assertNull(bindings(revoked = setOf(deviceId.uppercase())).parse(packet(), account))
  }

  @Test
  fun attachRejectsARevokedDevice() {
    val metadata = JSONObject().put("type", "HANDSHAKE_REQ").put("senderUpeerId", account)
    assertFalse(bindings(revoked = setOf(deviceId)).attach(metadata, packet(), account))
    assertFalse(metadata.has("deviceId"))
  }

  @Test
  fun isRevokedNormalizesBothSides() {
    val subject = bindings(revoked = setOf(deviceId.uppercase()))
    assertTrue(subject.isRevoked(account.uppercase(), deviceId))
    assertFalse(subject.isRevoked(account, "ee".repeat(32)))
  }
}
