package app.upeer.transport

import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEnrollmentTest {
  private val account = "ab".repeat(32)
  private val accountPublicKey = "11".repeat(32)
  private val deviceId = "cd".repeat(32)
  private val deviceSecretKey = "44".repeat(64)
  private val inviteId = "ef".repeat(32)
  private val authorizer = "99".repeat(32)
  private val now = 1_800_000_000_000L
  private val inviteJSON = JSONObject()
    .put(
      "offer",
      JSONObject()
        .put("version", 1)
        .put("accountUpeerId", account)
        .put("accountPublicKey", accountPublicKey)
        .put("authorizerDeviceId", authorizer)
        .put("inviteId", inviteId)
        .put("expiresAt", now + 600_000),
    )
    .put("secret", "55".repeat(32))
    .toString()

  @Test
  fun beginsAndPersistsThePendingRequest() {
    val persistence = MemoryEnrollmentPersistence()
    val enrollment = enrollment(persistence)

    val result = JSONObject(enrollment.begin("code"))

    assertEquals(account, result.getJSONObject("offer").getString("accountUpeerId"))
    assertEquals("sealed-request", result.getString("request"))
    assertEquals("sealed-request", enrollment.requestJSON())
    assertEquals(1, persistence.saves)
    val status = JSONObject(enrollment.statusJSON())
    assertEquals(account, status.getJSONObject("pending").getString("accountUpeerId"))
    assertEquals(authorizer, status.getJSONObject("pending").getString("authorizerDeviceId"))
    assertFalse(status.has("installed"))
  }

  @Test
  fun restoresThePendingRequestAfterARestart() {
    val persistence = MemoryEnrollmentPersistence()
    enrollment(persistence).begin("code")

    val restarted = enrollment(persistence)

    assertEquals("sealed-request", restarted.requestJSON())
    assertEquals(account, JSONObject(restarted.statusJSON()).getJSONObject("pending").getString("accountUpeerId"))
  }

  @Test
  fun rejectsAnInvitationWithoutAUsableAccount() {
    val enrollment = enrollment(MemoryEnrollmentPersistence(), invite = JSONObject().put("offer", JSONObject()).toString())

    val failure = runCatching { enrollment.begin("code") }.exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
    assertEquals("", enrollment.requestJSON())
  }

  @Test
  fun rejectsAnInvitationTheCodecCannotParse() {
    val enrollment = enrollment(
      MemoryEnrollmentPersistence(),
      parseInvite = { _, _ -> throw IllegalStateException("enrollment invitation has expired") },
    )

    val failure = runCatching { enrollment.begin("expired") }.exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
    assertEquals("La invitación no es válida o ha caducado.", failure?.message)
  }

  @Test
  fun installsTheCertificateAndClearsThePendingRequest() {
    val persistence = MemoryEnrollmentPersistence()
    val enrollment = enrollment(persistence)
    enrollment.begin("code")

    val installed = JSONObject(enrollment.finish("sealed-response"))

    assertEquals(account, installed.getString("accountUpeerId"))
    assertEquals(deviceId, installed.getString("deviceId"))
    assertEquals(1, installed.getInt("epoch"))
    assertEquals("", enrollment.requestJSON())
    val status = JSONObject(enrollment.statusJSON())
    assertFalse(status.has("pending"))
    assertEquals(deviceId, status.getJSONObject("installed").getString("deviceId"))
    assertEquals(2, persistence.saves)
  }

  @Test
  fun keepsTheInstalledCertificateAcrossRestarts() {
    val persistence = MemoryEnrollmentPersistence()
    val enrollment = enrollment(persistence)
    enrollment.begin("code")
    enrollment.finish("sealed-response")

    val restarted = enrollment(persistence)

    assertEquals(deviceId, JSONObject(restarted.statusJSON()).getJSONObject("installed").getString("deviceId"))
  }

  @Test
  fun rejectsACertificateIssuedForAnotherAccount() {
    val enrollment = enrollment(MemoryEnrollmentPersistence(), certificateAccount = "ff".repeat(32))
    enrollment.begin("code")

    val failure = runCatching { enrollment.finish("sealed-response") }.exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
    assertEquals("El certificado pertenece a otra cuenta.", failure?.message)
    assertEquals("sealed-request", enrollment.requestJSON())
  }

  @Test
  fun rejectsACertificateIssuedForAnotherDeviceKey() {
    val enrollment = enrollment(MemoryEnrollmentPersistence(), certificateDeviceId = "ff".repeat(32))
    enrollment.begin("code")

    val failure = runCatching { enrollment.finish("sealed-response") }.exceptionOrNull()

    assertEquals("El certificado no corresponde a la clave de este dispositivo.", failure?.message)
  }

  @Test
  fun rejectsAFinishWithoutAPendingEnrollment() {
    val enrollment = enrollment(MemoryEnrollmentPersistence())

    val failure = runCatching { enrollment.finish("sealed-response") }.exceptionOrNull()

    assertEquals("No hay un emparejamiento en curso.", failure?.message)
  }

  @Test
  fun cancellingDropsThePendingRequestAndKeepsTheInstalledCertificate() {
    val persistence = MemoryEnrollmentPersistence()
    val enrollment = enrollment(persistence)
    enrollment.begin("code")
    enrollment.cancel()
    assertEquals("", enrollment.requestJSON())
    assertFalse(JSONObject(enrollment.statusJSON()).has("pending"))

    enrollment.begin("code")
    enrollment.finish("sealed-response")
    enrollment.cancel()

    assertEquals(deviceId, JSONObject(enrollment.statusJSON()).getJSONObject("installed").getString("deviceId"))
  }

  @Test
  fun discardsAStoredStateWithAnUnknownVersion() {
    val persistence = MemoryEnrollmentPersistence()
    persistence.save(JSONObject().put("version", 99).toString().toByteArray())

    val enrollment = enrollment(persistence)

    assertFalse(JSONObject(enrollment.statusJSON()).has("pending"))
    assertFalse(JSONObject(enrollment.statusJSON()).has("installed"))
    assertNull(persistence.value?.let { JSONObject(String(it)).optJSONObject("pending") })
  }

  private fun enrollment(
    persistence: EnrollmentPersistence,
    invite: String = inviteJSON,
    certificateAccount: String = account,
    certificateDeviceId: String = deviceId,
    derivedDeviceId: String = deviceId,
    parseInvite: (String, Long) -> String = { _, _ -> invite },
  ) = DeviceEnrollment(
    persistence = persistence,
    nowMillis = { now },
    newDeviceSecretKey = { DhtLocation.decodeHex(deviceSecretKey, 64)!! },
    devicePublicKeyHex = { "77".repeat(32) },
    deriveDeviceId = { derivedDeviceId },
    parseInvite = parseInvite,
    buildRequest = { _, _, _, _ -> "sealed-request" },
    openCertificate = { _, sealed, accountUpeerId, _ ->
      require(sealed == "sealed-response") { "unexpected sealed response" }
      require(accountUpeerId == account) { "unexpected account" }
      testCertificate(certificateAccount, certificateDeviceId, now, now + NINETY_DAYS)
    },
  )

  private class MemoryEnrollmentPersistence : EnrollmentPersistence {
    var value: ByteArray? = null
      private set
    var saves = 0
      private set

    override fun load(): ByteArray? = value?.copyOf()

    override fun save(value: ByteArray) {
      this.value = value.copyOf()
      saves += 1
    }

    override fun clear() {
      value = null
    }
  }

  private companion object {
    private const val NINETY_DAYS = 90L * 24L * 60L * 60L * 1000L
  }
}
