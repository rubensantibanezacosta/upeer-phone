package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSelfAuthTest {
  private val self = "aa".repeat(32)
  private val device = "dd".repeat(32)
  private val deviceKey = "bb".repeat(32)
  private val now = 1_700_000_000_000L

  private fun certificate(account: String = self, deviceId: String = device, publicKey: String = deviceKey) =
    JSONObject()
      .put("accountUpeerId", account)
      .put("deviceId", deviceId)
      .put("devicePublicKey", publicKey)
      .put("issuedAt", now - 1_000L)

  private fun packet(
    sender: String = self,
    senderDeviceId: String = device,
    cert: JSONObject? = certificate(),
    signature: String = "cc".repeat(64),
  ) = JSONObject()
    .put("type", SyncPackets.HELLO)
    .put("senderUpeerId", sender)
    .put("senderDeviceId", senderDeviceId)
    .apply { if (cert != null) put("senderDeviceCertificate", cert) }
    .put("signature", signature)

  private fun authenticate(
    value: JSONObject,
    certificateValid: Boolean = true,
    revoked: Boolean = false,
  ) = SyncSelfAuth.authenticate(value, self, now, { _, _, _ -> certificateValid }, { revoked })

  @Test
  fun `a well formed own-device packet is accepted`() {
    val result = authenticate(packet())

    assertTrue(result is SyncSelfAuthResult.Accepted)
    val identity = (result as SyncSelfAuthResult.Accepted).identity
    assertEquals(device, identity.deviceId)
    assertEquals(deviceKey, identity.devicePublicKey)
  }

  @Test
  fun `packets from another account are rejected`() {
    val result = authenticate(packet(sender = "ee".repeat(32)))

    assertEquals("remitente que no es la propia cuenta", (result as SyncSelfAuthResult.Rejected).reason)
  }

  @Test
  fun `packets without a certificate are rejected`() {
    val result = authenticate(packet(cert = null))

    assertEquals("certificado ausente", (result as SyncSelfAuthResult.Rejected).reason)
  }

  @Test
  fun `a certificate from another account is rejected`() {
    val result = authenticate(packet(cert = certificate(account = "ee".repeat(32))))

    assertEquals("certificado de otra cuenta", (result as SyncSelfAuthResult.Rejected).reason)
  }

  @Test
  fun `a certificate for another device is rejected`() {
    val result = authenticate(packet(cert = certificate(deviceId = "ff".repeat(32))))

    assertEquals("dispositivo que no coincide con el certificado", (result as SyncSelfAuthResult.Rejected).reason)
  }

  @Test
  fun `an unverifiable certificate is rejected`() {
    val result = authenticate(packet(), certificateValid = false)

    assertEquals("certificado no verificable", (result as SyncSelfAuthResult.Rejected).reason)
  }

  @Test
  fun `a revoked device is rejected`() {
    val result = authenticate(packet(), revoked = true)

    assertEquals("dispositivo revocado", (result as SyncSelfAuthResult.Rejected).reason)
  }

  @Test
  fun `a missing or short signature is rejected`() {
    assertEquals("firma ausente", (authenticate(packet(signature = "")) as SyncSelfAuthResult.Rejected).reason)
    assertEquals("firma ausente", (authenticate(packet(signature = "cc")) as SyncSelfAuthResult.Rejected).reason)
  }

  @Test
  fun `a malformed device id is rejected`() {
    val result = authenticate(packet(senderDeviceId = "zz"))

    assertEquals("dispositivo emisor inválido", (result as SyncSelfAuthResult.Rejected).reason)
  }

  @Test
  fun `an invalid own identity is rejected`() {
    val result = SyncSelfAuth.authenticate(packet(), "aa", now, { _, _, _ -> true }, { false })

    assertEquals("identidad propia inválida", (result as SyncSelfAuthResult.Rejected).reason)
  }

  @Test
  fun `the certificate is verified at a reference time that is never in the future`() {
    var seen = 0L
    SyncSelfAuth.authenticate(packet(), self, now, { _, _, reference -> seen = reference; true }, { false })

    assertEquals(now, seen)
  }

  @Test
  fun `a throwing certificate verifier rejects instead of propagating`() {
    val result = SyncSelfAuth.authenticate(
      packet(),
      self,
      now,
      { _, _, _ -> throw IllegalStateException("bridge unavailable") },
      { false },
    )

    assertEquals("certificado no verificable", (result as SyncSelfAuthResult.Rejected).reason)
  }
}
