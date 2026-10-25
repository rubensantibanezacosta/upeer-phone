package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InstalledCertificateTest {
  private val account = "aa".repeat(32)
  private val deviceId = "bb".repeat(32)
  private val now = 1_800_000_000_000L

  private fun certificate(
    accountUpeerId: String = account,
    device: String = deviceId,
    expiresAt: Long = now + 86_400_000L,
  ): String = JSONObject()
    .put("version", 1)
    .put("accountUpeerId", accountUpeerId)
    .put("accountPublicKey", "cc".repeat(32))
    .put("deviceId", device)
    .put("devicePublicKey", "dd".repeat(32))
    .put("epoch", 1)
    .put("issuedAt", now - 1_000L)
    .put("expiresAt", expiresAt)
    .toString()

  @Test
  fun acceptsACertificateOfTheSameAccountAndDevice() {
    val check = InstalledCertificate.check(certificate(), account, deviceId, now)

    assertNotNull(check.certificate)
    assertNull(check.failure)
    assertEquals(deviceId, check.certificate!!.getString("deviceId"))
  }

  @Test
  fun acceptsUppercaseCertificateFields() {
    val check = InstalledCertificate.check(
      certificate(accountUpeerId = account.uppercase(), device = deviceId.uppercase()),
      account,
      deviceId,
      now,
    )

    assertNotNull(check.certificate)
  }

  @Test
  fun rejectsACertificateOfAnotherAccount() {
    val check = InstalledCertificate.check(certificate(accountUpeerId = "ff".repeat(32)), account, deviceId, now)

    assertNull(check.certificate)
    assertEquals("pertenece a otra cuenta", check.failure)
  }

  @Test
  fun rejectsACertificateOfAnotherDevice() {
    val check = InstalledCertificate.check(certificate(device = "ff".repeat(32)), account, deviceId, now)

    assertNull(check.certificate)
    assertEquals("no corresponde a la clave del dispositivo", check.failure)
  }

  @Test
  fun rejectsAnExpiredCertificate() {
    val check = InstalledCertificate.check(certificate(expiresAt = now - 1_000L), account, deviceId, now)

    assertNull(check.certificate)
    assertEquals("está caducado", check.failure)
  }

  @Test
  fun rejectsACertificateWithoutExpiry() {
    val check = InstalledCertificate.check(certificate(expiresAt = 0L), account, deviceId, now)

    assertNull(check.certificate)
    assertEquals("no declara caducidad", check.failure)
  }

  @Test
  fun rejectsMalformedJSON() {
    val check = InstalledCertificate.check("no es json", account, deviceId, now)

    assertNull(check.certificate)
    assertEquals("no es JSON válido", check.failure)
  }

  @Test
  fun rejectsACertificateThatExpiresExactlyNow() {
    val check = InstalledCertificate.check(certificate(expiresAt = now), account, deviceId, now)

    assertNull(check.certificate)
    assertEquals("está caducado", check.failure)
  }
}
